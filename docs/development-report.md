# FICE Selection Committee — Development Report

**Date:** 2026-03-25
**Phases Completed:** 11 of 11 (backend scope)
**Status:** All backend phases delivered

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Phase 01 — Shared Auth Library Extraction](#phase-01--shared-auth-library-extraction)
3. [Phase 02 — Identity Service Polish](#phase-02--identity-service-polish)
4. [Phase 03 — Operator Auto-Assignment Polish](#phase-03--operator-auto-assignment-polish)
5. [Phase 04 — Personal File PDF Refinement](#phase-04--personal-file-pdf-refinement)
6. [Phase 05 — Group Distribution Polish](#phase-05--group-distribution-polish)
7. [Phase 06 — Order Generation Polish](#phase-06--order-generation-polish)
8. [Phase 07 — Notifications Production Wiring (P0)](#phase-07--notifications-production-wiring-p0)
9. [Phase 08 — Gateway Enhancements](#phase-08--gateway-enhancements)
10. [Phase 09 — Cross-Service E2E Tests](#phase-09--cross-service-e2e-tests)
11. [Phase 10 — Code Quality Fixes](#phase-10--code-quality-fixes)
12. [Phase 11 — Performance & Observability](#phase-11--performance--observability)
13. [Architecture Decisions](#architecture-decisions)
14. [Out of Scope](#out-of-scope)

---

## Executive Summary

This report covers the full backend development effort for the FICE Selection Committee system, spanning 11 phases. The work addressed foundational infrastructure (shared auth library, notification wiring), core business logic refinements (operator assignment, group distribution, order generation, PDF generation), cross-cutting concerns (gateway hardening, code quality, observability), and end-to-end test coverage.

All 11 phases have been completed successfully. The system is ready for frontend integration and production deployment.

---

## Phase 01 — Shared Auth Library Extraction

**Status:** COMPLETED

Extracted a reusable authentication library (`sc-auth-starter`) shared across all backend services, eliminating duplicated auth logic.

### Deliverables

- Created `server/libs/sc-auth-starter/` as a `java-library` Gradle module under package `edu.kpi.fice.common.auth`.
- Extracted components: `AuthFilter`, `SecurityConfigDefaults`, `AuthUtils`, `IdentityServiceClient` (base), `FeignForwardHeadersInterceptor`, `UserDto`, `RoleDto`, `PermissionDto`.
- Spring Boot auto-configuration with `@ConditionalOnMissingBean` to allow consuming services to override any bean.
- `AuthProperties` provides configurable `identityServiceUrl`, `skipFilterPaths`, and `publicPaths`.
- Migrated 3 services to consume the library via Gradle composite builds (`includeBuild`).
- Admission Service retains a local extended `IdentityServiceClient` (adds `getUsersByRole()`).
- Documents Service retains its `WebhookAuthFilter` and custom `SecurityConfig`.
- 10 tests covering `AuthFilter` and auto-configuration logic.
- All 3 consuming services compile cleanly (main, test, and integration test sources).

---

## Phase 02 — Identity Service Polish

**Status:** COMPLETED

Addressed technical debt and raised test coverage requirements in the Identity Service.

### Deliverables

- Fixed 13 instances of `"field field"` typos (reduced to `"field"`) across 4 DTO files and 2 test files.
- Confirmed `"grater then"` typo was already resolved or never present.
- Raised JaCoCo code coverage threshold from 50% to 80% in `build.gradle`.

---

## Phase 03 — Operator Auto-Assignment Polish

**Status:** COMPLETED

Enhanced the operator auto-assignment mechanism with load balancing, capacity limits, and fault tolerance.

### Deliverables

- Enhanced `OperatorAssignmentService` with load-balancing logic (counts `REVIEWING` applications per operator).
- Added configurable max-per-operator limit (default: 20) and batch-size limiting (default: 50).
- Integrated Resilience4j circuit breaker on the Identity Service call with a fallback path.
- Created `AutoAssignProperties` configuration class.
- Added `ApplicationRepository` methods for workload counting and pageable queries.
- 12 unit tests + 6 integration tests, all passing.

---

## Phase 04 — Personal File PDF Refinement

**Status:** COMPLETED

Improved the personal file PDF output with a professional layout, QR codes, and structured content.

### Deliverables

- Added ZXing dependency for QR code generation.
- Created `QrCodeService` producing base64-encoded QR codes.
- Enhanced `cover.html` template with university header, full applicant information, and embedded QR code.
- Enhanced `contents.html` template with a numbered section list.
- Created `documents.html` template for a document inventory table.
- Updated `PersonalFileService` to generate a 3-page PDF (cover + table of contents + documents).
- 18 tests (6 QrCode + 6 PersonalFile + 6 PdfGenerator).

---

## Phase 05 — Group Distribution Polish

**Status:** COMPLETED

Fixed a critical distribution bug and added capacity tracking and detailed reporting.

### Deliverables

- **Critical bug fix:** `autoDistribute()` now correctly matches applications to groups by educational program. The previous implementation was ignoring the `groupsByProgram` map entirely.
- Added `maxCapacity` field to the `Group` entity.
- Added `educationalProgram` field to the `Application` entity.
- Created migration `V8__add_group_capacity_and_application_program.sql`.
- Rewrote `GroupDistributionProtocol` with detailed breakdown: `totalProcessed`, `totalAssigned`, per-group breakdown, and unassigned reasons.
- Handles edge cases: `NO_PROGRAM`, `NO_MATCHING_GROUP`, `GROUP_FULL`.
- Updated DTOs and mappers accordingly.
- 12 unit tests + 6 integration tests.

---

## Phase 06 — Order Generation Polish

**Status:** COMPLETED

Added signing metadata, optimistic locking, and PDF export to the order subsystem.

### Deliverables

- Added `signedAt` and `signedBy` fields to the `Order` entity for signing audit trail.
- Added `@Version` annotation for optimistic locking to prevent concurrent signing.
- Created migration `V9__add_order_signing_fields.sql`.
- Updated `signOrder()` to accept a `userId` and record signing metadata.
- Created `OrderPdfService` using Thymeleaf + openhtmltopdf.
- Created `order.html` template with a professional layout.
- Added `GET /api/v1/orders/{id}/pdf` endpoint.
- Tests covering signing metadata, concurrent rejection, and PDF generation.

---

## Phase 07 — Notifications Production Wiring (P0)

**Status:** COMPLETED

Wired the Identity Service to the Notifications Service via RabbitMQ for production-ready event delivery.

### Deliverables

- Created `RabbitNotificationClient.java` in Identity Service implementing the `NotificationClient` interface.
- Uses `@ConditionalOnProperty("notifications.rabbitmq.enabled")` for backward compatibility.
- Changed `StubNotificationClient` from `@Profile("!prod")` to `@ConditionalOnProperty(matchIfMissing = true)` for clean mutual exclusivity.
- Added `identity.events` TopicExchange to Identity Service's `RabbitMQConfig.java`.
- Added `identity.queue` with a dead-letter queue to Notifications Service's `RabbitConfig.java`.
- Added `identity.queue` to `EventListenerService`'s `@RabbitListener`.
- Created `NotificationWiringIT.java` integration test.
- Configuration properties: `notifications.rabbitmq.enabled`, `notifications.rabbitmq.exchange`.
- Documented required Telegram environment variables (`TELEGRAM_BOT_TOKEN`, etc.).

---

## Phase 08 — Gateway Enhancements

**Status:** COMPLETED

Hardened the API gateway with full circuit breaker coverage, access logging, and downstream health monitoring.

### Deliverables

- Added explicit Resilience4j configuration for all 5 circuit breakers (previously only `notifCircuit` was configured).
- Created `AccessLogFilter` (reactive `GlobalFilter`) logging method, path, status, duration, and `X-Request-Id`.
- Created `DownstreamHealthIndicator` (`ReactiveHealthIndicator`) that checks all 5 downstream services.
- Documented JWT validation decision (Option A: individual service validation).
- Tests for `AccessLogFilter`, `DownstreamHealthIndicator`, and circuit breaker configuration.

---

## Phase 09 — Cross-Service E2E Tests

**Status:** COMPLETED

Added comprehensive end-to-end tests validating cross-service workflows.

### Deliverables

- **`AuthFlowE2ETest.java`** — 7 ordered steps: register, verify email via Mailpit, login, get user, invalid login attempt, duplicate registration rejection.
- **`ContractOrderE2ETest.java`** — 17 ordered steps across 4 roles: full contract and order lifecycle.
- **`RbacMatrixE2ETest.java`** — Extended with `CONTRACT_MANAGER` and `EXECUTIVE_SECRETARY` roles (6 new test cases).
- Added test fixtures for new roles.
- Added `verifyEmail()` helper to `ApiClient`.

---

## Phase 10 — Code Quality Fixes

**Status:** COMPLETED

Addressed code quality gaps, security concerns, and formatting consistency.

### Deliverables

| Task | Outcome |
|------|---------|
| 10.1 — DTO typos | Already resolved in Phase 02 |
| 10.2 — HikariCP config | Verified already configured in all JDBC services |
| 10.3 — Error response format | Verified consistent across services (minor Admission difference noted, non-blocking) |
| 10.4 — Default credentials | Removed default MinIO credentials from Documents Service |
| 10.5 — Formatting & static analysis | Added Spotless (Google Java Format) + ErrorProne to 4 services (Admission, Identity, Environment, Notifications); ran `spotlessApply` on all |

---

## Phase 11 — Performance & Observability

**Status:** COMPLETED

Added distributed tracing, structured logging, custom metrics, and monitoring dashboards across all services.

### Deliverables

- Added Micrometer Tracing (Brave) + Zipkin reporter to all 6 services.
- Added or updated structured logging configurations:
  - Log4j2 with `traceId`/`spanId` for 4 services.
  - Logback for 2 services.
- Custom Prometheus metrics:
  - `auth.login.attempts`
  - `application.status.transitions`
  - `document.uploads`
  - `notification.sent`
  - `feature.flag.evaluations`
- Enabled liveness and readiness probes in all services.
- Created 3 Grafana dashboards in `infra/grafana/`:
  - `services-overview`
  - `rabbitmq`
  - `postgresql`

---

## Architecture Decisions

| ID | Decision | Rationale |
|----|----------|-----------|
| AD-01 | Gradle composite builds for the shared auth library | Avoids `publishToMavenLocal` workflow; changes are picked up immediately during development |
| AD-02 | Identity Service uses a dedicated `identity.events` exchange | Clean separation; avoids piggybacking on the admission exchange |
| AD-03 | `StubNotificationClient` uses `@ConditionalOnProperty` instead of `@Profile` | Ensures mutual exclusivity with the real client without profile-based coupling |
| AD-04 | JWT validation at individual service level (not gateway) | Simpler architecture; revisit if Identity Service load grows significantly |
| AD-05 | Log4j2 `JsonLayout` for structured logging where Log4j2 is used; Logback where Logback is used | Respects each service's existing logging framework rather than forcing a migration |

---

## Out of Scope

The following items were explicitly excluded from the backend development phases:

- **Phase 12 — Frontend (Next.js):** No task file created; out of scope for backend work.
- **Computer Vision Service (OCR):** Deferred to a future iteration.
- **EDEBO Integration:** Planned for post-launch.
- **WebSocket Support on Gateway:** Identified as a future enhancement.
