# Execution Prompt — STEP-08 → STEP-14 (CV-Service rollout)

Paste this verbatim into a fresh Claude Code conversation to continue the FICE Computer Vision rollout from where STEP-07 left off. The prompt is self-contained: it captures all live state, lessons inherited from STEP-01 → STEP-07, the polyrepo split, the operating rules, the per-step gotchas, and the per-step pre-flight notes for STEP-08 → STEP-14.

---

## Mission

Deliver, end to end, the event-driven Python (FastAPI + PaddleOCR) CV microservice that consumes `cv.document.requested` from RabbitMQ, runs download → preprocess → OCR → field-extract, and publishes `cv.document.parsed` / `cv.document.failed`. Integrate with documents-service (Java) for persistence and with the Next.js frontend for auto-fill. Match FLOW-07 SLOs: ≥ 50 docs/min/instance, queue lag < 1 min, zero message loss across CV restarts.

---

## Current State (as of start of STEP-08)

| Item | State | Where |
|---|---|---|
| Plan tracker | STEP-00 → STEP-07 ✅; STEP-08 → STEP-14 ⏳ | `progress/README.md` (post-STEP-07 monorepo PR #46 merged at `02cbeb3`) |
| sc-libs version | **1.3.3** (will bump to 1.4.0 in STEP-14) | `server/version.properties` |
| Java event records | `CvDocumentRequestedEvent`, `CvDocumentParsedEvent`, `CvDocumentFailedEvent` published in `sc-event-contracts` 1.3.3 | `server/libs/sc-event-contracts/src/main/java/edu/kpi/fice/sc/events/cv/` |
| Python event mirrors | `cv_service.events.{requested,parsed,failed,constants}` (Pydantic v2, `extra="forbid"`, `frozen=True`, camelCase) | cv polyrepo `src/cv_service/events/` |
| Storage adapter | `cv_service.storage.MinioStorageClient` — async, streaming, error-mapped (`ObjectNotFoundError`, `StorageTransientError`, `StorageError`); 6 Testcontainers gates green | cv polyrepo `src/cv_service/storage/` |
| Preprocessing pipeline | `cv_service.preprocessing.{loader,pipeline,exceptions,models}` — MIME-sniff loader (page cap), 5-stage OpenCV transform; 10 gates green | cv polyrepo `src/cv_service/preprocessing/` |
| OCR engine | `cv_service.ocr.{engine,models,exceptions}` — async PaddleOCR adapter, `uk` primary + `en` fallback below `_LOW_CONFIDENCE_THRESHOLD = 0.4`, `asyncio.Lock`-serialised, eager dual-model load via FastAPI lifespan gated by `CV_OCR_EAGER_LOAD` (default True) | cv polyrepo `src/cv_service/ocr/` |
| Field extraction | `cv_service.extraction.{base,exceptions,regex_patterns,passport,ipn,foreign_passport,router}` — `ExtractionResult(fields, confidence, warnings)`, `FieldExtractor` ABC, three concrete extractors + case-insensitive router, 40 acceptance gates green | cv polyrepo `src/cv_service/extraction/` |
| **Messaging (NEW from STEP-07)** | `cv_service.messaging.{consumer,publisher,idempotency,topology,types}` — aio-pika 9.6 consumer + publisher_confirms publisher, manual ack, LRU dedup on `(documentId, s3Key)`, task-per-delivery so prefetch caps in-flight, passive `verify_topology` on startup; 17 acceptance gates green via Testcontainers RabbitMQ | cv polyrepo `src/cv_service/messaging/` |
| Settings | `minio_*`, `max_pages` (10), `max_image_dimension` (8000), `ocr_langs` (`ukr,en`), `ocr_eager_load` (True), `rabbit_prefetch` (8), `rabbitmq_url`, `zipkin_endpoint` | cv polyrepo `src/cv_service/config.py` |
| Paddle stack | **paddlepaddle==3.0.\*** + **paddleocr==2.10.\*** + numpy<2.0 + setuptools>=70 | cv polyrepo `pyproject.toml`, `Dockerfile` |
| RabbitMQ topology | `cv.events` (topic) + `cv.dlx` (topic) exchanges; queues `cv.document.requested`, `cv.document.results`, `cv.dlq`, `cv.retry.{5s,30s,5m}` | `infra/rabbitmq/definitions.example.json` |
| cv polyrepo | `main = STEP-07 merged at 92e8a97`, runtime image ~573 MB (STEP-07 added aio-pika + cachetools, ~10 MB) | https://github.com/fice-selection-committee/selection-committee-computer-vision |
| **cv polyrepo CI is a no-op stub** | Every push event yields a 0-second `failure` run; STEP-01 → STEP-07 all merged without server-side CI gating. Local gates are the actual quality gate. | https://github.com/fice-selection-committee/selection-committee-computer-vision/actions |
| Auto-load rules | `server/CLAUDE.md`, `infra/CLAUDE.md`, `client/web/CLAUDE.md`, root `CLAUDE.md` — all auto-loading | already merged |
| Compose entry | cv-service block in `infra/docker-compose.services.yml` (depends on rabbitmq + minio-init) | already merged |
| Env vars | `CV_*` block in `infra/.env.example` (still missing `CV_MINIO_ACCESS_KEY` / `CV_MINIO_SECRET_KEY` / `CV_MINIO_SECURE` — STEP-08 lands those) | already merged |

**Open carry-over**: `infra/.env.example` still lacks `CV_MINIO_ACCESS_KEY`, `CV_MINIO_SECRET_KEY`, `CV_MINIO_SECURE`. STEP-08 (orchestrator wiring) lands these in the infra polyrepo, alongside any new orchestrator env vars.

---

## Polyrepo Split — re-read this before EVERY step

Per the project's `.gitignore`:

- `server/services/*` → each is its own polyrepo (gitignored from monorepo).
- `infra/*` → its own polyrepo (selection-committee-infra), with narrow exceptions (`infra/docker-compose.services.yml`, `infra/grafana/dashboards/`) tracked in the monorepo too.
- `client/` → its own polyrepo.

Per-step split:

| Step | Polyrepo PR? | Monorepo PR? | Other repos |
|---|---|---|---|
| 08 | yes (cv-service) | yes (progress + maybe `docs/architecture/cv-service.md`) | infra polyrepo (env vars), maybe `infra/docker-compose.services.yml` (monorepo too) |
| 09 | yes (cv-service) | yes (Grafana dashboard JSON in `infra/grafana/dashboards/cv-service.json` — monorepo-tracked) | infra polyrepo (Prometheus alert rules) |
| 10 | — | yes (planning ticks) | documents-service polyrepo (Flyway + consumer + `ocr_results` table); infra polyrepo if `postgres/init/01-create-dbs.sql` changes |
| 11 | — | yes (planning ticks) | client/web polyrepo (Next.js — selection-committee-web) |
| 12 | yes (cv-service) | yes (progress tracker tick) | documents-service polyrepo if publish-side resilience |
| 13 | — | yes (planning ticks) | e2e-tests polyrepo (selection-committee-e2e-tests, k6 scripts) |
| 14 | yes (cv-service README expansion) | yes (sc-libs 1.4.0 bump + planning closure + `docs/architecture/cv-service.md`) | all 7 backend service consumers (sc-libs final bump) |

**Polyrepo workflow per step**:
1. `cd server/services/<repo>/` (or `infra/`, or `client/web/`).
2. `git fetch origin && git checkout <default-branch> && git pull --ff-only`. Default branches:
   - cv-service: `main`
   - infra: `master`
   - identity, admission, documents, environment, notifications, gateway, telegram-bot, e2e-tests, web: `develop` (verify with `git branch --show-current` after fetch).
3. `git checkout -b feature/cv-step-NN-<slug>`.
4. Do NOT touch unrelated dirty files in these polyrepos.

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
6. **Update the tracker** — after each step's PRs are merged, edit `progress/README.md` to flip the row from ⏳ TODO to ✅ (matching the dense one-line format used for STEP-01 → STEP-07), AND tick "Definition of Done" checkboxes inside the step file. Add a "Regressions Caught" section to the step file documenting any non-obvious fix made during execution. This goes in a small dedicated monorepo PR.
7. **Auto-loaded CLAUDE.md** — when you touch files under `server/`, `client/web/`, `infra/`, or `client/web/tests/`, the corresponding CLAUDE.md auto-loads. Its rules take precedence.
8. **Architectural invariants** — do not violate:
   - cv-service is **stateless** — no DB, no gateway route, no public HTTP endpoint other than `/health` and `/metrics`.
   - Persistence belongs to documents-service (`ocr_results` table — STEP-10).
   - Communication is **RabbitMQ + S3 only**. No Feign client to cv-service.
   - PaddleOCR models are **pre-baked into the Docker image** (STEP-05). Cold-start downloads are forbidden.
   - PaddleOCR `.ocr()` is **not thread-safe** — already serialised via `asyncio.Lock` on `OcrEngine` (STEP-05). STEP-08 must reuse that lock, not introduce a new one.
   - Field extraction is **stateless and synchronous** (STEP-06) — call `cv_service.extraction.extract(document_type, ocr_result)`. The extractor selection is case-insensitive on the document-type string.
   - **Messaging owns transport, not business logic** (STEP-07) — `CvConsumer.run()` dispatches to a caller-supplied `async (event, ctx) -> None` handler. STEP-08 supplies that handler. Do NOT instantiate a second `aio_pika.Connection` for the publish side; reuse `CvPublisher` with its own robust connection (separate connection from the consumer is fine — they have independent lifecycles).
   - documents-service publish is **best-effort with circuit breaker** (STEP-12) — cv-service downtime must never break upload.
9. **No scope creep** — implement exactly what the step specifies. If you spot a gap or ambiguity, stop and ask the user before adding scope.
10. **Best-practice defaults** — when the user has said "do everything with best practices," resolve ambiguities by choosing the simplest option that satisfies the spec, the architectural invariants above, and the existing patterns in the repo. Document any deviation under "Regressions Caught" in the step file.
11. **Never touch unrelated polyrepo churn** — several polyrepos carry in-flight uncommitted work that is NOT part of the CV rollout. Leave those alone unless explicitly told otherwise. The `progress/EXECUTION-PROMPT-STEP-*-TO-14.md` files in the monorepo working tree are local execution notes; they are intentionally untracked and are NOT part of any PR.
12. **Auto-merge mode is ON by default for this run** — open each PR, run gates green locally, squash-merge each PR, sync default branches, continue. Always report each merged PR URL in the per-step closing message. The user can override at any time by saying "stop merging" / "wait for review" / equivalent.
13. **Pause and ask before merging anything for the user** — applies only when auto-merge is OFF. In auto-merge mode (this run), proceed.

---

## Per-Step Workflow

For each STEP-NN starting with STEP-08:

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
9. **Update the status row** in `progress/README.md` (also kept uncommitted until the polyrepo merge). Match the dense one-line format used for STEP-01 → STEP-07.
10. **Commit per repo** (`feat(cv): STEP-NN — <slug>` in cv polyrepo / `chore(cv): tick STEP-NN done in tracker` in monorepo / matching prefix in others). Read `git log --oneline -5` first to match house style.
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
- **Ukrainian-language assets**: PaddleOCR `lang="uk"` covers Cyrillic + Latin via the cyrillic PP-OCRv3 model. Test fixtures with Ukrainian text use real Cyrillic strings, never transliterations. Watch for ruff `RUF001`/`RUF002`/`RUF003` (ambiguous unicode) — `tests/ocr/**`, `tests/extraction/**`, `tests/messaging/**`, and `src/cv_service/extraction/**` are already opted in to per-file-ignores in `pyproject.toml`. For new test/src directories with Cyrillic content, extend that block.
- **No real PII**: every test fixture (passport, IPN, foreign passport) is synthetic. Generate IPN values via the mod-11 algorithm in `cv_service.extraction.ipn.compute_ipn_check_digit`; never use real ones.
- **Docker validation**: every step that touches `infra/` runs `cd infra && docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --wait` and confirms all services reach `(healthy)`.
- **FastAPI healthchecks**: `wget --spider` (HEAD) returns 405. Use `wget -q -O - http://localhost:PORT/health > /dev/null` (GET, output discarded) — recorded as a regression in STEP-01.
- **Poetry 2.x quirks**: `--only-root` cannot combine with `--with`/`--without`. Two-pass install when needed. Editable-install `.pth` files hard-code WORKDIR — keep `WORKDIR /app` consistent across multi-stage builds.
- **Frontend verification (STEP-11+)**: per project memory `feedback_verify_with_playwright`, run Playwright against the live UI. Code review alone is not acceptance.
- **rabbitmqadmin gotcha**: use explicit `--username=…` and `--password=…` flags, NOT `-u` / `-p` short flags. Recorded as a STEP-02 regression.
- **`infra/rabbitmq/definitions.json` is gitignored**; the tracked source-of-truth is `definitions.example.json` in the infra polyrepo.
- **mypy + un-stubbed packages**: `pyproject.toml` overrides exist for `minio.*`, `testcontainers.*`, `urllib3.*`, `paddleocr.*`, `paddle.*`, `paddlepaddle.*`, `aio_pika.*`, `aiormq.*`, `cachetools.*`. Add new ones only when a new third-party import lacks stubs.
- **numpy <2.0 is mandatory** for paddle ABI compatibility.
- **Pytest filterwarnings quarantine list**: paddle / paddleocr / ppocr / google.protobuf / astor / testcontainers `DeprecationWarning` instances are already silenced. Any new transitive that throws Python-3.12-deprecation noise during `cv_service.*` import-chain needs an additive entry in `[tool.pytest.ini_options].filterwarnings`.
- **`infra/.env.example` carry-over** — add `CV_MINIO_ACCESS_KEY`, `CV_MINIO_SECRET_KEY`, `CV_MINIO_SECURE` in STEP-08 (the first step that runs the storage adapter against the live compose stack).

### STEP-05 lessons inherited

- **PaddleOCR import-time silence**: `_silence_ppocr_loggers()` (in `cv_service.ocr.engine`) demotes the `ppocr` and `paddleocr` Python loggers to WARNING+. STEP-09's observability wiring should NOT reverse this.
- **`OcrEngine` lock is the SINGLE serialisation point** for paddle inference. STEP-08's orchestrator must call `OcrEngine.recognize()` rather than instantiating its own `PaddleOCR`.
- **`CV_OCR_EAGER_LOAD=false` for tests** — already set in `tests/conftest.py`. Any new test module that uses the FastAPI ASGI client inherits this. OCR-engine tests build the engine via the session-scoped `ocr_engine` fixture in `tests/ocr/conftest.py`; copy that pattern.
- **Synthetic Cyrillic fixtures use mixed-case words** (`Тест`, `Слава`, `Україна`, `Київ`). Avoid all-uppercase Cyrillic test strings — the model transliterates them to Latin lookalikes.
- **Image size grew to 563.6 MB** in STEP-05 (was 155.7 MB after STEP-04). STEP-07 added ~10 MB (aio-pika + cachetools); STEP-08+ should not push it materially higher.

### STEP-06 lessons inherited

- **Field extraction public surface**: `from cv_service.extraction import extract, ExtractionResult, FieldExtractor, PassportExtractor, IpnExtractor, ForeignPassportExtractor, ExtractionError, UnsupportedDocumentTypeError`. STEP-08's orchestrator calls `extract(document_type, ocr_result)` — case-insensitive dispatch. Unknown types raise `UnsupportedDocumentTypeError`; this is **terminal** (`retriable=False` on the resulting `CvDocumentFailedEvent`).
- **`ExtractionResult.fields: dict[str, str]`** — every value is a string. JSON serialisation is one-to-one. Frontend (STEP-11) consumes this shape verbatim.
- **MRZ year inference uses a 50/50 pivot**, not "current year + 5". `YY > 50 → 19YY else 20YY`. Documented in `cv_service.extraction.foreign_passport`. STEP-13 fixtures must respect this.
- **`compute_ipn_check_digit(prefix9: str) -> int`** is the canonical mod-11 implementation. STEP-13 load fixtures generate synthetic IPNs via this function — never hard-code 10-digit strings without computing the check digit.
- **`compute_mrz_check_digit(field: str) -> int`** is the canonical ICAO 9303 weights `[7, 3, 1]` implementation. STEP-13 load fixtures generate MRZ check digits via this function.
- **Avoid `cv_service.ocr.__init__` in test modules that don't need paddle** — import models directly from `cv_service.ocr.models`. The package `__init__` chains into paddle on every import.
- **`tests/extraction/conftest.py`** exposes `make_token(text, *, x, y, width, height, confidence)` and `make_ocr_result(tokens, *, page_index)`. STEP-08's orchestrator tests can reuse these helpers via `from tests.extraction.conftest import make_token, make_ocr_result`.

### STEP-07 lessons inherited

- **Messaging public surface**: `from cv_service.messaging import CvConsumer, CvPublisher, IdempotencyCache, MessageContext, MessagingError, TransientError, TopologyError, verify_topology`. STEP-08's orchestrator wraps the actual business logic in a `handler(event, ctx) -> None` callable and hands it to `CvConsumer(settings, handler)`.
- **Failure semantics are owned by the handler.** The consumer translates handler exceptions to broker-level acks/nacks: `TransientError` → `nack(no-requeue)` (dead-letters via `cv.dlx` to `cv.dlq`); any other exception → `nack(no-requeue)` too; `ValidationError` is caught BEFORE the handler runs (malformed JSON → `reject(no-requeue)`). STEP-08's handler should classify exceptions and either complete normally (then publish parsed/failed) or re-raise as `TransientError` for the messaging layer to dead-letter.
- **Publisher is async-context-managed.** `async with CvPublisher(settings) as publisher:` — STEP-08 holds one publisher for the lifetime of the FastAPI app and shares it with the consumer's handler closure. Do NOT open a fresh publisher per delivery.
- **`verify_topology(connection)` raises `TopologyError` on missing entities.** STEP-08's lifespan should call this once at startup and let the exception propagate so the FastAPI app fails liveness fast on a half-wired broker.
- **`IdempotencyCache` is per-instance** (in-memory LRU). Cross-replica dedup is documents-service's job (STEP-10's `ocr_results` upsert).
- **`tests/messaging/conftest.py`** exposes `rabbit_url` (session-scoped) + `topology` (per-test re-declared) + `amqp_connection` fixtures. STEP-08's orchestrator integration tests can reuse them via `from tests.messaging.conftest import rabbit_url, topology, amqp_connection`.
- **testcontainers DeprecationWarning is quarantined** in `pyproject.toml`'s `filterwarnings` list. Do NOT remove the `"ignore::DeprecationWarning:testcontainers.*"` line — STEP-07's messaging tests rely on it.
- **Task-per-delivery dispatch is what makes prefetch real.** The consumer spawns one `asyncio.Task` per message; STEP-08's handler runs concurrently up to `CV_RABBIT_PREFETCH`. Do NOT introduce a global lock around the handler that would serialise inference — the OCR engine's internal `asyncio.Lock` is already the single serialisation point for paddle (per STEP-05).

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

### STEP-08 — Orchestrator & resilience (cv-service polyrepo + infra polyrepo + monorepo)

- Wire download → preprocess → OCR → extract → publish. Use:
  - `cv_service.storage.MinioStorageClient` (STEP-03)
  - `cv_service.preprocessing.PreprocessingPipeline` (STEP-04)
  - `cv_service.ocr.OcrEngine` (STEP-05) — **call its existing `recognize()` method**; do not instantiate `PaddleOCR` directly
  - `cv_service.extraction.extract(document_type, ocr_result)` (STEP-06) — case-insensitive router. Unknown types raise `UnsupportedDocumentTypeError` (terminal).
  - `cv_service.messaging.{CvConsumer, CvPublisher, verify_topology, TransientError}` (STEP-07)
- **Reuse the `OcrEngine`'s internal `asyncio.Lock`** rather than introducing a new one. The lock is per-instance; the orchestrator gets a single shared instance from `app.state.ocr_engine` (set by lifespan).
- Pin `cv2.setNumThreads(1)` at process start for byte-determinism with the goldens.
- New module: `src/cv_service/orchestrator/pipeline.py` — pure function-style: takes a `CvDocumentRequestedEvent` + `MessageContext`, returns nothing but publishes `CvDocumentParsedEvent | CvDocumentFailedEvent` via the injected publisher. The messaging layer (STEP-07) wraps it.
- Resilience: timeouts per stage (download 30s, preprocess 15s, OCR 60s, extract 5s — verify against the step file), classify exceptions into retriable vs terminal. Existing exception classifications:
  - `ObjectNotFoundError` → `retriable=False`
  - `StorageTransientError` → `retriable=True`
  - `UnsupportedFormatError` → `retriable=False`
  - `PreprocessingError` (other) → `retriable=False`
  - `OcrInputError` → `retriable=False`
  - `OcrEngineError` → `retriable=True`
  - `UnsupportedDocumentTypeError` → `retriable=False`
  - `ExtractionError` (other) → `retriable=False` (extraction is deterministic on a fixed token stream)
- Idempotency: track `(documentId, delivery_tag)` in an in-process LRU; cv-service is stateless; documents-service handles persistence-side idempotency in STEP-10.
- Tempfile lifecycle: `try / finally: downloaded.path.unlink(missing_ok=True)` after every download.
- Map `ExtractionResult` → `CvDocumentParsedEvent.fields` directly (`dict[str, str]` round-trips through the Pydantic event model). Pass `ExtractionResult.confidence` as the event's confidence; append `ExtractionResult.warnings` to the event's `warnings` list (if the event model supports it — verify against STEP-02's mirror, otherwise log only).
- **Carry-over**: add `CV_MINIO_ACCESS_KEY`, `CV_MINIO_SECRET_KEY`, `CV_MINIO_SECURE` to `infra/.env.example` (infra polyrepo) and to `infra/docker-compose.services.yml`'s `cv-service` env block (monorepo-tracked).
- After this step the cv-service is functionally complete end-to-end against a live RabbitMQ + MinIO.

### STEP-09 — Observability (cv-service polyrepo + monorepo + infra polyrepo) ∥ STEP-10

- Polyrepo: structured logs with `traceId` propagation, custom Prometheus metrics (`cv_documents_processed_total{result}`, `cv_pipeline_stage_seconds_bucket{stage}`, `cv_queue_depth`, `cv_ocr_lock_wait_seconds`). Wire OpenTelemetry spans for each stage. The OTel exporter is already wired in `cv_service.main` (STEP-01); just register new spans.
- **Do NOT reverse `_silence_ppocr_loggers()`** — paddle's INFO output is noise. Add new structured logs under the `cv_service.*` namespace instead.
- Monorepo: Grafana dashboard JSON in `infra/grafana/dashboards/cv-service.json` (monorepo-tracked path). Include panels for: per-stage latency p50/p95/p99, throughput, error rate by class, OCR lock wait time.
- Infra polyrepo: Prometheus alert rules under `infra/prometheus/rules/cv-service.yml`.
- Parallelizable with STEP-10. Use git worktrees if you want true parallelism.

### STEP-10 — documents-service integration (Java) ∥ STEP-09

- documents-service polyrepo + monorepo (planning ticks; possibly `infra/postgres/init/01-create-dbs.sql` if a new DB-init line is needed — though `documents` DB already exists).
- New `ocr_results` Flyway migration (next migration number after the latest in `src/main/resources/db/migration/`). Schema: `id PK`, `document_id FK`, `fields JSONB`, `confidence numeric`, `status enum('parsed','failed')`, `error text NULL`, `retriable boolean NULL`, `trace_id text`, `created_at timestamptz`. Verify the next-available migration number with `ls src/main/resources/db/migration/ | sort | tail`.
- New consumer for `cv.document.parsed` and `cv.document.failed` (Spring AMQP `@RabbitListener`). **Idempotent** — upsert by `document_id`, ignore duplicates.
- New publisher: when a document upload completes, publish `CvDocumentRequestedEvent` to `cv.events` with routing key `cv.document.requested`. Wrap the publish in a circuit breaker (Resilience4j) — STEP-12 hardens this further.
- New REST endpoint: `GET /api/v1/documents/{id}/ocr` — read-side for the frontend (STEP-11). Returns 404 while pending, 200 with the result row once parsed/failed. (Verify the exact path against the step file — older docs may say `GET /api/documents/{id}/ocr-result`.)
- Tests: integration test with Testcontainers RabbitMQ + Postgres. Publish a fake parsed event → assert row exists. Publish duplicate → assert no duplicate row. Publish failed event → assert row with `status='failed'`.
- Contract test in `src/integrationTest/**/contract/` — deserialize a sample JSON identical to a cv-service-produced parsed event JSON and assert the Java record decodes it.

### STEP-11 — Frontend integration (client/web polyrepo)

- Adds OCR result polling and auto-fill UX.
- Branch base: client/web polyrepo `develop` (assumed — verify).
- New TypeScript types in `src/types/ocrResult.ts` mirroring the documents-service response.
- New TanStack Query hook `useOcrResult(documentId)` polling the documents-service OCR endpoint every 2s while status is pending. Backoff to 10s after 30s elapsed. Stop polling once `status` is `parsed` or `failed`.
- New UI component `<OcrAutoFillSuggestion>` — shows the parsed fields, lets the user accept (fills the surrounding form via React Hook Form's `setValue`) or reject (dismisses).
- **Verify with Playwright against the live UI** (per `feedback_verify_with_playwright` memory). Code review is NOT acceptance.

### STEP-12 — Boundary resilience (cv-service polyrepo + maybe documents-service)

- Circuit breakers, retry policies, idempotent consumers.
- cv-service: harden the consumer to never lose a message on restart (manual ack only after publish succeeds; reject on publish failure so the broker requeues). STEP-07's consumer acks AFTER the handler completes — STEP-12 must verify the handler's publish-then-ack ordering and tighten the test that kills cv-service mid-flight.
- documents-service: wrap the `cv.document.requested` publish in a Resilience4j circuit breaker. When open, log a warning but do NOT block the upload — cv-service downtime must never break upload (architectural invariant).
- Tests: integration test that kills the publish path mid-flight and asserts the upload still succeeds.

### STEP-13 — Performance & load validation (e2e-tests polyrepo)

- `server/services/selection-committee-e2e-tests/` is its own polyrepo. Default branch likely `develop` — verify.
- k6 scripts under `src/loadTest/` (or wherever the step file specifies).
- Targets the FLOW-07 SLOs: ≥ 50 docs/min/instance, queue lag < 1 min, **zero loss across CV restart**. The restart test is the hardest: kill cv-service mid-batch, start it back up, assert every document either has a result row or is still in flight. Use `docker compose restart cv-service` mid-load.
- Synthetic IPNs use `cv_service.extraction.ipn.compute_ipn_check_digit`; synthetic MRZ uses `cv_service.extraction.foreign_passport.compute_mrz_check_digit`. Never hard-code check digits.

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

**Start with STEP-08.** First action: confirm pre-flight, then read `progress/STEP-08-*.md` end-to-end (`ls progress/STEP-08*` to confirm the filename), then write the failing orchestrator tests in `tests/orchestrator/test_*.py` (or wherever the step file specifies) before implementing anything.

Confirm before starting that:
- cv polyrepo `origin/main` HEAD is `feat(cv): STEP-07 — RabbitMQ consumer + publisher (manual ack, idempotency, prefetch) (#6)` (sha `92e8a97`)
- monorepo `origin/main` HEAD is `chore(cv): tick STEP-07 done in tracker (#46)` (sha `02cbeb3`)

If either is behind, STOP and ask the user to merge first — starting STEP-08 from a non-`main` base creates merge churn.

**Auto-merge mode is ON for this run.** Open each PR, run gates green locally, squash-merge, sync defaults, continue. Always report each merged PR URL in the per-step closing message.
