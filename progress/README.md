# Computer Vision System — Implementation Progress

This folder contains the step-by-step rollout plan for the FICE Computer Vision system (FLOW-07 / UC-02). The master plan lives at `~/.claude/plans/we-need-to-develop-linked-torvalds.md`. Each `STEP-NN-*.md` file in this folder is a self-contained execution document with explicit acceptance tests.

## Stack Decision

- **CV-Service**: Python 3.12 + FastAPI + PaddleOCR + aio-pika + minio-py
- **Java side**: Existing `documents-service` integrates via shared `sc-event-contracts` library
- **Frontend**: Existing `client/web` (Next.js 16) — adds OCR result polling and auto-fill UI

## Architecture (one-line)

```
docs-service ──► RabbitMQ(cv.events) ──► cv-service ──► RabbitMQ(cv.events) ──► docs-service ──► Postgres + Frontend
```

CV-Service is **stateless** and **internal-only** (no gateway route, no Feign endpoint, no DB). Persistence belongs to documents-service.

## Step Dependency Graph

```
STEP-00 (cleanup) ✅ DONE
   │
   ▼
STEP-01 (scaffold) ──► STEP-02 (contracts) ──► STEP-03 (S3) ──► STEP-04 (preprocess) ──► STEP-05 (OCR) ──► STEP-06 (extract)
                                                                                                               │
                                                                            ┌──────────────────────────────────┘
                                                                            ▼
                                                                       STEP-07 (messaging) ──► STEP-08 (orchestrator)
                                                                                                       │
                                          ┌────────────────────────────────────────────────────────────┤
                                          ▼                                                            ▼
                                STEP-09 (observability)                                       STEP-10 (docs-service Java)
                                                                                                       │
                                                                                                       ▼
                                                                                              STEP-11 (frontend)
                                                                                                       │
                                                                                                       ▼
                                                                                              STEP-12 (resilience)
                                                                                                       │
                                                                                                       ▼
                                                                                              STEP-13 (load test)
                                                                                                       │
                                                                                                       ▼
                                                                                              STEP-14 (docs)
```

## Status Tracker

| # | Step | Status |
|---|---|---|
| 00 | Repository cleanup | ✅ done — 123 junk files removed |
| 01 | CV-Service scaffolding (Python + Docker) | ✅ done — FastAPI shell, /health + /metrics, 55.8 MB runtime image, CI workflow added |
| 02 | Event contracts (sc-event-contracts → 1.3.3) | ✅ done — Java records + EventConstants, Pydantic mirrors, RabbitMQ topology (cv.events + cv.dlx + retry queues), libs 1.3.3 published |
| 03 | MinIO download adapter | ✅ done — async streaming adapter (minio-py + asyncio.to_thread), terminal/transient/error-class mapping, 6/6 acceptance gates green via Testcontainers MinIO |
| 04 | Image preprocessing pipeline | ✅ done — loader (MIME-sniff + page cap) + 5-stage pipeline (grayscale, Hough deskew ±15°, NLMD, adaptive threshold, 300-DPI resize), 10/10 acceptance gates green, 155.7 MB runtime image |
| 05 | OCR engine (PaddleOCR) | ✅ done — async PaddleOCR adapter with uk primary + en fallback, asyncio.Lock-serialised, models pre-baked into 563.6 MB runtime image, 7/7 acceptance gates green, paddlepaddle 3.0 + paddleocr 2.10 |
| 06 | Field extractors | ✅ done — passport / IPN / foreign-passport extractors + router, 40/40 acceptance gates green (incl. 20 parametrised IPN cases), pure-Python (no new system deps), 50/50 MRZ year-pivot for DOB+expiry coverage |
| 07 | RabbitMQ consumer & publisher | ✅ done — aio-pika 9.6 consumer + publisher, manual ack, publisher_confirms, LRU idempotency on (documentId, s3Key), task-per-delivery so prefetch caps in-flight, passive topology check fails fast on missing entities, 17/17 acceptance gates green via Testcontainers RabbitMQ |
| 08 | Orchestrator & resilience | ✅ done — `run_pipeline` wires download→preprocess→OCR→extract→publish with per-stage timeouts, async-native `OcrBreaker` (fail_max=5, reset_timeout=30s), TTL-queue retry policy (5s/30s/5m → DLQ on attempt 4), error classification routes to terminal/transient publish_failed or schedule_retry, FastAPI lifespan owns publisher+consumer, 28/28 orchestrator gates green; pybreaker dropped (call_async broken on non-Tornado runtimes); cv.retry.* queues added to verify_topology; CV_MINIO_ACCESS_KEY/SECRET_KEY/SECURE landed in infra/.env.example + compose |
| 09 | Observability | ✅ done — 7 Prometheus metrics (cv_documents_processed_total, cv_documents_failed_total, cv_processing_seconds, cv_ocr_confidence, cv_ocr_breaker_state, cv_idempotent_skipped_total, cv_dlq_routed_total), OTel TracerProvider + Zipkin exporter, W3C traceparent propagation through AMQP headers, structlog JSON with traceId/spanId correlation, Grafana dashboard `sc-cv-service` (8 panels: throughput / cumulative / stage latency p50/p95/p99 / confidence heatmap / failure-rate-by-reason / breaker state / queue depth / DLQ depth), Prometheus scrape job for cv-service:8088, 20/20 observability acceptance gates green, 137 total cv-service tests passing, ruff + mypy --strict clean. Existing publisher test updated for W3C traceparent format (regex `^00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]$`); legacy free-form `traceparent: <trace_id>` shim removed. |
| 10 | Documents-service integration (Java) | ✅ done — V5 Flyway migration (`ocr_results` table, `UNIQUE(document_id)` idempotency anchor), JPA entity + `OcrResultRepository`, `OcrResultService` (UPSERT-keyed `upsertPending` / `markParsed` / `markFailed`), `CvRequestPublisher` (publishes `CvDocumentRequestedEvent` on `cv.events`, inserts `PENDING` row), `CvResultListener` (`@RabbitListener` on `cv.document.results`, routes by AMQP key, malformed JSON logged + dropped), `OcrController` GET `/api/v1/documents/{id}/ocr` with applicant-owner-check + `ResourceNotFoundException`-mapped 404, `OcrResultDto` mirroring `CvDocumentParsedEvent` shape verbatim, `RabbitMQConfig` declares `cv.events` exchange + `cv.document.results` queue + parsed/failed bindings, `DocumentService.confirm` triggers CV publish for `passport`/`ipn`/`foreign_passport` (failures logged-and-skipped — STEP-12 swaps for Resilience4j), sc-libs 1.3.1 → 1.3.3. Tests: 4 unit + 4 listener + 6 service + 3 controller = 17 unit cases passing; 4 + 6 = 10 Testcontainers integration cases (require Docker on host). `./gradlew check` clean (JaCoCo 80%/file enforced; spotless clean). Migration numbered V5 to avoid colliding with the user's pending V4 rejection-flow WIP. |
| 11 | Frontend integration | ⏳ TODO |
| 12 | Boundary resilience | ⏳ TODO |
| 13 | Performance & load validation | ⏳ TODO |
| 14 | Documentation | ⏳ TODO |

When closing a step, update the status here and tick the **Definition of Done** checklist inside the step file.

## How to Execute a Step

1. Read the step's `STEP-NN-*.md` end-to-end.
2. Verify all listed dependencies are ✅.
3. Implement files in the order under "Implementation Outline".
4. Run **every test** under "Tests (Acceptance Gates)" — all must pass.
5. Tick the "Definition of Done" checklist.
6. Update the status table above to ✅.
7. Commit on a feature branch named `feature/cv-step-NN-<slug>`.
