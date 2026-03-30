# Release Report: v1.2.0

**Repository:** fice-selection-committee/fice-selection-committee
**Branch:** `feature/docker-auth-infra-overhaul` -> `main`
**Pull Request:** #25
**Previous Version:** v1.1.0 (tag `v1.1.0`, commit `ba49643`)
**Target Version:** v1.2.0
**Date:** 2026-03-30
**Commits in Release:** 8

---

## 1. Executive Summary

Release v1.2.0 is a major infrastructure and operational overhaul of the FICE Selection Committee platform. The primary themes are:

- **Docker maturity**: All service Dockerfiles rewritten to multi-stage builds with health checks; `docker-compose.services.yml` overhauled with proper health-based dependency coordination, volume mounts, and environment variable corrections.
- **New service**: Telegram Bot Service for feature flag management, adding webhook-based Telegram integration, Feign client communication with the environment service, RabbitMQ event subscriptions, chat-based authentication, rate limiting, i18n support, and Flyway migrations (79 source files).
- **Auth contract fix**: `getCurrentUser` corrected from POST to GET across the shared auth library, with corresponding contract test updates.
- **CI hardening**: Docker CI workflow expanded with tag-based triggers, Trivy vulnerability scanning, broader build matrix, and dynamic context resolution.
- **Monitoring**: Grafana provisioning and four pre-built dashboards (feature flags, PostgreSQL, RabbitMQ, services overview).
- **Engineering documentation**: CLAUDE.md engineering operating standard and 10 supporting reference files established as the project's Claude Code governance framework.

This release touches all existing services (Dockerfile changes), introduces one new service, fixes a breaking API contract, and significantly improves the project's infrastructure-as-code posture.

---

## 2. Uncommitted State Review

At the time of release preparation, the working tree contained the following categories of uncommitted changes:

### Staged (committed in this release)
| Category | Files | Disposition |
|---|---|---|
| CI workflow | `.github/workflows/service-docker.yml` | Committed -- CI enhancements |
| Git configuration | `.gitignore` | Committed -- pattern additions |
| Infrastructure | `infra/docker-compose.services.yml`, `infra/.env.example`, Grafana configs | Committed -- compose overhaul |
| Dockerfiles | 3 service Dockerfiles + `server/.dockerignore` | Committed -- multi-stage rewrites |
| Auth library | `IdentityServiceClient.java` | Committed -- POST to GET fix |
| Contract tests | `IdentityClientContractIT.java`, `ProviderContractIT.java` | Committed -- test updates |
| Deleted contract test | `EnvClientContractIT.java` | Committed -- removed (obsolete) |
| Gradle wrapper | `server/gradlew` | Committed -- permission fix |
| Telegram bot service | ~79 new files under `server/services/selection-committee-telegram-bot-service/` | Committed -- new service |
| Documentation | `CLAUDE.md`, `REVIEW_REPORT.md`, `docs/claude/`, `docs/analysis/` | Committed -- engineering standards |
| Task plans | 19 new task plan files, 14 old task plans deleted | Committed -- planning refresh |
| Build tooling | `package.json`, `package-lock.json`, Gradle buildSrc conventions | Committed -- root tooling |

### Excluded from release (correctly ignored or transient)
| Category | Files | Reason |
|---|---|---|
| Dependencies | `node_modules/` | Added to `.gitignore`; never committed |
| Screenshots | `screenshots/`, `applicant-page.png` | Development artifacts; gitignored |
| Test scripts | `test-loading-spinner.py` | Transient utility; not part of release |
| Deleted artifacts | `start-all.sh`, 14 old `tasks/phase-*.md` files | Intentionally removed |

---

## 3. .gitignore Changes

The following patterns were added to `.gitignore` to prevent accidental commits of development artifacts:

| Pattern | Rationale |
|---|---|
| `node_modules/` | NPM dependency directory (root `package.json` added for shadcn CLI) |
| `screenshots/` | Development and testing screenshot artifacts |
| Temp/transient files | Build outputs, editor artifacts, OS-specific files |

These additions align with the introduction of root-level Node.js tooling (`package.json` for shadcn CLI) and the growing use of screenshot-based verification during development.

---

## 4. Repository/Remote Review

| Property | Value |
|---|---|
| **Remote URL** | `git@github.com:fice-selection-committee/fice-selection-committee.git` |
| **Default branch** | `main` |
| **Local `main` HEAD** | `ebf103c` |
| **Remote `origin/main` HEAD** | `ebf103c` (synchronized) |
| **Feature branch** | `feature/docker-auth-infra-overhaul` (pushed to remote) |
| **PR status** | #25 created, pending review |
| **Existing tags** | `v1.0.0`, `v1.1.0` |
| **Branch naming** | Compliant with repository hook enforcement (`feature/` prefix) |

The local and remote `main` branches are fully synchronized. The feature branch has been pushed and a pull request opened.

---

## 5. Commits and PRs Created

### Pull Request #25: Docker, Auth, and Infrastructure Overhaul

**Branch:** `feature/docker-auth-infra-overhaul` -> `main`
**Total commits:** 8

| # | Hash (short) | Type | Summary |
|---|---|---|---|
| 1 | -- | `chore` | **Update .gitignore and remove obsolete files** -- Added `node_modules/`, `screenshots/`, and temp file patterns to `.gitignore`. Removed `start-all.sh` and 14 legacy task plan files (`tasks/phase-01-scaffolding.md` through `tasks/phase-14-polish.md`). |
| 2 | -- | `feat(docker)` | **Rewrite service Dockerfiles to multi-stage builds** -- All 3 existing Dockerfiles (environment, gateway, notifications) rewritten to a consistent 3-stage pattern: `lib-builder` (publishes shared libs to mavenLocal), `service-builder` (builds service JAR), `runtime` (JRE 21 Alpine). Added `server/.dockerignore` to exclude unnecessary files from build context. Added `HEALTHCHECK` directives to each Dockerfile. |
| 3 | -- | `fix(auth)` | **Change getCurrentUser from POST to GET and update contract tests** -- `IdentityServiceClient.java` changed `@PostMapping` to `@GetMapping` for the `getCurrentUser` endpoint. `ProviderContractIT` rewritten to align with magic link authentication flow. `IdentityClientContractIT` display names updated. `EnvClientContractIT` removed as obsolete. |
| 4 | -- | `feat(infra)` | **Overhaul docker-compose with health checks, telegram bot, and monitoring** -- Major `docker-compose.services.yml` overhaul: `service_started` replaced with `service_healthy` for Redis and RabbitMQ dependencies; build context changed to `server/` root; JWT secret volume mounts added; magic link rate limiting configured; telegram-bot-service, ngrok, and webhook-init containers added (behind `telegram` profile); environment variable naming corrected (`RABBIT_*` to `RABBITMQ_*`); gateway health check port fixed; `infra/.env.example` created; Grafana provisioning configuration and 4 dashboards added. |
| 5 | -- | `feat(ci)` | **Enhance Docker CI with tag triggers, Trivy scanning, and expanded matrix** -- CI workflow now triggers on changes to shared libraries and `buildSrc`, as well as semver tag patterns. Build matrix expanded to include web frontend and telegram bot service. Trivy SARIF vulnerability scanning added. Dynamic context and Dockerfile resolution per service. 30-minute job timeout configured. |
| 6 | -- | `feat(telegram)` | **Add telegram bot service for feature flag management** -- New Spring Boot service (79 source files) providing webhook-based Telegram bot integration for feature flag CRUD operations. Communicates with environment-service via Feign client. Subscribes to RabbitMQ notifications. Supports chat-based authentication, per-user rate limiting, internationalization (Ukrainian and English), Flyway database migrations. Includes Gradle `buildSrc` conventions and comprehensive unit test suite. |
| 7 | -- | `docs` | **Add engineering standards, Claude Code references, and review report** -- `CLAUDE.md` (engineering operating standard for Claude Code), `docs/claude/` (10 reference files covering architecture, testing, Docker, build, agents, skills, interaction standards, iOS atmosphere design), `docs/analysis/` (eCampus investigation), `REVIEW_REPORT.md` (48-finding codebase audit). |
| 8 | -- | `chore` | **Add telegram buildSrc, updated task plans, and root tooling deps** -- Gradle `buildSrc` conventions for telegram bot service. 19 new task plan files covering backend, frontend, redesign, and telegram bot phases. Root `package.json` with shadcn CLI dependency. |

---

## 6. Validation Status

| Check | Status | Notes |
|---|---|---|
| **CI pipeline** | Pending | Awaiting PR #25 merge trigger; Docker CI will run on merge to `main` |
| **Trivy scanning** | Pending | New in this release; will execute as part of CI on first run |
| **Contract tests** | Updated | `ProviderContractIT` and `IdentityClientContractIT` rewritten; `EnvClientContractIT` removed |
| **Telegram bot unit tests** | Included | Comprehensive unit test suite committed with the service |
| **Dockerfile builds** | Locally verified | Multi-stage builds tested during development |
| **Compose startup** | Locally verified | Health-based dependency ordering validated |

**Action required:** CI results must be verified after PR merge before tagging v1.2.0.

---

## 7. Merge Status

| Item | Status |
|---|---|
| **PR #25** | Created, pending review |
| **Target branch** | `main` |
| **Merge strategy** | Standard merge (no squash -- 8 commits preserved for traceability) |
| **Conflicts** | None detected at time of PR creation |
| **Required reviews** | Per repository settings |

**Next steps:**
1. Complete code review of PR #25
2. Verify CI passes on the feature branch
3. Merge PR #25 to `main`
4. Verify CI passes on `main` post-merge
5. Create tag `v1.2.0` and GitHub release

---

## 8. Branch Synchronization Status

| Branch | Local | Remote | Status |
|---|---|---|---|
| `main` | `ebf103c` | `ebf103c` | Synchronized |
| `feature/docker-auth-infra-overhaul` | HEAD | Pushed | Synchronized |

No divergence detected between local and remote refs. The feature branch is ready for merge review.

---

## 9. Tags/Releases

| Tag | Commit | Status |
|---|---|---|
| `v1.0.0` | (existing) | Released |
| `v1.1.0` | `ba49643` | Released -- "bump version to 1.1.0" |
| `v1.2.0` | TBD | **To be created after PR #25 merge and CI validation** |

### Release creation procedure:
```bash
# After PR #25 is merged and CI passes on main:
git checkout main
git pull origin main
git tag -a v1.2.0 -m "release: v1.2.0 - Docker overhaul, auth fix, telegram bot, monitoring"
git push origin v1.2.0

# Create GitHub release:
gh release create v1.2.0 --title "v1.2.0" --notes-file RELEASE_REPORT_v1.2.0.md
```

The CI workflow is configured to trigger on semver tag patterns, so pushing the `v1.2.0` tag will automatically initiate Docker image builds for all services.

---

## 10. Documentation Updates

### New documentation
| File/Directory | Description |
|---|---|
| `CLAUDE.md` | Engineering operating standard governing Claude Code behavior in this repository. Covers agents, skills, operational rules, decision framework, failure-aware thinking, change proposal format, and output expectations. |
| `docs/claude/agents.md` | Agent role specifications (Solution Architect, Backend/Frontend Engineer, QA, DevOps, etc.) |
| `docs/claude/skills.md` | Skill activation procedures and verification outputs |
| `docs/claude/architecture.md` | Service topology, shared libraries, infrastructure, compose topology |
| `docs/claude/docker.md` | Docker startup, known gaps, developer workflows, improvement mandate |
| `docs/claude/testing.md` | TDD protocol, bug fix protocol, test infrastructure, layer mapping |
| `docs/claude/build.md` | Build commands, dependency chain, code style, CI/CD reference |
| `docs/claude/interaction-standards.md` | Hover, focus, disabled, loading, cursor, transition, color-state rules |
| `docs/claude/ios-atmosphere.md` | Typography scale, button system, motion tokens, elevation, color rules |
| `docs/analysis/` | eCampus investigation and analysis files |
| `REVIEW_REPORT.md` | 48-finding codebase audit with categorized issues and recommendations |
| `infra/.env.example` | Environment variable template for local development |
| `infra/grafana/provisioning/` | Grafana datasource and dashboard provisioning configuration |
| `infra/grafana/dashboards/` | 4 dashboards: feature-flags, postgresql, rabbitmq, services-overview |

### Updated documentation
| File | Change |
|---|---|
| `.gitignore` | New patterns for `node_modules/`, screenshots, temp files |
| Task plans | 14 old files removed; 19 new files added covering redesign and telegram bot phases |

---

## 11. Breaking Changes and Migration Guide

### Breaking Change 1: `getCurrentUser` HTTP Method (POST -> GET)

**Affected component:** `sc-auth-starter` shared library (`IdentityServiceClient.java`)
**Impact:** All services consuming `sc-auth-starter` that call `getCurrentUser`

**What changed:**
- `@PostMapping` on `getCurrentUser` in `IdentityServiceClient` changed to `@GetMapping`
- This aligns the client with the actual identity service endpoint implementation

**Migration steps:**
1. Run `./gradlew publishToMavenLocal` from `server/` to publish the updated `sc-auth-starter`
2. Rebuild all dependent services to pick up the corrected HTTP method
3. No code changes required in consuming services -- the fix is in the shared library annotation

**Risk:** If a service is rebuilt against the old library version while the identity service expects GET, authentication calls will fail with 405 Method Not Allowed.

---

### Breaking Change 2: Docker Build Context Change

**Affected component:** All service Dockerfiles and `docker-compose.services.yml`
**Impact:** Docker build commands and CI pipelines

**What changed:**
- Build context changed from individual service directories to `server/` root
- Dockerfiles rewritten to 3-stage multi-stage builds requiring access to shared libraries at build time
- `server/.dockerignore` added to control context size

**Migration steps:**
1. Pull latest `docker-compose.services.yml`
2. Ensure `infra/.env.example` is copied to `infra/.env` and populated
3. Run `docker compose -f infra/docker-compose.services.yml build` (builds will use new context)
4. Old standalone `docker build` commands targeting individual service directories will no longer work -- use compose or specify `--file` and context explicitly

---

### Breaking Change 3: Environment Variable Renaming (RABBIT_* -> RABBITMQ_*)

**Affected component:** `docker-compose.services.yml`
**Impact:** Any existing `.env` files or deployment configurations using the old naming

**What changed:**
- Environment variables prefixed `RABBIT_` renamed to `RABBITMQ_` for consistency
- Affected variables: host, port, username, password references in compose

**Migration steps:**
1. Update local `infra/.env` to use `RABBITMQ_` prefixed variable names
2. Reference `infra/.env.example` for the correct variable names
3. Update any deployment scripts or CI secrets that use the old naming

---

## 12. Remaining Risks and Follow-up Items

### High Priority

| # | Risk/Item | Description | Recommended Action |
|---|---|---|---|
| 1 | **CI validation pending** | No CI results yet for the combined changeset on `main` | Do not tag v1.2.0 until CI passes post-merge |
| 2 | **Shared library rebuild required** | The `sc-auth-starter` POST->GET fix requires all services to rebuild against the updated library | Ensure `publishToMavenLocal` is run before any service build; verify in CI |
| 3 | **Telegram bot secrets** | Telegram bot token, ngrok auth token, and related secrets must be provisioned | Document required secrets in deployment runbook; verify `.env.example` completeness |
| 4 | **Trivy baseline** | First Trivy scan may surface existing vulnerabilities in base images | Review initial SARIF results and establish suppression baseline for known acceptable findings |

### Medium Priority

| # | Risk/Item | Description | Recommended Action |
|---|---|---|---|
| 5 | **Contract test coverage gap** | `EnvClientContractIT` was removed; verify no untested contract surface remains | Audit remaining Feign clients for contract test coverage |
| 6 | **Grafana dashboard accuracy** | Four dashboards added but may need tuning for actual metric names and service labels | Validate dashboards against live Prometheus metrics after deployment |
| 7 | **Telegram bot integration tests** | Unit tests are comprehensive; integration and contract tests (Feign to environment-service) need verification | Add `EnvironmentClientContractIT` for the telegram bot's Feign client |
| 8 | **Health check tuning** | New `HEALTHCHECK` directives in Dockerfiles use default intervals; may need adjustment for slow-starting services | Monitor container health transition times in staging and adjust intervals if needed |

### Low Priority

| # | Risk/Item | Description | Recommended Action |
|---|---|---|---|
| 9 | **Task plan freshness** | 19 new task plan files added; these are planning artifacts and should be reviewed for relevance | Prune completed or superseded task plans in next release cycle |
| 10 | **Root package.json** | Introduced for shadcn CLI tooling; `node_modules/` at root may cause confusion | Consider documenting the purpose of root Node.js tooling in the developer setup guide |
| 11 | **Ngrok dependency** | Telegram webhook setup depends on ngrok for local development; ngrok free tier has session limits | Document ngrok alternatives for long-running development sessions |
| 12 | **Dashboard .gitkeep** | `infra/grafana/dashboards/.gitkeep` is redundant now that dashboard JSON files exist | Remove `.gitkeep` in a follow-up cleanup commit |

---

*Report generated: 2026-03-30*
*Repository: fice-selection-committee/fice-selection-committee*
*Release: v1.2.0 (pending merge of PR #25)*
