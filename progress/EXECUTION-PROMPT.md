# Execution Prompt — Computer Vision System (STEP-02 → STEP-14)

> **v2** — supersedes the v1 prompt. Hardened with everything learned during STEP-01: the polyrepo split, toolchain bootstrap, hook-enforced workflows, and the three Dockerfile pitfalls already burned (and recorded as "Regressions Caught" in `progress/STEP-01-cv-service-scaffold.md`). Copy-paste this whole file into a fresh Claude Code session opened at `D:\develop\fice-selection-committee` to drive STEP-02 onward to completion.

---

## PROMPT

You are continuing the Computer Vision system rollout for the FICE Selection Committee project. STEP-00 and STEP-01 are merged on `main` (PRs #37, #39); the polyrepo `fice-selection-committee/selection-committee-computer-vision` is seeded at `main = c4fe0f4`. You execute **STEP-02 through STEP-14** in order. The plan is approved — do not redesign, execute.

### Mission

Deliver, end to end, an event-driven Python (FastAPI + PaddleOCR) CV microservice that consumes `cv.document.requested` from RabbitMQ, runs `download → preprocess → OCR → field-extract`, and publishes `cv.document.parsed` / `cv.document.failed`. Integrate with documents-service (Java) for persistence and with the Next.js frontend for auto-fill. Match FLOW-07 SLOs: ≥ 50 docs/min/instance, queue lag < 1 min, zero message loss across CV restarts.

### Current State (read once at session start)

| Item | State | Where |
|---|---|---|
| Plan tracker | STEP-00 + STEP-01 ✅; STEP-02 → STEP-14 ⏳ | `progress/README.md` |
| cv-service polyrepo | seeded on `main` at commit `c4fe0f4` (FastAPI shell, /health + /metrics, 56 MB image) | https://github.com/fice-selection-committee/selection-committee-computer-vision |
| Auto-load rules | `server/CLAUDE.md`, `infra/CLAUDE.md`, `client/web/CLAUDE.md`, root `CLAUDE.md` | already merged |
| Compose entry | `cv-service` block in `infra/docker-compose.services.yml` (depends on rabbitmq + minio-init) | already merged |
| Env vars | `CV_*` block in `infra/.env.example` | already merged |
| sc-libs version | 1.3.2 (bumped to 1.3.3 in STEP-02; final bump to 1.4.0 in STEP-14) | `server/version.properties` |

### The Polyrepo Split — read this before EVERY step that touches cv-service code

Per the project's `.gitignore`, **every directory under `server/services/`** is a separately-cloned git repo. cv-service is at `fice-selection-committee/selection-committee-computer-vision`. This means each step that touches cv-service Python source produces **two PRs**:

- **Polyrepo PR** (cv-service): `pyproject.toml`, `Dockerfile`, `src/cv_service/**`, `tests/**`, `.github/workflows/**`. Branch: `feature/cv-step-NN-<slug>`. Base: polyrepo's `main`.
- **Monorepo PR** (this repo): only orchestration glue — `infra/docker-compose.services.yml`, `infra/.env.example`, `progress/STEP-NN-*.md` (DoD ticks), `progress/README.md` (status). For STEP-10 also: Java code under `server/services/selection-committee-documents-service/` lives in **its own polyrepo** — same pattern.

Most cv-service-only steps (STEP-03, STEP-04, STEP-05, STEP-06, STEP-07, STEP-08, STEP-09) are **polyrepo-only PRs**. Steps that touch contracts, compose, frontend, or Java services need monorepo PRs too:

| Step | Polyrepo PR? | Monorepo PR? | Java repos? |
|---|---|---|---|
| 02 | — | yes (libs/sc-event-contracts → 1.3.3) | identity, admission, documents (consumers rebuild) |
| 03 | yes (cv-service) | — | — |
| 04 | yes (cv-service) | — | — |
| 05 | yes (cv-service) | — | — |
| 06 | yes (cv-service) | — | — |
| 07 | yes (cv-service) | — | — |
| 08 | yes (cv-service) | — | — |
| 09 | yes (cv-service) | maybe (Grafana dashboard, prometheus rules) | — |
| 10 | — | yes (`infra/postgres/init`, planning ticks) | **documents-service** (Flyway migration + consumer + ocr_results table) |
| 11 | — | yes (planning ticks only) | **client/web** (Next.js — also a polyrepo at `selection-committee-web`) |
| 12 | yes (cv-service circuit breakers) | maybe | documents-service if publish-side resilience |
| 13 | — | yes (k6 scripts under `server/services/selection-committee-e2e-tests/` — its own polyrepo) | e2e-tests polyrepo |
| 14 | yes (README expansion) | yes (sc-libs 1.4.0 bump + planning closure) | all consumers (sc-libs final bump) |

For a polyrepo, run from inside `server/services/<repo>/`. For the monorepo, run from `D:\develop\fice-selection-committee`.

### Operating Rules (non-negotiable)

1. **Read first** — open the step's `progress/STEP-NN-*.md` end to end before doing anything. Do not paraphrase from memory.
2. **Sequential execution** — follow the dependency graph in `progress/README.md`. Do not start STEP-N+1 until STEP-N's "Definition of Done" is fully ticked AND its PR(s) merged. STEP-09 and STEP-10 are the only parallelizable pair (run them in two worktrees if you want speed).
3. **Test-first inside each step** — write the tests listed under "Tests (Acceptance Gates)" before the implementation, watch them fail for the right reason, then make them pass. TDD is mandatory per `CLAUDE.md`.
4. **Gating** — a step is done only when (a) every "Files to Create" exists, (b) every "Files to Modify" reflects the listed change, (c) every "Acceptance Gate" test passes, (d) lint / format / typecheck / build for the affected stack is clean: Python `ruff check && ruff format --check && mypy --strict && pytest -q`; Java `./gradlew check`; Frontend `pnpm lint && pnpm build && pnpm test`.
5. **One feature branch per step per repo** — `feature/cv-step-NN-<slug>` in each affected repo. Never bypass hooks (no `--no-verify`).
6. **Update the tracker** — after each step's PRs are merged, edit `progress/README.md` to flip the row from `⏳ TODO` to `✅ done — <one-line note>`, AND tick "Definition of Done" checkboxes inside the step file. Add a "Regressions Caught" section to the step file documenting any non-obvious fix made during execution.
7. **Auto-loaded `CLAUDE.md`** — when you touch files under `server/`, `client/web/`, `infra/`, or `client/web/tests/`, the corresponding `CLAUDE.md` auto-loads. Its rules take precedence over generic guidance.
8. **Architectural invariants** — do not violate:
   - cv-service is **stateless** — no DB, no gateway route, no public HTTP endpoint other than `/health` and `/metrics`.
   - Persistence belongs to documents-service (`ocr_results` table — STEP-10).
   - Communication is **RabbitMQ + S3 only**. No Feign client to cv-service.
   - PaddleOCR models are **pre-baked into the Docker image** (STEP-05). Cold-start downloads are forbidden.
   - PaddleOCR `recognize()` is **not thread-safe** — serialize via `asyncio.Lock` (STEP-08).
   - documents-service publish is **best-effort** with circuit breaker (STEP-12) — cv-service downtime must never break upload.
9. **No scope creep** — implement exactly what the step specifies. If you spot a gap or ambiguity, **stop and ask the user before adding scope.**

### Toolchain Bootstrap (do this once at session start, before STEP-02)

If `poetry --version` fails, the local toolchain is missing. Install via `uv` (single binary, no admin):

```bash
# PowerShell
irm https://astral.sh/uv/install.ps1 | iex

# bash
export PATH="/c/Users/$USER/.local/bin:$PATH"
uv python install 3.12
uv tool install poetry==2.3.4
poetry --version  # → 2.3.4
```

For each polyrepo with a `pyproject.toml`:

```bash
cd server/services/selection-committee-computer-vision
poetry env use 3.12
poetry install --without ml   # add `--with ml` from STEP-04 onward
```

For Java work (STEP-02, STEP-10): `java -version` must report 21. Gradle wrapper is checked in.

### Per-Step Workflow

For each STEP-NN starting with STEP-02:

1. **Branch off clean main** in every affected repo:
   ```bash
   git checkout main && git pull --ff-only && git checkout -b feature/cv-step-NN-<slug>
   ```
2. Read `progress/STEP-NN-*.md` end to end. Read every auto-loaded `CLAUDE.md` whose subtree you'll touch.
3. **Write failing tests first** for each "Acceptance Gate". Confirm they fail for the right reason — module-not-found and import errors count, but a test that passes by accident does not.
4. Implement files in the order under "Implementation Outline".
5. Run the full acceptance-gate test list. Iterate until green.
6. Run lint / format / typecheck / build for the affected stack:
   - **cv-service polyrepo**: `poetry run ruff check src tests && poetry run ruff format --check src tests && poetry run mypy --strict src && poetry run pytest -q`
   - **Java**: `cd server && ./gradlew :sc-event-contracts:publishToMavenLocal` (whenever a `server/libs/` lib changed) **then** `cd server/services/selection-committee-<svc> && ./gradlew check`
   - **Frontend**: `cd client/web && pnpm lint && pnpm build && pnpm test`
7. **Validate compose changes**: `cd infra && docker compose -f docker-compose.yml -f docker-compose.services.yml config --quiet`. For full-stack readiness: `docker compose ... up -d --wait` and confirm every container reaches `(healthy)`.
8. Tick "Definition of Done" checkboxes inside `progress/STEP-NN-*.md`. Add "Regressions Caught" if any non-obvious fix was needed.
9. Update the status row in `progress/README.md`.
10. Commit per repo (`feat(cv): step-NN <slug>` in cv polyrepo, `feat(cv): STEP-NN — <slug>` in monorepo). Read recent `git log --oneline -5` first to match house style.
11. Push and open one PR per repo. Title: `feat(cv): STEP-NN — <slug>`. Body: a `## Summary`, a `## What's in this PR`, a `## Test plan` checklist, links to peer PRs.
12. **Wait for the user to merge before STEP-(NN+1).** Do not start the next step until all of the current step's PRs are merged on `main`.

### Hook-Enforced Conventions (do not work around)

The repo has Claude-Code hooks that block non-conforming git operations. They are enforced; treat their messages as gospel:

- **`.claude/hooks/validate-branch-name.py`** — every branch must match `feature/<name>`, `release/v<MAJOR>.<MINOR>.<PATCH>`, or `hotfix/<name>`. `chore/...`, `wip/...`, `feat/...`, `fix/...` are rejected. Use `feature/cv-step-NN-<slug>` for every CV step.
- **`.claude/hooks/prevent-direct-push.py`** — direct push to `main` or `develop` is blocked in every repo (the hook reads the cwd of the bash invocation). Use feature branches and PRs always. For seeding a brand-new polyrepo `main`, use the GitHub API trick recorded in `progress/STEP-01-cv-service-scaffold.md` Architectural Note (PATCH default-branch + create-ref + restore default).

### Cross-Cutting Reminders

- **Build chain**: any change to `server/libs/sc-event-contracts/` (STEP-02) requires `./gradlew :sc-event-contracts:publishToMavenLocal` from `server/` **before** building any consumer service. Skipping this causes stale-artifact failures (project memory: `project_build_setup`).
- **sc-libs version bumps**: `1.3.2 → 1.3.3` happens in STEP-02 (event contract additions). `1.3.3 → 1.4.0` in STEP-14 as the final commit.
- **Ukrainian-language assets**: PaddleOCR `lang="uk"` covers Cyrillic + Latin. Test fixtures with Ukrainian text **must use real Cyrillic strings**, never transliterations.
- **No real PII**: every test fixture (passport, IPN, foreign passport) is synthetic. Generate IPN values via the mod-11 algorithm; never use real ones.
- **Docker validation**: every step that touches `infra/` runs `cd infra && docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --wait` and confirms all services reach `(healthy)`. The full stack needs `infra/secrets/` populated — run `bash infra/scripts/gen-jwt-keys.sh` once if missing.
- **FastAPI healthchecks**: `wget --spider` (HEAD) returns 405. Use `wget -q -O - http://localhost:PORT/health > /dev/null` (GET, output discarded) — recorded as a regression in STEP-01.
- **Poetry 2.x quirks**: `--only-root` cannot combine with `--with`/`--without`. Two-pass install: `poetry install --without ml,dev --no-root` then `poetry install --only-root`. Editable-install `.pth` files hard-code `WORKDIR` — keep `WORKDIR /app` consistent across multi-stage builds.
- **Frontend verification (STEP-11+)**: per project memory `feedback_verify_with_playwright`, run Playwright against the live UI. Code review alone is not acceptance.

### Bug-Fix Protocol (when tests fail)

If during execution any test (existing or new) regresses:

1. Reproduce the failure with the exact command from the step's "Acceptance Gates".
2. Write a regression test that reproduces it (per `docs/claude/testing.md` bug-fix mandate).
3. Fix the root cause. Do not bypass with mocks, `pytest.skip`, or `@Disabled`.
4. Confirm both the new regression test and the original suite pass.
5. Document the fix in the step file under a "Regressions Caught" section so future steps inherit the lesson.

### What to Ask the User About

Pause and ask **only** for:
- Ambiguity in a step that the file does not resolve.
- Architectural deviations you believe are necessary (e.g., another polyrepo split discovery).
- Production credentials or PII (never invent these).
- Approval before merging each step's PR(s).

Do **not** ask for:
- "Should I proceed?" between substeps within one STEP file — just proceed.
- Naming bikesheds — follow what the step file specifies.
- Lint-rule disputes — follow each repo's existing config.

### Reporting

After each step closes, post a single message to the user:

```
✅ STEP-NN <slug> complete
- Branch(es): feature/cv-step-NN-<slug> in <repo(s)>
- PR(s): <url1> [+ <url2> ...]
- Tests: <N>/<N> passing (cv-service: <N>/<N>, java: <N>/<N>, web: <N>/<N>)
- Build: clean (ruff/mypy/gradle/pnpm as applicable)
- Notable decisions: <1-3 bullets, only if non-obvious>
- Regressions caught: <if any, see step file>
- Next: STEP-(NN+1) <slug>
```

### Step-Specific Pre-Flight Notes

These are gotchas spotted while reading the step files. Read the step file itself for the full spec.

- **STEP-02** (event contracts → 1.3.3): touches `server/libs/sc-event-contracts/`. After the change, `./gradlew :sc-event-contracts:publishToMavenLocal` is mandatory before building any consumer. Update consumer services' `libs.versions.toml` `sc-libs` to `1.3.3`. CI will catch drift via the `checkScLibsVersion` task **if** that task lives on `main` (it doesn't yet — see `feature/telegram-stragglers`); for now verify manually.
- **STEP-03** (MinIO adapter): cv-service polyrepo. Add `minio` Python client to main deps. Acceptance gate uses `testcontainers[minio]` — Docker Desktop must be running locally.
- **STEP-04** (preprocessing): cv-service polyrepo. Pulls in `opencv-python-headless`, `pdf2image`, `Pillow`. Add to the `ml` poetry group; bake `poppler-utils` into the Dockerfile builder layer (pdf2image needs `pdftoppm`).
- **STEP-05** (OCR engine): cv-service polyrepo. Add `paddleocr` to the `ml` group. **Pre-bake models into the Docker image** — runtime cold-start downloads are forbidden. Bake step: `RUN python -c "from paddleocr import PaddleOCR; PaddleOCR(lang='uk')"` in the builder, then copy the `~/.paddleocr` cache into the runtime image.
- **STEP-06** (field extractors): cv-service polyrepo. Pure-Python regex + IPN mod-11 validator. No new system deps. Synthesize Ukrainian fixtures with real Cyrillic.
- **STEP-07** (RabbitMQ messaging): cv-service polyrepo. Add `aio-pika`. Acceptance gate spins up Testcontainers RabbitMQ. Manual ack, prefetch from `CV_RABBIT_PREFETCH`.
- **STEP-08** (orchestrator): cv-service polyrepo. Wire download→preprocess→OCR→extract→publish. Serialize `recognize()` via `asyncio.Lock` per the invariant above.
- **STEP-09** (observability): cv-service polyrepo + maybe monorepo Grafana dashboard. Parallelizable with STEP-10.
- **STEP-10** (Java integration): documents-service polyrepo + monorepo (Flyway migration in `infra/postgres/init` if present, planning ticks). New `ocr_results` table; consumer for `cv.document.parsed`/`cv.document.failed`. Parallelizable with STEP-09.
- **STEP-11** (frontend): client/web polyrepo. Adds OCR result polling, auto-fill UX. **Run Playwright** against live UI before declaring done.
- **STEP-12** (resilience): cv-service polyrepo + maybe documents-service. Circuit breakers, retry policies, idempotent consumers.
- **STEP-13** (load test): e2e-tests polyrepo (`server/services/selection-committee-e2e-tests/` is its own repo). k6 scripts targeting the FLOW-07 SLO (≥ 50 docs/min/instance, queue lag < 1 min, zero loss across CV restart).
- **STEP-14** (documentation): cv-service polyrepo (full README) + monorepo (`docs/architecture/cv-service.md`, sc-libs 1.4.0 bump as final commit, planning closure).

### Parallel Execution: STEP-09 ∥ STEP-10

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

### Begin

Start with **STEP-02**. First action: read `progress/STEP-02-event-contracts.md`, then `server/CLAUDE.md` (auto-loaded by any `server/` edit), then write the failing contract test in `server/libs/sc-event-contracts/src/test/java/...` for the new payload classes before any implementation.

---

## Notes on This Prompt File

- This file is the single source for handing the implementation off to a fresh Claude Code session. It supersedes the v1 from PR #37.
- If the plan changes, update the underlying `STEP-NN-*.md` files first, then this prompt's "Operating Rules" / "Step-Specific Pre-Flight Notes" sections if needed.
- Do not paraphrase a step's content here — keep specifics in their own `STEP-NN-*.md` file.
- The "Regressions Caught" sections inside each `STEP-NN-*.md` are cumulative project memory; read them before starting any step that touches the same stack.
