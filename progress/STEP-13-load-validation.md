# STEP-13 — Performance & Load Validation

**Status**: ⏳ TODO
**Depends on**: STEP-09 (metrics), STEP-12 (resilience)
**Blocks**: STEP-14

## Goal

Prove the FLOW-07 SLO empirically: **≥ 50 docs/min/instance throughput, queue lag < 1 minute at peak load**. Establish micro-benchmarks for individual pipeline stages so future regressions are caught.

## Files to Create

```
server/services/selection-committee-e2e-tests/load/
├── cv-pipeline.js                     k6 scenario
├── cv-pipeline-utils.js               helpers (RMQ publish, Prom query)
├── fixtures/
│   ├── passports/                     20 synthetic passport images (varied skew, quality)
│   ├── ipns/                          20 synthetic IPN scans
│   └── foreign_passports/             10 ICAO-9303 sample images
└── README.md
```

```
server/services/selection-committee-computer-vision/benches/
├── __init__.py
├── bench_pipeline.py                  pytest-benchmark
└── bench_extractors.py
```

```
.github/workflows/
└── cv-load-test.yml                   nightly @ 02:00 UTC
```

## Implementation Outline

### k6 scenario `cv-pipeline.js`
```js
import { Counter, Trend } from 'k6/metrics';
import http from 'k6/http';

export const options = {
  scenarios: {
    burst: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1m',
      stages: [
        { target: 60, duration: '30s' },
        { target: 60, duration: '5m' },     // sustained 60 rpm to exceed 50/min SLO
        { target: 0,  duration: '30s' },
      ],
      preAllocatedVUs: 10,
      maxVUs: 50,
    },
  },
  thresholds: {
    'iteration_duration': ['p(95)<5000'],
    'cv_queue_lag': ['max<60'],                  // queue depth at scrape time
    'cv_processing_seconds': ['p(95)<5'],
    'http_req_failed': ['rate<0.02'],
  },
};

const queueLag = new Trend('cv_queue_lag');
const successRate = new Counter('cv_success');

export default function () {
  // 1. Pre-stage a fixture in MinIO (on first iter only — use init scenario)
  // 2. POST /api/v1/documents (upload metadata) and confirm
  // 3. Poll /api/v1/documents/{id}/ocr until PARSED, max 60s
  // 4. Record queue depth via Prometheus query http://prometheus:9090/api/v1/query?query=...
}

export function teardown() {
  // Pull final metrics from Prometheus and assert SLO
}
```

### Bench micro-benchmarks (`bench_pipeline.py`)
```python
def test_bench_download(benchmark, minio_client, fixture_passport):
    benchmark(asyncio.run, minio_client.download(fixture_passport.key))
    assert benchmark.stats["mean"] < 0.5      # 500ms

def test_bench_preprocess(benchmark, fixture_image):
    benchmark(preprocessing.run, fixture_image)
    assert benchmark.stats["mean"] < 0.7      # 700ms

def test_bench_ocr(benchmark, ocr_engine, fixture_image):
    benchmark(asyncio.run, ocr_engine.recognize(fixture_image))
    assert benchmark.stats["mean"] < 2.5      # 2.5s

def test_bench_extract(benchmark, ocr_result_passport):
    benchmark(extraction.extract, "passport", ocr_result_passport)
    assert benchmark.stats["mean"] < 0.05     # 50ms
```

### CI workflow `cv-load-test.yml`
```yaml
name: CV Load Test
on:
  schedule: [{ cron: '0 2 * * *' }]      # nightly 02:00 UTC
  workflow_dispatch:
jobs:
  load:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4
      - uses: docker/setup-buildx-action@v3
      - run: cd infra && docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --wait
      - run: docker run --network=host -v $PWD/server/services/selection-committee-e2e-tests/load:/scripts grafana/k6 run /scripts/cv-pipeline.js
      - if: failure()
        run: |
          docker compose -f infra/docker-compose.yml -f infra/docker-compose.services.yml logs cv-service > cv.log
          # Optionally post to Slack/PR
      - uses: actions/upload-artifact@v4
        with:
          name: cv-load-results
          path: |
            cv.log
            k6-results.json
```

## Tests (Acceptance Gates)

- [ ] **k6 thresholds green** (per `options.thresholds` above):
  - `iteration_duration p(95) < 5s`
  - `cv_queue_lag max < 60` messages
  - `cv_processing_seconds p(95) < 5s`
  - `http_req_failed rate < 2%`
- [ ] **Throughput SLO**: total parsed events / test duration ≥ 50/min sustained for 5 minutes.
- [ ] **DLQ depth**: 0 across the test run (no terminal failures on synthetic clean fixtures).
- [ ] **Bench thresholds**: each stage stays under its budget.
- [ ] **Regression gate**: nightly workflow fails if `iteration_duration p(95)` is > 110% of the prior run's value (stored in a separate baseline file under version control).

## Definition of Done

- [ ] k6 scenario runs end-to-end against full docker-compose stack
- [ ] All thresholds met
- [ ] Bench file runs as part of `pytest -m bench`
- [ ] Nightly CI workflow operational
- [ ] One real run's results saved as the baseline (committed to `progress/baselines/cv-load-baseline.json`)
- [ ] `progress/README.md` STEP-13 row marked ✅

## Notes

- **Synthetic fixtures only**: do NOT use real applicant data, ever. The 50 fixture images should be generated programmatically (PIL drawing of fake passport/IPN layouts) — checked into the repo.
- **MinIO pre-staging**: in the k6 init scenario, upload all fixtures to MinIO once. The load run only triggers events; it does NOT re-upload (we're testing the CV pipeline, not S3 throughput).
- **Multi-instance scaling**: MVP is single-instance. If the SLO can't be met, scale horizontally via compose `deploy.replicas: 2` and rerun. RabbitMQ's competing consumers + the in-memory idempotency cache's per-instance scope is acceptable for the throughput target.
- **Realistic sizing**: 50 docs/min ≈ 1 doc per 1.2s. Per-stage budgets (download 0.5s + preprocess 0.7s + ocr 2.5s + extract 0.05s) sum to 3.75s — beyond the budget for serial execution. **Parallelism is required** (prefetch=8 + async pipeline). The 2.5s OCR budget is the bottleneck; tune Paddle batch size if needed.
