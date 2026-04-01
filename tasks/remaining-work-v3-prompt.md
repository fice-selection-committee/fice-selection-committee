# Remaining Work — Implementation Prompt (v3)

## Context

v1.3.0 shipped (Notification Center). Three audit rounds completed.
Phase 1 (Sonner fix), Phase 2 (Proxy migration), and Phase 5 (Visual regression infra) are done.
This prompt covers the **two remaining workstreams** in separate repositories:

1. **Telegram Bot — HealthCallbackHandler configuration** (minor improvement)
2. **Backend E2E Test Maintenance** — sync with v1.3.0 + security changes

---

## Items Verified as DONE (do NOT reimplement)

### From remaining-work-prompt.md (v1) — ALL COMPLETE:
- Security hardening (CORS, actuator, AuthFilter delegation, magic link referrer, CSP headers)
- UX fixes (sidebar active state, useIsMobile hydration, refreshUser error handling)
- Accessibility (ARIA, focus management, skip-to-content, prefers-reduced-motion)
- Infrastructure (Trivy scanning, Grafana alerts, health check tuning)
- Test fixes (role-guard.test.tsx, AuthFilterTest for admission/documents)
- E2E specs (error handling, admin/operator dashboards, DataTable)
- Cleanup (task files pruned, .gitkeep removed)

### From remaining-work-v2-prompt.md (v2) — ALL COMPLETE:
- Phase 1: Sonner toast theme bug fixed (`useThemeStore` replaces `next-themes`)
- Phase 2: Frontend proxy migration complete (Next.js rewrites, same-origin API calls)
- Phase 5: Visual regression testing infrastructure (playwright config, theme-snapshots.spec.ts, theme-toggle.spec.ts)

### Telegram Bot Phases 2-4 — ALREADY IMPLEMENTED (verified by code inspection):
All items from the original prompt's Phase 3 are already present in the codebase:
- **3.1.1** FlagsCallbackHandler toggle button — uses `msg.msg(locale, "flags.view.btn.toggle")` correctly
- **3.1.2** NotificationsCallbackHandler — shows subscription status with subscribe/unsubscribe buttons
- **3.1.3** CommandMenuRegistrar — exists with `@EventListener(ApplicationReadyEvent.class)` + test
- **3.1.4** RBAC — `BotAuthorizationService.canToggleFlags()` wired into FlagsCallbackHandler
- **3.1.5** Rate Limiting — `RateLimitService` with per-chat (5 req/sec) and per-user write (60s) limits
- **3.1.6** Deep Link Support — `BotCommandDispatcher` handles `/start flag_<key>` at line 55
- **3.1.7** NavigationStateManager — exists, wired into `CallbackQueryDispatcher` for nav:back/nav:home
- **3.2.1** Micrometer Metrics — `bot.commands.total`, `bot.callbacks.total`, `bot.users.active` all present
- **3.2.2** Audit Logging — `log.info("AUDIT ...")` in FlagsCallbackHandler and NotificationsCallbackHandler
- **3.2.3** Circuit Breaker — `ResilientEnvironmentServiceClient` with `@CircuitBreaker` and cache fallback
- **3.3.1** DB-backed subscriptions — `FlagChangeNotificationListener` uses `botUserService.getSubscribedUsers()`. `NotificationChatRegistry` retained for group chat support.
- **3.3.2** Health Handler — configurable timeout, response time display, "last checked" timestamp
- **3.3.3** TelegramHealthIndicator — calls `getMe` API, reports UP/DOWN
- **3.3.4** TelegramApiClient error recovery — 429 retry with exponential backoff, 403 handling, 4096-char truncation

---

## Phase 1: Telegram Bot — HealthCallbackHandler Service URL Configuration

> **Repository:** `server/services/selection-committee-telegram-bot-service/`

### 1.1 Problem Statement

`HealthCallbackHandler` hardcodes default service URLs in `getDefaultEndpoints()`:

```java
private static Map<String, String> getDefaultEndpoints() {
    var defaults = new LinkedHashMap<String, String>();
    defaults.put("Environment", "http://sc-environment:8085/actuator/health");
    defaults.put("Identity", "http://sc-identity:8081/actuator/health");
    defaults.put("Gateway", "http://sc-gateway:8080/actuator/health");
    defaults.put("Admission", "http://sc-admission:8083/actuator/health");
    defaults.put("Documents", "http://sc-documents:8084/actuator/health");
    defaults.put("Notifications", "http://sc-notifications:8086/actuator/health");
    return defaults;
}
```

The constructor accepts custom `Map<String, String> serviceEndpoints` but there's no way to configure it from `application.yml`. The `@Autowired` constructor only takes `BotMessageResolver msg` and `int timeoutMs`.

### 1.2 Target

Make health check service URLs configurable via `application.yml`:

```yaml
bot:
  health-check:
    timeout-ms: 2000
    services:
      Environment: http://sc-environment:8085/actuator/health
      Identity: http://sc-identity:8081/actuator/health
      Gateway: http://sc-gateway:8080/actuator/health
      Admission: http://sc-admission:8083/actuator/health
      Documents: http://sc-documents:8084/actuator/health
      Notifications: http://sc-notifications:8086/actuator/health
```

### 1.3 Implementation

**Step 1: Add configuration properties to `TelegramProperties.java`**

Current `TelegramProperties` only has `bot` record. Add a `healthCheck` record:

```java
@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(Bot bot) {
    // ... existing Bot record
}
```

Create a new config record or extend existing config:

**File:** `src/main/java/edu/kpi/fice/telegram_service/config/HealthCheckProperties.java`

```java
@ConfigurationProperties(prefix = "bot.health-check")
public record HealthCheckProperties(
    @DefaultValue("2000") int timeoutMs,
    Map<String, String> services
) {}
```

**Step 2: Update `HealthCallbackHandler` constructor**

Replace the `@Value`-based `@Autowired` constructor with one that takes `HealthCheckProperties`:

```java
@Autowired
public HealthCallbackHandler(BotMessageResolver msg, HealthCheckProperties props) {
    this(msg, props.services(), props.timeoutMs());
}
```

**Step 3: Update `application.yml`**

Add `services` map under `bot.health-check`:

```yaml
bot:
  health-check:
    timeout-ms: 2000
    services:
      Environment: http://sc-environment:8085/actuator/health
      Identity: http://sc-identity:8081/actuator/health
      Gateway: http://sc-gateway:8080/actuator/health
      Admission: http://sc-admission:8083/actuator/health
      Documents: http://sc-documents:8084/actuator/health
      Notifications: http://sc-notifications:8086/actuator/health
```

**Step 4: Update `HealthCallbackHandlerTest`**

The test currently creates the handler with a custom `serviceEndpoints` map directly. Update to also test the properties-based constructor path.

### 1.4 Verification

```bash
cd server/services/selection-committee-telegram-bot-service
./gradlew test
./gradlew build
```

- All 27 existing tests pass
- Health check handler uses configured URLs
- Defaults still work if `services` map is empty

---

## Phase 2: Backend E2E Test Maintenance

> **Repository:** `server/services/selection-committee-e2e-tests/`
> **Stack:** JUnit 5, REST Assured, Awaitility, Jackson
> **CI:** `.github/workflows/ci.yml` (PR), `nightly.yml` (daily 3AM UTC), `regression.yml` (reusable)

### 2.1 Current State

8 E2E test classes exist:

| Test Class | Steps | What It Tests |
|---|---|---|
| `AuthFlowE2ETest` | 7 | Registration, email verify (Mailpit), login, current user, negatives |
| `FullAdmissionFlowE2ETest` | 18 | Complete application lifecycle across roles |
| `DocumentRejectionFlowE2ETest` | 5 | Document upload, submission, operator rejection |
| `ContractOrderE2ETest` | 18 | Contract + order management across roles |
| `EnvironmentServiceE2ETest` | 5 | Feature flag CRUD, scope listing |
| `NegativeFlowE2ETest` | 6 | No token, invalid token, bad data, wrong role |
| `NotificationDeliveryE2ETest` | 4 | Mailpit email delivery after registration |
| `RbacMatrixE2ETest` | ~22 | 5 roles × endpoints permission matrix |

**Infrastructure:**
- `AbstractE2ETest` — base class, URL resolution (system props → env vars → localhost defaults)
- `ApiClient` — REST Assured wrapper, 50+ endpoints across identity/admission/documents/environment
- `TestUsers` — pre-seeded test accounts (admin, applicant, operator, contract_manager, executive_secretary)

### 2.2 What Needs Syncing

#### 2.2.1 Add Notification Service to ApiClient

**Problem:** `ApiClient.java` has no methods for the notification service endpoints added in v1.3.0.

**Notification service endpoints (port 8086):**

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/notifications` | Paginated list (filters: type, read, priority) |
| GET | `/api/v1/notifications/unread-count` | Unread count |
| PATCH | `/api/v1/notifications/{id}/read` | Mark as read |
| PATCH | `/api/v1/notifications/read-all` | Mark all read |
| DELETE | `/api/v1/notifications/{id}` | Archive |
| GET | `/api/v1/notifications/stream` | SSE stream |
| GET | `/api/v1/notifications/preferences` | Get preferences |
| PUT | `/api/v1/notifications/preferences` | Update preferences |

**Implementation:**

1. Add `notificationsUrl` field to `ApiClient`:

```java
private final String notificationsUrl;

public ApiClient(String identityUrl, String admissionUrl,
                 String documentsUrl, String environmentUrl,
                 String notificationsUrl) {
    // ...
    this.notificationsUrl = notificationsUrl;
}
```

2. Add `NOTIFICATIONS_URL` to `AbstractE2ETest`:

```java
protected static final String NOTIFICATIONS_URL =
    resolveUrl("e2e.notifications.url", "E2E_NOTIFICATIONS_URL", "http://localhost:8086");
```

3. Update all `ApiClient` instantiations in test classes to include `NOTIFICATIONS_URL`.

4. Add notification API methods:

```java
// GET /api/v1/notifications
public Response getNotifications(String accessToken) { ... }
public Response getNotifications(String accessToken, Boolean read, String type) { ... }

// GET /api/v1/notifications/unread-count
public Response getUnreadCount(String accessToken) { ... }

// PATCH /api/v1/notifications/{id}/read
public Response markAsRead(String accessToken, Long notificationId) { ... }

// PATCH /api/v1/notifications/read-all
public Response markAllAsRead(String accessToken) { ... }

// DELETE /api/v1/notifications/{id}
public Response archiveNotification(String accessToken, Long notificationId) { ... }

// GET /api/v1/notifications/preferences
public Response getNotificationPreferences(String accessToken) { ... }

// PUT /api/v1/notifications/preferences
public Response updateNotificationPreferences(String accessToken, boolean soundEnabled,
    double soundVolume, boolean desktopNotifications) { ... }
```

#### 2.2.2 New Test: NotificationCenterE2ETest

**File:** `src/test/java/edu/kpi/fice/e2e/flow/NotificationCenterE2ETest.java`

Tests the full notification lifecycle added in v1.3.0:

```
Step 1:  Login as admin → get token
Step 2:  GET /api/v1/notifications → verify empty or existing list shape
Step 3:  GET /api/v1/notifications/unread-count → verify count response shape
Step 4:  Trigger a notification (e.g., register new user → verification email → check in-app notification)
Step 5:  GET /api/v1/notifications → verify notification appears
         Assert: id, type, priority, title, message, sourceService, read=false, createdAt are present
Step 6:  PATCH /api/v1/notifications/{id}/read → mark as read
         Assert: 200, read=true, readAt is not null
Step 7:  GET /api/v1/notifications/unread-count → verify count decreased by 1
Step 8:  PATCH /api/v1/notifications/read-all → mark all as read
         Assert: 204 No Content
Step 9:  GET /api/v1/notifications/preferences → verify default preferences
         Assert: soundEnabled, soundVolume, desktopNotifications fields present
Step 10: PUT /api/v1/notifications/preferences → update preferences
         Assert: 200, values match request
Step 11: GET /api/v1/notifications/preferences → verify persistence
Step 12: DELETE /api/v1/notifications/{id} → archive notification
         Assert: 204
Step 13: GET /api/v1/notifications → verify archived notification is gone
```

**SSE test consideration:** The SSE endpoint (`/api/v1/notifications/stream`) is a long-lived streaming connection. Testing it via REST Assured is non-trivial. Options:
- Use Java's `HttpClient` with async response handling to connect to SSE, then trigger an action that should emit an event, and verify the event arrives within a timeout.
- Or skip SSE testing in backend E2E (frontend Playwright E2E already tests it via the UI).
- **Recommended:** Skip SSE in backend E2E. The frontend E2E notification lifecycle spec already validates SSE delivery. The backend E2E should focus on the REST CRUD endpoints.

#### 2.2.3 Sync NotificationDeliveryE2ETest

**Current state:** Only tests email notification delivery via Mailpit (registration → email → verify token). Does not test in-app notifications.

**Enhancement:** After step 4 (verify email content), add steps to check that the notification service also received an in-app notification for the event:

```java
@Test
@Order(5)
void step05_verifyInAppNotificationCreated() {
    // Login with the newly registered user (use verification token from step 4)
    // or login with admin to check notification service directly

    // Poll notification service for notifications
    await().atMost(10, TimeUnit.SECONDS)
        .pollInterval(1, TimeUnit.SECONDS)
        .until(() -> {
            Response resp = api.getNotifications(adminToken);
            return resp.statusCode() == 200
                && resp.jsonPath().getList("content").size() > 0;
        });
}
```

**Note:** This depends on whether user registration triggers an in-app notification via RabbitMQ. If registration only sends email (via notification service), then in-app notification creation happens through a different event path. Verify the actual event flow before implementing.

#### 2.2.4 Sync NegativeFlowE2ETest with AuthFilter Changes

**Context:** v1.3.0 changed `AuthFilter` in admission-service and documents-service to delegate auth failures to Spring Security instead of returning 401 directly (commit `14217fd`).

**Impact:** The 401 response body shape may differ:
- **Before:** Custom JSON: `{"status": 401, "message": "Unauthorized"}`
- **After:** Spring Security default: `{"error": "Unauthorized", "status": 401, "path": "..."}`

**Action:** Review `NegativeFlowE2ETest`:
- `unauthorizedAccess_noToken_returns401()` — tests via GATEWAY, not direct service. Gateway's own auth filter handles this. **Likely unaffected.**
- `invalidToken_returns401()` — same as above, via gateway. **Likely unaffected.**

**Verification:** Run the test suite against the current stack and check if the 401 tests pass. If they do, no code changes needed. If the response body assertions fail, update them.

#### 2.2.5 Sync with CORS + Proxy Migration

**Context:** Gateway CORS was restricted from `*` to `http://localhost:3000` (commit `2f101d2`). Frontend now proxies API calls through Next.js rewrites.

**Impact on E2E tests:**
- `ApiClient` targets individual service URLs directly (bypasses gateway for most calls). **Not affected by CORS.**
- `NegativeFlowE2ETest` makes 2 calls via `GATEWAY_URL`. These are server-side REST Assured calls, not browser requests. **CORS headers are only relevant for browsers. Not affected.**
- `RbacMatrixE2ETest` — if any tests go through the gateway, same logic applies. Check.

**Action:** No code changes needed for CORS. REST Assured doesn't send `Origin` headers by default, so CORS is not enforced.

#### 2.2.6 Add Notification Endpoints to RbacMatrixE2ETest

The RBAC matrix test should verify that notification endpoints enforce correct permissions:

| Endpoint | APPLICANT | OPERATOR | ADMIN | CONTRACT_MGR | EXEC_SEC |
|---|---|---|---|---|---|
| GET /notifications | 200 (own) | 200 (own) | 200 (own) | 200 (own) | 200 (own) |
| PATCH /notifications/{id}/read | 200 (own) | 200 (own) | 200 (own) | 200 (own) | 200 (own) |
| PATCH /notifications/read-all | 204 | 204 | 204 | 204 | 204 |
| GET /preferences | 200 | 200 | 200 | 200 | 200 |
| PUT /preferences | 200 | 200 | 200 | 200 | 200 |

All notification endpoints are user-scoped (each user sees only their own). Add test methods to verify:
- Each role can access their own notifications
- No role can access another user's notifications (if the API supports user-scoping via token)

#### 2.2.7 Update application-e2e.yml

Add notification service URL:

```yaml
e2e:
  notifications:
    url: ${E2E_NOTIFICATIONS_URL:http://localhost:8086}
```

### 2.3 Implementation Order

```
2.2.7 (config) → 2.2.1 (ApiClient) → 2.2.2 (NotificationCenterE2ETest)
                                    → 2.2.3 (NotificationDeliveryE2ETest sync)
                                    → 2.2.6 (RBAC matrix additions)
2.2.4 (run tests, verify AuthFilter compat — may be no-op)
2.2.5 (verify CORS compat — should be no-op)
```

### 2.4 Build & Test

```bash
# Start full stack
cd infra && docker compose -f docker-compose.yml -f docker-compose.services.yml up -d

# Wait for all services to be healthy
docker compose -f docker-compose.yml -f docker-compose.services.yml ps

# Run E2E tests
cd server/services/selection-committee-e2e-tests
./gradlew test

# Run only smoke tests
./gradlew test -PincludeTags=smoke

# Run with custom service URLs
./gradlew test \
  -De2e.gateway.url=http://localhost:8080 \
  -De2e.identity.url=http://localhost:8081 \
  -De2e.notifications.url=http://localhost:8086
```

### 2.5 CI Workflow Updates

If `NOTIFICATIONS_URL` is needed in CI, update `.github/workflows/regression.yml`:

```yaml
env:
  E2E_NOTIFICATIONS_URL: http://localhost:8086
```

The nightly workflow calls `regression.yml` — changes propagate automatically.

---

## Dependency Graph

```
Phase 1 (Health config)           ── independent, minor, telegram bot repo
Phase 2 (Backend E2E)             ── independent, e2e-tests repo
  └── 2.2.1 (ApiClient)          ── prerequisite for all new tests
  └── 2.2.2 (NotificationCenter) ── main new test class
  └── 2.2.3 (Delivery sync)      ── enhancement to existing test
  └── 2.2.4 (AuthFilter compat)  ── verification only, likely no-op
  └── 2.2.5 (CORS compat)        ── verification only, no-op
  └── 2.2.6 (RBAC additions)     ── depends on 2.2.1
```

**Both phases can run in parallel** — they target different repositories.

---

## Verification Checklist

### Phase 1 (Telegram Bot)
- [ ] `HealthCheckProperties` record created with `timeoutMs` and `services`
- [ ] `HealthCallbackHandler` uses properties-based constructor
- [ ] `application.yml` has `bot.health-check.services` map
- [ ] `./gradlew build` passes (all tests + spotless + coverage)

### Phase 2 (Backend E2E)
- [ ] `ApiClient` has notification service methods (7 new endpoints)
- [ ] `AbstractE2ETest` has `NOTIFICATIONS_URL` constant
- [ ] `application-e2e.yml` has `e2e.notifications.url` config
- [ ] `NotificationCenterE2ETest` exists with 13 ordered steps
- [ ] `NotificationDeliveryE2ETest` optionally checks in-app notifications
- [ ] `RbacMatrixE2ETest` has notification endpoint permission tests
- [ ] All `ApiClient` instantiations updated with 5th parameter
- [ ] `./gradlew test` passes against running stack
- [ ] `./gradlew test -PincludeTags=smoke` passes
- [ ] CI workflows have `E2E_NOTIFICATIONS_URL` if needed

---

## What is NOT in this prompt (and why)

| Item | Reason |
|---|---|
| Sonner toast fix | Done in remaining-work-v2 |
| Frontend proxy migration | Done in remaining-work-v2 |
| Visual regression tests | Done in remaining-work-v2 |
| Telegram bot Phases 2-4 | Already implemented — verified by code inspection |
| Gateway CORS simplification | Functional as-is; proxy handles same-origin, CORS still needed for Swagger UI dev profile |
| `next-themes` removal | Done in remaining-work-v2 |
| SSE testing in backend E2E | Frontend Playwright E2E already validates SSE delivery |
| Performance test updates | K6/JMH scripts in E2E repo are maintenance-only, no new gaps |
| Docker compose changes for E2E | E2E tests use URL config, not compose — no changes needed |
