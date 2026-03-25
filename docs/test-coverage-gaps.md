# Test Coverage Gaps — Detailed Analysis

> Updated: 2026-03-25 | Based on comprehensive code exploration of all 7 services

## Summary

| Service | Source Files | Unit Tests | Integration Tests | Total Test Files | JaCoCo Threshold | CI/CD |
|---------|:-----------:|:----------:|:-----------------:|:----------------:|:----------------:|:-----:|
| Identity Service | 101 | 30 | 12 | **42** | 80% | ✓ |
| Environment Service | 39 | 10 | 7 | **17** | 50% | ✓ |
| Admission Service | 104 | 26 | 24 | **50** | 80% | ✓ |
| Documents Service | 35 | 8 | 12 | **20** | 80% | ✓ |
| Notifications Service | 24 | 8 | 6 | **14** | 80% | ✓ |
| Gateway | 7 | 8 | 7 | **15** | 80% | ✓ |
| Computer Vision | 0 | 0 | 0 | **0** | — | — |
| **TOTAL** | **310** | **90** | **68** | **158** | — | ✓ |

### Changes Since Last Audit

| Service | Previous Test Files | Current Test Files | Change |
|---------|:-------------------:|:------------------:|:------:|
| Identity | 40 | **42** | +2 |
| Environment | 18 | **17** | -1 (count correction) |
| Admission | 48 | **50** | +2 |
| Documents | 20 | **20** | 0 |
| Notifications | 14 | **14** | 0 |
| Gateway | 13 | **15** | +2 |
| **TOTAL** | **153** | **158** | **+5** |

---

## 1. Identity Service (101 src / 42 test files)

### 1.1 Controller Test Coverage

| Controller | Endpoints | Test File | Status |
|-----------|:---------:|-----------|--------|
| **AuthController** | 8 | `AuthControllerTest.java` | **TESTED** ✓ |
| **UserController** | 12 | `UserControllerTest.java` + `UserControllerUnitTest.java` | **TESTED** ✓ |
| **RoleController** | 7 | `RoleControllerTest.java` | **TESTED** ✓ |
| **PermissionController** | 5 | `PermissionControllerTest.java` | **TESTED** ✓ |

### 1.2 Service Layer Test Coverage

| Service Class | Public Methods | Tested | Status |
|--------------|:-------:|:------:|--------|
| UserServiceImpl | 17 | 17 | **FULLY TESTED** ✓ |
| RoleServiceImpl | 9 | 9 | **FULLY TESTED** ✓ |
| PermissionServiceImpl | 9 | 9 | **FULLY TESTED** ✓ |
| AuthServiceImpl | 8 | 8 | **FULLY TESTED** ✓ |
| JwtServiceImpl | 9 | 9 | **FULLY TESTED** ✓ |
| OutboxEventServiceImpl | 3 | 3 | **FULLY TESTED** ✓ |
| OutboxEventPublisher | 1 | 1 | **FULLY TESTED** ✓ |
| AuditAspect | 1 | 1 | **FULLY TESTED** (6 scenarios) ✓ |
| FeatureFlagCacheManager | — | 8 | **TESTED** ✓ |
| StubNotificationClient | — | 2 | **TESTED** ✓ |

### 1.3 Repository Test Coverage

| Repository | Has Tests | Test Count | Notes |
|-----------|:---------:|:----------:|-------|
| UserRepository | Yes | 14+ | Testcontainers-based |
| RoleRepository | Yes | 9+ | `RoleEntityRepositoryTest.java` |
| PermissionRepository | Yes | 13+ | `PermissionRepositoryTest.java` |
| TokenRepository | Yes | 5+ | `TokenRepositoryTest.java` |
| VerificationTokenRepository | Yes | 5+ | `VerificationTokenRepositoryTest.java` |
| OutboxEventRepository | Yes | 4+ | `OutboxEventRepositoryTest.java` |

### 1.4 Integration Tests (NEW)

| Test File | Tests | Covers |
|-----------|:-----:|--------|
| `AuthFlowIT` | 5+ | Full login/register/verify/refresh/logout flow |
| `JwtSecurityIT` | 5+ | Token validation, expired tokens, tampered tokens |
| `RateLimitIT` | 3+ | Per-endpoint rate limiting with Redis |
| `RefreshTokenRotationIT` | 3+ | Token rotation, reuse detection |
| `RbacEndpointIT` | 5+ | Role-based access control on all admin endpoints |
| `JwtHardeningIT` | 4+ | JWT algorithm confusion, key manipulation |
| `PasswordLengthIT` | 3+ | Password validation boundaries |
| `SecurityEdgeCasesIT` | 4+ | Edge cases in security filters |
| `AuthValidationIT` | 5+ | Request validation on auth endpoints |
| `IdentityIntegrationSmokeTest` | 2 | Context load + health |

### 1.5 Validation Test Coverage

| Test File | Tests | Status |
|-----------|:-----:|--------|
| `RegisterRequestValidationTest` | 7 | ✓ |
| `PermissionCreateRequestValidationTest` | 3 | ✓ |
| `RoleCreateRequestValidationTest` | 3 | ✓ |
| `UserCreateRequestValidationTest` | 6 | ✓ |
| `UserUpdateRequestValidationTest` | 4 | ✓ |

### 1.6 Remaining Gaps

- `RabbitNotificationClient` only implements `sendVerificationEmail()` — needs `sendPasswordResetEmail()` and `sendWelcomeEmail()`
- `repsonce` package name typo (cosmetic, not functional)

---

## 2. Environment Service (39 src / 17 test files)

### 2.1 Unit Tests (10 files)

| Test File | Tests | Covers |
|-----------|:-----:|--------|
| `FeatureFlagControllerTest` | 7+ | All CRUD + evaluate + enabled endpoints |
| `ScopeControllerTest` | 2+ | Scope listing |
| `AuditControllerTest` | 4+ | Ingest + search endpoints |
| `FeatureFlagServiceTest` | 10+ | All service methods |
| `FeatureFlagServiceUtilsTest` | 7+ | All static utility methods |
| `AuditServiceTest` | 4+ | Ingest + search |
| `AuditIngestConsumerTest` | 2+ | RabbitMQ listener |
| `AuditTTLJobTest` | 2+ | Scheduled cleanup |
| `AuthFilterTest` | 3+ | Auth filter logic |
| `SecurityIntegrationTest` | 4+ | Security config verification |
| `EnvironmentServiceApplicationTests` | 1 | Context load |

### 2.2 Integration Tests (7 files — NEW)

| Test File | Tests | Covers |
|-----------|:-----:|--------|
| `EnvironmentIntegrationSmokeTest` | 2 | Context load + health with Testcontainers |
| `FeatureFlagControllerIT` | 5+ | Full CRUD via REST Assured |
| `FeatureFlagCacheIT` | 3+ | Redis cache behavior |
| `AuditIngestConsumerIT` | 3+ | RabbitMQ consumer with real broker |
| `AuditDlqIT` | 2+ | Dead letter queue routing |
| `RedisFailureIT` | 2+ | Graceful degradation when Redis is down |

### 2.3 Remaining Gaps

- No test for `FeatureFlagRequest` enum conversion error handling
- `AuditTTLJob` integration test would verify actual DB purge

---

## 3. Admission Service (104 src / 50 test files)

### 3.1 Unit Tests (26 files)

| Category | Test Files | Covers |
|----------|:----------:|--------|
| Controller tests | 9 | All 9 controllers: Application, Cathedra, Contract, EducationalProgram, Faculty, Group, GroupAssignment, Order, Privilege |
| Service tests | 12 | ApplicationService, CathedraService, ContractService, EducationalProgramService, FacultyService, GroupService, GroupAssignmentService, OrderService, PrivilegeService, OperatorAssignmentService, ContractNumberGenerator, OrderNumberGenerator |
| Security tests | 2 | AuthFilter, SecurityConfig |
| Event tests | 1 | ApplicationEventPublisher |
| Spec tests | 1 | ApplicationSpecificationService |
| Context test | 1 | AdmissionServiceApplicationTests |

### 3.2 Integration Tests (24 files — NEW)

| Test File | Tests | Covers |
|-----------|:-----:|--------|
| `ApplicationWorkflowIT` | 5+ | Full status lifecycle (draft→enrolled/archived) |
| `FlywayMigrationIT` | 2+ | All 9 migrations (V1-V9) execute correctly |
| `FeignClientContractIT` | 3+ | Identity + Documents Feign clients |
| `EventPublisherIT` | 3+ | RabbitMQ event publishing |
| `AfterCommitPublishIT` | 2+ | Transaction synchronization |
| `CacheIT` | 3+ | @Cacheable/@CacheEvict behavior |
| `N1QueryIT` | 2+ | EntityGraph prevents N+1 queries |
| `TransactionConsistencyIT` | 2+ | @Transactional behavior |
| `PaginationIT` | 2+ | Pageable implementations |
| `ContractNumberConcurrencyIT` | 2+ | Contract number uniqueness under concurrency |
| `OrderNumberConcurrencyIT` | 2+ | Order number uniqueness under concurrency |
| `AdmissionRbacIT` | 5+ | @PreAuthorize role-based access |
| `InputValidationIT` | 3+ | @Valid and DTO validation annotations |
| `ErrorDisclosureIT` | 2+ | GlobalExceptionHandler doesn't leak info |
| `RabbitMqFailureIT` | 2+ | Graceful degradation when RabbitMQ fails |
| `FeignClientFailureIT` | 2+ | Graceful degradation when services fail |
| `IdempotencyIT` | 2+ | Duplicate request handling |
| `EnumTypesIT` | 2+ | PostgreSQL enum type mapping |
| `FkConstraintsIT` | 2+ | Foreign key referential integrity |

### 3.3 Remaining Gaps

- No load/performance tests (only concurrency tests for number generators)
- Some edge cases in GroupAssignmentService auto-distribute algorithm
- No E2E tests for complete admission-to-enrollment flow across services

---

## 4. Documents Service (35 src / 20 test files)

### 4.1 Unit Tests (8 files)

| Test File | Tests | Covers |
|-----------|:-----:|--------|
| `DocumentControllerTest` | 10+ | All 6 document endpoints |
| `PersonalFileControllerTest` | 3+ | Personal file generation endpoint |
| `DocumentServiceTest` | 21+ | Service methods |
| `DocumentWebhookTest` | 10+ | Webhook validation scenarios |
| `PdfGeneratorServiceTest` | 3+ | HTML→PDF generation |
| `PersonalFileServiceTest` | 5+ | Personal file generation flow |
| `AuthFilterTest` | 3+ | Auth filter logic |
| `DocumentsServiceApplicationTests` | 1 | Context load |

### 4.2 Integration Tests (12 files — NEW)

| Test File | Tests | Covers |
|-----------|:-----:|--------|
| `DocumentsIntegrationSmokeTest` | 2 | Context load + health |
| `DocumentControllerIT` | 5+ | Full CRUD via REST Assured |
| `PersonalFilePdfIT` | 3+ | PDF generation with real Thymeleaf |
| `DocumentLifecycleIT` | 4+ | Upload → validate → download → delete with MinIO |
| `S3BucketInitializerIT` | 2+ | Auto bucket creation |
| `DocumentsMigrationIT` | 2+ | Flyway migrations |
| `DocumentValidationIT` | 3+ | DTO validation annotations |
| `WebhookAuthFilterIT` | 3+ | Webhook secret validation |
| `AdmissionClientContractIT` | 2+ | Feign client contract |
| `FeignClientFailureIT` | 2+ | Graceful degradation |
| `MinioFailureIT` | 2+ | Graceful degradation when MinIO fails |

### 4.3 Remaining Gaps

- Some internal methods not directly tested: `buildKey()`, `deletePrevFilesInMinio()`
- S3 encryption modes (AES256/KMS) not integration-tested

---

## 5. Notifications Service (24 src / 14 test files)

### 5.1 Unit Tests (8 files)

| Test File | Tests | Covers |
|-----------|:-----:|--------|
| `EmailNotificationChannelTest` | 5 | send, supports |
| `NotificationServiceTest` | 6 | Channel routing, error handling |
| `EventListenerServiceTest` | 6 | Email+telegram, DLQ, single channel |
| `ThymeleafTemplateServiceTest` | 10 | All 9 template types + error case |
| `TelegramNotificationChannelTest` | 5 | send, null chatId, supports |
| `DefaultTelegramBotClientTest` | 3 | URL construction, exception handling |
| `StubTelegramBotClientTest` | 4 | No-op behavior, null handling |
| `WelcomeMessageTemplateIntegrationTest` | 6 | HTML and text rendering with payloads |

### 5.2 Integration Tests (6 files — NEW)

| Test File | Tests | Covers |
|-----------|:-----:|--------|
| `NotificationsIntegrationSmokeTest` | 1 | Context load with RabbitMQ |
| `EmailDeliveryIT` | 3 | GreenMail SMTP, email delivery, from address |
| `AdmissionEventListenerIT` | 2 | Admission queue processing, approval event |
| `DlqHandlerIT` | 1 | DLQ routing for invalid messages |
| `AllTemplatesRenderIT` | 3 | Parameterized: mail/text rendering, XSS escaping |

### 5.3 Template Coverage

| Template Type | Unit Test | Integration Test | Email Test | Telegram Test |
|--------------|:---------:|:----------------:|:----------:|:-------------:|
| VERIFY_EMAIL | **Yes** | **Yes** | **Yes** | No |
| PASSWORD_RESET | **Yes** | **Yes** | No | **No** (template file missing) |
| WELCOME_MESSAGE | **Yes** | **Yes** | No | No |
| APPLICATION_SUBMITTED | **Yes** | **Yes** | No | No |
| APPLICATION_STATUS_CHANGED | **Yes** | **Yes** | No | No |
| APPLICATION_APPROVED | **Yes** | **Yes** | **Yes** | No |
| DOCUMENT_RECEIVED | **Yes** | **Yes** | No | No |
| DOCUMENT_REJECTED | **Yes** | **Yes** | No | No |
| SYSTEM_MAINTENANCE | **Yes** | **Yes** | No | **No** (template file missing) |

### 5.4 Remaining Gaps

- No per-template email delivery integration tests (only VERIFY_EMAIL and APPLICATION_APPROVED tested via GreenMail)
- No real Telegram API integration test (only DefaultTelegramBotClient unit test)
- password_reset and system_maintenance not tested via DLQ path

---

## 6. Gateway (7 src / 15 test files)

### 6.1 Unit Tests (8 files)

| Test File | Tests | Covers |
|-----------|:-----:|--------|
| `FallbackControllerTest` | 25 | All 5 fallback endpoints (5 assertions each) |
| `CorsConfigurationTest` | 11 | CORS config: origins, methods, headers, credentials, maxAge |
| `GatewaySupportConfigTest` | 26+ | UUID generation, X-Request-Id, Reactor context, error propagation |
| `RateLimitKeyResolversTest` | 12 | Principal extraction, IP extraction, trusted proxy, fallback |
| `GatewayApplicationTests` | 1 | Context load |

### 6.2 Integration Tests (7 files — NEW)

| Test File | Tests | Covers |
|-----------|:-----:|--------|
| `GatewayIntegrationSmokeTest` | 2 | Context load + actuator health with Redis |
| `CircuitBreakerIT` | 3 | All 5 services down → fallback, consistency, structure |
| `CorsIT` | 3 | Preflight allowed, POST preflight, disallowed origin |
| `FallbackIT` | 1 (parameterized ×5) | All 5 fallback endpoints via routing |
| `RateLimiterIT` | 1 | Burst capacity exhaustion → 429 response |
| `SecurityHeadersIT` | 6 | All 6 security response headers verified |

### 6.3 Remaining Gaps

- No test for actual downstream routing (would require mock backend services)
- SecurityHeadersFilter excluded from JaCoCo (simple WebFilter, low risk)

---

## 7. Computer Vision (Python — empty repo)

No code exists. No tests possible.

---

## 8. External Test Suites

| Suite | Location | Status |
|-------|----------|--------|
| API Tests | `tests/api-tests/` | **Empty** — Git repo initialized, no test files |
| Web Tests | `tests/web-tests/` | **Empty** — Git repo initialized, no test files |

---

## Priority Recommendations

### P0 — Remaining Gaps to Close
1. Add `sendPasswordResetEmail()` and `sendWelcomeEmail()` to Identity RabbitNotificationClient
2. Create missing Telegram templates: `password_reset.txt`, `system_maintenance.txt`

### P1 — Improve Coverage Quality
3. Raise Environment Service JaCoCo threshold from 50% to 80% (add tests if needed)
4. Add integration test for full PDF generation pipeline (merged output)
5. Create cross-service E2E test suite in `tests/api-tests/`

### P2 — Long-term Quality
6. Add per-template email delivery integration tests in notifications-service
7. Add S3 encryption integration tests in documents-service
8. Add load/performance tests for critical paths
