# Backend Engineering Rules

Auto-loaded when working on `server/` files. These rules are mandatory. For detailed specifications, read the referenced docs.

---

## Build Chain

```
1. server/libs/*  -->  ./gradlew publishToMavenLocal  (from server/)
2. Each service   -->  ./gradlew build                (from service directory)
3. Docker images  -->  docker compose up --build      (from infra/)
```

**Skipping step 1 when shared libraries changed = stale artifacts. Builds pass locally, fail in CI or produce wrong behavior.**

When to run step 1: Any time a file in `server/libs/` has been modified. Check this before building any service.

---

## Service Directory Convention

- Each service: `server/services/selection-committee-<name>/`
- Each has its own standalone `settings.gradle` (**Groovy DSL**, NOT `.kts`)
- Services are NOT included in `server/settings.gradle.kts` (that file is libraries only)
- Do NOT modify `server/settings.gradle.kts` to include services

---

## Code Style

| Aspect | Convention |
|---|---|
| Java | 21 |
| Spring Boot | 3.5.6 |
| Spring Cloud | 2025.0.0 |
| Formatter | Google Java Format via Spotless (`./gradlew spotlessApply`) |
| Static analysis | Error Prone (compile-time) |
| Code generation | Lombok (`@Data`, `@Builder`, `@RequiredArgsConstructor`) |
| Object mapping | MapStruct (compile-time, not manual) |
| Coverage | JaCoCo 80% minimum per file |
| Dependencies | `server/gradle/libs.versions.toml` — all versions centralized |

**Rules**:
- Do NOT add dependency versions directly in service `build.gradle` -- use `libs.versions.toml`
- Do NOT bypass Error Prone warnings without documented justification
- Do NOT use manual object mapping when MapStruct can generate it

---

## TDD Mandate

Every non-trivial change follows this sequence. No exceptions.

```
1. Define expected behavior (acceptance criteria)
2. Identify the test layer (unit / integration / E2E / contract)
3. Write the failing test FIRST
4. Implement the minimal code to make the test pass
5. Run validation (build + all affected tests)
6. Refactor while keeping tests green
```

- Do NOT write implementation code before a test exists for the behavior
- Do NOT treat tests as "will add later" follow-up work
- Do NOT accept a change as complete if test coverage is missing

---

## Bug Fix Protocol

Every bug fix MUST include a regression test. Non-negotiable.

```
1. REPRODUCE: Identify exact failure mode
2. SEARCH: Check if a test already covers this scenario
3. CREATE TEST: Write a failing test that demonstrates the bug (BEFORE the fix)
4. FIX: Minimal change to fix the specific bug
5. VERIFY: Previously failing test passes, no regressions
6. ASSESS: Are related edge cases covered?
```

---

## Test Layer Mapping

| Change Type | Required Layer |
|---|---|
| Business logic, validators, mappers | Unit test (`src/test/java/`) |
| Database operations (repository, queries) | Integration test (`src/integrationTest/java/`) with Testcontainers |
| Feign client changes | Contract test (`src/integrationTest/**/contract/`) |
| API endpoint changes | Integration test + contract test if consumed by other services |
| RabbitMQ consumer/producer | Integration test with Testcontainers RabbitMQ |
| Performance-critical paths | JMH benchmark (`selection-committee-e2e-tests/src/performanceTest/`) |
| Cross-service flows | E2E test (`selection-committee-e2e-tests/`) |
| Bug fix | Test at the layer where the bug manifests |

---

## Test Infrastructure

### Unit Tests (`src/test/java/`)
- **Framework**: JUnit 5 + Mockito + AssertJ
- **Purpose**: Business logic, mappers, validators in isolation. No external dependencies.
- **Controller tests**: `@WebMvcTest` -- loads only the web layer. All dependencies need `@MockitoBean`.

### Integration Tests (`src/integrationTest/java/`)
- **Framework**: Spring Boot Test + Testcontainers (PostgreSQL, RabbitMQ, Redis, MinIO) + REST Assured
- **Purpose**: Test with real infrastructure. Database operations, messaging, API endpoints.
- **Requires**: Docker running on the host (Testcontainers connects to host Docker daemon).
- **Cannot run inside Docker** -- no Docker-in-Docker support. Always run on host JVM.
- **Isolation**: Testcontainers provides isolated instances per test class.

### Contract Tests (`src/integrationTest/**/contract/`)
- **Purpose**: Verify Feign client interfaces match the provider service's actual API.
- **Trigger**: Any change to Feign client interfaces, API endpoints consumed by other services, or shared DTOs.
- **Rule**: If a provider changes an endpoint, the consumer's contract test MUST fail.

### E2E Tests (Backend)
- **Location**: `server/services/selection-committee-e2e-tests/`
- **Framework**: JUnit 5 + REST Assured against running services
- **Requires**: Full service stack running (Docker compose)

### Test Utilities (`server/libs/sc-test-common/`)
- Pre-configured Testcontainers for PostgreSQL, RabbitMQ, Redis, MinIO
- Test fixtures and base test classes
- Tag filtering: `-PincludeTags=tagName` / `-PexcludeTags=tagName`

### Coverage
- **JaCoCo 80% minimum per file** (enforced by `sc.jacoco-conventions` plugin)
- **Allure** reporting for test results
- JaCoCo uses `fileTree("jacoco/*.exec")` which globs ALL exec files including `integrationTest.exec`. Task ordering fixed via `mustRunAfter(integrationTest)`.

---

## Test Strategy Decision Guide

Before writing tests, answer these:

1. **What behavior is being added or modified?** -- determines what to test
2. **What is the correct test layer?** -- use the layer mapping table
3. **Does this touch a service boundary?** -- if yes, contract tests required
4. **Is this a bug fix?** -- if yes, follow Bug Fix Protocol (regression test mandatory)
5. **Will JaCoCo 80% threshold still be met?** -- check before submitting
6. **Does the test actually test the specific behavior?** -- a test passing for the wrong reason provides false confidence

---

## Known Test Regression Patterns

These are systemic issues that recur. Check first when tests break.

### DTO Constructor Mismatch
Identity service's local `UserDto` (extended fields) diverges from shared lib's. When identity's UserDto gains fields, ALL `new UserDto(...)` calls in tests break. Grep `new UserDto(` across `src/test/`.

### Missing @MockitoBean After Controller Refactors
When a `@RestController` gains a new constructor dependency, all `@WebMvcTest` tests fail with `UnsatisfiedDependencyException`. Fix: add `@MockitoBean` for the new dependency.

### Missing Mock Stubs for New Repository Methods
New repository methods return Mockito defaults (0 for int, null for objects) which trigger error branches. Add explicit `when(...).thenReturn(...)` stubs.

### @PostConstruct Not Called in Unit Tests
Services with `@PostConstruct` init (e.g., `JwtServiceImpl.initKeys()`) need manual init call when constructed via `new` in tests.

### Integration Tests Misplaced in src/test/
Tests using `@Testcontainers`/`@SpringBootTest` with real DB MUST be in `src/integrationTest/`, not `src/test/`. Tests in `src/test/` run during `./gradlew test` and fail without Docker.

### H2 Schema Init
Entities with `@Table(schema = "environment")` need `INIT=CREATE SCHEMA IF NOT EXISTS environment` in H2 JDBC URL (`application-test.yml`).

### Stale Security Test Assertions
When `sc.auth.public-paths` changes in `application.yml`, `SecurityIntegrationTest` assertions about 401 become stale -- `@SpringBootTest` loads production config.

---

## Test Commands
```bash
./gradlew test                              # Unit tests
./gradlew integrationTest                   # Integration tests (needs Docker)
./gradlew test --tests "*.ClassName"        # Single class
./gradlew test -PincludeTags=tagName        # By JUnit tag
./gradlew integrationTest -PincludeTags=tagName  # Integration by tag
./gradlew jacocoTestReport                  # Coverage report
./gradlew spotlessCheck                     # Format check
./gradlew spotlessApply                     # Auto-format
```

---

## Microservices Rules

- NEVER introduce direct database access between services
- NEVER create shared mutable state outside explicit contracts (Feign, RabbitMQ events)
- NEVER bypass the gateway for external-facing traffic
- Every inter-service call can fail, time out, or return errors -- handle it
- New inter-service calls MUST have circuit breaker and timeout configuration
- New Feign clients MUST have contract tests in `src/integrationTest/**/contract/`
- Prefer eventual consistency through events over synchronous orchestration

---

## Shared Libraries (`server/libs/`)

| Library | Purpose |
|---|---|
| `sc-bom` | Bill of Materials -- version alignment |
| `sc-auth-starter` | JWT validation, RBAC, security filter chains, `IdentityServiceClient` |
| `sc-common` | Shared exceptions, DTOs, base responses, utilities |
| `sc-event-contracts` | RabbitMQ event payload definitions (async contract surface) |
| `sc-test-common` | Testcontainers helpers, test fixtures, base test classes |
| `sc-observability-starter` | Micrometer Prometheus metrics, Zipkin tracing |
| `sc-s3-starter` | MinIO/S3 integration auto-configuration |

**A library change affects every consumer.** Always `./gradlew publishToMavenLocal` from `server/` after modification. Version tracked in `server/version.properties`.

---

## API Contract Rules

- Adding optional field: compatible
- Adding required field: **BREAKING**
- Removing/renaming/retyping a field: **BREAKING**
- Changing endpoint path: **BREAKING**
- Changing event payload structure: **BREAKING**

Before any contract change: identify ALL consumers by searching the codebase. Prove backward compatibility by reading consumer code, not assuming.

---

## Detailed References

Read these on-demand for deep dives:

| Doc | Content |
|---|---|
| `docs/claude/agents.md` | Full agent specifications and constraints |
| `docs/claude/skills.md` | Skill verification procedures |
| `docs/claude/testing.md` | Full TDD protocol, test infrastructure details |
| `docs/claude/build.md` | Build commands, CI/CD workflows, convention plugins |
| `docs/claude/architecture.md` | Service topology, infrastructure, compose topology |
| `docs/claude/feature-flags.md` | Feature flag architecture and integration plan |
