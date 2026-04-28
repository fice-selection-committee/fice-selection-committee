# STEP-09 — Observability

**Status**: ⏳ TODO
**Depends on**: STEP-08
**Blocks**: STEP-13 (load test reads these metrics)

## Goal

Match the Java services' observability surface: Prometheus metrics + Zipkin distributed traces + structured logs. CV-Service's metric and trace names must integrate cleanly with existing Grafana dashboards (no new naming convention).

## Files to Create

```
server/services/selection-committee-computer-vision/src/cv_service/observability/
├── __init__.py
├── metrics.py            Prometheus counter/histogram registry
├── tracing.py            OpenTelemetry → Zipkin exporter setup
└── logging.py            structlog JSON formatter (matches Java logback JSON)
```

```
tests/observability/
├── __init__.py
├── test_metrics.py
└── test_tracing.py
```

```
infra/
├── prometheus/
│   └── prometheus.yml                    add scrape job for cv-service:8088
└── grafana/dashboards/
    └── cv.json                           CV throughput / latency / confidence / DLQ panels
```

## Implementation Outline

### `metrics.py` — counters & histograms

| Metric | Type | Labels |
|---|---|---|
| `cv_documents_processed_total` | Counter | `status=parsed\|failed`, `document_type` |
| `cv_documents_failed_total` | Counter | `reason`, `document_type` |
| `cv_processing_seconds` | Histogram (buckets: 0.5, 1, 2, 5, 10, 30) | `stage=download\|preprocess\|ocr\|extract\|publish` |
| `cv_ocr_confidence` | Histogram (buckets: 0.0–1.0 by 0.1) | `document_type` |
| `cv_ocr_breaker_state` | Gauge (0=closed, 1=half_open, 2=open) | — |
| `cv_idempotent_skipped_total` | Counter | — |
| `cv_dlq_routed_total` | Counter | `reason=transient\|terminal` |

Provide `@timed_stage("stage_name")` async context manager that auto-observes the histogram.

### `tracing.py`
- OpenTelemetry SDK with Zipkin exporter pointing at `CV_ZIPKIN_ENDPOINT`.
- Resource attributes: `service.name=cv-service`, `service.version=<from pyproject>`.
- Auto-instrument FastAPI (incoming HTTP), aio-pika (manual — wrap publish/consume in spans).
- Span hierarchy:
  ```
  cv.event.consume
    └── cv.pipeline
          ├── cv.download
          ├── cv.preprocess
          ├── cv.ocr
          │     └── cv.ocr.page (per page)
          ├── cv.extract
          └── cv.publish
  ```
- Trace ID propagation: read from `traceparent` AMQP header on consume; inject on publish.

### `logging.py`
- `structlog.configure(processors=[..., structlog.processors.JSONRenderer()])`
- Standard fields per log: `timestamp`, `level`, `message`, `service=cv-service`, `traceId`, `documentId` (when available).
- Format compatible with Loki/ELK that consumes Java logback-json output (same JSON shape).

### Grafana dashboard (`infra/grafana/dashboards/cv.json`)
Panels:
1. **Throughput** — `rate(cv_documents_processed_total[1m]) * 60` (target ≥ 50/min)
2. **Stage latency p50/p95/p99** — `histogram_quantile(...)`
3. **Confidence distribution** — heatmap of `cv_ocr_confidence`
4. **Failure rate** — `rate(cv_documents_failed_total[5m])` per reason
5. **DLQ depth** — `rabbitmq_queue_messages{queue="cv.dlq"}` (existing rabbitmq-exporter)
6. **Breaker state** — `cv_ocr_breaker_state`
7. **Queue lag** — `rabbitmq_queue_messages{queue="cv.document.requested"}` (target < 50 at peak)

### `prometheus.yml` addition
```yaml
- job_name: cv-service
  static_configs:
    - targets: ["cv-service:8088"]
  metrics_path: /metrics
  scrape_interval: 15s
```

## Tests (Acceptance Gates)

### `test_metrics.py`
- [ ] After processing 1 happy event: `GET /metrics` contains `cv_documents_processed_total{status="parsed",...} 1`.
- [ ] After 1 failure: `cv_documents_failed_total{reason="...",...} 1`.
- [ ] Histogram `cv_processing_seconds` has observations for all 5 stages with `_count > 0`.
- [ ] Confidence histogram observed exactly once per parsed event.

### `test_tracing.py`
- [ ] Use `opentelemetry.sdk.trace.export.in_memory_span_exporter.InMemorySpanExporter` in tests. Process 1 event. Assert: span tree has root `cv.pipeline` with 5 child spans named per the hierarchy.
- [ ] `traceId` from event body → span trace_id. Verify by hex match.
- [ ] On terminal failure: pipeline span has `status=ERROR` with `error.type` attribute.

### Manual verification (post-deploy)
- [ ] `curl http://localhost:9090/api/v1/query?query=cv_documents_processed_total` returns data after running an event through.
- [ ] Grafana CV dashboard renders all panels with non-empty data.
- [ ] Zipkin UI shows full trace from documents-service → cv-service → documents-service.

### Dashboard JSON validation
- [ ] `jq empty < infra/grafana/dashboards/cv.json` passes
- [ ] Dashboard imports cleanly into Grafana on container start (no errors in Grafana log)

## Definition of Done

- [ ] All observability files implemented
- [ ] All 7 metric names present
- [ ] Span tree structured correctly
- [ ] Prometheus scrapes successfully
- [ ] Grafana dashboard auto-provisioned and renders
- [ ] All tests pass
- [ ] `progress/README.md` STEP-09 row marked ✅

## Notes

- Reuse existing Grafana dashboard naming/folder conventions in `infra/grafana/dashboards/`.
- The rabbitmq-exporter is already running in compose (via the existing infra setup). Confirm; if not, add it.
- Span attributes follow OTel semantic conventions where possible (`messaging.system=rabbitmq`, `messaging.destination=cv.events`, `messaging.message_id=<delivery_tag>`).
