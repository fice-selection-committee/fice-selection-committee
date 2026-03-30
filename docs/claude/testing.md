# TDD-First Development & Testing Reference

This file defines the mandatory testing workflow, bug fix protocol, and test infrastructure for the repository. Claude MUST read this file before executing any task that involves writing, modifying, or verifying tests.

---

## 1. Mandatory TDD Sequencing

For every non-trivial change, Claude MUST follow this sequence. No exceptions.

```
1. Define expected behavior (acceptance criteria, specific inputs → expected outputs)
2. Identify the test layer (unit / integration / E2E / contract)
3. Write the failing test FIRST
4. Implement the minimal code to make the test pass
5. Run validation (build + all affected tests)
6. Refactor while keeping tests green
```

**Enforcement rules**:
- Claude MUST NOT write implementation code before a test exists for the behavior being added
- Claude MUST NOT treat tests as "will add later" follow-up work — tests are part of the deliverable
- Claude MUST NOT skip test design when proposing implementation plans
- Claude MUST NOT propose a refactor without verifying existing tests cover the affected behavior
- Claude MUST NOT accept a change as complete if test coverage is missing for the modified behavior

**What "failing test first" means in practice**:
- For a new feature: write a test that asserts the expected behavior, run it, confirm it fails (because the feature doesn't exist yet), then implement
- For a bug fix: write a test that reproduces the bug, run it, confirm it fails (because the bug exists), then fix
- For a refactor: verify existing tests pass, then refactor, then verify they still pass

---

## 2. Bug Fix Protocol

Every bug fix MUST follow this protocol. A bug fix without a regression test is incomplete delivery.

```
1. REPRODUCE: Identify the exact failure mode
   - What input triggers the bug?
   - What is the incorrect behavior?
   - What is the expected behavior?

2. SEARCH: Check if a test already covers this scenario
   - Search existing tests for related assertions
   - If found: does the test actually catch this specific failure mode?

3. CREATE OR STRENGTHEN TEST:
   - If no test exists: write a failing test that demonstrates the bug
   - If a test exists but doesn't catch it: strengthen the test with better assertions or edge case data
   - Run the test — it MUST fail (confirming it reproduces the bug)

4. IMPLEMENT THE FIX:
   - Minimal change to fix the specific bug
   - Do not combine with unrelated refactoring

5. VERIFY:
   - The previously failing test now passes
   - No adjacent tests regressed
   - Run the full test suite for the affected area

6. ASSESS COVERAGE:
   - Is the regression test specific enough to catch this exact failure mode?
   - Are there related edge cases that should also be covered?
   - Would this test have caught the bug before it was reported?
```

**Non-negotiable**: Claude MUST NOT submit or recommend a bug fix that relies solely on manual verification when an automated test is feasible. "Manual verification sufficient" is never acceptable when the bug can be reproduced in a test.

---

## 3. Test Infrastructure — Backend

### 3.1 Unit Tests
- **Location**: `src/test/java/`
- **Framework**: JUnit 5 + Mockito + AssertJ
- **Run**: `./gradlew test` (from service directory)
- **Run single class**: `./gradlew test --tests "*.ClassName"`
- **Run by tag**: `./gradlew test -PincludeTags=tagName`
- **Purpose**: Test business logic, mappers, validators in isolation. No external dependencies.

### 3.2 Integration Tests
- **Location**: `src/integrationTest/java/`
- **Framework**: Spring Boot Test + Testcontainers (PostgreSQL, RabbitMQ, Redis, MinIO) + REST Assured
- **Run**: `./gradlew integrationTest` (from service directory)
- **Run by tag**: `./gradlew integrationTest -PincludeTags=tagName`
- **Purpose**: Test with real infrastructure. Database operations, messaging, external service calls. Uses Testcontainers to spin up real PostgreSQL, RabbitMQ, Redis, MinIO instances.

### 3.3 Contract Tests
- **Location**: `src/integrationTest/**/contract/`
- **Framework**: Spring Boot Test + Testcontainers + WireMock or real Feign client verification
- **Purpose**: Verify that Feign client interfaces match the provider service's actual API. If a provider changes an endpoint, the consumer's contract test must fail.
- **Trigger**: Any change to Feign client interfaces, API endpoints consumed by other services, or shared DTOs

### 3.4 E2E Tests (Backend)
- **Location**: `server/services/selection-committee-e2e-tests/`
- **Purpose**: Cross-service integration flows. Requires the full service stack running.
- **Framework**: JUnit 5 + REST Assured against running services

### 3.5 Performance Tests
- **Location**: `server/services/selection-committee-e2e-tests/src/performanceTest/`
- **Framework**: JMH (Java Microbenchmark Harness)
- **Trigger**: Performance-critical changes

### 3.6 Coverage
- **Tool**: JaCoCo
- **Threshold**: 80% minimum per file (enforced by `sc.jacoco-conventions` plugin)
- **Reporting**: Allure reporting for test results
- **Verification**: `./gradlew jacocoTestReport` (from service directory)

### 3.7 Test Utilities
- **Base classes and fixtures**: `server/libs/sc-test-common/`
- **Testcontainers configuration**: Pre-configured containers for PostgreSQL, RabbitMQ, Redis, MinIO
- **Test tag filtering**: `-PincludeTags=tagName` / `-PexcludeTags=tagName` on any test task

---

## 4. Test Infrastructure — Frontend

### 4.1 Unit Tests
- **Location**: Colocated in `src/**/*.test.ts` or in `tests/unit/`
- **Framework**: Vitest + jsdom
- **Run**: `pnpm test` (from `client/web/`)
- **Coverage**: `pnpm test:coverage`
- **Purpose**: Test component rendering, hooks, utility functions. API calls mocked via MSW.

### 4.2 E2E Tests
- **Location**: `tests/e2e/`
- **Framework**: Playwright
- **Run**: `pnpm test:e2e` (from `client/web/`)
- **Purpose**: Test user-facing workflows end-to-end against a running backend stack.
- **Requirement**: Backend must be running (Docker stack or host services) before E2E tests execute.

### 4.3 API Mocking
- **Tool**: MSW (Mock Service Worker)
- **Purpose**: Mock backend API responses in unit tests without a running backend.
- **Constraint**: MSW mocks must match the actual backend API contract. If the contract changes, mocks must be updated.

---

## 5. Test Layer Mapping

Use this table to determine which test layer applies to a given change:

| Change Type | Unit | Integration | Contract | E2E (Backend) | E2E (Frontend) | Performance |
|---|---|---|---|---|---|---|
| Business logic, validators, mappers | **Required** | — | — | — | — | — |
| Database operations (repository, queries) | Optional | **Required** | — | — | — | — |
| Feign client changes | — | — | **Required** | — | — | — |
| API endpoint changes | Optional | **Required** | If consumed by other services | — | If user-facing | — |
| RabbitMQ consumer/producer changes | — | **Required** | — | — | — | — |
| UI component rendering | **Required** | — | — | — | — | — |
| User workflow changes | — | — | — | — | **Required** | — |
| Cross-service flows | — | — | — | **Required** | — | — |
| Performance-critical paths | — | — | — | — | — | **Required** |
| Bug fix | **At the layer where the bug manifests** | — | — | — | — | — |

---

## 6. Dockerized Test Execution Considerations

### 6.1 Integration Tests with Testcontainers
Integration tests use Testcontainers to spin up real infrastructure (PostgreSQL, RabbitMQ, Redis, MinIO). This requires Docker to be running on the machine executing the tests.

- **CI**: Docker is available in GitHub Actions runners.
- **Local**: Docker Desktop or equivalent must be running.
- **In Docker**: Running integration tests inside a Docker container requires Docker-in-Docker or socket mounting. This is not currently supported — integration tests run on the host JVM with Testcontainers connecting to the host Docker daemon.

### 6.2 E2E Tests (Backend)
Backend E2E tests require the full service stack running. Options:
- Start the stack via `docker compose up -d` before running E2E tests
- CI workflows start the stack as part of the pipeline

### 6.3 E2E Tests (Frontend - Playwright)
Playwright tests require:
- Backend stack running (Docker)
- Frontend dev server or built frontend running
- Browser binaries installed (`pnpm exec playwright install`)

### 6.4 Test Isolation
- Unit tests: No external dependencies, always isolated
- Integration tests: Testcontainers provides isolated instances per test class
- Contract tests: Use Testcontainers for provider-side verification
- E2E tests: Shared stack — tests must handle data setup/teardown

---

## 7. Test Strategy Decision Guide

When planning tests for a change, answer these questions:

1. **What behavior is being added or modified?** → This determines what must be tested.
2. **What is the correct test layer?** → Use the Test Layer Mapping table above.
3. **Does this change touch a service boundary?** → If yes, contract tests are required.
4. **Does this change affect user-facing workflows?** → If yes, Playwright E2E consideration is required.
5. **Is this a bug fix?** → If yes, follow the Bug Fix Protocol (Section 2). Regression test is mandatory.
6. **Will JaCoCo 80% threshold still be met?** → Check before submitting.
7. **Does the test actually test the specific behavior?** → A test that passes for the wrong reason provides false confidence.
