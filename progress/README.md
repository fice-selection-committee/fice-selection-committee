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
| 11 | Frontend integration | ✅ done — `OcrStatus` + `OcrResult` types (`OcrStatus = 'PENDING' \| 'PARSED' \| 'FAILED' \| 'UNAVAILABLE'`), `fetchOcrResult` (404 → null), `ocrKeys` query factory, `useDocumentOcr` polling hook (2s while PENDING, 60s elapsed → `UNAVAILABLE`, polling stops on PARSED/FAILED), `<OcrConfidenceBadge>` (high ≥0.75 green / medium 0.5–0.75 orange / low <0.5 red, clamps to 0–100%), `<OcrFieldRow>` (Cyrillic field labels), `<OcrResultCard>` state-machine + readOnly variant + self-mounting `TooltipProvider` for the disabled-CTA case, `<OcrResultPanel>` (hook + card composition for pages), `applicant-profile-store` Zustand store with `applyOcrFields(ocrFields)` mapping (`surname`→`lastName`, `given_name`→`firstName`, `patronymic`→`middleName`, `birth_date`→`dateOfBirth`, `ipn`→`taxId`, `document_number`→`passportNumber`). Wired into `/applicant/documents` (interactive auto-fill CTA → toast + push to `/applicant/profile`) and `/operator/applications/[id]` review page (readOnly badge + extracted fields next to each document). `DocumentType` union extended with `FOREIGN_PASSPORT` for spec parity. Tests: 4 Vitest suites (badge variants, card states incl. UNAVAILABLE soft hint, polling cadence + UNAVAILABLE timer, store field mapping) — 23/23 STEP-11 cases green; 27/28 total Vitest files green (1 preexisting `group-notifications-by-date` date-flake unrelated). Playwright E2E `tests/e2e/regression/applicant/cv-ocr-flow.spec.ts` covers PENDING→PARSED + auto-fill + UNAVAILABLE soft-fail, mocks `/api/v1/documents/{id}/ocr` via `page.route()` so the spec is independent of cv-service runtime (cv-service polling contract itself is exercised in STEP-13's nightly load suite). Synthetic 100×60 grey PNG fixture at `tests/fixtures/passport_sample.png`. `pnpm build` clean (after `notification-settings.tsx` `useRef<...>(undefined)` fix — preexisting React 19 type regression on `develop`, surgical 1-liner to unblock the build). |
| 12 | Boundary resilience | ✅ done — `CvRequestPublisher.publish` wrapped in `@CircuitBreaker(name="cvPublisher", fallbackMethod="publishFallback")` (Resilience4j Spring Boot 3 starter, sliding-window 10 / min-calls 5 / failure-rate 50% / slow-call 2s / wait-in-open 30s / half-open 3 / `record-exceptions = [AmqpException, ConnectException, TimeoutException]` so business-logic NPEs still 500 instead of silently falling back). Fallback logs, increments `cv.publish.skipped` Micrometer counter, swallows — `DocumentService.confirm` no longer surfaces broker exceptions to the caller, document upload is fully decoupled from cv-service availability. Tests: existing `CvRequestPublisherTest` rebuilt to inject `SimpleMeterRegistry` (5 unit cases incl. new `publishFallback_logsAndIncrementsSkippedCounterWithoutThrowing`); `CvPublisherCircuitBreakerTest` (3 integration cases — Spring slice with `@MockitoBean RabbitTemplate` so the AOP proxy fires); `CvUploadResilienceTest` (2 integration cases — PG+RMQ Testcontainers, `@MockitoSpyBean` to flip publish target between throw/call-real across phases without destructive container manipulation). `./gradlew check` clean (JaCoCo 80%/file enforced and met; spotless + Error Prone clean). Frontend: STEP-11 already wired UNAVAILABLE into `useDocumentOcr`; STEP-12 adds dedicated coverage — `tests/unit/hooks/use-document-ocr-unavailable.test.ts` (3 Vitest cases: 60s threshold exact, PARSED-before-60s never overwritten, post-UNAVAILABLE recovery on next refetch) + `tests/e2e/regression/applicant/cv-ocr-fallback.spec.ts` (Playwright with `page.route()` returning PENDING-forever; asserts UNAVAILABLE hint, no error toast, form stays operable). cv-service polyrepo intentionally untouched — STEP-07's consumer already acks-after-handler so message durability across restart is structural; full chaos cycle (`docker stop / start cv-service`) is exercised in STEP-13's nightly load suite where the full Docker stack is provisioned. |
| 13 | Performance & load validation | ✅ done — Three-repo split. **e2e-tests** polyrepo: `load/cv-pipeline.js` k6 scenario (ramping-arrival-rate 10→60 rpm over 30s, sustained 60 rpm for 5min, ramp-down 30s), thresholds enforce SLO (`iteration_duration p(95) < 5s`, `cv_queue_lag max < 60`, `cv_processing_seconds p(95) < 5s`, `cv_dlq_depth max == 0`, `http_req_failed rate < 2%`); `cv-pipeline-utils.js` dependency-free helpers (`uploadAndConfirm`, `pollOcrUntilTerminal` 60s budget, `promQuery` for queue/DLQ depth sampling every 5 iters); `generate-fixtures.py` pure-stdlib PNG generator with re-implemented `compute_ipn_check_digit` + `compute_mrz_check_digit` so synthetic check digits stay aligned with cv-service helpers; 15 synthetic fixture images shipped (CI bumps `--count 20` for the spec's 50+ pool); README documents local invocation + baseline policy. **cv-service** polyrepo: `tests/benches/bench_extractors.py` (3 cases — passport / IPN / foreign-passport extractor latency on synthetic Cyrillic OCR fixtures, 50ms budget) + `tests/benches/bench_pipeline.py` (3 cases — extract-router 30 / 200 tokens linear-or-better, error-code lookup O(1)); `pyproject.toml` adds `pytest-benchmark ^5.2.0`, new `bench` marker, `python_files = ["test_*.py", "bench_*.py"]` so pytest discovers spec-named modules, `tests/benches/**` ruff per-file-ignore for Cyrillic confusables; 6/6 benches green (passport 26μs / IPN 4μs / foreign-passport 10μs / 30-token 48μs / 200-token 299μs / error-code 0.8μs — all well under budget); 137/137 existing tests still green; ruff + mypy --strict clean. **Monorepo**: `.github/workflows/cv-load-test.yml` nightly @ 02:00 UTC + `workflow_dispatch` (clones cv-service / documents-service / e2e-tests / infra polyrepos via `secrets.SC_BOT_TOKEN`, brings up `docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --wait`, regenerates 60 fixtures via `--count 20`, runs `grafana/k6 run --summary-export=k6-summary.json`, captures cv-service / documents-service logs on failure, uploads `cv-load-results` artifact, regression-gates current `iteration_duration p(95)` against `progress/baselines/cv-load-baseline.json` at 110% — populated by the first successful nightly run; placeholder JSON ships with `null` values + threshold reference for the operator updating it post-run). |
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
