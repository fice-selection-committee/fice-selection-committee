# Execution Prompt — STEP-11 → STEP-14 (Final FLOW-07 / UC-02 Push)

## Mission

Close out FLOW-07 / UC-02. Four steps remain after STEP-09 + STEP-10 shipped:

| Step | Title | Polyrepos touched |
|---|---|---|
| 11 | Frontend integration (TanStack Query polling + auto-fill UX + Playwright E2E) | client/web polyrepo + monorepo (planning tick) |
| 12 | Boundary resilience (Resilience4j circuit breaker + idempotent consumer + UNAVAILABLE UX) | documents-service polyrepo + cv-service polyrepo + client/web polyrepo + monorepo (planning tick) |
| 13 | Performance & load validation (k6 + pytest-benchmark + nightly CI) | e2e-tests polyrepo + cv-service polyrepo + monorepo (`.github/workflows/cv-load-test.yml`?) |
| 14 | Documentation + sc-libs 1.4.0 final bump | cv-service polyrepo + monorepo + ALL 7 backend service polyrepos (libs.versions.toml) |

All four are **strictly sequential**. STEP-11 ∥ nothing — STEP-12 reads STEP-11's UI patterns, STEP-13 reads STEP-12's resilience metrics, STEP-14 documents everything and bumps the lib version everyone depends on.

---

## Pre-Flight (re-confirm before EVERY step)

| Repo | Default branch | Expected origin/HEAD before STEP-11 starts |
|---|---|---|
| monorepo | `main` | `fe18456 chore(cv): tick STEP-10 done in tracker (#49)` |
| cv-service polyrepo | `main` | `21b8da5 feat(cv): STEP-09 -- observability ... (#8)` |
| infra polyrepo | `master` | `fe34cda feat(infra): cv-service observability ... (#9)` |
| documents-service polyrepo | `develop` | `0a95cbd feat(documents): STEP-10 -- CV pipeline integration (#39)` |
| client/web polyrepo | `develop` (verify) | last `develop` HEAD as of run start |
| e2e-tests polyrepo | `develop` (verify) | last `develop` HEAD as of run start |
| identity / admission / environment / notifications / gateway / telegram-bot polyrepos | `develop` | only touched in STEP-14 (sc-libs 1.4.0 bump) |

**If any HEAD has moved**, fast-forward and re-validate STEP-10's gates locally before starting STEP-11. Stale base = merge churn = wasted cycle.

---

## Polyrepo Split — re-read this before EVERY step

Per `.gitignore` in monorepo:
- `server/services/*` → each is its own polyrepo (gitignored from monorepo).
- `infra/*` → its own polyrepo (`selection-committee-infra`); narrow exceptions tracked in monorepo too:
  - `infra/docker-compose.services.yml`
  - `infra/grafana/dashboards/**`
  - `infra/.env.example` (grandfathered — tracked in BOTH repos; edits show as diffs in both).
- `client/` → its own polyrepo.

**Polyrepo workflow per step**:
1. `cd <polyrepo>`
2. `git fetch origin && git checkout <default> && git pull --ff-only`
3. `git checkout -B feature/cv-step-NN-<slug> origin/<default>` — `-B` so a stale local branch from a prior aborted run does not pin the wrong base.
4. **Do NOT touch unrelated dirty files**. Several polyrepos carry in-flight WIP unrelated to the CV rollout:
   - `selection-committee-infra` carries notification-center WIP on `feature/cv-step-02-rabbitmq-topology` (uncommitted prometheus/alerts.yml, .env.example, docker-compose.services.yml additions). LEAVE IT ALONE.
   - `selection-committee-documents-service` carries V4 rejection-flow WIP (untracked `V4__add_document_rejection_reason.sql`, modified DocumentDto / DocumentController / DocumentService and a fleet of integration tests). LEAVE IT ALONE.
   - `client/web` carries the dashboard-redesign WIP on `feature/dashboard-redesign` (modified .gitignore / CLAUDE.md / package.json / playwright config / public assets). LEAVE IT ALONE.
   - `selection-committee-e2e-tests` carries develop-branch WIP on every flow test class. LEAVE IT ALONE.
   - The monorepo carries `infra/.env.example` and `infra/docker-compose.services.yml` deletions in the working tree (a partial rollback of the cv-service entries); LEAVE THEM ALONE.

   STEP-09 and STEP-10 both proved the pattern: `git stash push -m "STEP-NN stash: unrelated <slug> WIP" -u` → `git checkout -B feature/... origin/<default>` → work → commit → push → leave the stash where it is. Do **not** pop other people's stashes; they belong to the user's other in-progress work.

**Monorepo tracker tick** is its own small PR at the end of each step, after all polyrepo PRs merge. Branch name `feature/cv-step-NN-tracker-tick` from a freshly-pulled `main`.

---

## Operating Rules (non-negotiable, carried over from STEP-08/09/10)

1. **Read first** — open `progress/STEP-NN-*.md` end-to-end before doing anything. Do not paraphrase from memory.
2. **Sequential execution** — STEP-11 → STEP-12 → STEP-13 → STEP-14, no exceptions. Do not start STEP-N+1 until STEP-N's "Definition of Done" is fully ticked AND its PR(s) merged.
3. **Test-first inside each step** — write the tests listed under "Tests / Acceptance Gates" before implementation; confirm they fail for the right reason (`ModuleNotFoundError`, `cannot find symbol`, `404 vs expected 200`, missing fixture file all count); then make them pass.
4. **Gating** — a step is done only when (a) every "Files to Create" exists, (b) every "Files to Modify" reflects the listed change, (c) every "Acceptance Gate" test passes, (d) lint/format/typecheck/build for the affected stack is clean.
   - cv-service polyrepo: `poetry run ruff check src tests && poetry run ruff format --check src tests && poetry run mypy --strict src && poetry run pytest -q -m "not slow"`
   - Java service: `cd server && ./gradlew :libs:sc-event-contracts:publishToMavenLocal` (only if libs changed) then `cd server/services/selection-committee-<svc> && ./gradlew check` (integrationTest is a separate task; CI runs it when Docker is available).
   - Frontend: `cd client/web && pnpm lint && pnpm typecheck && pnpm test && pnpm build`
   - Compose-touching steps: `cd infra && docker compose -f docker-compose.yml -f docker-compose.services.yml config --quiet`. Full-stack readiness via `docker compose ... up -d --wait` (needs `infra/secrets/` populated; `bash infra/scripts/gen-jwt-keys.sh` once if missing).
5. **One feature branch per step per repo** — `feature/cv-step-NN-<slug>`. Never bypass hooks (`--no-verify` is forbidden).
6. **Update the tracker** — flip the `progress/README.md` row from ⏳ to ✅, tick "Definition of Done" boxes inside `progress/STEP-NN-*.md`, add a "Regressions Caught" section if anything non-obvious was fixed. Keep the dense one-line format used for STEP-01..10.
7. **Auto-loaded CLAUDE.md** — when you touch files under `server/`, `client/web/`, `infra/`, or `client/web/tests/`, the corresponding `CLAUDE.md` auto-loads. Its rules take precedence.
8. **Architectural invariants** — do NOT violate:
   - cv-service stays **stateless** — no DB, no gateway route, no public HTTP endpoint other than `/health` and `/metrics`. Persistence belongs to documents-service (`ocr_results` table — STEP-10).
   - Communication is **RabbitMQ + S3 only**. No Feign client to cv-service.
   - PaddleOCR models are **pre-baked into the Docker image**. Cold-start downloads are forbidden.
   - PaddleOCR `.ocr()` is **not thread-safe** — already serialised via `asyncio.Lock` on `OcrEngine` (STEP-05). Reuse it.
   - Field extraction is stateless and synchronous (STEP-06). Unknown types → `UnsupportedDocumentTypeError` (terminal).
   - Messaging owns transport, not business logic (STEP-07). The orchestrator (STEP-08) is the single business handler.
   - documents-service publish is **best-effort with circuit breaker** (STEP-12 finishes wiring this). cv-service downtime must never break upload.
   - Frontend authorization is **UX, not security** — never place a guard not also enforced by the backend (per project memory).
9. **No scope creep** — implement exactly what the step specifies. If you spot a gap, stop and ask before adding scope.
10. **Best-practice defaults** — when ambiguous, choose the simplest option that satisfies the spec, the architectural invariants above, and existing patterns in the repo. Document any deviation under "Regressions Caught" in the step file.
11. **Never touch unrelated polyrepo churn** — see the per-repo WIP list above. The `progress/EXECUTION-PROMPT-STEP-*-TO-14.md` files are local notes; intentionally untracked; not part of any PR.
12. **Auto-merge mode is ON by default for this run** — open each PR, run gates green locally, squash-merge each PR (`gh pr merge <num> --squash --delete-branch`), sync defaults, continue. Always report each merged PR URL in the per-step closing message. The user can override at any time ("stop merging", "wait for review", etc.).
13. **Pause and ask only for**:
    - Ambiguity that the step file does not resolve and "best practice" cannot disambiguate.
    - Architectural deviations you believe are necessary (e.g., another polyrepo split discovery, an sc-libs bump outside STEP-14, dropping a dep that the spec named).
    - Production credentials or PII (never invent these).
    - Anything destructive that lacks prior authorization (force-push to default, deleting branches, etc.).
14. **Do not ask for**:
    - "Should I proceed?" between substeps within one STEP file — just proceed.
    - "Should I merge?" — auto-merge mode is on; merge after gates green.
    - Naming bikesheds — follow what the step file specifies.
    - Lint-rule disputes — follow each repo's existing config.

---

## Per-Step Workflow

For each STEP-NN starting with STEP-11:

1. **Branch off** clean default in every affected repo (see Pre-Flight table). Use `git checkout -B feature/cv-step-NN-<slug> origin/<default>` to force-pin to the remote.
2. **Read** `progress/STEP-NN-*.md` end-to-end. Read every auto-loaded `CLAUDE.md` whose subtree you'll touch.
3. **Write failing tests first** for each "Acceptance Gate". Confirm they fail for the right reason.
4. **Implement** files in the order under "Implementation Outline".
5. **Run the full acceptance-gate test list**. Iterate until green.
6. **Run lint/format/typecheck/build** for the affected stack.
7. **Validate compose changes**: `cd infra && docker compose -f docker-compose.yml -f docker-compose.services.yml config --quiet`. For full-stack readiness: `docker compose ... up -d --wait` and confirm every container is `(healthy)`.
8. **Tick "Definition of Done"** checkboxes inside `progress/STEP-NN-*.md`. Add "Regressions Caught" if any non-obvious fix was needed. Keep these edits **uncommitted** in the monorepo until the step's polyrepo PR(s) merge.
9. **Update the status row** in `progress/README.md` (also kept uncommitted until polyrepo merge). Match the dense one-line format used for STEP-01..10.
10. **Commit per repo** — match the relevant prefix:
    - cv polyrepo: `feat(cv): STEP-NN -- <slug>`
    - documents-service polyrepo: `feat(documents): STEP-NN -- <slug>`
    - client/web polyrepo: `feat(web): STEP-NN -- <slug>`
    - e2e-tests polyrepo: `feat(e2e): STEP-NN -- <slug>`
    - infra polyrepo: `feat(infra): STEP-NN -- <slug>`
    - monorepo tracker tick: `chore(cv): tick STEP-NN done in tracker`
    Read `git log --oneline -5` first to match house style.
11. **Push and open one PR per repo**. Title `<prefix>: STEP-NN — <slug>`. Body sections: `## Summary`, `## What's in this PR`, `## Test plan`, `## Peer PRs (STEP-NN)`, `## Notes`.
12. **In auto-merge mode**: squash-merge each PR (`gh pr merge <num> --squash --delete-branch`); sync local defaults; continue.
13. **After polyrepo merges** — branch the monorepo off freshly-pulled `main`, commit progress edits as `chore(cv): tick STEP-NN done in tracker`, push, open and merge the tracker-tick PR.
14. **Sync** all touched repos' local default branches before starting STEP-(NN+1).

---

## Hook-Enforced Conventions (do not work around)

- `.claude/hooks/validate-branch-name.py` — branches must match `feature/<name>`, `release/v<MAJOR>.<MINOR>.<PATCH>`, or `hotfix/<name>`. Use `feature/cv-step-NN-<slug>` everywhere.
- `.claude/hooks/prevent-direct-push.py` — direct push to `main` / `develop` / `master` is blocked. Use feature branches and PRs.

---

## Cross-Cutting Reminders (carried over)

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
- **Pytest filterwarnings quarantine list**: paddle / paddleocr / ppocr / google.protobuf / astor / testcontainers DeprecationWarning instances are silenced. Any new transitive that throws Python-3.12-deprecation noise during `cv_service.*` import-chain needs an additive entry.
- **OTel `_TRACER_PROVIDER_SET_ONCE._done` reset** is the standard escape hatch for swapping providers between test cases (STEP-09 regression).
- **prom_client metric reset** uses in-place `Counter.clear()` / `_value.set(0)` — never unregister/re-register (would invalidate test imports). STEP-09 regression.
- **JaCoCo 80%/file is per-file**, not aggregate. Every new Java file needs a unit-test counterpart; integration tests don't count when Docker is absent on the dev host. STEP-10 regression.
- **404 mapping in documents-service** uses `sc-common`'s `ResourceNotFoundException`; the local `DocumentNotFoundException` is unmapped (existing convention → 500). STEP-10 regression.
- **`MockedStatic<AuthUtils>`** for `@WebMvcTest` slices that hit `AuthUtils.getUserFromContext()`. STEP-10 regression.
- **`@Spy ObjectMapper`** for `@RabbitListener` unit tests so `@InjectMocks` doesn't leave it null. STEP-10 regression.

### STEP-05/06/07/08/09/10 surface lessons inherited

- cv-service public surfaces (do NOT redeclare):
  - `from cv_service.events import CvDocumentRequestedEvent, CvDocumentParsedEvent, CvDocumentFailedEvent, CV_*`
  - `from cv_service.extraction import extract, ExtractionResult, ExtractionError, UnsupportedDocumentTypeError`
  - `from cv_service.messaging import CvConsumer, CvPublisher, IdempotencyCache, MessageContext, MessagingError, TransientError, TopologyError, verify_topology`
  - `from cv_service.orchestrator import RetryPolicy, run_pipeline; from cv_service.orchestrator.pipeline import DefaultPreprocessor`
  - `from cv_service.resilience import OcrBreaker, BreakerOpenError, classify`
  - `from cv_service.observability import (timed_stage, start_pipeline_span, start_stage_span, inject_headers, extract_context, record_parsed, record_failed, record_dlq_routed, record_idempotent_skip, record_breaker_state, setup_tracing, reset_tracer)`
- `_silence_ppocr_loggers()` (in `cv_service.ocr.engine`) demotes `ppocr` and `paddleocr` Python loggers to WARNING+. STEP-09's observability wiring did NOT reverse this; STEP-13's k6 / pytest-benchmark must NOT either.
- `OcrEngine._lock` is the SINGLE serialisation point for paddle inference. Reuse it.
- `CV_OCR_EAGER_LOAD=false` for tests — set in `tests/conftest.py`.
- Per-stage timeouts (STEP-08): download 30s, preprocess 15s, OCR 60s, extract 5s.
- `OcrBreaker`: async-native (NOT pybreaker). `fail_max=5`, `reset_timeout=30s`, `OcrInputError` excluded from fail count.
- `_ERROR_CODE_TABLE` (in `cv_service.orchestrator.pipeline`) maps exceptions to short error codes. STEP-12 must NOT change the codes (frontend reads them as labels).
- documents-service polyrepo:
  - `OcrResultStatus`: `PENDING` / `PARSED` / `FAILED`. The frontend (STEP-11) consumes the `OcrResultDto` shape **verbatim** from `GET /api/v1/documents/{id}/ocr`.
  - `DocumentService.confirm` already triggers CV publish for `passport` / `ipn` / `foreign_passport` (STEP-10). STEP-12 wraps the publish in a Resilience4j circuit breaker.
  - V5 is the latest applied migration. STEP-12 likely needs no new migration; if it does, use the next free number after coordinating with the user's pending V4 rejection-flow WIP.

---

## Bug-Fix Protocol (when tests fail mid-step)

1. Reproduce with the exact command from the step's "Acceptance Gates".
2. Write a regression test that reproduces it.
3. Fix the root cause. Do not bypass with mocks, `pytest.skip`, `@Disabled`, or `--no-verify`.
4. Confirm the new regression test and the original suite both pass.
5. Document the fix in the step file under "Regressions Caught".

---

## Reporting

After each step closes, post a single message to the user in the EXACT shape used at the end of STEP-09 / STEP-10:

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

These are gotchas spotted while reading the step files. **Read each step file itself for the full spec.**

### STEP-11 — Frontend integration (client/web polyrepo)

- Branch base: `client/web` polyrepo `develop` (verify with `git branch --show-current` after fetch). The polyrepo currently sits on `feature/dashboard-redesign` with significant dashboard-redesign WIP. Stash → branch off origin/develop per the pattern.
- New TypeScript types in `src/types/ocr.ts` mirroring the documents-service `OcrResultDto`:
  - `OcrResultStatus = 'PENDING' | 'PARSED' | 'FAILED'`
  - `OcrResult = { documentId, status, fields?, confidence?, errorReason?, retriable?, traceId, createdAt, updatedAt }`
  - Field set is `Record<string, string>` per cv-service contract.
- New TanStack Query hook `useOcrResult(documentId)` hitting `GET /api/v1/documents/{id}/ocr`. Spec interval (verify against STEP-11 file): poll every 2s while `status === 'PENDING'`; backoff after 30s elapsed; stop polling once `status` is `PARSED` or `FAILED`. Use the existing Axios client (`src/lib/api/`) — do NOT introduce a new HTTP client.
- New UI components: `<OcrResultCard>` (confidence-banded display + auto-fill CTA), `<OcrConfidenceBadge>`, `<OcrFieldRow>`. The auto-fill CTA fills the surrounding form via React Hook Form's `setValue`.
- Tests: Vitest unit tests for the hook + components, Playwright E2E (`cv-ocr-flow.spec.ts`) against the **live UI**. **Verify with Playwright per `feedback_verify_with_playwright` memory** — code review is NOT acceptance. Start the dev server, exercise the flow in a browser, monitor for regressions in surrounding screens.
- Test fixture: synthetic passport image in `tests/fixtures/`. Use the same Cyrillic mixed-case convention as cv-service tests (`Шевченко`, `Тарас`).
- Auto-loaded `client/web/CLAUDE.md` mandates: TanStack Query for server state (NOT mixed with Zustand), React Hook Form + Zod for forms (NOT alternative libs), `src/proxy.ts` is the only auth boundary on the frontend (do not bypass).
- The frontend is Next.js 16+ (per project memory). Do not assume earlier Next.js conventions.
- Tracker-tick PR after merge.

### STEP-12 — Boundary resilience (documents-service polyrepo + cv-service polyrepo + client/web polyrepo)

- documents-service:
  - Wrap `CvRequestPublisher#publish` in `@CircuitBreaker(name="cvPublisher", fallbackMethod="publishFallback")` (Resilience4j Spring Boot 3 starter is already in `libs.versions.toml`). Fallback logs a warning, increments a metric, and does NOT block the upload.
  - Tighten the consumer's idempotency. Existing UPSERT-by-`document_id` from STEP-10 covers the happy path; STEP-12 adds an explicit "duplicate parsed event silently dropped" test case (the integration test already covers this; verify it remains green).
  - Add Resilience4j config under `application.yml` (`resilience4j.circuitbreaker.instances.cvPublisher.*`) — failure-rate-threshold, sliding-window-size, wait-duration.
- cv-service:
  - Harden the consumer to never lose a message on restart. STEP-07's consumer acks AFTER the handler completes; STEP-12 must verify the publish-then-ack ordering and add a regression test that kills cv-service mid-flight (use `aio_pika`'s `AbstractRobustConnection.close()` mid-handler).
  - No new public surface; this is a verification + hardening pass.
- Frontend:
  - `useOcrResult` adds an `UNAVAILABLE` derived state after 60s of polling without a 200 (or 5 consecutive 5xx). `<OcrResultCard>` renders a soft "OCR temporarily unavailable" message; **no error toast, no blocked form**.
  - Vitest unit test `use-document-ocr-unavailable.test.ts` + a Playwright case asserting the form remains submittable while OCR is unavailable.
- Tests:
  - documents-service integration: `CvPublisherCircuitBreakerTest` (broker down → fallback path), `CvUploadResilienceTest` (upload succeeds while cv-service is down).
  - cv-service integration: existing messaging suite + new "killed mid-flight" test.
  - Frontend: `use-document-ocr-unavailable.test.ts` plus Playwright "form still submittable" case.
- Tracker-tick PR after all three polyrepo merges.

### STEP-13 — Performance & load validation (e2e-tests polyrepo + cv-service polyrepo + monorepo)

- e2e-tests polyrepo. Default branch likely `develop` — verify after fetch.
- k6 scenario `load/cv-pipeline.js` reading Prometheus metrics. Targets:
  - **≥ 50 docs/min/instance throughput**.
  - **Queue lag < 1 minute at peak load** (read `rabbitmq_queue_messages{queue="cv.document.requested"}` from Prometheus).
  - **Zero loss across CV restart** (kill cv-service mid-batch via `docker compose restart cv-service`; assert every document either has a result row or is still in flight).
- pytest-benchmark suites in `cv-service/benches/bench_pipeline.py` and `bench_extractors.py` — micro-benchmarks for individual stages. Use `pytest-benchmark` (add to `[tool.poetry.group.dev.dependencies]`).
- Synthetic IPNs use `cv_service.extraction.ipn.compute_ipn_check_digit`; synthetic MRZ uses `cv_service.extraction.foreign_passport.compute_mrz_check_digit`. **Never hard-code check digits.**
- Optional: nightly CI workflow `.github/workflows/cv-load-test.yml` (monorepo). If the workflow is monorepo-tracked but pulls polyrepos for execution, ensure GH PAT permissions are right; otherwise scope the workflow to a single repo and document the deviation.
- Regenerate the cv Grafana dashboard panels (or add new ones) only if STEP-13 specifies — otherwise reuse what STEP-09 shipped.
- Tracker-tick PR after merge.

### STEP-14 — Documentation + final sc-libs bump (cv-service polyrepo + monorepo + ALL backend service polyrepos)

This step is **the largest and most coordinated** in the rollout. It touches 9+ repos.

- cv-service polyrepo:
  - Full `README.md` expansion — architecture diagram, dev workflow, Docker workflow, troubleshooting, observability, SLOs.
  - New `CLAUDE.md` (auto-loaded) — operational rules + run commands. Mirror the structure of `server/CLAUDE.md` and `client/web/CLAUDE.md`.
- Monorepo:
  - `docs/architecture/cv-service.md` (or extend the existing CV section in `docs/claude/architecture.md`).
  - Update `docs/flows/FLOW-07_*.md` with an "As-Implemented" section.
  - Update `docs/use-cases/UC-02_*.md` with the live OCR endpoint reference.
  - New `docs/runbooks/cv-service.md` — DLQ inspection, retry, troubleshooting.
- **Final sc-libs bump 1.3.3 → 1.4.0**:
  - `server/version.properties` — bump.
  - `server/gradle/libs.versions.toml` (in monorepo) — bump.
  - All 7 consumer `gradle/libs.versions.toml` files in: identity, admission, environment, documents, notifications, gateway, telegram-bot polyrepos — bump.
  - `./gradlew :libs:sc-event-contracts:publishToMavenLocal` from `server/`, then `./gradlew check` in EACH consumer service to validate.
  - This is **7+ polyrepo PRs in one step**. Open them in parallel, merge as gates go green.
- Tick STEP-14 ✅ in `progress/README.md`. Add a "FLOW-07 / UC-02 — DELIVERED" stamp at the top with the final PR list.
- This is also a good moment to clean up the `progress/EXECUTION-PROMPT-STEP-*-TO-14.md` files (they are local notes and never tracked, but the user may want them removed).

---

## Parallel Execution

**Inside a step, never parallelise** — each step's polyrepo PRs depend on the same base state and on each other (cv polyrepo's PR may need to be open + merged before infra polyrepo's, etc.).

**STEP-14 is the only exception**: 7 service polyrepos all do the same `sc-libs 1.3.3 → 1.4.0` bump. Open all 7 PRs in parallel; merge as each one's `./gradlew check` goes green.

---

## Begin

**Start with STEP-11.** First action: confirm pre-flight HEADs in every relevant repo, then read `progress/STEP-11-*.md` end-to-end (`ls progress/STEP-11*` to confirm filename), then write the failing tests before implementing anything.

Confirm before starting that:
- monorepo `origin/main` HEAD is `fe18456 chore(cv): tick STEP-10 done in tracker (#49)`
- cv-service polyrepo `origin/main` HEAD is `21b8da5 feat(cv): STEP-09 -- observability ... (#8)`
- infra polyrepo `origin/master` HEAD is `fe34cda feat(infra): cv-service observability ... (#9)`
- documents-service polyrepo `origin/develop` HEAD is `0a95cbd feat(documents): STEP-10 -- CV pipeline integration (#39)`
- client/web / e2e-tests polyrepos' default branches are checked out and freshly pulled (HEAD will be whatever it is at run start)

If any HEAD has moved, fast-forward and re-validate STEP-10's gates locally before starting STEP-11. Starting from a stale base creates merge churn.

**Auto-merge mode is ON for this run.** Open each PR, run gates green locally, squash-merge each PR (`gh pr merge <num> --squash --delete-branch`), sync default branches, continue. Always report each merged PR URL in the per-step closing message.

**For STEP-11 specifically**: per `feedback_verify_with_playwright` project memory, you MUST run Playwright against the live UI (start the dev server, exercise the flow, monitor for regressions in surrounding screens) before claiming STEP-11 done. Code review and unit tests alone are NOT acceptance for frontend work in this repo.
