# Execution Prompt — Computer Vision System Implementation

Copy-paste this prompt into a fresh Claude Code session in `D:\develop\fice-selection-committee` to execute the full CV rollout. The prompt is self-contained: it references the 15 step files in `progress/` and enforces gating, sequencing, and test-first discipline.

---

## PROMPT

You are implementing the Computer Vision system for the FICE Selection Committee project. The complete plan lives in `progress/` (16 files: `README.md` + `STEP-00` through `STEP-14`). The plan was approved by the user; do not redesign — execute it.

### Mission

Deliver, end to end, an event-driven Python (FastAPI + PaddleOCR) CV microservice that consumes `cv.document.requested` from RabbitMQ, runs `download → preprocess → OCR → field-extract`, and publishes `cv.document.parsed` / `cv.document.failed`. Integrate it with documents-service (Java) for persistence and with the Next.js frontend for auto-fill UX. Match the FLOW-07 SLO: ≥ 50 docs/min/instance, queue lag < 1 min, zero message loss across CV restarts.

### Operating rules (non-negotiable)

1. **Read first**: before starting any step, read its `progress/STEP-NN-*.md` file end to end. Do not paraphrase from memory.
2. **Sequential execution**: follow the dependency graph in `progress/README.md`. Do not start STEP-N+1 until STEP-N's "Definition of Done" is fully ticked. Steps 09 and 10 are the only parallelizable pair (run in two worktrees if you want speed).
3. **Test-first inside each step**: write the tests listed under "Tests (Acceptance Gates)" before the implementation, watch them fail, then make them pass. TDD is mandatory per `CLAUDE.md`.
4. **Gating**: a step is done only when (a) every file under "Files to Create" exists, (b) every file under "Files to Modify" reflects the listed change, (c) every test under "Acceptance Gates" passes, (d) `ruff` + `mypy --strict` (Python) or `./gradlew check` (Java) or `pnpm lint` + `pnpm build` (frontend) is clean for the touched code.
5. **One branch per step**: `feature/cv-step-NN-<slug>`. Commit only when the step is green. Never bypass hooks (no `--no-verify`).
6. **Update the tracker**: after each step closes, edit `progress/README.md` to flip the row from `⏳ TODO` to `✅ done — <one-line note>`, AND tick the "Definition of Done" checkboxes inside the step file.
7. **Read the auto-loaded `CLAUDE.md` files** that fire when you touch each subtree (`server/`, `client/web/`, `infra/`, etc.). Their rules take precedence.
8. **Architectural invariants** (do not violate):
   - CV-Service is **stateless** — no DB, no gateway route, no public HTTP endpoint other than `/health` and `/metrics`.
   - Persistence belongs to documents-service (`ocr_results` table, STEP-10).
   - Communication is **RabbitMQ + S3 only**. No Feign client to CV-Service.
   - Models are **pre-baked into the Docker image**; cold-start downloads are forbidden.
   - PaddleOCR is **not thread-safe** — serialize `recognize()` via `asyncio.Lock`.
   - Documents-service publish is **best-effort** with circuit breaker (STEP-12) — CV downtime must never break upload.
9. **No scope creep**: implement exactly what the step specifies. If you spot a gap, stop and ask the user before adding scope.

### Step-by-step execution order

```
STEP-00 ✅ done (already executed in the planning session — skip)
STEP-01 → STEP-02 → STEP-03 → STEP-04 → STEP-05 → STEP-06 → STEP-07 → STEP-08
                                                                        │
                                          ┌─────────────────────────────┴─────────────────────────────┐
                                          ▼                                                           ▼
                                    STEP-09 (observability)                              STEP-10 (Java integration)
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

### Per-step workflow

For each step (start with STEP-01):

1. `git checkout main && git pull && git checkout -b feature/cv-step-NN-<slug>`
2. Read `progress/STEP-NN-*.md` end to end.
3. Read every auto-loaded `CLAUDE.md` that will fire for the files you'll touch.
4. **Write failing tests first** for each "Acceptance Gate" — confirm they fail for the right reason.
5. Implement files in the order listed under "Implementation Outline".
6. Run the full acceptance-gate test list. Iterate until all green.
7. Run lint/typecheck/build for the affected stack:
   - Python: `cd server/services/selection-committee-computer-vision && poetry run ruff check && poetry run mypy --strict src && poetry run pytest -q`
   - Java: `cd server && ./gradlew :selection-committee-<service>:check`
   - Frontend: `cd client/web && pnpm lint && pnpm build && pnpm test`
8. Tick the "Definition of Done" checkboxes inside `progress/STEP-NN-*.md`.
9. Update the status table in `progress/README.md`.
10. `git add -p && git commit -m "feat(cv): step-NN <slug>"` (use repository commit-message style — read recent `git log` first).
11. Open a PR titled `feat(cv): STEP-NN — <slug>`. Wait for the user to merge before STEP-N+1.

### Cross-cutting reminders

- **Build chain**: any change to `server/libs/sc-event-contracts/` (STEP-02) requires `./gradlew :sc-event-contracts:publishToMavenLocal` from `server/` BEFORE building any consumer service. Skipping this causes stale-artifact failures (see project memory `project_build_setup`).
- **Library version bump 1.3.2 → 1.3.3** happens in STEP-02; bump 1.3.3 → 1.4.0 happens in STEP-14 as the final commit.
- **Ukrainian-language assets**: PaddleOCR `lang="uk"` covers Cyrillic + Latin. Test fixtures with Ukrainian text must use real Cyrillic strings, never transliterations.
- **No real PII**: every test fixture (passport, IPN, foreign passport) is synthetic. Generate IPN values via the mod-11 algorithm, never use real ones.
- **Docker**: validate every step that touches `infra/` by running `cd infra && docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --wait` and confirming all services reach `(healthy)`.
- **Frontend verification** (STEP-11+): per `feedback_verify_with_playwright`, run Playwright against the live UI. Code review alone is not acceptance.

### Bug-fix protocol (when tests fail)

If during execution any test (existing or new) regresses:

1. Reproduce the failure with the exact command in the step's "Acceptance Gates".
2. Write a regression test that reproduces it (per `docs/claude/testing.md` bug-fix mandate).
3. Fix the root cause. Do not bypass with mocks or skip annotations.
4. Confirm both the new regression test and all original tests pass.
5. Document the fix in the step file under a new "Regressions Caught" section.

### What to ask the user about

Pause and ask **only** for:
- Ambiguity in a step that the file does not resolve.
- Architectural deviations you believe are necessary.
- Production credentials or PII (never invent these).
- Approval before merging each step's PR.

Do **not** ask for:
- "Should I proceed?" between substeps within one STEP file — just proceed.
- Naming bikesheds — follow what the step file specifies.
- Lint-rule disputes — follow the project's existing config.

### Reporting

After each step closes, post a single message to the user:

```
✅ STEP-NN <slug> complete
- Branch: feature/cv-step-NN-<slug>
- PR: <url>
- Tests: <N>/<N> passing
- Build: clean (ruff/mypy/gradle/pnpm as applicable)
- Notable decisions: <1-3 bullets, only if non-obvious>
- Next: STEP-(NN+1) <slug>
```

### Begin

Start with **STEP-01**. First action: read `progress/STEP-01-cv-service-scaffold.md`, then `server/CLAUDE.md` (auto-loads on `server/` files), then write the smoke test in `tests/test_smoke.py` before any implementation file.

---

## Notes on This Prompt File

- This file is the single source for handing the implementation off to a fresh Claude Code session.
- If the plan changes, update the underlying `STEP-NN-*.md` files first, then this prompt's "Operating rules" section if needed.
- Do not edit individual steps' content here — keep them in their own files.
