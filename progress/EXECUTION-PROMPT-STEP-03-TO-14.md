# Execution Prompt — STEP-03 → STEP-14 (CV system rollout)

Paste this whole document into a fresh Claude Code session in `D:\develop\fice-selection-committee` to continue the Computer Vision rollout where STEP-02 left off. STEP-00, STEP-01, and STEP-02 are merged on `main` (PRs #37, #39, #41 in the monorepo plus their peers in the cv-service and infra polyrepos). The plan is approved — execute, do not redesign.

---

## Mission

Deliver, end to end, an event-driven Python (FastAPI + PaddleOCR) CV microservice that consumes `cv.document.requested` from RabbitMQ, runs download → preprocess → OCR → field-extract, and publishes `cv.document.parsed` / `cv.document.failed`. Integrate with documents-service (Java) for persistence and with the Next.js frontend for auto-fill. Match FLOW-07 SLOs: ≥ 50 docs/min/instance, queue lag < 1 min, zero message loss across CV restarts.

---

## Current State (as of start of STEP-03)

| Item | State | Where |
|---|---|---|
| Plan tracker | STEP-00, STEP-01, STEP-02 ✅; STEP-03 → STEP-14 ⏳ | `progress/README.md` |
| sc-libs version | **1.3.3** (will bump to 1.4.0 in STEP-14) | `server/version.properties` |
| Java event records | `CvDocumentRequestedEvent`, `CvDocumentParsedEvent`, `CvDocumentFailedEvent` published in `sc-event-contracts` 1.3.3 | `server/libs/sc-event-contracts/src/main/java/edu/kpi/fice/sc/events/cv/` |
| Python event mirrors | `cv_service.events.{requested,parsed,failed,constants}` (Pydantic v2, `extra="forbid"`, `frozen=True`, camelCase) | cv polyrepo `src/cv_service/events/` |
| RabbitMQ topology | `cv.events` (topic) + `cv.dlx` (topic) exchanges; queues `cv.document.requested`, `cv.document.results`, `cv.dlq`, `cv.retry.{5s,30s,5m}`; full DLX + retry wiring | `infra/rabbitmq/definitions.example.json` |
| cv polyrepo | `main = c4fe0f4 + STEP-02 merge`, FastAPI shell, /health + /metrics, 56 MB image | https://github.com/fice-selection-committee/selection-committee-computer-vision |
| Auto-load rules | `server/CLAUDE.md`, `infra/CLAUDE.md`, `client/web/CLAUDE.md`, root CLAUDE.md, all auto-loading | already merged |
| Compose entry | cv-service block in `infra/docker-compose.services.yml` (depends on rabbitmq + minio-init) | already merged |
| Env vars | `CV_*` block in `infra/.env.example` | already merged |

---

## Polyrepo Split — re-read this before EVERY step

Per the project's `.gitignore`:

- `server/services/*` → each is its own polyrepo (gitignored from monorepo).
- `infra/*` → its own polyrepo (selection-committee-infra), with narrow exceptions (`infra/docker-compose.services.yml`, `infra/grafana/dashboards/`) tracked in the monorepo too.
- `client/` → its own polyrepo.

This means most steps produce **multiple PRs**, one per affected repo. Per-step split:

| Step | Polyrepo PR? | Monorepo PR? | Other repos |
|---|---|---|---|
| 03 | yes (cv-service) | — | — |
| 04 | yes (cv-service) | — | — |
| 05 | yes (cv-service) | — | — |
| 06 | yes (cv-service) | — | — |
| 07 | yes (cv-service) | — | — |
| 08 | yes (cv-service) | — | — |
| 09 | yes (cv-service) | maybe (Grafana dashboard, prometheus rules — both `infra/grafana/dashboards/` (monorepo) AND infra polyrepo) | — |
| 10 | — | yes (planning ticks) | documents-service polyrepo (Flyway + consumer + ocr_results table); infra polyrepo if `postgres/init/01-create-dbs.sql` changes |
| 11 | — | yes (planning ticks) | client/web polyrepo (Next.js — selection-committee-web) |
| 12 | yes (cv-service) | maybe | documents-service if publish-side resilience |
| 13 | — | yes (planning ticks) | e2e-tests polyrepo (selection-committee-e2e-tests, k6 scripts) |
| 14 | yes (cv-service README expansion) | yes (sc-libs 1.4.0 bump + planning closure + `docs/architecture/cv-service.md`) | all consumers (sc-libs final bump) |

**Polyrepo workflow per step**:
1. `cd server/services/<repo>/` (or `infra/`, or `client/web/`).
2. `git fetch origin && git checkout <default-branch> && git pull --ff-only`. Default branches observed:
   - cv-service: `main`
   - infra: `master`
   - identity, admission, documents, environment, notifications, gateway, telegram-bot, e2e-tests, web: `develop` (assumed; verify with `git branch --show-current` after fetch). `main` may not exist on these — fall back to `develop`.
3. `git checkout -b feature/cv-step-NN-<slug>`.
4. Do NOT touch unrelated dirty files in these polyrepos. Several of them carry in-flight churn that's out of scope for the CV rollout. Only `git add` files you actually changed for the current step.

**Monorepo workflow**: from `D:\develop\fice-selection-committee`, branch off `main`.

---

## Operating Rules (non-negotiable)

1. **Read first** — open the step's `progress/STEP-NN-*.md` end-to-end before doing anything. Do not paraphrase from memory.
2. **Sequential execution** — follow the dependency graph in `progress/README.md`. Do not start STEP-N+1 until STEP-N's "Definition of Done" is fully ticked AND its PR(s) merged. STEP-09 ∥ STEP-10 are the only parallelizable pair.
3. **Test-first inside each step** — write the tests listed under "Tests (Acceptance Gates)" before the implementation, watch them fail for the right reason (module-not-found, import errors, missing methods all count — but a test that passes by accident does NOT), then make them pass.
4. **Gating** — a step is done only when (a) every "Files to Create" exists, (b) every "Files to Modify" reflects the listed change, (c) every "Acceptance Gate" test passes, (d) lint / format / typecheck / build for the affected stack is clean:
   - cv-service polyrepo: `poetry run ruff check src tests && poetry run ruff format --check src tests && poetry run mypy --strict src && poetry run pytest -q`
   - Java: `cd server && ./gradlew :libs:sc-event-contracts:publishToMavenLocal` (whenever `server/libs/` changed) then `cd server/services/selection-committee-<svc> && ./gradlew check`
   - Frontend: `cd client/web && pnpm lint && pnpm build && pnpm test`
5. **One feature branch per step per repo** — `feature/cv-step-NN-<slug>` in each affected repo. Never bypass hooks (no `--no-verify`).
6. **Update the tracker** — after each step's PRs are merged, edit `progress/README.md` to flip the row from ⏳ TODO to ✅, AND tick "Definition of Done" checkboxes inside the step file. Add a "Regressions Caught" section to the step file documenting any non-obvious fix made during execution.
7. **Auto-loaded CLAUDE.md** — when you touch files under `server/`, `client/web/`, `infra/`, or `client/web/tests/`, the corresponding CLAUDE.md auto-loads. Its rules take precedence.
8. **Architectural invariants** — do not violate:
   - cv-service is **stateless** — no DB, no gateway route, no public HTTP endpoint other than `/health` and `/metrics`.
   - Persistence belongs to documents-service (`ocr_results` table — STEP-10).
   - Communication is **RabbitMQ + S3 only**. No Feign client to cv-service.
   - PaddleOCR models are **pre-baked into the Docker image** (STEP-05). Cold-start downloads are forbidden.
   - PaddleOCR `recognize()` is **not thread-safe** — serialize via `asyncio.Lock` (STEP-08).
   - documents-service publish is **best-effort with circuit breaker** (STEP-12) — cv-service downtime must never break upload.
9. **No scope creep** — implement exactly what the step specifies. If you spot a gap or ambiguity, stop and ask the user before adding scope.
10. **Best-practice defaults** — when the user has said "do everything with best practices," resolve ambiguities by choosing the simplest option that satisfies the spec, the architectural invariants above, and the existing patterns in the repo. Document any deviation under "Regressions Caught" in the step file.
11. **Never touch unrelated polyrepo churn** — several polyrepos carry in-flight uncommitted work that is NOT part of the CV rollout (e.g., identity-service has UserDto modifications, infra has prior compose drift). Leave those alone unless explicitly told otherwise.

---

## Per-Step Workflow

For each STEP-NN starting with STEP-03:

1. **Branch off** clean default in every affected repo:
   ```bash
   git fetch origin && git checkout <default> && git pull --ff-only && git checkout -b feature/cv-step-NN-<slug>
   ```
2. **Read** `progress/STEP-NN-*.md` end-to-end. Read every auto-loaded CLAUDE.md whose subtree you'll touch.
3. **Write failing tests first** for each "Acceptance Gate". Confirm they fail for the right reason.
4. **Implement** files in the order under "Implementation Outline".
5. **Run the full acceptance-gate test list**. Iterate until green.
6. **Run lint / format / typecheck / build** for the affected stack (see Gating above).
7. **Validate compose changes**: `cd infra && docker compose -f docker-compose.yml -f docker-compose.services.yml config --quiet`. For full-stack readiness: `docker compose ... up -d --wait` and confirm every container reaches `(healthy)`.
8. **Tick "Definition of Done"** checkboxes inside `progress/STEP-NN-*.md`. Add "Regressions Caught" if any non-obvious fix was needed.
9. **Update the status row** in `progress/README.md`.
10. **Commit per repo** (`feat(cv): step-NN <slug>` in cv polyrepo / `feat(cv): STEP-NN — <slug>` in monorepo / matching prefix in others). Read `git log --oneline -5` first to match house style.
11. **Push and open one PR per repo**. Title: `feat(cv): STEP-NN — <slug>`. Body sections: `## Summary`, `## What's in this PR`, `## Test plan`, `## Peer PRs (STEP-NN)`.
12. **Wait for the user to merge** before STEP-(NN+1). Do not start the next step until all of the current step's PRs are merged on their default branch.

---

## Hook-Enforced Conventions (do not work around)

The repo has Claude-Code hooks that block non-conforming git operations. They are enforced; treat their messages as gospel.

- `.claude/hooks/validate-branch-name.py` — every branch must match `feature/<name>`, `release/v<MAJOR>.<MINOR>.<PATCH>`, or `hotfix/<name>`. `chore/...`, `wip/...`, `feat/...`, `fix/...` are rejected. Use `feature/cv-step-NN-<slug>` for every CV step.
- `.claude/hooks/prevent-direct-push.py` — direct push to `main` or `develop` is blocked in every repo. Use feature branches and PRs always. For brand-new polyrepo `main` seeding, use the GitHub API trick recorded in `progress/STEP-01-cv-service-scaffold.md` (PATCH default-branch + create-ref + restore default).

---

## Cross-Cutting Reminders

- **Build chain**: any change to `server/libs/sc-event-contracts/` (or any `server/libs/*`) requires `./gradlew :libs:sc-event-contracts:publishToMavenLocal` from `server/` before building any consumer service. Skipping this causes stale-artifact failures.
- **sc-libs version bumps**: 1.3.3 → 1.4.0 in **STEP-14** as the final commit. No other bumps until then unless STEP files explicitly require one.
- **Ukrainian-language assets**: PaddleOCR `lang="uk"` covers Cyrillic + Latin. Test fixtures with Ukrainian text use real Cyrillic strings, never transliterations. Watch for ruff `RUF001` (ambiguous unicode) — tag with `# noqa: RUF001 — Cyrillic …` only on lines that genuinely contain mixed-script ambiguity.
- **No real PII**: every test fixture (passport, IPN, foreign passport) is synthetic. Generate IPN values via the mod-11 algorithm (STEP-06); never use real ones.
- **Docker validation**: every step that touches `infra/` runs `cd infra && docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --wait` and confirms all services reach `(healthy)`. The full stack needs `infra/secrets/` populated — run `bash infra/scripts/gen-jwt-keys.sh` once if missing.
- **FastAPI healthchecks**: `wget --spider` (HEAD) returns 405. Use `wget -q -O - http://localhost:PORT/health > /dev/null` (GET, output discarded) — recorded as a regression in STEP-01.
- **Poetry 2.x quirks**: `--only-root` cannot combine with `--with`/`--without`. Two-pass install: `poetry install --without ml,dev --no-root` then `poetry install --only-root`. Editable-install `.pth` files hard-code WORKDIR — keep `WORKDIR /app` consistent across multi-stage builds.
- **Frontend verification (STEP-11+)**: per project memory `feedback_verify_with_playwright`, run Playwright against the live UI. Code review alone is not acceptance.
- **rabbitmqadmin gotcha**: use explicit `--username=…` and `--password=…` flags, NOT `-u` / `-p` short flags. Recorded as a STEP-02 regression.
- **`infra/rabbitmq/definitions.json` is gitignored**; the tracked source-of-truth is `definitions.example.json` in the infra polyrepo. Edit the example file; developers render the runtime copy with their hashed password.

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
- Architectural deviations you believe are necessary (e.g., another polyrepo split discovery).
- Production credentials or PII (never invent these).
- Approval before merging each step's PR(s).

Do **not** ask for:
- "Should I proceed?" between substeps within one STEP file — just proceed.
- Naming bikesheds — follow what the step file specifies.
- Lint-rule disputes — follow each repo's existing config.

---

## Reporting

After each step closes, post a single message to the user:

```
✅ STEP-NN <slug> complete

Branches: feature/cv-step-NN-<slug> in <repo(s)>
PRs:
├─ <repo1>: <url1>
└─ <repo2>: <url2>

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

### STEP-03 — MinIO download adapter (cv-service polyrepo only)

- Branch base: cv polyrepo `main` (NOT `develop`).
- Add `minio` Python client to **main deps** in `pyproject.toml` (not `ml` group — needed at runtime always).
- Acceptance gate uses `testcontainers[minio]` — Docker Desktop must be running locally. Add to `dev` group.
- New module: `src/cv_service/storage/minio_adapter.py` with an async `download(key: str) → bytes` interface (or `pathlib.Path` to a temp file — pick whichever the step file specifies). Wire timeouts and a structured error type (`StorageError`).
- Tests: integration test that spins up MinIO via Testcontainers, uploads a tiny PNG, downloads it through the adapter, asserts byte equality. Plus unit tests with a mocked `minio.Minio` client for the error paths (404, network timeout).
- Configure adapter via `CV_MINIO_ENDPOINT`, `CV_MINIO_ACCESS_KEY`, `CV_MINIO_SECRET_KEY`, `CV_MINIO_BUCKET` from `cv_service.config.Settings` (already wired in STEP-01).

### STEP-04 — Image preprocessing pipeline (cv-service polyrepo only)

- Add `opencv-python-headless`, `pdf2image`, `Pillow` to **`ml` poetry group** (large, only needed at runtime when ML is active).
- Bake `poppler-utils` into the Dockerfile **builder layer** (`pdf2image` invokes `pdftoppm`). Use `apt-get install -y --no-install-recommends poppler-utils && rm -rf /var/lib/apt/lists/*`.
- New module: `src/cv_service/preprocess/pipeline.py` — orchestrates: PDF→PNG (if `application/pdf`), grayscale, deskew (Hough or moments), denoise (Gaussian + bilateral), threshold (Otsu or adaptive). Output: a single `np.ndarray` in `uint8` shape `(H, W)`.
- Tests: parametrized fixtures — clean PNG, skewed PNG, multi-page PDF, low-contrast scan. Assert output shape + dtype + non-zero pixel count > threshold.
- Add `--with ml` to the dev-time `poetry install` call so tests can run locally. CI runs both with-ml and without-ml gates per existing workflow.

### STEP-05 — OCR engine (PaddleOCR) (cv-service polyrepo only)

- Add `paddleocr` to the **`ml` group**. Pin a known-good version range (e.g. `^2.7`) — verify against the step file.
- **Pre-bake models into the Docker image** — runtime cold-start downloads are forbidden by an architectural invariant. Bake step in the builder stage:
  ```dockerfile
  RUN python -c "from paddleocr import PaddleOCR; PaddleOCR(lang='uk')"
  ```
  Then copy the `~/.paddleocr` cache (or whichever path PaddleOCR writes to in your version) into the runtime image. Verify the runtime image starts with **no network access** (use `docker run --network=none` in CI).
- New module: `src/cv_service/ocr/engine.py` — exposes `recognize(image: np.ndarray) → list[OcrLine]` where `OcrLine = (text: str, bbox: tuple[int,int,int,int], confidence: float)`.
- **Thread safety**: PaddleOCR's `recognize()` is not thread-safe. Wrap calls in an `asyncio.Lock` (or use a single-worker thread pool). The lock lives at module scope; STEP-08 will use it.
- Tests: small fixture image with known Ukrainian text. Assert at least the first line decodes correctly. Skip the full recognition test in CI if `paddleocr` is not installed (mark with `pytest.importorskip`).

### STEP-06 — Field extractors (cv-service polyrepo only)

- Pure-Python: regex + `IPN` mod-11 validator. **No new system deps**.
- New modules under `src/cv_service/extractors/`: `passport.py`, `ipn.py`, `foreign_passport.py`, `base.py` (abstract `Extractor` protocol with `extract(lines: list[OcrLine]) → dict[str, str]`).
- IPN mod-11: implement per the Ukrainian individual-tax-number spec. Generate synthetic test IPNs via the same algorithm — never use real ones. The validator function should return `bool`; the extractor returns the IPN only if valid.
- Tests: golden-line fixtures (lists of `OcrLine` synthesized from realistic passport/IPN OCR output) → expected dict. Edge cases: misspelled labels, two-line addresses, missing fields (extractor returns partial dict, never raises).

### STEP-07 — RabbitMQ consumer & publisher (cv-service polyrepo only)

- Add `aio-pika` to **main deps**.
- Acceptance gate spins up Testcontainers RabbitMQ. Manual ack, prefetch from `CV_RABBIT_PREFETCH` (already in `Settings`).
- New module: `src/cv_service/messaging/{consumer,publisher,topology}.py`. The topology module declares `cv.events`, `cv.dlx`, `cv.document.requested`, `cv.document.results`, `cv.dlq`, `cv.retry.{5s,30s,5m}` using the constants from `cv_service.events.constants` (already in place).
- Use `aio_pika.connect_robust(...)` for auto-reconnect.
- Publish events with `delivery_mode=PERSISTENT`, `content_type="application/json"`, and `correlation_id=traceId`.
- Idempotency: tag every consumed message with its `delivery_tag` and dedupe in STEP-08 (this step just wires the transport).

### STEP-08 — Orchestrator & resilience (cv-service polyrepo only)

- Wire download → preprocess → OCR → extract → publish.
- **Serialize OCR via `asyncio.Lock`** (the lock from STEP-05). Document the throughput trade-off.
- New module: `src/cv_service/pipeline/orchestrator.py` — pure function-style: takes a `CvDocumentRequestedEvent`, returns `CvDocumentParsedEvent | CvDocumentFailedEvent`. The messaging layer (STEP-07) wraps it.
- Resilience: timeouts per stage (download 30s, preprocess 15s, OCR 60s, extract 5s — but verify against the step file), classify exceptions into retriable vs terminal, build the `CvDocumentFailedEvent.retriable` flag accordingly.
- Idempotency: track `(documentId, delivery_tag)` in an in-process LRU; if the same `documentId` arrives twice within a TTL, skip (the cv-service is stateless; documents-service handles persistence-side idempotency in STEP-10).

### STEP-09 — Observability (cv-service polyrepo + maybe monorepo) ∥ STEP-10

- Polyrepo: structured logs with `traceId` propagation, custom Prometheus metrics (`cv_documents_processed_total{result}`, `cv_pipeline_stage_seconds_bucket{stage}`, `cv_queue_depth`, `cv_ocr_lock_wait_seconds`). Wire OpenTelemetry spans for each stage.
- Monorepo (optional in this step, depends on what the step file says): Grafana dashboard JSON in `infra/grafana/dashboards/cv-service.json` (this path IS tracked in the monorepo). Prometheus alerts in the infra polyrepo.
- Parallelizable with STEP-10. Use git worktrees if you want true parallelism:
  ```bash
  cd server/services/selection-committee-computer-vision
  git worktree add ../cv-service-step-09 feature/cv-step-09-observability
  ```

### STEP-10 — documents-service integration (Java) ∥ STEP-09

- documents-service polyrepo + monorepo (planning ticks; possibly `infra/postgres/init/01-create-dbs.sql` if a new DB-init line is needed — though `documents` DB already exists).
- New `ocr_results` Flyway migration (next migration number after the latest in `src/main/resources/db/migration/`). Schema: `id PK`, `document_id FK`, `fields JSONB`, `confidence numeric`, `status enum('parsed','failed')`, `error text NULL`, `retriable boolean NULL`, `trace_id text`, `created_at timestamptz`.
- New consumer for `cv.document.parsed` and `cv.document.failed` (use Spring AMQP `@RabbitListener`). **Idempotent** — upsert by `document_id`, ignore duplicates.
- New publisher: when a document upload completes, publish `CvDocumentRequestedEvent` to `cv.events` with routing key `cv.document.requested`. Wrap the publish in a circuit breaker (Resilience4j) — STEP-12 hardens this further.
- Tests: integration test with Testcontainers RabbitMQ + Postgres. Publish a fake parsed event → assert row exists. Publish duplicate → assert no duplicate row. Publish failed event → assert row with `status='failed'`.
- Contract test in `src/integrationTest/**/contract/` — deserialize a sample JSON identical to `cv-service/tests/events/samples/parsed.json` and assert the Java record decodes it.

### STEP-11 — Frontend integration (client/web polyrepo)

- Adds OCR result polling and auto-fill UX.
- Branch base: client/web polyrepo `develop` (assumed — verify).
- New TanStack Query hook `useOcrResult(documentId)` polling `GET /api/documents/{id}/ocr-result` every 2s while status is pending. Backoff to 10s after 30s elapsed.
- New UI component `<OcrAutoFillSuggestion>` — shows the parsed fields, lets the user accept (fills the surrounding form via React Hook Form's `setValue`) or reject (dismisses).
- **Verify with Playwright against the live UI** (per `feedback_verify_with_playwright` memory). Code review is NOT acceptance.

### STEP-12 — Boundary resilience (cv-service polyrepo + maybe documents-service)

- Circuit breakers, retry policies, idempotent consumers.
- cv-service: harden the consumer to never lose a message on restart (manual ack only after publish succeeds).
- documents-service: wrap the `cv.document.requested` publish in a Resilience4j circuit breaker. When open, log a warning but do NOT block the upload — cv-service downtime must never break upload (architectural invariant).

### STEP-13 — Performance & load validation (e2e-tests polyrepo)

- `server/services/selection-committee-e2e-tests/` is its own polyrepo. Default branch likely `develop` — verify.
- k6 scripts under `src/loadTest/` (or wherever the step file specifies).
- Targets the FLOW-07 SLOs: ≥ 50 docs/min/instance, queue lag < 1 min, **zero loss across CV restart**. The restart test is the hardest: kill cv-service mid-batch, start it back up, assert every document either has a result row or is still in flight.

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

Open both PRs simultaneously; wait for both to merge before STEP-11.

---

## Begin

**Start with STEP-03.** First action: read `progress/STEP-03-minio-adapter.md` end-to-end (or whatever the actual filename is — `ls progress/STEP-03*`), then read auto-loaded `server/CLAUDE.md` (it loads the moment you touch anything under `server/`), then write the failing MinIO integration test in `tests/storage/test_minio_adapter.py` before implementing anything.
