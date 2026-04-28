# Execution Prompt — STEP-06 → STEP-14

## Mission

Deliver, end to end, the event-driven Python (FastAPI + PaddleOCR) CV microservice that consumes `cv.document.requested` from RabbitMQ, runs download → preprocess → OCR → field-extract, and publishes `cv.document.parsed` / `cv.document.failed`. Integrate with documents-service (Java) for persistence and with the Next.js frontend for auto-fill. Match FLOW-07 SLOs: ≥ 50 docs/min/instance, queue lag < 1 min, zero message loss across CV restarts.

---

## Current State (as of start of STEP-06)

| Item | State | Where |
|---|---|---|
| Plan tracker | STEP-00 → STEP-05 ✅; STEP-06 → STEP-14 ⏳ | `progress/README.md` (post-STEP-05 monorepo PR #44 merged at `e493f23`) |
| sc-libs version | **1.3.3** (will bump to 1.4.0 in STEP-14) | `server/version.properties` |
| Java event records | `CvDocumentRequestedEvent`, `CvDocumentParsedEvent`, `CvDocumentFailedEvent` published in `sc-event-contracts` 1.3.3 | `server/libs/sc-event-contracts/src/main/java/edu/kpi/fice/sc/events/cv/` |
| Python event mirrors | `cv_service.events.{requested,parsed,failed,constants}` (Pydantic v2, `extra="forbid"`, `frozen=True`, camelCase) | cv polyrepo `src/cv_service/events/` |
| Storage adapter | `cv_service.storage.MinioStorageClient` — async, streaming, error-mapped (`ObjectNotFoundError`, `StorageTransientError`, `StorageError`); 6 Testcontainers gates green | cv polyrepo `src/cv_service/storage/` |
| Preprocessing pipeline | `cv_service.preprocessing.{loader, pipeline, exceptions, models}` — MIME-sniff loader (page cap), 5-stage OpenCV transform; 10 gates green | cv polyrepo `src/cv_service/preprocessing/` |
| **OCR engine (NEW from STEP-05)** | `cv_service.ocr.{engine, models, exceptions}` — async PaddleOCR adapter, `uk` primary + `en` fallback below `_LOW_CONFIDENCE_THRESHOLD = 0.4`, `asyncio.Lock`-serialised, eager dual-model load via FastAPI lifespan gated by `CV_OCR_EAGER_LOAD` (default True) | cv polyrepo `src/cv_service/ocr/` |
| Settings | `minio_*`, `max_pages` (10), `max_image_dimension` (8000), `ocr_langs` (`ukr,en`), `ocr_eager_load` (True), `rabbit_prefetch` (8), `rabbitmq_url`, `zipkin_endpoint` | cv polyrepo `src/cv_service/config.py` |
| Paddle stack | **paddlepaddle==3.0.\*** + **paddleocr==2.10.\*** + numpy<2.0 + setuptools>=70 (the spec's 2.6/2.7 pair segfaults on Linux at PaddleOCR import — known py3.12 + glibc 2.36 ABI mismatch). PP-OCRv3 cyrillic + PP-OCRv4 latin weights pre-baked into runtime image. | cv polyrepo `pyproject.toml`, `Dockerfile` |
| RabbitMQ topology | `cv.events` (topic) + `cv.dlx` (topic) exchanges; queues `cv.document.requested`, `cv.document.results`, `cv.dlq`, `cv.retry.{5s,30s,5m}`; full DLX + retry wiring | `infra/rabbitmq/definitions.example.json` |
| cv polyrepo | `main = STEP-05 merged at 7cb09a6`, **563.6 MB runtime image** (was 155.7 MB; +408 MB for paddle wheel + baked weights), libgomp1/libgl1/libglib2.0-0 added to both Dockerfile stages, ml group active, poppler-utils + libmagic1 still in builder + runtime | https://github.com/fice-selection-committee/selection-committee-computer-vision |
| CI workflow | `--with ml`, apt installs `poppler-utils libmagic1 fonts-dejavu-core`, `pytest -m "not slow"`, 25-minute timeout (was 10) | cv polyrepo `.github/workflows/cv-service-ci.yml` |
| **cv polyrepo CI is a no-op stub** | Every push event yields a 0-second `failure` run; `pull_request` triggers have never fired since STEP-01. STEP-01 → STEP-05 all merged without server-side CI gating. Local gates (`ruff`, `ruff format --check`, `mypy --strict`, `pytest -m "not slow"`, `docker build`, `docker run --network=none` cold-start) are the actual quality gate. | https://github.com/fice-selection-committee/selection-committee-computer-vision/actions |
| Auto-load rules | `server/CLAUDE.md`, `infra/CLAUDE.md`, `client/web/CLAUDE.md`, root `CLAUDE.md` — all auto-loading | already merged |
| Compose entry | cv-service block in `infra/docker-compose.services.yml` (depends on rabbitmq + minio-init) | already merged |
| Env vars | `CV_*` block in `infra/.env.example` (still missing `CV_MINIO_ACCESS_KEY` / `CV_MINIO_SECRET_KEY` / `CV_MINIO_SECURE` — STEP-08 lands those alongside any new orchestrator env) | already merged |

**Open carry-over**: `infra/.env.example` still lacks `CV_MINIO_ACCESS_KEY`, `CV_MINIO_SECRET_KEY`, `CV_MINIO_SECURE`. STEP-08 (orchestrator wiring) is the natural moment to add them in the infra polyrepo, alongside any other env additions the orchestrator needs.

---

## Polyrepo Split — re-read this before EVERY step

Per the project's `.gitignore`:

- `server/services/*` → each is its own polyrepo (gitignored from monorepo).
- `infra/*` → its own polyrepo (selection-committee-infra), with narrow exceptions (`infra/docker-compose.services.yml`, `infra/grafana/dashboards/`) tracked in the monorepo too.
- `client/` → its own polyrepo.

This means most steps produce **multiple PRs**, one per affected repo. Per-step split:

| Step | Polyrepo PR? | Monorepo PR? | Other repos |
|---|---|---|---|
| 06 | yes (cv-service) | yes (progress tracker tick) | — |
| 07 | yes (cv-service) | yes (progress tracker tick) | — |
| 08 | yes (cv-service) | yes (progress + maybe `docs/architecture/cv-service.md`) | infra polyrepo (env vars), maybe `infra/docker-compose.services.yml` (monorepo too) |
| 09 | yes (cv-service) | yes (Grafana dashboard JSON in `infra/grafana/dashboards/cv-service.json` — monorepo-tracked) | infra polyrepo (Prometheus alert rules) |
| 10 | — | yes (planning ticks) | documents-service polyrepo (Flyway + consumer + `ocr_results` table); infra polyrepo if `postgres/init/01-create-dbs.sql` changes |
| 11 | — | yes (planning ticks) | client/web polyrepo (Next.js — selection-committee-web) |
| 12 | yes (cv-service) | yes (progress tracker tick) | documents-service polyrepo if publish-side resilience |
| 13 | — | yes (planning ticks) | e2e-tests polyrepo (selection-committee-e2e-tests, k6 scripts) |
| 14 | yes (cv-service README expansion) | yes (sc-libs 1.4.0 bump + planning closure + `docs/architecture/cv-service.md`) | all 7 backend service consumers (sc-libs final bump) |

**Polyrepo workflow per step**:
1. `cd server/services/<repo>/` (or `infra/`, or `client/web/`).
2. `git fetch origin && git checkout <default-branch> && git pull --ff-only`. Default branches observed:
   - cv-service: `main`
   - infra: `master`
   - identity, admission, documents, environment, notifications, gateway, telegram-bot, e2e-tests, web: `develop` (assumed; verify with `git branch --show-current` after fetch).
3. `git checkout -b feature/cv-step-NN-<slug>`.
4. Do NOT touch unrelated dirty files in these polyrepos. Several of them carry in-flight churn that's out of scope for the CV rollout.

**Monorepo workflow**: from `D:\develop\fice-selection-committee`, branch off `main`. The progress-tracker tick (`progress/README.md` row + DoD checkboxes inside `progress/STEP-NN-*.md`) is a small monorepo PR at the **end** of each step, after the polyrepo PR(s) merge — never bundled inside an unrelated PR.

---

## Operating Rules (non-negotiable)

1. **Read first** — open the step's `progress/STEP-NN-*.md` end-to-end before doing anything. Do not paraphrase from memory.
2. **Sequential execution** — follow the dependency graph in `progress/README.md`. Do not start STEP-N+1 until STEP-N's "Definition of Done" is fully ticked AND its PR(s) merged. STEP-09 ∥ STEP-10 are the only parallelizable pair.
3. **Test-first inside each step** — write the tests listed under "Tests (Acceptance Gates)" before the implementation, watch them fail for the right reason (module-not-found, import errors, missing methods all count — but a test that passes by accident does NOT), then make them pass.
4. **Gating** — a step is done only when (a) every "Files to Create" exists, (b) every "Files to Modify" reflects the listed change, (c) every "Acceptance Gate" test passes, (d) lint / format / typecheck / build for the affected stack is clean. **The cv polyrepo's GitHub Actions CI does NOT gate PRs (zero-second failure stubs since STEP-01) — local gates are the actual gate.** Required local commands:
   - cv-service polyrepo: `poetry run ruff check src tests && poetry run ruff format --check src tests && poetry run mypy --strict src && poetry run pytest -q -m "not slow"`
   - For Docker-touching steps: `docker build --target runtime .` succeeds AND `docker run --network=none cv-service:<tag>` reaches "Application startup complete".
   - Java: `cd server && ./gradlew :libs:sc-event-contracts:publishToMavenLocal` (whenever `server/libs/` changed) then `cd server/services/selection-committee-<svc> && ./gradlew check`
   - Frontend: `cd client/web && pnpm lint && pnpm build && pnpm test`
5. **One feature branch per step per repo** — `feature/cv-step-NN-<slug>` in each affected repo. Never bypass hooks (no `--no-verify`).
6. **Update the tracker** — after each step's PRs are merged, edit `progress/README.md` to flip the row from ⏳ TODO to ✅ (matching the format used for STEP-01 → STEP-05: a single dense line summarising what landed), AND tick "Definition of Done" checkboxes inside the step file. Add a "Regressions Caught" section to the step file documenting any non-obvious fix made during execution. This goes in a small dedicated monorepo PR.
7. **Auto-loaded CLAUDE.md** — when you touch files under `server/`, `client/web/`, `infra/`, or `client/web/tests/`, the corresponding CLAUDE.md auto-loads. Its rules take precedence.
8. **Architectural invariants** — do not violate:
   - cv-service is **stateless** — no DB, no gateway route, no public HTTP endpoint other than `/health` and `/metrics`.
   - Persistence belongs to documents-service (`ocr_results` table — STEP-10).
   - Communication is **RabbitMQ + S3 only**. No Feign client to cv-service.
   - PaddleOCR models are **pre-baked into the Docker image** (STEP-05). Cold-start downloads are forbidden.
   - PaddleOCR `.ocr()` is **not thread-safe** — already serialised via `asyncio.Lock` on `OcrEngine` (STEP-05). STEP-08 should reuse that lock, not introduce a new one.
   - documents-service publish is **best-effort with circuit breaker** (STEP-12) — cv-service downtime must never break upload.
9. **No scope creep** — implement exactly what the step specifies. If you spot a gap or ambiguity, stop and ask the user before adding scope.
10. **Best-practice defaults** — when the user has said "do everything with best practices," resolve ambiguities by choosing the simplest option that satisfies the spec, the architectural invariants above, and the existing patterns in the repo. Document any deviation under "Regressions Caught" in the step file.
11. **Never touch unrelated polyrepo churn** — several polyrepos carry in-flight uncommitted work that is NOT part of the CV rollout. Leave those alone unless explicitly told otherwise.
12. **Auto-merge mode is ON by default for this run** — open each PR, run gates green locally, squash-merge each PR, sync default branches, continue. Always report each merged PR URL in the per-step closing message. The user can override at any time by saying "stop merging" / "wait for review" / equivalent.
13. **Pause and ask before merging anything for the user** — applies only when auto-merge is OFF. In auto-merge mode (this run), proceed.

---

## Per-Step Workflow

For each STEP-NN starting with STEP-06:

1. **Branch off** clean default in every affected repo:
   ```bash
   git fetch origin && git checkout <default> && git pull --ff-only && git checkout -b feature/cv-step-NN-<slug>
   ```
2. **Read** `progress/STEP-NN-*.md` end-to-end. Read every auto-loaded CLAUDE.md whose subtree you'll touch.
3. **Write failing tests first** for each "Acceptance Gate". Confirm they fail for the right reason (ModuleNotFoundError / AttributeError / missing fixture file all count — a green test that passes by accident does NOT).
4. **Implement** files in the order under "Implementation Outline".
5. **Run the full acceptance-gate test list**. Iterate until green.
6. **Run lint / format / typecheck / build** for the affected stack (see Gating above).
7. **Validate compose changes**: `cd infra && docker compose -f docker-compose.yml -f docker-compose.services.yml config --quiet`. For full-stack readiness: `docker compose ... up -d --wait` and confirm every container reaches `(healthy)`. The full stack needs `infra/secrets/` populated — run `bash infra/scripts/gen-jwt-keys.sh` once if missing.
8. **Tick "Definition of Done"** checkboxes inside `progress/STEP-NN-*.md`. Add "Regressions Caught" if any non-obvious fix was needed. Keep these edits **uncommitted** in the monorepo until the step's polyrepo PR(s) merge.
9. **Update the status row** in `progress/README.md` (also kept uncommitted until the polyrepo merge). Match the dense one-line format used for STEP-01 → STEP-05.
10. **Commit per repo** (`feat(cv): step-NN <slug>` in cv polyrepo / `chore(cv): tick STEP-NN done in tracker` in monorepo / matching prefix in others). Read `git log --oneline -5` first to match house style.
11. **Push and open one PR per repo**. Title: `feat(cv): STEP-NN — <slug>`. Body sections: `## Summary`, `## What's in this PR`, `## Test plan`, `## Peer PRs (STEP-NN)`, `## Notes`.
12. **In auto-merge mode**: squash-merge each PR (`gh pr merge <num> --squash --delete-branch`); sync local defaults; continue.
13. **After polyrepo merges** — branch the monorepo off freshly-pulled `main`, commit the progress edits as `chore(cv): tick STEP-NN done in tracker`, push, open and merge the tracker-tick PR.
14. **Sync** all touched repos' local default branches before starting STEP-(NN+1).

---

## Hook-Enforced Conventions (do not work around)

The repo has Claude-Code hooks that block non-conforming git operations. They are enforced; treat their messages as gospel.

- `.claude/hooks/validate-branch-name.py` — every branch must match `feature/<name>`, `release/v<MAJOR>.<MINOR>.<PATCH>`, or `hotfix/<name>`. `chore/...`, `wip/...`, `feat/...`, `fix/...` are rejected. Use `feature/cv-step-NN-<slug>` for every CV step (in monorepo too — `feature/cv-step-NN-tracker-tick` is fine).
- `.claude/hooks/prevent-direct-push.py` — direct push to `main` or `develop` is blocked in every repo. Use feature branches and PRs always. For brand-new polyrepo `main` seeding, use the GitHub API trick recorded in `progress/STEP-01-cv-service-scaffold.md`.

---

## Cross-Cutting Reminders

- **Build chain**: any change to `server/libs/sc-event-contracts/` (or any `server/libs/*`) requires `./gradlew :libs:sc-event-contracts:publishToMavenLocal` from `server/` before building any consumer service. Skipping this causes stale-artifact failures.
- **sc-libs version bumps**: 1.3.3 → 1.4.0 in **STEP-14** as the final commit. No other bumps until then unless STEP files explicitly require one.
- **Ukrainian-language assets**: PaddleOCR `lang="uk"` covers Cyrillic + Latin via the cyrillic PP-OCRv3 model. Test fixtures with Ukrainian text use real Cyrillic strings, never transliterations. Watch for ruff `RUF001`/`RUF002` (ambiguous unicode) — `tests/ocr/**` is already excluded in `pyproject.toml`'s `per-file-ignores`. For new test directories with Cyrillic content, extend that block.
- **No real PII**: every test fixture (passport, IPN, foreign passport) is synthetic. Generate IPN values via the mod-11 algorithm (STEP-06); never use real ones.
- **Docker validation**: every step that touches `infra/` runs `cd infra && docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --wait` and confirms all services reach `(healthy)`.
- **FastAPI healthchecks**: `wget --spider` (HEAD) returns 405. Use `wget -q -O - http://localhost:PORT/health > /dev/null` (GET, output discarded) — recorded as a regression in STEP-01.
- **Poetry 2.x quirks**: `--only-root` cannot combine with `--with`/`--without`. Two-pass install when needed. Editable-install `.pth` files hard-code WORKDIR — keep `WORKDIR /app` consistent across multi-stage builds.
- **Frontend verification (STEP-11+)**: per project memory `feedback_verify_with_playwright`, run Playwright against the live UI. Code review alone is not acceptance.
- **rabbitmqadmin gotcha**: use explicit `--username=…` and `--password=…` flags, NOT `-u` / `-p` short flags. Recorded as a STEP-02 regression.
- **`infra/rabbitmq/definitions.json` is gitignored**; the tracked source-of-truth is `definitions.example.json` in the infra polyrepo.
- **mypy + un-stubbed packages**: `pyproject.toml` overrides exist for `minio.*`, `testcontainers.*`, `urllib3.*`, `paddleocr.*`, `paddle.*`, `paddlepaddle.*`. Add new ones only when a new third-party import lacks stubs — keep the override list minimal.
- **numpy <2.0 is mandatory** for paddle ABI compatibility. With numpy 1.x stubs, mypy `--strict` requires explicit type args on `np.ndarray` — use `npt.NDArray[Any]` (already migrated for `cv_service.preprocessing.*` and `cv_service.ocr.*` in STEP-05).
- **`infra/.env.example` carry-over** — add `CV_MINIO_ACCESS_KEY`, `CV_MINIO_SECRET_KEY`, `CV_MINIO_SECURE` in STEP-08 (the first step that actually runs the storage adapter against the live compose stack).

### STEP-05 lessons inherited

- **PaddleOCR import-time silence**: `_silence_ppocr_loggers()` (in `cv_service.ocr.engine`) demotes the `ppocr` and `paddleocr` Python loggers to WARNING+. STEP-09's observability wiring should NOT reverse this — INFO-level paddle output floods the cv-service log stream.
- **`OcrEngine` lock is the SINGLE serialisation point** for paddle inference. STEP-08's orchestrator must call `OcrEngine.recognize()` rather than instantiating its own `PaddleOCR` and re-doing the lock.
- **`CV_OCR_EAGER_LOAD=false` for tests** — already set in `tests/conftest.py`. Any new test module that uses the FastAPI ASGI client inherits this. OCR-engine tests build the engine via the session-scoped `ocr_engine` fixture in `tests/ocr/conftest.py`; copy that pattern.
- **Synthetic Cyrillic fixtures use mixed-case words** the PP-OCRv3 model handles cleanly (`Тест`, `Слава`, `Україна`, `Київ`). Avoid all-uppercase Cyrillic test strings — the model transliterates them to Latin lookalikes.
- **STEP-04's `passport_clean.png` has zero text** — it is a deskew-only black-bar fixture. STEP-06 extractor tests should generate their own synthetic passport pages with realistic OCR token output rather than running OCR on STEP-04 fixtures.
- **Image size grew to 563.6 MB** in STEP-05 (was 155.7 MB after STEP-04). STEP-06 should not push it materially higher; STEP-07 (aio-pika) adds ~10 MB.

---

## Bug-Fix Protocol (when tests fail)

If during execution any test (existing or new) regresses:

1. Reproduce the failure with the exact command from the step's "Acceptance Gates".
2. Write a regression test that reproduces it (per `docs/claude/testing.md` bug-fix mandate).
3. Fix the root cause. Do not bypass with mocks, `pytest.skip`, or `@Disabled`.
4. Confirm both the new regression test and the original suite pass.
5. Document the fix in the step file under a "Regressions Caught" section so future steps inherit the lesson.

---

## What to Ask the User About

Pause and ask **only** for:
- Ambiguity in a step that the file does not resolve and that "best practice" cannot disambiguate.
- Architectural deviations you believe are necessary (e.g., another polyrepo split discovery, a sc-libs bump outside STEP-14).
- Production credentials or PII (never invent these).

Do **not** ask for:
- "Should I proceed?" between substeps within one STEP file — just proceed.
- "Should I merge?" — auto-merge mode is on; merge after gates green.
- Naming bikesheds — follow what the step file specifies.
- Lint-rule disputes — follow each repo's existing config.

---

## Reporting

After each step closes, post a single message to the user:

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

These are gotchas spotted while reading the step files. Read the step file itself for the full spec.

### STEP-06 — Field extractors (cv-service polyrepo only)

- Pure-Python: regex + `IPN` mod-11 validator. **No new system deps.**
- New modules under `src/cv_service/extractors/`: `passport.py`, `ipn.py`, `foreign_passport.py`, `base.py` (abstract `Extractor` protocol with `extract(lines: list[OcrLine]) → dict[str, str]`).
- The protocol's input shape is the spec's name `OcrLine`. STEP-05 actually uses **`OcrToken`** (with `text`, `bbox: BoundingBox`, `confidence`) and the wrapping **`OcrResult`** (with `tokens`, `mean_confidence`, `page_index`). Adapter your extractors to consume `OcrToken` lists; if STEP-06 spec uses `OcrLine`, treat it as a typo for `OcrToken` and document under Regressions.
- IPN mod-11: implement per the Ukrainian individual-tax-number spec. Generate synthetic test IPNs via the same algorithm — never use real ones. The validator function should return `bool`; the extractor returns the IPN only if valid.
- Tests: golden-line fixtures (lists of `OcrToken` synthesised from realistic passport/IPN OCR output) → expected dict. Edge cases: misspelled labels, two-line addresses, missing fields (extractor returns partial dict, never raises). Do NOT run real OCR in extractor tests — feed token lists directly.

### STEP-07 — RabbitMQ consumer & publisher (cv-service polyrepo only)

- Add `aio-pika` to **main deps** (not the ml group — messaging is not optional).
- Acceptance gate spins up Testcontainers RabbitMQ. Manual ack, prefetch from `CV_RABBIT_PREFETCH` (already in `Settings`).
- New module: `src/cv_service/messaging/{consumer,publisher,topology}.py`. The topology module declares `cv.events`, `cv.dlx`, `cv.document.requested`, `cv.document.results`, `cv.dlq`, `cv.retry.{5s,30s,5m}` using the constants from `cv_service.events.constants` (already in place).
- Use `aio_pika.connect_robust(...)` for auto-reconnect.
- Publish events with `delivery_mode=PERSISTENT`, `content_type="application/json"`, and `correlation_id=traceId`.
- Idempotency: tag every consumed message with its `delivery_tag` and dedupe in STEP-08 (this step just wires the transport).

### STEP-08 — Orchestrator & resilience (cv-service polyrepo + infra polyrepo + monorepo)

- Wire download → preprocess → OCR → extract → publish. Use:
  - `cv_service.storage.MinioStorageClient` (STEP-03)
  - `cv_service.preprocessing.PreprocessingPipeline` (STEP-04)
  - `cv_service.ocr.OcrEngine` (STEP-05) — **call its existing `recognize()` method**; do not instantiate `PaddleOCR` directly
  - `cv_service.extractors.*` (STEP-06)
  - `cv_service.messaging.*` (STEP-07)
- **Reuse the `OcrEngine`'s internal `asyncio.Lock`** rather than introducing a new one. The lock is per-instance; the orchestrator gets a single shared instance from `app.state.ocr_engine` (set by lifespan).
- Pin `cv2.setNumThreads(1)` at process start for byte-determinism with the goldens (matches the test-time pin in `tests/preprocessing/conftest.py` and `tests/ocr/conftest.py`).
- New module: `src/cv_service/pipeline/orchestrator.py` — pure function-style: takes a `CvDocumentRequestedEvent`, returns `CvDocumentParsedEvent | CvDocumentFailedEvent`. The messaging layer (STEP-07) wraps it.
- Resilience: timeouts per stage (download 30s, preprocess 15s, OCR 60s, extract 5s — verify against the step file), classify exceptions into retriable vs terminal, build the `CvDocumentFailedEvent.retriable` flag accordingly. Existing exception classifications:
  - `ObjectNotFoundError` → `retriable=False`
  - `StorageTransientError` → `retriable=True`
  - `UnsupportedFormatError` → `retriable=False`
  - `PreprocessingError` (other) → `retriable=False`
  - `OcrInputError` → `retriable=False`
  - `OcrEngineError` → `retriable=True`
- Idempotency: track `(documentId, delivery_tag)` in an in-process LRU; if the same `documentId` arrives twice within a TTL, skip (cv-service is stateless; documents-service handles persistence-side idempotency in STEP-10).
- Tempfile lifecycle: `try / finally: downloaded.path.unlink(missing_ok=True)` after every download — see STEP-03 lessons.
- **Carry-over**: add `CV_MINIO_ACCESS_KEY`, `CV_MINIO_SECRET_KEY`, `CV_MINIO_SECURE` to `infra/.env.example` (infra polyrepo) and to `infra/docker-compose.services.yml`'s `cv-service` env block (monorepo-tracked).
- After this step the cv-service is functionally complete end-to-end against a live RabbitMQ + MinIO.

### STEP-09 — Observability (cv-service polyrepo + monorepo + infra polyrepo) ∥ STEP-10

- Polyrepo: structured logs with `traceId` propagation, custom Prometheus metrics (`cv_documents_processed_total{result}`, `cv_pipeline_stage_seconds_bucket{stage}`, `cv_queue_depth`, `cv_ocr_lock_wait_seconds`). Wire OpenTelemetry spans for each stage. The OTel exporter is already wired in `cv_service.main` (STEP-01); just register new spans.
- **Do NOT reverse `_silence_ppocr_loggers()`** — paddle's INFO output is noise. Add new structured logs under the `cv_service.*` namespace instead.
- Monorepo: Grafana dashboard JSON in `infra/grafana/dashboards/cv-service.json` (monorepo-tracked path). Include panels for: per-stage latency p50/p95/p99, throughput, error rate by class, OCR lock wait time.
- Infra polyrepo: Prometheus alert rules under `infra/prometheus/rules/cv-service.yml` (or wherever existing service rules live).
- Parallelizable with STEP-10. Use git worktrees if you want true parallelism:
  ```bash
  cd server/services/selection-committee-computer-vision
  git worktree add ../cv-service-step-09 feature/cv-step-09-observability
  ```

### STEP-10 — documents-service integration (Java) ∥ STEP-09

- documents-service polyrepo + monorepo (planning ticks; possibly `infra/postgres/init/01-create-dbs.sql` if a new DB-init line is needed — though `documents` DB already exists).
- New `ocr_results` Flyway migration (next migration number after the latest in `src/main/resources/db/migration/`). Schema: `id PK`, `document_id FK`, `fields JSONB`, `confidence numeric`, `status enum('parsed','failed')`, `error text NULL`, `retriable boolean NULL`, `trace_id text`, `created_at timestamptz`. Verify the next-available migration number with `ls src/main/resources/db/migration/ | sort | tail`.
- New consumer for `cv.document.parsed` and `cv.document.failed` (use Spring AMQP `@RabbitListener`). **Idempotent** — upsert by `document_id`, ignore duplicates.
- New publisher: when a document upload completes, publish `CvDocumentRequestedEvent` to `cv.events` with routing key `cv.document.requested`. Wrap the publish in a circuit breaker (Resilience4j) — STEP-12 hardens this further.
- New REST endpoint: `GET /api/documents/{id}/ocr-result` — read-side for the frontend (STEP-11). Returns 404 while pending, 200 with the result row once parsed/failed.
- Tests: integration test with Testcontainers RabbitMQ + Postgres. Publish a fake parsed event → assert row exists. Publish duplicate → assert no duplicate row. Publish failed event → assert row with `status='failed'`.
- Contract test in `src/integrationTest/**/contract/` — deserialize a sample JSON identical to `cv-service/tests/events/samples/parsed.json` and assert the Java record decodes it.

### STEP-11 — Frontend integration (client/web polyrepo)

- Adds OCR result polling and auto-fill UX.
- Branch base: client/web polyrepo `develop` (assumed — verify).
- New TypeScript types in `src/types/ocrResult.ts` mirroring the documents-service response.
- New TanStack Query hook `useOcrResult(documentId)` polling `GET /api/documents/{id}/ocr-result` every 2s while status is pending. Backoff to 10s after 30s elapsed. Stop polling once `status` is `parsed` or `failed`.
- New UI component `<OcrAutoFillSuggestion>` — shows the parsed fields, lets the user accept (fills the surrounding form via React Hook Form's `setValue`) or reject (dismisses).
- **Verify with Playwright against the live UI** (per `feedback_verify_with_playwright` memory). Code review is NOT acceptance.

### STEP-12 — Boundary resilience (cv-service polyrepo + maybe documents-service)

- Circuit breakers, retry policies, idempotent consumers.
- cv-service: harden the consumer to never lose a message on restart (manual ack only after publish succeeds; reject on publish failure so the broker requeues).
- documents-service: wrap the `cv.document.requested` publish in a Resilience4j circuit breaker. When open, log a warning but do NOT block the upload — cv-service downtime must never break upload (architectural invariant).
- Tests: integration test that kills the publish path mid-flight and asserts the upload still succeeds.

### STEP-13 — Performance & load validation (e2e-tests polyrepo)

- `server/services/selection-committee-e2e-tests/` is its own polyrepo. Default branch likely `develop` — verify.
- k6 scripts under `src/loadTest/` (or wherever the step file specifies).
- Targets the FLOW-07 SLOs: ≥ 50 docs/min/instance, queue lag < 1 min, **zero loss across CV restart**. The restart test is the hardest: kill cv-service mid-batch, start it back up, assert every document either has a result row or is still in flight. Use `docker compose restart cv-service` mid-load.

### STEP-14 — Documentation (cv-service polyrepo + monorepo)

- cv-service polyrepo: full `README.md` expansion — architecture diagram, dev workflow, Docker workflow, troubleshooting, observability, SLOs.
- Monorepo: new `docs/architecture/cv-service.md` (or update existing CV section in `docs/claude/architecture.md`). **Final** sc-libs version bump 1.3.3 → 1.4.0 in `server/version.properties`, all 7 consumer `gradle/libs.versions.toml` files, and `./gradlew publishToMavenLocal`. Tick STEP-14 as ✅ in `progress/README.md`. Add a "FLOW-07 / UC-02 — DELIVERED" stamp at the top of `progress/README.md` with the final PR list.

---

## Parallel Execution: STEP-09 ∥ STEP-10

These two steps have no shared files. To run in parallel:

```bash
# Worktree A: STEP-09 in cv-service polyrepo
cd server/services/selection-committee-computer-vision
git worktree add ../cv-service-step-09 feature/cv-step-09-observability

# Worktree B: STEP-10 in documents-service polyrepo
cd ../selection-committee-documents-service
git worktree add ../documents-service-step-10 feature/cv-step-10-docs-integration
```

Open both PRs simultaneously; merge each as gates go green.

---

## Begin

**Start with STEP-06.** First action: confirm pre-flight, then read `progress/STEP-06-extractors.md` end-to-end (`ls progress/STEP-06*` to confirm the filename), then write the failing extractor tests in `tests/extractors/test_*.py` (or wherever the step file specifies) before implementing anything.

Confirm before starting that:
- cv polyrepo `origin/main` HEAD is `feat(cv): STEP-05 — OCR engine (PaddleOCR) (#4)` (sha `7cb09a6`)
- monorepo `origin/main` HEAD is `chore(cv): tick STEP-05 done in tracker (#44)` (sha `e493f23`)

If either is behind, STOP and ask the user to merge first — starting STEP-06 from a non-`main` base creates merge churn.

**Auto-merge mode is ON for this run.** Open each PR, run gates green locally, squash-merge, sync defaults, continue. Always report each merged PR URL in the per-step closing message.
