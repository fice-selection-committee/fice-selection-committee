# STEP-09 — Observability

**Status**: ✅ DONE
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
- [x] After processing 1 happy event: `GET /metrics` contains `cv_documents_processed_total{status="parsed",...} 1`.
- [x] After 1 failure: `cv_documents_failed_total{reason="...",...} 1`.
- [x] Histogram `cv_processing_seconds` has observations for all 5 stages with `_count > 0`.
- [x] Confidence histogram observed exactly once per parsed event.

### `test_tracing.py`
- [x] Use `opentelemetry.sdk.trace.export.in_memory_span_exporter.InMemorySpanExporter` in tests. Process 1 event. Assert: span tree has root `cv.pipeline` with 5 child spans named per the hierarchy.
- [x] `traceId` from event body → span trace_id. Verify by hex match.
- [x] On terminal failure: pipeline span has `status=ERROR` with `error.type` attribute.

### Manual verification (post-deploy)
- [x] `curl http://localhost:9090/api/v1/query?query=cv_documents_processed_total` returns data after running an event through.
- [x] Grafana CV dashboard renders all panels with non-empty data.
- [x] Zipkin UI shows full trace from documents-service → cv-service → documents-service.

### Dashboard JSON validation
- [x] `jq empty < infra/grafana/dashboards/cv.json` passes
- [x] Dashboard imports cleanly into Grafana on container start (no errors in Grafana log)

## Definition of Done

- [x] All observability files implemented
- [x] All 7 metric names present
- [x] Span tree structured correctly
- [x] Prometheus scrapes successfully
- [x] Grafana dashboard auto-provisioned and renders
- [x] All tests pass
- [x] `progress/README.md` STEP-09 row marked ✅

## Notes

- Reuse existing Grafana dashboard naming/folder conventions in `infra/grafana/dashboards/`.
- The rabbitmq-exporter is already running in compose (via the existing infra setup). Confirm; if not, add it.
- Span attributes follow OTel semantic conventions where possible (`messaging.system=rabbitmq`, `messaging.destination=cv.events`, `messaging.message_id=<delivery_tag>`).

## Regressions Caught

1. **OTel `TracerProvider` once-set guard** — `opentelemetry.trace.set_tracer_provider` enforces a single install per process. Tests need to swap providers between cases (each fixture installs a fresh `InMemorySpanExporter`-backed provider). Standard OTel test escape hatch is to reset `trace._TRACER_PROVIDER_SET_ONCE._done = False`; otherwise the second `setup_tracing(...)` is silently ignored and tests assert against an empty exporter. Documented inside `_force_reset_once_flag` in `cv_service/observability/tracing.py`. The same helper is called at app boot so a re-import in dev (uvicorn `--reload`) does not silently keep the previous Zipkin endpoint.
2. **W3C traceparent vs legacy shim** — STEP-07's publisher emitted a free-form `traceparent: <trace_id>` header (just stuffed the documents-service-assigned trace_id into the header). That is not W3C-conformant: traceparent must be `00-<traceid>-<spanid>-<flags>`. STEP-09 swaps to the OTel propagator, which requires an active span context to inject. Existing `test_traceid_present_in_body_and_headers` updated to (a) wrap the publish in `start_pipeline_span`, (b) call `setup_tracing()` so OTel has a recording provider, and (c) assert the W3C regex format. The JSON body's `traceId` remains the canonical correlation id for non-OTel consumers (logs, audit events).
3. **prom_client metric reset between tests** — `prometheus_client.REGISTRY` is process-global; the FastAPI Instrumentator can only read the default registry. Naive test patterns that unregister + re-register break import-time references in test modules. The pragmatic fix is in-place reset: `Counter.clear()` / `Histogram.clear()` for labeled metrics, `_value.set(0)` for unlabeled. Documented in `reset_metrics()` and used by the `prometheus_registry` autouse fixture.
4. **OcrBreaker state gauge timing** — Initial breaker construction is synchronous and runs before the `OcrEngine` is fully wired in `cv_service.main`. The breaker emits `record_breaker_state("closed")` from `__init__` so the gauge has a value at scrape time even before the first OCR call (otherwise Grafana shows "no data" for a fresh boot, which is indistinguishable from a missing series).
