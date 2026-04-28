# Execution Prompt — STEP-09 → STEP-14 (Closeout)

## Mission

Close out FLOW-07 / UC-02. Six steps remain after STEP-08 (orchestrator) shipped:

| Step | Title | Polyrepos touched |
|---|---|---|
| 09 | Observability (metrics + tracing + logs + Grafana + Prom) | cv-service polyrepo + monorepo (`infra/grafana/dashboards/cv.json`) + infra polyrepo (`prometheus/prometheus.yml`, optional alert rules) |
| 10 | documents-service Java integration (publish + consume + persist + REST) | documents-service polyrepo + monorepo (planning tick) |
| 11 | Frontend integration (TanStack Query polling + auto-fill UX + Playwright E2E) | client/web polyrepo + monorepo (planning tick) |
| 12 | Boundary resilience (Resilience4j circuit breaker + idempotent consumer + UNAVAILABLE UX) | documents-service polyrepo + cv-service polyrepo + client/web polyrepo + monorepo (planning tick) |
| 13 | Performance & load validation (k6 + pytest-benchmark + nightly CI) | e2e-tests polyrepo + cv-service polyrepo + monorepo (`.github/workflows/cv-load-test.yml`?) |
| 14 | Documentation + sc-libs 1.4.0 final bump | cv-service polyrepo + monorepo + ALL 7 backend service polyrepos (libs.versions.toml) |

STEP-09 ∥ STEP-10 are parallelizable. Everything else is strictly sequential.

---

## Pre-Flight (re-confirm before EVERY step)

| Repo | Default branch | Expected origin/HEAD before STEP-09 starts |
|---|---|---|
| monorepo | `main` | `7f5de2a chore(cv): tick STEP-08 done in tracker (#47)` |
| cv-service polyrepo | `main` | `f3960fb feat(cv): STEP-08 — orchestrator + resilience (pipeline, breaker, retry policy) (#7)` |
| infra polyrepo | `master` | `d646a8f feat(infra): cv-service support — compose, env vars, RabbitMQ topology (#8)` |
| documents-service polyrepo | `develop` (verify) | last `develop` HEAD as of run start |
| client/web polyrepo | `develop` (verify) | last `develop` HEAD as of run start |
| e2e-tests polyrepo | `develop` (verify) | last `develop` HEAD as of run start |
| identity / admission / environment / notifications / gateway / telegram-bot polyrepos | `develop` | only touched in STEP-14 (sc-libs 1.4.0 bump) |

**If any HEAD has moved**, fast-forward and re-validate STEP-08's gates locally before starting STEP-09.

---

## Polyrepo Split — re-read this before EVERY step

Per `.gitignore`:
- `server/services/*` → each is its own polyrepo (gitignored from monorepo).
- `infra/*` → its own polyrepo (`selection-committee-infra`), narrow exceptions tracked in monorepo too:
  - `infra/docker-compose.services.yml`
  - `infra/grafana/dashboards/**`
  - `infra/.env.example` (grandfathered — tracked in BOTH repos; edits show as diffs in both).
- `client/` → its own polyrepo.

**Polyrepo workflow per step**:
1. `cd <polyrepo>`
2. `git fetch origin && git checkout <default> && git pull --ff-only`
3. `git checkout -b feature/cv-step-NN-<slug>`
4. Do NOT touch unrelated dirty files. The infra polyrepo carries notification-center WIP on `feature/cv-step-02-rabbitmq-topology` (uncommitted grafana/prometheus changes); LEAVE IT ALONE. STEP-08 already proved the pattern: stash → branch from default → cherry-pick + clean commit → restore stash.

**Monorepo tracker tick** is always its own small PR at the end of each step, after all polyrepo PRs merge. Branch name `feature/cv-step-NN-tracker-tick` from a freshly-pulled `main`.

---

## Operating Rules (non-negotiable, carried over from STEP-08 prompt)

1. **Read first** — open `progress/STEP-NN-*.md` end-to-end before doing anything. Do not paraphrase from memory.
2. **Sequential execution** — do not start STEP-N+1 until STEP-N's "Definition of Done" is fully ticked AND its PR(s) merged. STEP-09 ∥ STEP-10 are the ONLY parallelizable pair.
3. **Test-first inside each step** — write the tests listed under "Tests / Acceptance Gates" before implementation; confirm they fail for the right reason (`ModuleNotFoundError`, `AttributeError`, missing fixture file all count); then make them pass.
4. **Gating** — a step is done only when (a) every "Files to Create" exists, (b) every "Files to Modify" reflects the listed change, (c) every "Acceptance Gate" test passes, (d) lint/format/typecheck/build for the affected stack is clean. cv polyrepo CI is a no-op stub; **local gates are the actual gate**.
   - cv-service polyrepo: `poetry run ruff check src tests && poetry run ruff format --check src tests && poetry run mypy --strict src && poetry run pytest -q -m "not slow"`
   - Java service: `cd server && ./gradlew :libs:sc-event-contracts:publishToMavenLocal` (only if libs changed) then `cd server/services/selection-committee-<svc> && ./gradlew check`
   - Frontend: `cd client/web && pnpm lint && pnpm typecheck && pnpm test && pnpm build`
   - Compose-touching steps: `cd infra && docker compose -f docker-compose.yml -f docker-compose.services.yml config --quiet`. Full-stack readiness via `docker compose ... up -d --wait` (needs `infra/secrets/` populated; `bash infra/scripts/gen-jwt-keys.sh` once if missing).
5. **One feature branch per step per repo** — `feature/cv-step-NN-<slug>`. Never bypass hooks (`--no-verify` is forbidden).
6. **Update the tracker** — flip the `progress/README.md` row from ⏳ to ✅, tick "Definition of Done" boxes inside `progress/STEP-NN-*.md`, add a "Regressions Caught" section if anything non-obvious was fixed. Keep the dense one-line format used for STEP-01..08.
7. **Auto-loaded CLAUDE.md** — when you touch files under `server/`, `client/web/`, `infra/`, or `client/web/tests/`, the corresponding `CLAUDE.md` auto-loads. Its rules take precedence.
8. **Architectural invariants** — do NOT violate:
   - cv-service stays **stateless** — no DB, no gateway route, no public HTTP endpoint other than `/health` and `/metrics`. Persistence belongs to documents-service (`ocr_results` table — STEP-10).
   - Communication is **RabbitMQ + S3 only**. No Feign client to cv-service.
   - PaddleOCR models are **pre-baked into the Docker image**. Cold-start downloads are forbidden.
   - PaddleOCR `.ocr()` is **not thread-safe** — already serialised via `asyncio.Lock` on `OcrEngine` (STEP-05). Reuse it; do not introduce a second lock.
   - Field extraction is stateless and synchronous (STEP-06). Call `cv_service.extraction.extract(document_type, ocr_result)`. Unknown types → `UnsupportedDocumentTypeError` (terminal).
   - Messaging owns transport, not business logic (STEP-07). The orchestrator (STEP-08) is the single business handler.
   - documents-service publish is **best-effort with circuit breaker** (STEP-12). cv-service downtime must never break upload.
9. **No scope creep** — implement exactly what the step specifies. If you spot a gap, stop and ask before adding scope.
10. **Best-practice defaults** — when ambiguous, choose the simplest option that satisfies the spec, the architectural invariants above, and existing patterns in the repo. Document any deviation under "Regressions Caught" in the step file.
11. **Never touch unrelated polyrepo churn** — several polyrepos carry in-flight WIP unrelated to the CV rollout (notification-center stuff in `selection-committee-infra` on `feature/cv-step-02-rabbitmq-topology`, possibly more elsewhere). Leave them alone. The `progress/EXECUTION-PROMPT-STEP-*-TO-14.md` files are local notes; intentionally untracked; not part of any PR.
12. **Auto-merge mode is ON by default for this run** — open each PR, run gates green locally, squash-merge each PR, sync defaults, continue. Always report each merged PR URL in the per-step closing message. The user can override at any time ("stop merging", "wait for review", etc.).
13. **Pause and ask before merging anything for the user** — applies only when auto-merge is OFF. In auto-merge mode (this run), proceed.

---

## Per-Step Workflow

For each STEP-NN starting with STEP-09:

1. **Branch off** clean default in every affected repo (see Pre-Flight table).
2. **Read** `progress/STEP-NN-*.md` end-to-end. Read every auto-loaded `CLAUDE.md` whose subtree you'll touch.
3. **Write failing tests first** for each "Acceptance Gate". Confirm they fail for the right reason.
4. **Implement** files in the order under "Implementation Outline".
5. **Run the full acceptance-gate test list**. Iterate until green.
6. **Run lint/format/typecheck/build** for the affected stack.
7. **Validate compose changes**: `cd infra && docker compose -f docker-compose.yml -f docker-compose.services.yml config --quiet`. For full-stack readiness: `docker compose ... up -d --wait` and confirm every container is `(healthy)`.
8. **Tick "Definition of Done"** checkboxes inside `progress/STEP-NN-*.md`. Add "Regressions Caught" if any non-obvious fix was needed. Keep these edits **uncommitted** in the monorepo until the step's polyrepo PR(s) merge.
9. **Update the status row** in `progress/README.md` (also kept uncommitted until polyrepo merge). Match the dense one-line format used for STEP-01..08.
10. **Commit per repo** — `feat(cv): STEP-NN — <slug>` in cv polyrepo / `chore(cv): tick STEP-NN done in tracker` in monorepo / matching prefix in others. Read `git log --oneline -5` first to match house style.
11. **Push and open one PR per repo**. Title `feat(cv): STEP-NN — <slug>`. Body sections: `## Summary`, `## What's in this PR`, `## Test plan`, `## Peer PRs (STEP-NN)`, `## Notes`.
12. **In auto-merge mode**: squash-merge each PR (`gh pr merge <num> --squash --delete-branch`); sync local defaults; continue.
13. **After polyrepo merges** — branch the monorepo off freshly-pulled `main`, commit progress edits as `chore(cv): tick STEP-NN done in tracker`, push, open and merge the tracker-tick PR.
14. **Sync** all touched repos' local default branches before starting STEP-(NN+1).

---

## Hook-Enforced Conventions (do not work around)

- `.claude/hooks/validate-branch-name.py` — branches must match `feature/<name>`, `release/v<MAJOR>.<MINOR>.<PATCH>`, or `hotfix/<name>`. Use `feature/cv-step-NN-<slug>` everywhere.
- `.claude/hooks/prevent-direct-push.py` — direct push to `main` / `develop` / `master` is blocked. Use feature branches and PRs.

---

## Cross-Cutting Reminders

- **Build chain**: any change to `server/libs/sc-event-contracts/` (or any `server/libs/*`) requires `./gradlew :libs:sc-event-contracts:publishToMavenLocal` from `server/` before building any consumer. Skipping this causes stale-artifact failures.
- **sc-libs version bumps**: 1.3.3 → 1.4.0 in **STEP-14** as the FINAL commit. No other bumps until then unless STEP files explicitly require one.
- **No real PII**: every test fixture is synthetic. IPN values via `cv_service.extraction.ipn.compute_ipn_check_digit`; MRZ check digits via `cv_service.extraction.foreign_passport.compute_mrz_check_digit`. Never hard-code 10-digit IPNs or 7-digit MRZ check digits.
- **Docker validation**: every step that touches `infra/` runs `cd infra && docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --wait` and confirms all services reach `(healthy)`.
- **FastAPI healthchecks**: `wget --spider` (HEAD) returns 405 — use `wget -q -O - http://localhost:PORT/health > /dev/null` (GET, output discarded). Recorded as STEP-01 regression.
- **Poetry 2.x quirks**: `--only-root` cannot combine with `--with`/`--without`. Two-pass install when needed. Editable-install `.pth` files hard-code WORKDIR — keep `WORKDIR /app` consistent across multi-stage builds.
- **Frontend verification (STEP-11+)**: per project memory `feedback_verify_with_playwright`, run Playwright against the live UI. Code review alone is not acceptance.
- **rabbitmqadmin gotcha**: use explicit `--username=` and `--password=` flags, NOT `-u` / `-p`. STEP-02 regression.
- **`infra/rabbitmq/definitions.json` is gitignored**; the tracked source-of-truth is `definitions.example.json` in the infra polyrepo.
- **mypy + un-stubbed packages**: `pyproject.toml` overrides exist for `minio.*`, `testcontainers.*`, `urllib3.*`, `paddleocr.*`, `paddle.*`, `paddlepaddle.*`, `aio_pika.*`, `aiormq.*`, `cachetools.*`. Add new ones only when a new third-party import lacks stubs.
- **numpy <2.0** is mandatory for paddle ABI compatibility.
- **Pytest filterwarnings quarantine list**: paddle / paddleocr / ppocr / google.protobuf / astor / testcontainers DeprecationWarning instances are silenced. Any new transitive that throws Python-3.12-deprecation noise during `cv_service.*` import-chain needs an additive entry. paddleocr 2.10's `SyntaxWarning` for `"[\W_^\d]"` is NOT covered — if a new test module triggers it, add an explicit `ignore::SyntaxWarning:paddleocr.*` line (recorded in STEP-08 regressions).

### STEP-05 lessons inherited
- `_silence_ppocr_loggers()` (in `cv_service.ocr.engine`) demotes `ppocr` and `paddleocr` Python loggers to WARNING+. STEP-09's observability wiring must NOT reverse this.
- `OcrEngine` lock is the SINGLE serialisation point for paddle inference. Reuse it.
- `CV_OCR_EAGER_LOAD=false` for tests — already set in `tests/conftest.py`.

### STEP-06 lessons inherited
- Field extraction public surface: `from cv_service.extraction import extract, ExtractionResult, FieldExtractor, PassportExtractor, IpnExtractor, ForeignPassportExtractor, ExtractionError, UnsupportedDocumentTypeError`.
- `ExtractionResult.fields: dict[str, str]` — every value is a string. JSON serialisation is one-to-one. Frontend (STEP-11) consumes this shape verbatim.
- MRZ year inference is a 50/50 pivot (`YY > 50 → 19YY else 20YY`).

### STEP-07 lessons inherited
- Messaging public surface: `from cv_service.messaging import CvConsumer, CvPublisher, IdempotencyCache, MessageContext, MessagingError, TransientError, TopologyError, verify_topology`.
- `CvPublisher.publish_retry(*, body, headers, retry_queue)` was added in STEP-08 for the retry policy. STEP-12 may extend it.
- `IdempotencyCache` is per-instance (in-memory LRU). Cross-replica dedup is documents-service's job (STEP-10).

### STEP-08 lessons inherited
- Orchestrator public surface: `from cv_service.orchestrator import RetryPolicy, run_pipeline; from cv_service.orchestrator.pipeline import DefaultPreprocessor`.
- Resilience public surface: `from cv_service.resilience import OcrBreaker, BreakerOpenError, classify`.
- The orchestrator NEVER raises on classified failures — terminal → `publish_failed{retriable=False}`; transient with attempts left → `RetryPolicy.schedule_retry`; transient exhausted → `publish_failed{retriable=True}`. The consumer ack-on-success path acks the original delivery so only the retry copy persists.
- Per-stage timeouts: download 30s, preprocess 15s, OCR 60s, extract 5s. Surface as `asyncio.TimeoutError` → transient classification → retry path.
- `OcrBreaker` is async-native (NOT pybreaker — its `call_async` is broken without Tornado). `fail_max=5`, `reset_timeout=30s`, `OcrInputError` excluded from fail count.
- Required topology now includes `cv.retry.5s` / `cv.retry.30s` / `cv.retry.5m`.
- Stage error → short-code table is in `cv_service.orchestrator.pipeline._ERROR_CODE_TABLE`. STEP-09 should attach these as labels on the metrics; STEP-10's persistence MUST round-trip them.

---

## Bug-Fix Protocol (when tests fail mid-step)

1. Reproduce with the exact command from the step's "Acceptance Gates".
2. Write a regression test that reproduces it.
3. Fix the root cause. Do not bypass with mocks, `pytest.skip`, or `@Disabled`.
4. Confirm the new regression test and the original suite both pass.
5. Document the fix in the step file under "Regressions Caught".

---

## What to Ask the User About

**Pause and ask only for**:
- Ambiguity that the step file does not resolve and "best practice" cannot disambiguate.
- Architectural deviations you believe are necessary (e.g., another polyrepo split discovery, a sc-libs bump outside STEP-14, dropping a dep that the spec named).
- Production credentials or PII (never invent these).

**Do not ask for**:
- "Should I proceed?" between substeps within one STEP file — just proceed.
- "Should I merge?" — auto-merge mode is on; merge after gates green.
- Naming bikesheds — follow what the step file specifies.
- Lint-rule disputes — follow each repo's existing config.

---

## Reporting

After each step closes, post a single message to the user in the EXACT shape used at the end of STEP-08:

```
✅ STEP-NN <slug> complete

Branches: feature/cv-step-NN-<slug> in <repo(s)>
PRs (merged):
├─ <repo1>: <url1> → <merge-sha>
└─ <repo2>: <url2> → <merge-sha>

Tests: <N>/<N> passing
├─ cv-service: <N>/<N>
├─ java:       <N>/<N>
└─ web:        <N>/<N>

Build: clean (ruff/mypy/gradle/pnpm as applicable)

Notable decisions
• <1–3 bullets, only if non-obvious>

Regressions caught: <count, see step file>

Next: STEP-(NN+1) <slug>
```

---

## Step-Specific Pre-Flight Notes

These are gotchas spotted while reading the step files. Read each step file itself for the full spec.

### STEP-09 — Observability (cv-service polyrepo + monorepo + infra polyrepo) ∥ STEP-10

- New cv-service module: `src/cv_service/observability/{__init__,metrics,tracing,logging}.py`.
- Custom Prometheus metrics. Spec-required (verify against the step file, but at minimum):
  - `cv_documents_processed_total{result, document_type}` — counter; `result ∈ {parsed, failed_terminal, failed_retriable, retried}`.
  - `cv_pipeline_stage_seconds_bucket{stage}` — histogram; `stage ∈ {download, preprocess, ocr, extract, publish}`.
  - `cv_queue_depth{queue}` — gauge; sample `cv.document.requested` / `cv.dlq` / `cv.retry.*` periodically (every 15s).
  - `cv_ocr_lock_wait_seconds_bucket` — histogram around the `OcrEngine._lock` acquire.
  - `cv_ocr_breaker_state{state}` — gauge; mirror `OcrBreaker.state`.
- Wire OpenTelemetry spans for each pipeline stage. The OTel exporter is already wired in `cv_service.main` (STEP-01). Add new spans without reversing `_silence_ppocr_loggers()`.
- `with _timed("stage")` and `_async_timed("stage")` already exist in `cv_service.orchestrator.pipeline` as no-op stubs — STEP-09 fills them in via the metrics module without changing call sites.
- Monorepo: Grafana dashboard JSON in `infra/grafana/dashboards/cv.json` (monorepo-tracked path). Panels: per-stage latency p50/p95/p99, throughput by `result` label, error rate by class, OCR lock wait, breaker state, queue depth.
- Infra polyrepo: scrape job in `prometheus/prometheus.yml` (`cv-service:8088/metrics`); optional alert rules under `prometheus/alerts.yml`. **Be careful**: the infra polyrepo's `feature/cv-step-02-rabbitmq-topology` branch carries unrelated WIP for `prometheus/alerts.yml`. Branch off `master` cleanly per the STEP-08 pattern.
- Parallelizable with STEP-10. Use git worktrees if you want true parallelism:
  ```
  cd server/services/selection-committee-computer-vision
  git worktree add ../cv-service-step-09 feature/cv-step-09-observability
  ```

### STEP-10 — documents-service Java integration ∥ STEP-09

- documents-service polyrepo + monorepo (planning tick). Possibly `infra/postgres/init/01-create-dbs.sql` if a new DB-init is required (the `documents` DB already exists; new tables go via Flyway, not init script — verify against the step file).
- New `ocr_results` Flyway migration. Verify the next-available migration number with `ls src/main/resources/db/migration/ | sort | tail`.
  - Schema (per STEP-10 spec, verify): `id PK`, `document_id FK + UNIQUE`, `fields JSONB`, `confidence numeric(3,2)`, `status enum('parsed','failed')`, `error TEXT NULL`, `retriable BOOLEAN NULL`, `trace_id TEXT`, `created_at TIMESTAMPTZ DEFAULT now()`.
- New consumer for `cv.document.parsed` / `cv.document.failed` (Spring AMQP `@RabbitListener` on `cv.document.results`). **Idempotent** — upsert by `document_id`; ignore duplicates.
- New publisher: when document upload completes (Confirmed status), publish `CvDocumentRequestedEvent` to `cv.events` with routing key `cv.document.requested`. Wrap publish in a Resilience4j circuit breaker stub here; STEP-12 fills in the full config.
- New REST endpoint: `GET /api/v1/documents/{id}/ocr` — read-side for the frontend (STEP-11). Returns 404 while pending (no row), 200 with the result row once parsed/failed. (Verify exact path against the step file.)
- Tests:
  - Unit: `CvRequestPublisherTest`, `CvResultListenerTest`, `OcrResultServiceTest`.
  - Integration (Testcontainers Postgres + RabbitMQ): `CvIntegrationTest` — publish a fake parsed event → row exists; duplicate → no second row; failed event → status='failed'.
  - Contract test in `src/integrationTest/**/contract/` — deserialise sample JSON identical to a cv-service-produced parsed event and assert the Java record decodes it.
- DTO surface change: `OcrResultDto` is consumed verbatim by the frontend (STEP-11). Match the cv-service event shape exactly: `documentId`, `fields: Map<String,String>`, `confidence: BigDecimal`, `status`, `error`, `retriable`, `traceId`, `createdAt`.

### STEP-11 — Frontend integration (client/web polyrepo)

- Branch base: `client/web` polyrepo `develop` (verify with `git branch --show-current` after fetch).
- New TypeScript types in `src/types/ocr.ts` mirroring the documents-service response.
- New TanStack Query hook `useOcrResult(documentId)` polling the documents-service OCR endpoint. Spec interval (verify): every 2s while pending; backoff after 30s elapsed; stop polling once `status` is `parsed` or `failed`.
- New UI components: `<OcrResultCard>` (confidence-banded display + auto-fill CTA), `<OcrConfidenceBadge>`, `<OcrFieldRow>`. The auto-fill CTA fills the surrounding form via React Hook Form's `setValue`.
- Tests: Vitest unit tests for hook + components, Playwright E2E (`cv-ocr-flow.spec.ts`) against the live UI. **Verify with Playwright per `feedback_verify_with_playwright` memory** — code review is NOT acceptance.
- Test fixture: synthetic passport image in `tests/fixtures/`. Use the same Cyrillic mixed-case convention as cv-service tests (`Шевченко`, `Тарас`).

### STEP-12 — Boundary resilience (documents-service polyrepo + cv-service polyrepo + client/web polyrepo)

- documents-service: wrap `CvRequestPublisher` in `@CircuitBreaker(name="cvPublisher", fallbackMethod="publishFallback")`. Fallback logs a warning but does NOT block the upload — cv-service downtime must never break upload (architectural invariant).
- documents-service: tighten the consumer's idempotency. Existing upsert-by-`document_id` from STEP-10 covers the happy path; STEP-12 adds an explicit "duplicate parsed event silently dropped" test.
- cv-service: harden the consumer to never lose a message on restart. STEP-07's consumer acks AFTER the handler completes; STEP-12 must verify the handler's publish-then-ack ordering and add a test that kills cv-service mid-flight.
- Frontend: `useOcrResult` adds an `UNAVAILABLE` state after 60s of polling without a 200. `<OcrResultCard>` renders a soft "OCR temporarily unavailable" message; no error toast, no blocked form.
- Tests:
  - documents-service integration: `CvPublisherCircuitBreakerTest` (broker down → fallback path), `CvUploadResilienceTest` (upload succeeds while cv-service is down).
  - cv-service integration: existing messaging suite + new "killed mid-flight" test.
  - Frontend: `use-document-ocr-unavailable.test.ts` plus a Playwright case that proves the form is still submittable.

### STEP-13 — Performance & load validation (e2e-tests polyrepo + cv-service polyrepo + monorepo)

- e2e-tests polyrepo. Default branch likely `develop` — verify.
- k6 scenario `load/cv-pipeline.js` — reads Prometheus metrics. Targets:
  - **≥ 50 docs/min/instance throughput**.
  - **Queue lag < 1 minute at peak load**.
  - **Zero loss across CV restart** (kill cv-service mid-batch via `docker compose restart cv-service`; assert every document either has a result row or is still in flight).
- pytest-benchmark suites in `cv-service/benches/bench_pipeline.py` and `bench_extractors.py` — micro-benchmarks for individual stages.
- Synthetic IPNs use `cv_service.extraction.ipn.compute_ipn_check_digit`; synthetic MRZ uses `cv_service.extraction.foreign_passport.compute_mrz_check_digit`. Never hard-code check digits.
- Optional: nightly CI workflow `.github/workflows/cv-load-test.yml` (monorepo). If the workflow is monorepo-tracked but pulls polyrepos for execution, ensure GH PAT permissions are right; otherwise scope the workflow to a single repo and document.

### STEP-14 — Documentation + final sc-libs bump (cv-service polyrepo + monorepo + ALL backend service polyrepos)

- cv-service polyrepo: full `README.md` expansion — architecture diagram, dev workflow, Docker workflow, troubleshooting, observability, SLOs. New `CLAUDE.md` (auto-loaded) — operational rules + run commands.
- Monorepo: `docs/architecture/cv-service.md` (or extend existing CV section in `docs/claude/architecture.md`). Update `docs/flows/FLOW-07_*.md` with "As-Implemented" section. Update `docs/use-cases/UC-02_*.md` with the live OCR endpoint reference. New `docs/runbooks/cv-service.md` (DLQ inspection, retry, troubleshooting).
- **Final sc-libs bump 1.3.3 → 1.4.0**:
  - `server/version.properties`
  - `server/gradle/libs.versions.toml` (in monorepo)
  - All 7 consumer `gradle/libs.versions.toml` files in: identity, admission, environment, documents, notifications, gateway, telegram-bot polyrepos
  - `./gradlew :libs:sc-event-contracts:publishToMavenLocal` from `server/`, then `./gradlew check` in EACH consumer service to validate.
  - This is 7+ polyrepo PRs in one step. Open them in parallel, merge as gates go green.
- Tick STEP-14 ✅ in `progress/README.md`. Add a "FLOW-07 / UC-02 — DELIVERED" stamp at the top with the final PR list.

---

## Parallel Execution: STEP-09 ∥ STEP-10

Use git worktrees for true parallelism:

```bash
# Worktree A: STEP-09 in cv-service polyrepo
cd server/services/selection-committee-computer-vision
git worktree add ../cv-service-step-09 feature/cv-step-09-observability

# Worktree B: STEP-10 in documents-service polyrepo
cd ../selection-committee-documents-service
git worktree add ../documents-service-step-10 feature/cv-step-10-docs-integration
```

Open both PRs simultaneously; merge each as gates go green. Then their respective tracker-tick PRs in the monorepo (which CAN bundle into one tracker-tick PR if both finish in the same session — be explicit in the body).

---

## Begin

**Start with STEP-09 (or STEP-10 in parallel).** First action: confirm pre-flight HEADs in every relevant repo, then read `progress/STEP-NN-*.md` end-to-end (`ls progress/STEP-09*` to confirm filename), then write the failing tests before implementing anything.

Confirm before starting that:
- monorepo `origin/main` HEAD is `7f5de2a chore(cv): tick STEP-08 done in tracker (#47)`
- cv-service polyrepo `origin/main` HEAD is `f3960fb feat(cv): STEP-08 — orchestrator + resilience (pipeline, breaker, retry policy) (#7)`
- infra polyrepo `origin/master` HEAD is `d646a8f feat(infra): cv-service support — compose, env vars, RabbitMQ topology (#8)`
- documents-service / client/web / e2e-tests polyrepos' default branches are checked out and freshly pulled (HEAD will be whatever it is at run start)

If any HEAD has moved, fast-forward and re-validate STEP-08's gates locally before starting STEP-09. Starting from a stale base creates merge churn.

**Auto-merge mode is ON for this run.** Open each PR, run gates green locally, squash-merge each PR (`gh pr merge <num> --squash --delete-branch`), sync default branches, continue. Always report each merged PR URL in the per-step closing message.
