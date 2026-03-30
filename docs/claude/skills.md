# Skills Reference — Full Specification

Skills are reusable verification procedures Claude MUST apply when the corresponding trigger condition is met. Each skill produces specific deliverables and enforces specific constraints. Claude MUST read this file before executing any skill referenced in `CLAUDE.md` Section 3.

**Execution rule**: When a trigger condition is met, execute the skill's required checks and produce the specified deliverable. Do not skip checks. Do not produce partial deliverables.

**Testing integration**: Every skill that changes behavior, contracts, runtime wiring, or cross-service flow MUST define: what tests should fail first, what tests must exist after the change, whether new regression tests are required, and whether Dockerized execution affects the test strategy.

---

## 1. Service Analysis

**Trigger**: Any task targeting a specific backend service.

**Preconditions**: Read the service's `build.gradle`, `settings.gradle`, `application.yml`, `application-local.yml`, source tree structure, and existing tests. Do not proceed from memory.

**Required checks**:
- Identify all Feign client dependencies (who does this service call? grep for `@FeignClient`)
- Identify all RabbitMQ consumers/producers (grep for `@RabbitListener`, `RabbitTemplate`)
- Identify all database entities and their Flyway migration state (`src/main/resources/db/migration/`)
- Identify the service's health check endpoint and actuator configuration
- Identify shared library dependencies in `build.gradle`
- Identify environment variables the service requires (from `application.yml` and compose config)

**Deliverable**: Service context summary confirming: dependencies (Feign, events, DB, cache), contracts exposed and consumed, test coverage state, Docker configuration status.

**Anti-pattern**: Making changes to a service without understanding its full dependency graph and contract surface.

---

## 2. API Design and Validation

**Trigger**: Creating or modifying REST endpoints.

**Preconditions**: Read existing controller, DTO classes, OpenAPI annotations, and gateway routing configuration.

**Required checks**:
- Endpoint follows existing URL patterns (`/api/v1/{resource}`)
- Request/response DTOs use Jakarta validation annotations (`@NotNull`, `@NotBlank`, `@Valid`)
- Endpoint is covered by controller integration tests in `src/integrationTest/`
- Gateway routing configuration includes the new/modified path
- RBAC annotations (`@PreAuthorize`, `@Secured`, or custom) are present and correct
- Response DTOs match TypeScript types in `client/web/src/types/` if consumed by frontend
- Error responses follow existing patterns (shared exception handling in `sc-common`)

**Deliverable**: Endpoint specification with: URL, method, request/response types, validation rules, security annotations, gateway routing confirmation, test coverage confirmation.

**Anti-pattern**: Creating endpoints without gateway routing, security annotations, or contract tests.

**Testing requirement**: Failing controller integration test first, then implementation. Contract tests if a Feign client will consume this endpoint.

---

## 3. Test Strategy Design

**Trigger**: Any feature or change that requires new test coverage.

**Preconditions**: Understand the change scope, existing coverage, and available test infrastructure.

**Required checks**:
- Map change to affected test layers (unit, integration, E2E, contract, performance)
- Identify whether Testcontainers infrastructure is needed (PostgreSQL, RabbitMQ, Redis, MinIO)
- Identify whether Playwright E2E coverage is needed (user-facing workflow changes)
- Verify JaCoCo 80% threshold will still be met for affected files
- Identify whether contract tests need updating (Feign client or API changes)
- Assess existing test adequacy — are current tests sufficient or do they have gaps?

**Deliverable**: Test plan listing: specific test classes/methods to create or modify, test layer for each, infrastructure requirements, expected failing test before implementation, expected passing state after.

**Anti-pattern**: Writing tests only at one layer when multiple layers are affected. Treating test design as an afterthought.

---

## 4. Refactoring Safety Check

**Trigger**: Any refactoring operation (rename, extract, restructure, move).

**Preconditions**: Read all existing tests covering the code being refactored. Identify all callers/consumers.

**Required checks**:
- All existing tests pass before refactoring begins (verify, don't assume)
- Refactoring does not change public API surfaces (method signatures, endpoints, DTOs, event payloads)
- If public API changes are necessary: all consumers are identified and will be updated
- No behavior change is introduced — refactoring is structural only
- All existing tests still pass after refactoring without modification

**Deliverable**: Confirmation that: test suite is green before refactoring, public API is unchanged (or consumers are updated), test suite is green after refactoring.

**Anti-pattern**: Refactoring without running tests. Combining refactoring with behavior changes in the same commit. Changing public APIs without consumer impact analysis.

---

## 5. Dependency Impact Analysis

**Trigger**: Adding, removing, or upgrading a dependency in any `build.gradle` or `package.json`.

**Preconditions**: Read `server/gradle/libs.versions.toml` for backend dependencies. Read `client/web/package.json` for frontend dependencies.

**Required checks**:
- **Backend**: Is the dependency managed in `libs.versions.toml`? If not, provide explicit justification.
- **Backend**: Does the dependency conflict with Spring Boot BOM-managed versions?
- **Backend**: Does the dependency introduce transitive conflicts? (`./gradlew dependencies`)
- **Frontend**: Does the dependency conflict with React 19 / Next.js 16 / existing packages?
- **Security**: Does the dependency have known vulnerabilities? (check CVE databases)
- **License**: Is the license compatible with the project?
- **Size**: For frontend, does it significantly increase bundle size?

**Deliverable**: Impact assessment with: version justification, conflict analysis, security check, license compatibility.

**Anti-pattern**: Adding unmanaged dependency versions in individual build files. Adding dependencies without checking for conflicts.

---

## 6. Distributed Flow Reasoning

**Trigger**: Any change involving inter-service communication (Feign calls, RabbitMQ events).

**Preconditions**: Map the full request flow from gateway through all participating services. Read Feign client interfaces and event payload classes.

**Required checks**:
- Identify all synchronous call chains and their timeout configurations
- Identify all asynchronous event flows and their error handling
- Verify circuit breaker configuration for Feign clients
- Verify idempotency guarantees for event consumers
- Verify retry behavior does not cause cascading failures
- Verify that failure in one service does not cascade to callers
- Check that Feign client has a `fallback` or `fallbackFactory` configured

**Deliverable**: Flow diagram (textual) with: services involved, call type (sync/async), timeout values, circuit breaker status, failure mode annotations, idempotency status.

**Anti-pattern**: Adding inter-service calls without timeout, retry, or circuit breaker configuration. Assuming events are always delivered exactly once.

**Testing requirement**: Contract tests for modified Feign clients. Integration tests for modified event consumers with Testcontainers RabbitMQ.

---

## 7. Failure Scenario Analysis

**Trigger**: Any change to service startup, health checks, inter-service dependencies, or failure handling.

**Required checks**:
| Scenario | Verification |
|---|---|
| Dependency starts slowly | Callers wait via `depends_on: condition: service_healthy`? |
| Dependency is completely unavailable | Circuit breaker prevents cascade? Fallback exists? |
| Message is duplicated | Consumer is idempotent? (check for duplicate detection) |
| Message is delayed by minutes | Consumer handles stale data gracefully? |
| Redis cache is stale or empty | Service falls back to database? Cache warming strategy? |
| Flyway migration fails partway | Transaction-safe? Can be re-run? Database left consistent? |
| Only part of the stack is running | Service reports unhealthy, doesn't crash? |
| Docker startup succeeds but health check fails | Health check endpoint is configured and meaningful? |
| Init script hasn't completed | Service waits or retries? (MinIO bucket, RabbitMQ exchange) |
| Environment variable is missing | Service fails fast with clear error? |

**Deliverable**: Failure scenario matrix: scenario, expected behavior, actual behavior, gap identification, recommended mitigation.

**Anti-pattern**: Assuming the happy path is the only path. Ignoring init-order race conditions.

---

## 8. Docker Compose Design Review

**Trigger**: Any modification to compose files, Dockerfiles, or service startup configuration.

**Required checks**:
- Health checks are defined for ALL services (not just some)
- `depends_on` conditions use `service_healthy` where readiness matters (not just `service_started`)
- Environment variables in compose match what the service's `application.yml` / `application-local.yml` expects
- Named volumes are defined for persistent data (PostgreSQL, Redis, RabbitMQ, MinIO, Prometheus, Grafana)
- Network configuration uses `sc-net` consistently across both compose files
- Port mappings do not conflict across services
- `infra/.env.example` is updated with any new variables and documents all required variables
- Secrets are mounted correctly (`infra/secrets/` → `/run/secrets/`)
- Multi-stage Dockerfile builds are used for service images
- Environment variable naming is consistent across services (watch for `RABBITMQ_*` vs `RABBIT_*` drift)

**Deliverable**: Compose change with: verification instructions, health check confirmation, env var consistency check, `.env.example` update.

**Anti-pattern**: Adding services without health checks. Using `service_started` when `service_healthy` is needed. Allowing env var naming drift.

---

## 9. Contract Evolution Safety Review

**Trigger**: Any change to shared libraries (`server/libs/`), Feign client interfaces, or event payload classes (`sc-event-contracts`).

**Preconditions**: Identify ALL consumers of the changing contract by searching the codebase.

**Required checks**:
- Is the change backward-compatible? (classify with evidence)
- If breaking: what is the migration path for every consumer?
- Are contract tests in `src/integrationTest/**/contract/` updated?
- Is the library version bumped in `server/version.properties`?
- Has `./gradlew publishToMavenLocal` been run from `server/` after the library change?
- Do ALL consuming services build successfully with the updated library?
- Do ALL consuming services' tests pass with the updated library?

**Deliverable**: Consumer impact list: affected services, change classification (compatible/breaking), required coordinated changes, library publish order, test verification results.

**Testing requirement**: Contract tests must fail first for breaking changes, then be updated alongside the fix. All consumer integration tests must pass after.

**Anti-pattern**: Changing a shared library without verifying all consumers still compile and pass tests. Publishing without version bump.

---

## 10. Bug Reproduction and Validation

**Trigger**: Any bug report or defect fix.

**Required checks**:
1. Can the bug be reproduced with a test? (Write the reproduction test)
2. Does a test already exist that should have caught it?
3. If existing test missed it: why? (insufficient assertions, wrong test data, missing edge case, wrong test layer)
4. If no test exists: create a failing test that demonstrates the bug BEFORE implementing the fix
5. After fix: does the reproduction test pass?
6. After fix: do all related tests still pass?
7. Is additional regression coverage needed beyond the reproduction test?

**Deliverable**: Reproduction test (failing before fix, passing after) + fix implementation + regression verification + gap assessment.

**Anti-pattern**: Fixing a bug without a test proving it was broken. Accepting "manual verification only" when automated testing is feasible.

**Enforcement**: A bug fix without a regression test is incomplete delivery. This is non-negotiable.

---

## 11. Observability Readiness Check

**Trigger**: Adding a new service or modifying existing service instrumentation.

**Required checks**:
- Actuator health endpoint is exposed (`/actuator/health`)
- Prometheus metrics endpoint is exposed (`/actuator/prometheus`)
- Zipkin tracing is configured (`ZIPKIN_URL` environment variable, `sc-observability-starter` dependency)
- Prometheus scrape target is added in `infra/prometheus/prometheus.yml`
- Grafana dashboard is updated if new metrics are introduced (dashboards in `infra/grafana/dashboards/`)
- Health check in Docker compose uses the actuator endpoint

**Deliverable**: Observability checklist: health endpoint status, metrics endpoint status, tracing configuration, Prometheus scrape target, Grafana dashboard status.

**Anti-pattern**: Deploying services without health, metrics, or tracing endpoints. Adding services without Prometheus scrape targets.

---

## 12. Runtime Topology Validation

**Trigger**: Any change to service startup ordering, network configuration, dependency health checks, or Docker compose topology.

**Required checks**:
- The startup dependency graph is acyclic and correct
- Every service that depends on another uses `condition: service_healthy` (not `service_started`) when readiness matters
- Health check endpoints return meaningful status (not just HTTP 200 regardless)
- Network names are consistent across both compose files
- Init containers (minio-init) complete before dependent services need their output
- Port mappings are unique and documented
- The full stack can reach healthy state with a single `docker compose -f docker-compose.yml -f docker-compose.services.yml up -d` command

**Deliverable**: Topology assessment: dependency graph correctness, health check coverage, startup race conditions, single-command startup verification.

**Anti-pattern**: Creating circular dependencies. Using `service_started` for services that need readiness guarantees. Assuming init containers complete instantly.

---

## 13. Regression Test Gap Review

**Trigger**: After any bug fix or defect resolution, to verify regression protection is sufficient.

**Required checks**:
- Does the reproduction test exist and pass?
- Does the reproduction test actually test the specific failure mode, not just the general area?
- Would the test have caught the bug if it existed before the fix? (verify by mentally reverting the fix)
- Are there related failure modes that should also be covered?
- Is the test at the right layer? (unit test for logic bugs, integration for data bugs, E2E for workflow bugs)
- If the bug was in inter-service communication: is there a contract test?

**Deliverable**: Regression coverage assessment: test exists, test is specific, test is at correct layer, related gaps identified, recommendation for additional coverage if needed.

**Anti-pattern**: Writing a test that passes for the wrong reason. Testing the general area but not the specific failure mode. Placing the test at the wrong layer.
