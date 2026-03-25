# Service Feature Matrix — Completeness Assessment

> Updated: 2026-03-25 | Based on comprehensive code exploration of all services

## Overall Service Health

| Service | Port | Features | Test Health | Code Quality | Overall |
|---------|:----:|:--------:|:-----------:|:------------:|:-------:|
| Identity Service | 8081 | 94% | 88% | 90% | **92%** |
| Environment Service | 8085 | 93% | 82% | 90% | **90%** |
| Admission Service | 8083 | 95% | 90% | 90% | **93%** |
| Documents Service | 8084 | 92% | 85% | 85% | **88%** |
| Notifications Service | 8086 | 92% | 85% | 90% | **90%** |
| Gateway | 8080 | 97% | 93% | 95% | **95%** |
| Computer Vision | — | 0% | 0% | 0% | **0%** |

### Changes Since Last Audit

| Service | Previous Overall | Current Overall | Change |
|---------|:---------------:|:---------------:|:------:|
| Identity | 88% | **92%** | +4% |
| Environment | 88% | **90%** | +2% |
| Admission | 85% | **93%** | +8% |
| Documents | 86% | **88%** | +2% |
| Notifications | 87% | **90%** | +3% |
| Gateway | 93% | **95%** | +2% |

---

## Identity Service — Feature Breakdown

| Feature | Status | Completeness | Notes |
|---------|--------|:------------:|-------|
| User Registration | Implemented | **95%** | Rate limiting added ✓ |
| Email Verification | Implemented | **85%** | RabbitNotificationClient exists for production. Only sendVerificationEmail() implemented; password_reset and welcome pending |
| JWT Login (access+refresh) | Implemented | **95%** | HTTP-only cookies, RSA keys, token revocation, rotation |
| JWT Token Refresh | Implemented | **95%** | Cookie-based refresh flow with token rotation |
| Logout / Session Revocation | Implemented | **90%** | Clears tokens, revokes in DB. Missing: revoke-all-sessions |
| User CRUD (admin) | Implemented | **95%** | Full CRUD with pagination, search, soft delete |
| Role Management (admin) | Implemented | **95%** | CRUD + permission assignment/revocation |
| Permission Management (admin) | Implemented | **95%** | CRUD with filtering via Specification |
| RBAC Authorization | Implemented | **95%** | @PreAuthorize with permissions. 7 roles, 8 permissions |
| Lock/Unlock Users | Implemented | **95%** | Admin can lock/unlock users |
| Passwordless Registration | Implemented | **80%** | Endpoint exists, DTO validated. Feature-flag gated |
| Audit Logging (Outbox) | Implemented | **95%** | AuditAspect + OutboxEventPublisher. V6-V8 migrations. RabbitMQ publishing |
| Feature Flag Integration | Implemented | **85%** | Polls environment-service. FeatureFlagCacheManager with Redis |
| Rate Limiting | Implemented | **95%** | Redis-based per-endpoint. Login: 5/60s, Register: 3/300s ✓ |
| Spring Security Filter Chain | Implemented | **95%** | AccessTokenFilter + RateLimitFilter, SecurityConfig |
| OpenAPI / Swagger | Implemented | **95%** | All endpoints documented |
| Flyway Migrations | Implemented | **95%** | V1-V8 complete ✓ |
| Actuator / Prometheus | Implemented | **85%** | Health, info, prometheus endpoints |
| Users By Role Query | Implemented | **90%** | GET /admin/users/by-role endpoint ✓ |
| Extra Permissions | Implemented | **95%** | Grant/revoke extra permissions per user ✓ |

---

## Environment Service — Feature Breakdown

| Feature | Status | Completeness | Notes |
|---------|--------|:------------:|-------|
| Feature Flag CRUD | Implemented | **95%** | Create, update, delete, list with filters |
| Feature Flag Evaluation | Implemented | **95%** | Context-based evaluation. Overflow bug fixed ✓ |
| Targeting Strategies (AND/OR) | Implemented | **85%** | Works. OR strategy documented |
| Environment Targeting | Implemented | **95%** | Filter flags by environment (case-insensitive variant available) |
| Scope Targeting | Implemented | **95%** | Filter flags by scope. Dedicated ScopeController ✓ |
| User/Role/Permission Targeting | Implemented | **90%** | Target specific users, roles, permissions |
| Rollout Percentage | Implemented | **95%** | Hash-based rollout. Overflow bug fixed ✓ |
| Date-range Activation | Implemented | **95%** | startDate/endDate support |
| Redis Caching | Implemented | **95%** | 5-min TTL. All caches configured with Jackson2Json serializers. Error handling with DB fallback ✓ |
| Audit Event Ingestion | Implemented | **95%** | RabbitMQ consumer → PostgreSQL. Batch size limited (max 500). Publisher confirms ✓ |
| Audit Event Search | Implemented | **95%** | Paginated search with dynamic predicates (Specification). Max page size 200 ✓ |
| Audit TTL Cleanup | Implemented | **85%** | Scheduled cron job. 90-day retention |
| Audit DLQ Handling | Implemented | **80%** | DLQ routing configured. DLQ integration test exists ✓ |
| Spring Security | Implemented | **90%** | AuthFilter + SecurityConfig. RBAC with @PreAuthorize ✓ |
| OpenAPI / Swagger | Implemented | **95%** | All endpoints documented |
| Flyway Migrations | Implemented | **95%** | V1-V2 complete |
| Actuator / Metrics | Implemented | **85%** | health, info, env, metrics |
| Retry on Optimistic Lock | Implemented | **95%** | RetryTemplate configured ✓ |
| Feign Client (Identity) | Implemented | **85%** | With header forwarding interceptor |
| @Valid on Controllers | Implemented | **95%** | @Valid on create/update endpoints ✓ |
| Integration Tests | Implemented | **90%** | 7 integration tests with Testcontainers (PostgreSQL, Redis, RabbitMQ) ✓ |

---

## Admission Service — Feature Breakdown

| Feature | Status | Completeness | Notes |
|---------|--------|:------------:|-------|
| Application CRUD | Implemented | **90%** | Create, read, update, soft-delete with pagination |
| Application Status Workflow | Implemented | **90%** | 7 statuses. All transitions with event publishing ✓ |
| Status: DRAFT → SUBMITTED | Implemented | **90%** | Validates documents via DocumentsServiceClient ✓ |
| Status: SUBMITTED → REVIEWING | Implemented | **90%** | Sets operator. Role validated |
| Status: REVIEWING → ACCEPTED | Implemented | **90%** | Status transition + event publishing |
| Status: REVIEWING → REJECTED | Implemented | **90%** | With rejection reason ✓ |
| Status: ACCEPTED → ENROLLED | Implemented | **90%** | Status transition + event publishing |
| Status: REJECTED → ARCHIVED | Implemented | **90%** | Status transition + event publishing |
| Application Reassignment | Implemented | **90%** | POST /{id}/reassign endpoint. OperatorAssignmentService ✓ |
| Specification-based Filtering | Implemented | **90%** | ApplicationSpecificationService with integration tests ✓ |
| Auth via Identity (Feign) | Implemented | **85%** | Timeout 3s/5s. Header forwarding ✓ |
| Documents via Feign | Implemented | **85%** | DocumentsServiceClient for document validation ✓ |
| **Faculty CRUD** | **Implemented** | **95%** | Full CRUD with controller, service, mapper, DTO ✓ |
| **Cathedra CRUD** | **Implemented** | **95%** | Full CRUD with facultyId filter ✓ |
| **EducationalProgram CRUD** | **Implemented** | **95%** | Full CRUD with cathedraId filter ✓ |
| **Group CRUD** | **Implemented** | **95%** | Full CRUD with educationalProgramId and enrollmentYear filters ✓ |
| **Privilege CRUD** | **Implemented** | **95%** | Full CRUD with privilegeKey ✓ |
| **Contract Management** | **Implemented** | **90%** | Create draft, register, cancel, find by application. Auto-number generation ✓ |
| **Order Management** | **Implemented** | **90%** | Create (enrollment/expulsion), sign, cancel. Auto-number generation ✓ |
| **Group Assignment** | **Implemented** | **92%** | autoDistribute fully implemented with capacity limits, program matching, and GroupDistributionProtocol ✓ |
| **Application Event Publishing** | **Implemented** | **90%** | RabbitMQ publishing on status changes. AfterCommit synchronization ✓ |
| Grade Enum | Implemented | **95%** | bachelor, master, phd ✓ |
| Spring Security | Implemented | **90%** | SecurityConfig + AuthFilter. @PreAuthorize on all endpoints ✓ |
| OpenAPI / Swagger | Implemented | **95%** | Endpoints documented |
| Flyway Migrations | Implemented | **95%** | V1-V9 complete (includes group capacity, application program, order signing fields) ✓ |
| DTO Validation | Implemented | **90%** | @NotNull, @Positive, @NotBlank on all create DTOs ✓ |
| Caching | Implemented | **85%** | Redis caching on reference entity lookups ✓ |
| Idempotency | Implemented | **80%** | IdempotencyIT integration test ✓ |
| Error Disclosure Prevention | Implemented | **90%** | ErrorDisclosureIT verifies no sensitive info leak ✓ |

---

## Documents Service — Feature Breakdown

| Feature | Status | Completeness | Notes |
|---------|--------|:------------:|-------|
| Document Upload (multipart → MinIO) | Implemented | **95%** | Creates metadata + presigned upload URL. Encryption support (AES256/KMS) |
| Document Metadata (PostgreSQL) | Implemented | **95%** | Document entity with type, status, checksums, JSONB metadata |
| Presigned Upload URL | Implemented | **95%** | S3 presigner with 10-minute duration |
| Presigned Download URL | Implemented | **95%** | S3 presigner with 10-minute duration |
| Document Delete | Implemented | **90%** | Deletes from S3 + DB |
| MinIO Webhook Integration | Implemented | **90%** | Validates S3 events. WebhookAuthFilter with constant-time comparison ✓ |
| S3 Bucket Auto-initialization | Implemented | **95%** | S3BucketInitializer creates bucket on startup ✓ |
| Document Types (enum) | Implemented | **90%** | passport, ipn, foreign_passport, personal_file ✓ |
| Document Status Workflow | Implemented | **85%** | pending → uploaded → validated → rejected → archived |
| File Size Validation | Implemented | **95%** | 20MB limit. Calculation fixed ✓ |
| Auth via Identity (Feign) | Implemented | **90%** | Timeout configured (3s/5s). Header forwarding ✓ |
| **Admission Service Feign Client** | **Implemented** | **85%** | Fetches application data for personal file generation. Present and working ✓ |
| **Personal File Generation (PDF)** | **Implemented** | **88%** | PersonalFileController + PersonalFileService + PdfGeneratorService + QrCodeService. PDF merging placeholder (only cover page used as output) |
| **Thymeleaf PDF Templates** | **Implemented** | **80%** | HTML→PDF via OpenHtmlToPdf. ThymeleafConfig ✓ |
| Spring Security | Implemented | **90%** | SecurityConfig + AuthFilter + WebhookAuthFilter. @PreAuthorize with roles ✓ |
| OpenAPI / Swagger | Implemented | **95%** | Endpoints documented |
| Flyway Migrations | Implemented | **90%** | V1-V3 (added personal_file type) ✓ |
| Spotless Code Formatting | Configured | **95%** | Google Java Format 1.17.0 enforced |
| JaCoCo Coverage | Configured | **90%** | 80% threshold enforced ✓ |
| ErrorProne Static Analysis | Configured | **90%** | UnusedVariable + EqualsHashCode rules |
| DTO Validation | Implemented | **90%** | @NotBlank, @NotNull, @Positive annotations ✓ |
| Document Mapper | Implemented | **90%** | MapStruct-based DocumentMapper ✓ |
| S3 Key Layout | Implemented | **90%** | `{prefix}{year}/{type}/{userId}/{name}/{docType}/{grade}/{uuid}{ext}` |

---

## Notifications Service — Feature Breakdown

| Feature | Status | Completeness | Notes |
|---------|--------|:------------:|-------|
| Email via SMTP | Implemented | **95%** | JavaMailSender with SimpleMailMessage |
| RabbitMQ Event Consumer | Implemented | **90%** | **4 queues** (admission, documents, audit, **identity**) + DLQ. Retry: 3x with 3s interval |
| Thymeleaf Template Rendering | Implemented | **95%** | HTML + text + telegram templates per type |
| i18n (English + Ukrainian) | Implemented | **95%** | 6 property files (2 languages × 3 channels) |
| Template: verify_email | Implemented | **95%** | HTML + text + telegram + i18n keys |
| Template: password_reset | Implemented | **85%** | HTML + text + i18n keys. **Missing telegram/password_reset.txt template** |
| Template: welcome_message | Implemented | **95%** | HTML + text + telegram + i18n keys. Integration test ✓ |
| Template: application_submitted | Implemented | **95%** | HTML + text + telegram + i18n keys |
| Template: application_status_changed | Implemented | **95%** | HTML + text + telegram + i18n keys |
| Template: application_approved | Implemented | **95%** | HTML + text + telegram + i18n keys ✓ |
| Template: document_received | Implemented | **95%** | HTML + text + telegram + i18n keys |
| Template: document_rejected | Implemented | **95%** | HTML + text + telegram + i18n keys |
| Template: system_maintenance | Implemented | **85%** | HTML + text + i18n keys. **Missing telegram/system_maintenance.txt template** |
| Telegram Channel | Partially Implemented | **75%** | DefaultTelegramBotClient (real API) + StubTelegramBotClient (fallback). Conditional on bot-token. 2 telegram templates missing (password_reset.txt, system_maintenance.txt) |
| DLQ Handling | Implemented | **75%** | Routes to DLQ on failure. DLQ consumer configurable. DlqHandlerIT test ✓ |
| Queue Configuration | Implemented | **90%** | 4 topic exchanges + 1 DLX. 4 main queues + 1 DLQ |
| JaCoCo Coverage (80%) | Enforced | **90%** | Exclusions reduced ✓ |
| Actuator / Prometheus | Implemented | **90%** | Health, info, prometheus exposed |
| Base Email Template | Implemented | **90%** | Shared `base.html` template ✓ |
| XSS Prevention | Implemented | **90%** | AllTemplatesRenderIT verifies XSS escaping ✓ |

---

## Gateway — Feature Breakdown

| Feature | Status | Completeness | Notes |
|---------|--------|:------------:|-------|
| Route: Identity Service | Configured | **95%** | Path predicates + circuit breaker + rate limiter |
| Route: Environment Service | Configured | **95%** | Path predicates + circuit breaker + rate limiter |
| Route: Admission Service | Configured | **95%** | Extended path predicates for all new endpoints ✓ |
| Route: Documents Service | Configured | **95%** | Path predicates + circuit breaker + rate limiter |
| Route: Notifications Service | Configured | **95%** | Path predicates + circuit breaker + rate limiter ✓ |
| Circuit Breakers (Resilience4j) | Configured | **95%** | Per-service circuits. All 5 fallbacks. CircuitBreakerIT ✓ |
| Rate Limiting (Redis) | Implemented | **95%** | Per-route config. IP spoofing fixed. Trusted proxy validation ✓ |
| Request Size Limits | Configured | **95%** | 1-20MB per route |
| Retry on Transient Failures | Configured | **90%** | 2 retries on 502/503/504, GET only |
| Fallback Controller | Implemented | **95%** | All 5 handlers. FallbackIT + FallbackControllerTest ✓ |
| Request ID Propagation | Implemented | **95%** | UUID-based X-Request-Id. Reactor Context. Leak prevention tested ✓ |
| **CORS** | **Implemented** | **95%** | Global config: localhost:3000 (configurable). All methods. Credentials. CorsIT test ✓ |
| Security Headers | Implemented | **95%** | HSTS, X-Content-Type-Options, X-Frame-Options, CSP, Referrer-Policy, Permissions-Policy. SecurityHeadersIT ✓ |
| Actuator / Prometheus | Implemented | **90%** | health, metrics, prometheus, info, gateway |
| **AccessLogFilter** | **Implemented** | **95%** | Request/response access logging filter ✓ |
| **DownstreamHealthIndicator** | **Implemented** | **95%** | Health checks for downstream services ✓ |
| **Rate Limiter IT** | **Implemented** | **90%** | RateLimiterIT verifies 429 responses ✓ |

---

## Computer Vision (Python) — Feature Breakdown

| Feature | Status | Completeness | Notes |
|---------|--------|:------------:|-------|
| All planned features | **NOT STARTED** | **0%** | Repository is empty |

---

## Cross-Service Integration Features

| Integration | Status | Completeness | Notes |
|-------------|--------|:------------:|-------|
| Gateway → all services (HTTP proxy) | Working | **95%** | 5 routes configured. All 5 fallbacks. CORS ✓ |
| Admission → Identity (Feign) | Working | **85%** | Timeout 3s/5s. Header forwarding ✓ |
| Admission → Documents (Feign) | Working | **85%** | Document validation on submit ✓ |
| Documents → Identity (Feign) | Working | **90%** | Timeout configured ✓ |
| Documents → Admission (Feign) | Working | **85%** | Application data for personal files ✓ |
| Identity → Environment (feature flags) | Working | **85%** | HTTP polling with Redis cache |
| Environment → Identity (Feign) | Working | **85%** | Auth validation via Feign ✓ |
| Identity → RabbitMQ (audit outbox) | Working | **90%** | AuditAspect + OutboxEventPublisher ✓ |
| Admission → RabbitMQ (events) | Working | **90%** | ApplicationEventPublisher with afterCommit ✓ |
| RabbitMQ → Environment (audit ingest) | Working | **90%** | Consumer active. Publisher confirms ✓ |
| RabbitMQ → Notifications (all events) | Working | **85%** | 4 queues: admission, documents, audit, identity. DLQ ✓ |
| Documents → MinIO (S3) | Working | **95%** | Upload, download, delete, webhook, bucket init |
| Notifications → SMTP (email) | Working | **90%** | Local: Mailpit. Prod: configurable SMTP |
| All → PostgreSQL (Flyway) | Working | **95%** | 4 schemas, 20+ migrations total ✓ |
| All → Redis (cache/rate limit) | Working | **95%** | All services use Redis ✓ |

---

## Test Infrastructure Summary

| Service | Unit Tests | Integration Tests | Total Files | JaCoCo Threshold | Testcontainers | CI/CD |
|---------|:----------:|:-----------------:|:-----------:|:----------------:|:--------------:|:-----:|
| Identity | 30 | 12 | **42** | 80% | PostgreSQL, Redis, RabbitMQ | ✓ |
| Environment | 10 | 7 | **17** | 50% | PostgreSQL, Redis, RabbitMQ | ✓ |
| Admission | 26 | 24 | **50** | 80% | PostgreSQL, RabbitMQ | ✓ |
| Documents | 8 | 12 | **20** | 80% | PostgreSQL, MinIO | ✓ |
| Notifications | 8 | 6 | **14** | 80% | RabbitMQ, GreenMail | ✓ |
| Gateway | 8 | 7 | **15** | 80% | Redis | ✓ |
| **TOTAL** | **90** | **68** | **158** | — | — | ✓ |

### CI/CD Workflows (per service)

| Workflow | Trigger | Scope |
|----------|---------|-------|
| `ci.yml` | PR on main/develop | Smoke tests only (`@Tag("smoke")`) |
| `regression.yml` | Manual / workflow_call | Full test suite + JaCoCo + Allure |
| `nightly.yml` | Cron (2 AM UTC daily) | Calls regression.yml with suite=all |
