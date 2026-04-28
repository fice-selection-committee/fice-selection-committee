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
| 04 | Image preprocessing pipeline | ⏳ TODO |
| 05 | OCR engine (PaddleOCR) | ⏳ TODO |
| 06 | Field extractors | ⏳ TODO |
| 07 | RabbitMQ consumer & publisher | ⏳ TODO |
| 08 | Orchestrator & resilience | ⏳ TODO |
| 09 | Observability | ⏳ TODO |
| 10 | Documents-service integration (Java) | ⏳ TODO |
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
