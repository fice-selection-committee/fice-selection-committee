# Stabilization & Improvements — Comprehensive Implementation Prompt

Execute all tasks below in the order presented. Each task includes the exact files, current state, and expected outcome. Follow TDD where applicable. Create a feature branch per logical group.

---

## Group 1: Notification Center Stabilization

### Branch: `hotfix/notification-center-fixes`

---

### Task 1.1 — Fix SSE Async Dispatch Auth Error (HIGH)

**Problem:** The SSE endpoint `/api/v1/notifications/stream` is already listed in `sc.auth.public-paths` in the notifications-service `application.yml` (line 111). However, the `AuthFilter` (sc-auth-starter) still processes the request — it skips authentication but the async SSE dispatch can trigger "response already committed" errors when Spring Security's filter chain runs on the async thread.

**Current state:**
- `server/services/selection-committee-notifications-service/src/main/resources/application.yml` lines 103-111 — public-paths includes `/api/v1/notifications/stream`
- `server/libs/sc-auth-starter/src/main/java/edu/kpi/fice/common/auth/filter/AuthFilter.java` — `shouldNotFilter()` checks `skipFilterPaths`, NOT `publicPaths`
- `server/libs/sc-auth-starter/src/main/java/edu/kpi/fice/common/auth/config/SecurityConfigDefaults.java` — `publicPaths` used only in `requestMatchers().permitAll()`

**Root cause:** `publicPaths` are permit-all in Spring Security but the `AuthFilter` still fires for them. On async SSE dispatch, the filter chain re-runs on the async thread after the response is already committed, causing errors.

**Fix:**
1. In `AuthFilter.java`, override `shouldNotFilter()` to also skip requests matching `publicPaths` (not just `skipFilterPaths`). Inject `AuthProperties` and check both lists.
2. Add unit test in `server/libs/sc-auth-starter/src/test/java/edu/kpi/fice/common/auth/filter/AuthFilterTest.java` verifying that requests to public paths are skipped entirely.
3. Since this is a shared library change: bump patch version in `server/version.properties` → `1.3.1`, run `./gradlew publishToMavenLocal` from `server/`, rebuild notifications-service.

**Verification:** Start notifications-service in Docker. Connect to SSE stream. Confirm no "response already committed" errors in logs after token expiry or reconnection.

---

### Task 1.2 — Fix SseEmitterRegistry TOCTOU Race (LOW)

**Problem:** In `SseEmitterRegistry.java`, the `remove()` and `sendToUser()` methods have a time-of-check/time-of-use race between `emitters.get(userId)` and subsequent modification.

**File:** `server/services/selection-committee-notifications-service/src/main/java/edu/kpi/fice/notifications_service/sse/SseEmitterRegistry.java`

**Current state (lines 73-100):**
```java
public void remove(Long userId, SseEmitter emitter) {
  Set<SseEmitter> userEmitters = emitters.get(userId);  // CHECK
  if (userEmitters != null) {
    userEmitters.remove(emitter);
    if (userEmitters.isEmpty()) {
      emitters.remove(userId);  // USE — another thread could have added between check and here
    }
  }
}
```

**Fix:**
1. Use `emitters.computeIfPresent()` for atomic check-and-modify in `remove()`.
2. In `sendToUser()`, iterate over a snapshot (`new ArrayList<>(userEmitters)`) to avoid `ConcurrentModificationException`, then use `computeIfPresent()` for cleanup.
3. For the per-user limit check in `register()`, use `emitters.compute()` to atomically check size and add.

**Tests:** Add concurrency test with multiple threads calling `register()`, `remove()`, and `sendToUser()` simultaneously. Verify no exceptions and no orphaned empty sets.

---

### Task 1.3 — Fix Notification Settings Debounce Flush-on-Unmount (MEDIUM)

**Problem:** In `NotificationSettings`, closing the popover within 500ms of a preference change cancels the debounced sync. The unmount cleanup calls `updatePrefsRef.current.mutate()`, but React Query may cancel in-flight mutations on unmount.

**File:** `client/web/src/components/notifications/notification-settings.tsx` (lines 71-85)

**Fix:**
1. Replace the mutation-based flush with a synchronous `navigator.sendBeacon()` call in the unmount cleanup. `sendBeacon` is fire-and-forget and guaranteed to dispatch even during page unload.
2. Construct the beacon payload as JSON to `${API_BASE}/api/v1/notifications/preferences` with the current preferences state.
3. Alternatively, if `sendBeacon` doesn't support auth headers: use `fetch()` with `keepalive: true` in the cleanup, which survives unmount.
4. Keep the debounced mutation for normal (non-unmount) syncs.

**Tests:** Update `client/web/tests/unit/components/notification-settings-debounce.test.tsx`:
- Test: changing preference and immediately unmounting triggers sync (mock `navigator.sendBeacon` or `fetch` with `keepalive`).
- Test: normal debounce still works when component stays mounted.

---

### Task 1.4 — Fix SSE 401 Handling in Frontend (LOW)

**Problem:** The SSE hook at `client/web/src/hooks/use-notification-stream.ts` stops reconnecting on 401 (lines 91-94), but does not redirect to login or surface the auth failure to the user. The user sees a silent disconnection.

**Current state:** On 401, it sets `isConnected: false` and returns. No toast, no redirect.

**Fix:**
1. On 401 response, call `useAuth().logout()` or redirect to `/login` to force re-authentication.
2. If the auth provider exposes a `refreshToken()` method, attempt one refresh before giving up.
3. Show a toast (`sonner`) informing the user: "Session expired. Please log in again."

**Tests:** Add test in `client/web/tests/unit/hooks/` for the 401 handling path — mock a 401 fetch response and verify logout/redirect is triggered.

---

## Group 2: Frontend Modernization

### Branch: `feature/next16-proxy-migration`

---

### Task 2.1 — Migrate Middleware to Proxy Convention

**Problem:** Next.js 16.2.1 deprecated the `middleware.ts` convention with warning: `The "middleware" file convention is deprecated. Please use "proxy" instead.`

**Current state:**
- `client/web/src/middleware.ts` (73 lines) — handles auth redirects, onboarding enforcement, role-based routing
- `client/web/next.config.ts` (lines 9-15) — API rewrites to gateway

**Important context:** The Next.js "proxy" convention is for API proxying (replacing rewrites). The auth/routing middleware may need to stay as middleware or move to a layout-based approach. Research the Next.js 16 proxy convention before implementing.

**Steps:**
1. Read Next.js 16 docs on the proxy convention (`npx next info` or check `node_modules/next/` for proxy docs).
2. Create `client/web/src/proxy.ts` (or whatever the convention requires) to replace the API rewrite:
   ```typescript
   // Proxy all /api/* requests to the gateway
   export default { target: process.env.API_PROXY_TARGET ?? "http://localhost:8080" }
   ```
3. Remove the `rewrites()` function from `next.config.ts`.
4. For auth/routing logic currently in `middleware.ts`: if the proxy convention doesn't support this, keep the middleware approach (Next.js may support both). If middleware is fully deprecated, move auth checks to a root layout server component or route group layouts.
5. Delete `middleware.ts` only after all its functionality is covered.

**Verification:**
- `pnpm dev` — no deprecation warning in console.
- API calls from frontend still reach the gateway.
- Auth redirects (unauthenticated → login, wrong role → correct dashboard, incomplete onboarding → onboarding) still work.
- Run existing Playwright E2E tests to confirm no regressions.

---

## Group 3: Observability — Grafana Dashboard Gaps

### Branch: `feature/grafana-dashboard-improvements`

**Current state:** 8 Grafana dashboards already exist in `infra/grafana/dashboards/`:
- circuit-breakers.json, feature-flags.json, http-requests.json, jvm-metrics.json, postgresql.json, rabbitmq.json, service-overview.json, services-overview.json

**What's missing:** The dashboards exist but may reference metrics that services don't expose, or miss notification-center-specific metrics.

---

### Task 3.1 — Add Notification Center Dashboard

**Create:** `infra/grafana/dashboards/notifications.json`

**Panels to include:**
1. **SSE Connections** — Active SSE connections (gauge). Metric: custom counter from `SseEmitterRegistry` (needs backend instrumentation — see Task 3.2).
2. **Notifications Sent** — Rate of notifications sent per channel (IN_APP, EMAIL, TELEGRAM). Metric: custom counter.
3. **Notification Delivery Latency** — Time from event published to SSE delivery. Metric: custom timer.
4. **RabbitMQ Consumer Lag** — Messages pending in notification queues. Source: RabbitMQ metrics already scraped.
5. **Email Delivery Status** — Success/failure rate for email notifications.
6. **Cleanup Scheduler** — Archived/deleted notifications count per run.

### Task 3.2 — Instrument Notifications Service with Custom Metrics

**File:** `server/services/selection-committee-notifications-service/`

**Add Micrometer instrumentation:**
1. `SseEmitterRegistry` — register a `Gauge` for active connection count: `Metrics.gauge("sse.connections.active", emitters, map -> map.values().stream().mapToInt(Set::size).sum())`
2. Notification send path — add a `Counter` per channel type: `Metrics.counter("notifications.sent", "channel", channelType.name())`
3. Event processing — add a `Timer` for end-to-end delivery: from event received to SSE sent.
4. Cleanup scheduler — add counters for archived and deleted notifications per run.

---

### Task 3.3 — Verify Prometheus Scrape Targets

**File:** `infra/prometheus/prometheus.yml`

**Check:** All 7 services are listed as scrape targets. Currently:
- sc-gateway:8079 (management port, correct — gateway serves actuator on 8079)
- sc-identity:8081, sc-admission:8083, sc-documents:8084, sc-environment:8085, sc-notifications:8086, sc-telegram-bot:8087

**Verify:** Each target path is `/actuator/prometheus` and the service actually exposes that endpoint. If any service has a separate management port (like gateway at 8079), update the target accordingly.

**Verify alert rules** in `infra/prometheus/alerts.yml` — confirm the 7 existing alerts fire correctly by checking metric names match what services actually expose.

---

## Group 4: sc-libs Version Drift Audit

### Branch: `hotfix/libs-version-sync`

---

### Task 4.1 — Audit All Services for Consistent Library Versions

**Current state:** `server/version.properties` = `1.3.0`. All services use the BOM pattern via `libs.sc.bom`.

**Steps:**
1. Check `server/gradle/libs.versions.toml` (or `settings.gradle.kts`) for the version catalog definition of `sc-bom`.
2. Verify every service's `build.gradle` imports `libs.sc.bom` and doesn't override individual library versions.
3. Check `server/buildSrc/` and per-service `buildSrc/` copies are in sync (see memory: per-service buildSrc copies must stay in sync).
4. Run `./gradlew dependencies` for each service and grep for `sc-` artifacts — confirm all resolve to 1.3.0.
5. Fix any version mismatches.

**If Task 1.1 bumped version to 1.3.1:** Update all references to 1.3.1 and re-publish.

---

## Group 5: Docker Runtime Hardening

### Branch: `feature/docker-hardening`

---

### Task 5.1 — Verify All Health Checks Work Under Load

**Current state:** All services use `wget -q --spider http://localhost:PORT/actuator/health` for health checks.

**Steps:**
1. Start full stack: `docker compose -f docker-compose.yml -f docker-compose.services.yml up -d`
2. Wait for all services to be healthy: `docker compose ps` — all should show `(healthy)`.
3. Verify notifications-service starts correctly with its 45s start_period (longer than others due to RabbitMQ consumer init).
4. Kill identity-service and verify dependent services (environment, admission, documents, notifications) report unhealthy or degrade gracefully.
5. Restart identity-service and verify dependents recover.

### Task 5.2 — Add Gateway Circuit Breaker Dashboard Verification

**Current state:** Gateway has 5 circuit breaker instances: envCircuit, idCircuit, admCircuit, docCircuit, notifCircuit.

**Steps:**
1. Verify `infra/grafana/dashboards/circuit-breakers.json` panels reference these exact instance names.
2. Simulate a service failure (stop one service) and confirm the circuit breaker opens in Grafana.
3. Verify the Prometheus alert `CircuitBreakerOpen` fires.

---

## Group 6: Computer Vision Service Scaffold (Optional)

### Branch: `feature/computer-vision-scaffold`

**Current state:** `server/services/selection-committee-computer-vision/` exists but is empty/not started.

**Skip this group if the service is not yet needed.** If proceeding:

1. Scaffold Spring Boot 3.5.6 service with:
   - `build.gradle` importing sc-bom, sc-auth-starter, sc-observability-starter, sc-common
   - `application.yml` with actuator/prometheus/zipkin config matching other services
   - Dockerfile (multi-stage, matching existing service Dockerfiles)
   - Flyway migration baseline
2. Add to `infra/docker-compose.services.yml` with health check, depends_on postgres + identity-service.
3. Add to `infra/prometheus/prometheus.yml` scrape targets.
4. Add to gateway routes in gateway's `application.yml`.
5. Add to `service-ci.yml` and `service-docker.yml` change detection filters.

---

## Execution Order

```
1. Group 1 (Tasks 1.1-1.4) — Stabilize notification center
   └── 1.1 first (shared lib change, blocks others)
   └── 1.2, 1.3, 1.4 can be parallel after 1.1
2. Group 4 (Task 4.1) — Version drift audit (depends on Group 1 if version bumped)
3. Group 2 (Task 2.1) — Frontend proxy migration (independent)
4. Group 3 (Tasks 3.1-3.3) — Observability (independent)
5. Group 5 (Tasks 5.1-5.2) — Docker verification (after Groups 1-4 merged)
6. Group 6 — Optional, only if needed
```

## Completion Criteria

- [ ] No "response already committed" errors in SSE logs
- [ ] SseEmitterRegistry is thread-safe under concurrent access
- [ ] Notification settings persist even when popover closes quickly
- [ ] SSE 401 triggers re-authentication flow
- [ ] No Next.js deprecation warnings about middleware
- [ ] All services on consistent sc-libs version
- [ ] Notification-specific Grafana dashboard operational
- [ ] Custom metrics instrumented in notifications-service
- [ ] Prometheus scrapes all services successfully
- [ ] Full Docker stack starts and recovers from service failures
