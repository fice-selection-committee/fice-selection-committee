# Backend Gap Analysis

**Generated:** 2026-03-25
**System completeness:** **~91-92%**

## Executive Summary

The FICE Selection Committee backend consists of 7 microservices. Since the last audit, significant progress has been made: all reference entity CRUDs are implemented in the Admission Service, contract and order management are functional, personal file PDF generation works, all services have Spring Security with RBAC, and comprehensive integration test suites exist for every service. Auth filter and Feign client duplication has been resolved via `sc-auth-starter` shared library. UC-03 operator auto-assignment is now fully implemented with round-robin + least-loaded algorithms. The remaining gaps are concentrated in notification method completion, PDF merging in PersonalFileService, and the Computer Vision service.

---

## Service Health Overview

| Service | Port | Completeness | Critical Issues |
|---------|------|:------------:|-----------------|
| Gateway | 8080 | **95%** | None. CORS, security headers, all routes configured |
| Identity | 8081 | **92%** | RabbitNotificationClient exists; only sendVerificationEmail() implemented |
| Admission | 8083 | **93%** | None critical |
| Documents | 8084 | **88%** | PDF merging placeholder |
| Environment | 8085 | **90%** | JaCoCo at 50%, needs raising to 80% |
| Notifications | 8086 | **90%** | 2 missing Telegram templates |
| Computer Vision | 8087 | **0%** | Empty repository (deferred) |

---

## Use Case Implementation Status

| UC | Name | Previous | Current | Blocking Issues |
|----|------|:--------:|:-------:|-----------------|
| UC-01 | Applicant Registration | 87% | **93%** | Rate limiting added ✓. StubNotificationClient in non-prod |
| UC-02 | Document Upload | 88% | **92%** | OCR deferred (Computer Vision). Core upload/download/validate works |
| UC-03 | Auto-Assign to Operators | 0% | **95%** | Fully implemented: round-robin + least-loaded + circuit breaker + @Scheduled. OperatorAssignmentIT exists. |
| UC-04 | Operator Document Verification | 75% | **88%** | Review + accept/reject with event publishing. Missing: detailed checklist validation |
| UC-05 | Error/Missing Doc Notifications | 78% | **88%** | All templates exist. Real Telegram client available. DLQ handling tested |
| UC-06 | Cloud Document Storage | 85% | **92%** | MinIO working with presigned URLs, encryption support, bucket init |
| UC-07 | Personal File Generation | 0% | **88%** | Templates + QrCodeService exist. PDF merging is placeholder (only cover page used). |
| UC-08 | Contract Registration | 0% | **85%** | `ContractService` with create draft, register, cancel, find. Auto number generation. Concurrency-tested. 4 endpoints |
| UC-09 | Group Assignment | 0% | **93%** | autoDistribute fully implemented with capacity/program matching. GroupAssignmentIT exists. |
| UC-10 | Order Generation | 0% | **92%** | Full create/sign/cancel with status transitions and event publishing. |

---

## TODOs / FIXMEs Found in Code

### Environment Service — `AuditIngestConsumer.java`
| Line | TODO | Severity |
|------|------|----------|
| 24 | `// TODO: strong validation (actor registry, event type registry)` | LOW |

### ~~Identity Service — `FeatureFlagCacheManager.java`~~ LIKELY RESOLVED
| Line | TODO | Severity |
|------|------|----------|
| 41 | ~~`// TODO: replace FeatureFlags -> Map<String, Object> if needed`~~ | ~~LOW~~ — TODO not found in current codebase |

**Note:** All 6 Admission Service TODOs in `ApplicationService.java` have been **resolved** — business logic now implemented for all status transitions.

---

## Security Assessment

### RESOLVED Issues
1. ~~**Notifications Service** — `welcome_message` template files missing~~ ✓
2. ~~**Identity Service** — No rate limiting on auth endpoints~~ ✓ (Redis-based, per-endpoint)
3. ~~**Environment Service** — Zero Spring Security~~ ✓ (Full RBAC with @PreAuthorize)
4. ~~**Gateway** — No CORS configuration~~ ✓ (Global config with configurable origins)
5. ~~**Gateway** — Missing notifications route~~ ✓ (5 routes configured)

### Remaining Issues

**MEDIUM**
1. **Documents Service** — PersonalFileService PDF merging placeholder (only cover page used as final output)

**LOW**
2. **Identity Service** — `RabbitNotificationClient` exists but only `sendVerificationEmail()` implemented; missing `sendPasswordResetEmail()` and `sendWelcomeEmail()`
3. **Identity Service** — `repsonce` package name typo (should be `response`)
4. **Documents Service** — MinIO accessKey has default value `scminio` in application.yml
5. **Notifications Service** — 2 missing Telegram templates: `password_reset.txt`, `system_maintenance.txt`

---

## Missing Entity CRUD Endpoints — RESOLVED

All reference entities now have full CRUD:

| Entity | Controller | Service | Repository | Mapper | Tests |
|--------|:----------:|:-------:|:----------:|:------:|:-----:|
| Faculty | ✓ | ✓ | ✓ | ✓ | ✓ |
| Cathedra | ✓ | ✓ | ✓ | ✓ | ✓ |
| EducationalProgram | ✓ | ✓ | ✓ | ✓ | ✓ |
| Group | ✓ | ✓ | ✓ | ✓ | ✓ |
| Privilege | ✓ | ✓ | ✓ | ✓ | ✓ |
| Contract | ✓ | ✓ | ✓ | ✓ | ✓ |
| Order + OrderItem | ✓ | ✓ | ✓ | ✓ | ✓ |

**Grade** is an enum (bachelor, master, phd) — no separate CRUD needed.

---

## Test Coverage

| Service | Unit Tests | Integration Tests | Total | JaCoCo Threshold | Testcontainers |
|---------|:----------:|:-----------------:|:-----:|:----------------:|:--------------:|
| Identity | 29 | 11 | **40** | 80% | PostgreSQL, Redis, RabbitMQ |
| Environment | 11 | 7 | **18** | 50% | PostgreSQL, Redis, RabbitMQ |
| Admission | 26 | 22 | **48** | 80% | PostgreSQL, RabbitMQ |
| Documents | 8 | 12 | **20** | 80% | PostgreSQL, MinIO |
| Notifications | 8 | 6 | **14** | 80% | RabbitMQ, GreenMail |
| Gateway | 5 | 8 | **13** | 80% | Redis |
| **Total** | **87** | **66** | **153** | — | — |

**External test suites** (`tests/api-tests/`, `tests/web-tests/`) remain empty/placeholder.

---

## Infrastructure Status

| Component | Status | Notes |
|-----------|:------:|-------|
| PostgreSQL 16 | Working | 4 schemas, 20+ migrations |
| Redis 7 | Working | Caching, rate limiting, sessions |
| RabbitMQ 3.13 | Working | Async messaging, DLQ, publisher confirms |
| MinIO | Working | S3-compatible storage, webhook, encryption |
| Mailpit | Working | Dev SMTP |
| Docker Compose | Configured | `infra/docker-compose.yml` with all services |
| Flyway Migrations | Working | Identity V1-V8, Admission V1-V9, Documents V1-V3, Environment V1-V2 |
| CI/CD | **Configured** | ci.yml, regression.yml, nightly.yml per service ✓ |
| Bootstrap Scripts | Working | PowerShell-based one-command setup ✓ |
| Allure Reporting | Configured | Per-service. Artifact upload in CI ✓ |

---

## Cross-Service Integration

| Integration | Status | Notes |
|-------------|:------:|-------|
| Gateway → all 5 services | **95%** | CORS, circuit breakers, rate limiting, security headers |
| Admission → Identity (Feign) | **85%** | Timeout 3s/5s. Header forwarding |
| Admission → Documents (Feign) | **85%** | Document validation on submit |
| Admission → RabbitMQ (events) | **90%** | Status change events with afterCommit |
| Documents → Identity (Feign) | **90%** | Timeout configured |
| Documents → Admission (Feign) | **85%** | Application data for personal files |
| Documents → MinIO (S3) | **95%** | Full lifecycle + webhook + encryption |
| Environment → Identity (Feign) | **85%** | Auth validation |
| Identity → Environment (flags) | **85%** | HTTP polling with Redis cache |
| Identity → RabbitMQ (audit) | **90%** | Outbox pattern with aspect |
| RabbitMQ → Environment (audit) | **90%** | Consumer with publisher confirms |
| RabbitMQ → Notifications | **85%** | 4 queues + DLQ. All templates working (identity.queue exists but was undocumented) |
| Notifications → SMTP | **90%** | Local: Mailpit. Prod: configurable |
| Notifications → Telegram | **70%** | DefaultTelegramBotClient available, needs bot-token config |

---

## Remaining Gaps — Prioritized

### P0 — Production Blockers
1. Add `sendPasswordResetEmail()` and `sendWelcomeEmail()` to Identity NotificationClient + RabbitNotificationClient
2. Activate `notifications.rabbitmq.enabled: true` in production profile
3. Create missing Telegram templates: `password_reset.txt`, `system_maintenance.txt`

### P1 — Feature Gaps
4. Implement PDF merging in PersonalFileService (replace placeholder at line 86)

### P2 — Quality & Polish
5. Fix `repsonce` package name typo in Identity Service
6. Raise Environment Service JaCoCo from 50% to 80%
7. Create cross-service E2E test suite in `tests/api-tests/`
8. Add custom Micrometer metrics to Admission and Documents services

### P3 — Future / Deferred
9. Computer Vision service (OCR)
10. Frontend (Next.js)
11. ЄДЕБО integration
12. Google Drive archival
13. WebSocket support on Gateway
