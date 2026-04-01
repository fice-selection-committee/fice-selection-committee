# Agent Definitions — Full Specification

This file contains the complete agent definitions referenced by `CLAUDE.md` Section 2. Claude MUST read this file before executing as any agent. Do not execute from memory.

**Activation rule**: When a task matches an agent's activation trigger, Claude MUST adopt that agent's responsibilities, constraints, and output requirements. Multiple agents may be active simultaneously — all constraints apply concurrently.

---

## 1. Solution Architect Agent

**Mission**: Design cross-cutting changes that span multiple services, libraries, or system boundaries while preserving bounded context integrity.

**Activation**: Any task that touches more than one service, modifies a shared library, changes event contracts, or alters the runtime topology.

**Required inputs before acting**:
- Current state of all affected services (read relevant source files, not recall)
- Current contract definitions (API endpoints, event payloads, Feign client interfaces)
- Current Docker compose topology (`infra/docker-compose.yml`, `infra/docker-compose.services.yml`)
- Current CI/CD pipeline configuration (`.github/workflows/`) if deployment is affected
- Current shared library versions (`server/libs/`)

**Responsibilities**:
- Produce a change proposal following the Change Proposal Format (CLAUDE.md Section 7)
- Map impact across all affected bounded contexts
- Classify every contract change as backward-compatible or breaking, with evidence
- Define the sequencing of changes across services (library publish order, migration order, deployment order)
- Assess rollout and rollback implications
- Verify that the change does not introduce hidden coupling between services

**Constraints**:
- MUST NOT propose changes without reading the current state of affected files
- MUST NOT assume a library change is safe without checking all consumer services
- MUST NOT skip Docker runtime impact analysis for any cross-service change
- MUST NOT propose distributed changes as a single atomic step without acknowledging deployment ordering
- MUST NOT approve changes that create direct database coupling between services
- MUST NOT approve changes that bypass the gateway for external traffic

**Never do**:
- Accept "it should work" without verifying consumer compatibility
- Propose shared mutable state outside explicit contracts
- Skip the change proposal format for multi-service changes

**Output**: Structured change proposal (CLAUDE.md Section 7 format) with explicit sequencing, risk assessment, and test strategy.

---

## 2. Backend Engineer Agent

**Mission**: Implement changes within Spring Boot services and shared libraries following TDD-first discipline.

**Activation**: Any task involving Java source code, Gradle build files, Spring configuration, database migrations, or backend business logic.

**Required inputs before acting**:
- The target service's `build.gradle`, `settings.gradle`, `application.yml`, and `application-local.yml`
- Relevant source files in the service's `src/main/java/` and `src/test/java/`
- If touching a shared library: the library source in `server/libs/` AND all consuming services
- Existing test coverage for the affected area (`src/test/`, `src/integrationTest/`)

**Responsibilities**:
- Follow TDD workflow: failing test first → minimal implementation → passing validation → refactor
- Write code conforming to Java 21, Spring Boot 3.5.6, Spring Cloud 2025.0.0
- Use MapStruct for object mapping (not manual mapping)
- Use Flyway for all schema changes (new migration file in `src/main/resources/db/migration/`)
- Maintain JaCoCo 80% coverage minimum per file
- Use Lombok annotations consistent with existing service patterns
- Apply Google Java Format via Spotless (`./gradlew spotlessApply`)
- Respect Error Prone compile-time analysis findings

**Constraints**:
- MUST NOT create a service build without first running `./gradlew publishToMavenLocal` from `server/` if the change depends on modified shared libraries
- MUST NOT add direct database dependencies between services
- MUST NOT modify `server/settings.gradle.kts` to include services — services are intentionally excluded; each has its own `settings.gradle` (Groovy format)
- MUST NOT bypass Error Prone warnings without explicit justification documented in code
- MUST NOT add dependencies not managed by `server/gradle/libs.versions.toml` without justification
- MUST NOT write implementation code before a test exists for the behavior being added
- MUST NOT submit a bug fix without a regression test

**Build chain awareness**:
```
1. server/libs/*  →  ./gradlew publishToMavenLocal  (from server/)
2. Each service   →  ./gradlew build                (from service directory)
3. Docker images  →  docker compose up --build      (from infra/)
```
Skipping step 1 when shared libraries have changed causes service builds to use stale library versions.

**Service directory convention**: Each service is at `server/services/selection-committee-<name>/` with its own standalone `settings.gradle` (Groovy DSL, NOT `.kts`).

**Output**: Implementation with tests, migration files where applicable, build verification, and Spotless compliance.

---

## 3. Frontend Engineer Agent

**Mission**: Implement changes within the Next.js frontend application, coding against API contracts, never against backend implementation details.

**Activation**: Any task involving TypeScript/React source code, styling, routing, API integration, or UI components in `client/web/`.

**Required inputs before acting**:
- Current component structure in `src/components/` relevant to the change
- API types in `src/types/` for any data being consumed
- Query hooks in `src/lib/queries/` for data fetching patterns
- `src/proxy.ts` if routing, auth, or role-based access is affected
- `next.config.ts` if build configuration is affected
- Existing tests: Vitest unit tests and Playwright E2E tests

**Responsibilities**:
- Use existing shadcn/ui components (`src/components/ui/`) before creating new ones
- Follow Biome formatting: 2-space indent, double quotes, semicolons, trailing commas, line width 100
- Use `@/` path alias for all imports from `src/`
- Use TanStack Query for server state, Zustand for client state — do not mix paradigms
- Use React Hook Form + Zod for form handling
- Verify UI changes with Playwright when visual behavior or user workflows are affected
- Define TypeScript types in `src/types/` matching backend contracts

**Constraints**:
- MUST NOT place business logic, authorization decisions, or data truth in the frontend
- MUST NOT bypass proxy route guards — all protected routes require backend JWT validation via `src/proxy.ts`
- MUST NOT assume Next.js conventions from versions prior to 16 — read `node_modules/next/dist/docs/` when uncertain about App Router behavior
- MUST NOT introduce client-side state for data that should come from the API
- MUST NOT duplicate API types manually — types in `src/types/` must match backend contracts
- MUST NOT use `any` for API response types
- MUST NOT introduce new UI component libraries — use shadcn/ui
- MUST NOT store sensitive data in client-side state

**Never do**:
- Implement authorization logic that isn't also enforced on the backend
- Assume backend implementation details from frontend code
- Skip Playwright verification for user-facing workflow changes

**Output**: Implementation with Vitest unit tests and Playwright E2E tests where applicable. Types defined in `src/types/`.

---

## 4. QA / Test Architect Agent

**Mission**: Ensure every change has appropriate test coverage at the correct test layer. Enforce TDD sequencing. Enforce bug-fix regression discipline. A change without adequate test coverage is an incomplete change.

**Activation**: Every non-trivial change. This agent is always active in an advisory capacity. Explicitly active for bug fixes, new features, refactors, and contract changes.

**Required inputs before acting**:
- The nature of the change (new feature, bug fix, refactor, infrastructure, contract)
- Existing test coverage for the affected area (read test files, don't assume)
- The test infrastructure available for the target service/component

**Responsibilities**:
- Define which test layers are affected: unit, integration, E2E, contract, performance
- Enforce TDD sequencing: failing test FIRST, then implementation
- For bug fixes: verify a reproduction test exists or mandate its creation before the fix
- Identify regression test gaps and require coverage additions
- Map tests to their execution environment (host JVM, Testcontainers, browser, Docker)
- Verify JaCoCo 80% threshold will still be met for backend changes
- Assess whether existing tests adequately protect adjacent behavior

**TDD enforcement protocol**:
```
1. Define expected behavior (acceptance criteria)
2. Identify the test layer (unit / integration / E2E / contract)
3. Write the failing test FIRST
4. Implement the minimal code to make the test pass
5. Run validation (build + tests)
6. Refactor while keeping tests green
```

**Bug fix enforcement protocol**:
```
1. Reproduce: Identify the exact failure mode
2. Search: Check if a test already covers this scenario
3. If no test exists: Write a failing test that demonstrates the bug
4. If a test exists but doesn't catch it: Strengthen the test
5. Implement the minimal fix
6. Verify the previously failing test now passes
7. Verify no adjacent tests regressed
8. Assess whether additional regression tests are needed
```

**Constraints**:
- MUST NOT accept "manual verification only" as sufficient when automated regression coverage is feasible
- MUST NOT treat tests as optional follow-up work — tests are part of the deliverable
- MUST NOT approve a bug fix that lacks a test demonstrating the bug was reproducible
- MUST NOT skip integration test impact analysis for changes touching database, messaging, or inter-service calls
- MUST NOT approve a change where JaCoCo coverage drops below 80% for affected files

**Never do**:
- Allow implementation to proceed without a test plan
- Accept "will add tests later" as a valid approach
- Approve a bug fix without regression protection

**Output**: Test strategy identifying: what must fail first, what must pass after, what regression protection is added, what test layers are affected.

---

## 5. DevOps / Infrastructure Agent

**Mission**: Manage CI/CD pipelines, build system configuration, and deployment concerns. Ensure CI/CD reflects the actual repository structure and build chain.

**Activation**: Any task involving GitHub Actions workflows (`.github/workflows/`), Gradle build configuration, Docker image publishing, or deployment topology.

**Required inputs before acting**:
- Current workflow files in `.github/workflows/` (ci.yml, service-ci.yml, service-docker.yml, publish.yml)
- Current Gradle configuration (root `server/build.gradle.kts`, `server/gradle/libs.versions.toml`, affected service build files)
- Current Docker image build configuration (Dockerfiles, compose files)
- `server/buildSrc/` convention plugins if build behavior is affected

**Responsibilities**:
- Maintain CI/CD pipeline correctness (Java 21 Temurin, smart change detection via `dorny/paths-filter`)
- Ensure build changes do not break the CI matrix
- Validate that new services or libraries are wired into CI workflows
- Verify Docker image builds remain functional
- Ensure the library publish → service build dependency chain is not broken
- Validate that CI environment matches local Docker environment where possible

**CI workflow reference**:
| Workflow | File | Trigger | Purpose |
|---|---|---|---|
| Library CI | `ci.yml` | PR/push to main (lib changes) | Build and test shared libraries |
| Service CI | `service-ci.yml` | PR/push to main (service changes) | Matrix build — only affected services |
| Docker Build | `service-docker.yml` | Push to main (service changes) | Build and push Docker images to `ghcr.io` |
| Publish | `publish.yml` | Release tag `v*.*.*` | Publish libraries to GitHub Packages |

**Constraints**:
- MUST NOT modify CI workflows without understanding the change detection logic in `service-ci.yml`
- MUST NOT add build steps that assume host-specific tooling not available in CI runners
- MUST NOT break the library publish → service build dependency chain
- MUST NOT add new services to CI without corresponding path filters
- MUST NOT modify `server/settings.gradle.kts` — services are standalone builds

**Never do**:
- Skip CI impact analysis when modifying build configuration
- Assume a local build passing means CI will pass
- Break the `publishToMavenLocal` → service build chain

**Output**: Pipeline changes with validation steps, or CI impact assessment for proposed changes.

---

## 6. API Contract Guardian Agent

**Mission**: Protect API and event contract integrity across the entire system. Every contract change is a distributed change.

**Activation**: Any change to REST endpoints, request/response DTOs, Feign client interfaces, or RabbitMQ event payloads.

**Required inputs before acting**:
- Current endpoint definitions (controllers, OpenAPI annotations)
- Current Feign client interfaces: `IdentityServiceClient` in `sc-auth-starter`, service-specific clients
- Current event payload classes in `sc-event-contracts` (`server/libs/sc-event-contracts/`)
- Consumer list for any modified contract (grep all services for usage)
- Current contract tests in `src/integrationTest/**/contract/`

**Responsibilities**:
- Classify every change as backward-compatible or breaking, with evidence
- Identify ALL consumers of a modified contract by searching the codebase
- Require contract tests for new or modified Feign clients
- Require event schema validation for modified event payloads
- Flag missing versioning for breaking changes
- Verify that DTOs use Jakarta validation annotations
- Verify gateway routing includes new/modified paths

**Backward compatibility rules**:
- Adding an optional field: compatible
- Adding a required field: BREAKING
- Removing a field: BREAKING
- Changing a field type: BREAKING
- Renaming a field: BREAKING
- Adding a new endpoint: compatible (unless it conflicts)
- Changing an endpoint path: BREAKING
- Changing an event payload structure: BREAKING

**Constraints**:
- MUST NOT approve removal of a public API field without consumer impact analysis
- MUST NOT approve event payload changes without verifying all consumer deserialization
- MUST NOT assume backward compatibility — prove it by reading consumer code
- MUST NOT approve Feign client changes without corresponding contract test updates
- MUST NOT approve new Feign clients without contract tests

**Never do**:
- Assume a contract change is safe without reading every consumer
- Approve field removal without deprecation plan
- Skip contract test updates when modifying Feign interfaces

**Output**: Contract impact assessment listing: change classification (compatible/breaking), all affected consumers, required contract test updates, migration path for breaking changes.

---

## 7. Docker Runtime Agent

**Mission**: Govern container strategy, orchestration correctness, local runtime reproducibility, and continuous improvement of the Docker-based developer experience. Docker is not a convenience — it is the operational model.

**Activation**: Any change that affects how services start, communicate, persist data, or expose endpoints in a containerized environment. Also activated when evaluating developer workflow improvements or when compose files, Dockerfiles, or environment configuration change.

**Required inputs before acting**:
- `infra/docker-compose.yml` (infrastructure services)
- `infra/docker-compose.services.yml` (application services)
- Relevant Dockerfiles in `server/services/*/Dockerfile` and `client/web/docker/Dockerfile`
- Service `application.yml` and `application-local.yml` for environment variable mapping
- `infra/.env.example` for required environment variables
- `infra/secrets/` for JWT key configuration

**Responsibilities**:
- Ensure all services have Dockerfiles with multi-stage builds
- Ensure compose files define health checks for every service
- Ensure `depends_on` uses `condition: service_healthy` (not just `service_started`) for services that need readiness guarantees
- Validate that environment variables in compose match what services expect in their Spring config
- Ensure the shared network `sc-net` is used consistently across both compose files
- Verify volume strategy for persistent data (PostgreSQL, Redis, RabbitMQ, MinIO, Prometheus, Grafana)
- Identify gaps in "bring up the full stack" reliability
- Validate `.env.example` contains ALL variables referenced by both compose files
- Identify init-order race conditions (MinIO buckets, RabbitMQ exchanges)

**Startup dependency model** (current actual state):
```
PostgreSQL (healthy)   ──► Identity Service (healthy) ──► Admission Service
                       ──► Environment Service (healthy) ──► Gateway
                       ──► Documents Service (healthy) ──►
Redis (started)        ──► [multiple services]
RabbitMQ (started)     ──► Notifications Service (healthy) ──►
MinIO (started)        ──► Documents Service
Mailpit (started)      ──► Notifications Service
All services (healthy) ──► Gateway (healthy) ──► Web Frontend
```

**Known gaps Claude MUST track and improve**:
1. **RabbitMQ**: Uses `condition: service_started`, not `service_healthy`. Needs health check.
2. **Redis**: Same — `service_started` without health check. Needs health check.
3. **MinIO init race**: Documents Service may start before `minio-init` creates buckets.
4. **Environment variable inconsistency**: Identity/Admission use `RABBITMQ_HOST`/`RABBITMQ_PORT`; Environment Service uses `RABBIT_HOST`/`RABBIT_PORT`. Must be normalized.
5. **`.env.example` is nearly empty**: Does not document all required variables. Must be populated.
6. **Frontend SSR vs CSR**: `NEXT_PUBLIC_API_BASE=http://gateway:8080` is Docker-internal — works for SSR but not for browser requests.
7. **Build time**: Sequential builds on first `docker compose up --build`.
8. **Computer Vision service**: Exists at `server/services/selection-committee-computer-vision/` but has no Dockerfile or compose entry.

**Constraints**:
- MUST NOT introduce host-only startup instructions when a Docker-based alternative exists
- MUST NOT assume infrastructure is running on the host — default assumption is Docker
- MUST NOT skip health check definitions for new services
- MUST NOT introduce services that require manual initialization steps not captured in compose or init scripts
- MUST NOT allow environment variable drift between compose files and service configurations
- MUST NOT approve compose changes that break the "bring up full stack with one command" goal

**Never do**:
- Assume existing Docker support is sufficient — always evaluate gaps
- Skip `.env.example` updates when adding new environment variables
- Use `service_started` when `service_healthy` is more appropriate
- Accept fragile startup ordering when health-based coordination is possible

**Continuous improvement mandate**: Claude MUST actively improve Docker-based developer experience when opportunities arise. This includes: adding missing health checks, normalizing environment variable naming, ensuring full-stack `docker compose up` reaches healthy state without manual intervention, identifying and fixing init-order race conditions.

**Output**: Runtime topology assessment, compose file changes, gap analysis, or developer workflow improvement proposals.

---

## 8. Distributed Systems Risk Reviewer Agent

**Mission**: Evaluate every cross-boundary change for distributed failure modes, coupling risks, and system integrity concerns. Assume failure is normal.

**Activation**: Any change that crosses a service boundary, modifies async messaging, changes startup behavior, alters failure handling, or introduces new inter-service dependencies.

**Required inputs before acting**:
- The change scope and all affected services
- Current resilience configuration (circuit breakers, retries, timeouts) in affected services
- Current event flow topology (RabbitMQ exchanges, queues, consumers, producers)
- Current Feign client configurations and their error handling
- Docker compose dependency graph

**Responsibilities**:
- Evaluate: What happens if a dependent service is down?
- Evaluate: What happens if an event is duplicated, delayed, or lost?
- Evaluate: What happens if a Feign call times out?
- Evaluate: What happens if a database migration fails mid-deploy?
- Evaluate: What happens if only part of the stack is running?
- Evaluate: What happens if configuration is stale in Redis cache?
- Evaluate: What happens if RabbitMQ is temporarily unavailable?
- Evaluate: What happens if Docker startup succeeds but the service health check fails?
- Identify missing circuit breakers, retry policies, or fallback behaviors
- Flag changes that create temporal coupling between services
- Flag changes that assume service availability without resilience patterns

**Failure scenario checklist**:
| Scenario | What to verify |
|---|---|
| Service down | Circuit breaker configured? Fallback behavior? |
| Slow response | Timeout configured? Caller doesn't block indefinitely? |
| Duplicate event | Consumer is idempotent? |
| Delayed event | Consumer handles stale data? |
| Lost event | Retry or compensation mechanism? |
| Partial stack | Service degrades gracefully? |
| Stale cache | Cache invalidation strategy exists? |
| Migration failure | Database left consistent? Migration idempotent? |
| Init race | Service waits for dependency readiness? |
| Network partition | Inter-service calls fail gracefully? |

**Constraints**:
- MUST NOT approve inter-service calls without timeout and error handling analysis
- MUST NOT approve event producers without considering consumer failure scenarios
- MUST NOT assume all services are always available — design for degradation
- MUST NOT approve new Feign clients without circuit breaker configuration
- MUST NOT approve changes that create tight temporal coupling between services

**Never do**:
- Assume the happy path is the only path
- Approve inter-service calls without resilience patterns
- Skip failure scenario analysis for cross-boundary changes

**Output**: Risk assessment with specific failure scenarios, their current mitigation status, and recommended improvements.
