# Remaining Work — Comprehensive Implementation Prompt

Execute all tasks below in the order presented. Each task includes the exact files, current state, and expected outcome. Follow TDD where applicable.

---

## Prerequisite: Commit Uncommitted Sub-Repo Changes

The previous session made code changes in sub-repos (gitignored by the parent). These must be committed within each sub-repo before proceeding.

### Step 0.1 — Commit notifications-service changes

**Repo:** `server/services/selection-committee-notifications-service/` (its own git repo)

**Changed files:**
- `gradle/libs.versions.toml` — sc-libs bumped from 1.3.0 → 1.3.1
- `src/main/java/edu/kpi/fice/notifications_service/sse/SseEmitterRegistry.java` — TOCTOU race fix: `remove()` uses `computeIfPresent()`, `sendToUser()` collects failed emitters then atomic cleanup
- `src/main/resources/application.yml` — may have prior-session changes (check diff)
- `src/test/java/edu/kpi/fice/notifications_service/sse/SseEmitterRegistryTest.java` — added concurrency test

**Commit message:** `fix: thread-safe SseEmitterRegistry, bump sc-libs to 1.3.1`

### Step 0.2 — Commit client/web changes

**Repo:** `client/web/` (its own git repo)

**Changed files (from this session — verify each via `git diff`):**
- `src/middleware.ts` — **deleted** (renamed to proxy.ts)
- `src/proxy.ts` — **new file** (renamed from middleware.ts, `export function proxy()`)
- `src/components/notifications/notification-settings.tsx` — unmount cleanup uses `fetch` + `keepalive: true` instead of React Query mutate
- `src/hooks/use-notification-stream.ts` — 401 handling: clears token, shows toast, redirects to `/login`
- `tests/unit/components/notification-settings-debounce.test.tsx` — added unmount flush test
- `tests/unit/hooks/use-notification-stream-auth.test.ts` — **new file** (401 handling test)

**Also changed from prior sessions (already in working tree — verify diffs before committing):**
- `next.config.ts`, `package.json`, `playwright.config.ts`
- `src/app/(auth)/auth/verify/page.tsx`, `src/components/layout/sidebar-nav.tsx`
- `src/components/onboarding/progress.tsx`, `src/components/ui/sonner.tsx`
- `src/hooks/use-is-mobile.ts`, `src/lib/api/client.ts`, `src/lib/constants.ts`
- `src/providers/auth-provider.tsx`
- Various E2E and unit test files

**Commit in two parts:**
1. First commit the prior-session notification center work: `feat: notification center — SSE streaming, preferences, sound, cross-tab sync, E2E tests`
2. Then commit the stabilization fixes: `fix: proxy migration, SSE 401 handling, settings unmount flush`

### Step 0.3 — Commit remaining service version bumps

Each of these services has `gradle/libs.versions.toml` changed (sc-libs 1.3.0 → 1.3.1). Commit within each repo:

- `server/services/selection-committee-admission-service/` — `chore: bump sc-libs to 1.3.1`
- `server/services/selection-committee-documents-service/` — also has prior-session changes (check diff before committing)
- `server/services/selection-committee-environment-service/` — also has prior-session changes (maintenance module, V5 migration, feature flag controller)
- `server/services/selection-committee-gateway/` — also has prior-session changes (application.yml route updates)
- `server/services/selection-committee-identity-service/` — also has prior-session changes (notification wiring)
- `server/services/selection-committee-telegram-bot-service/` — also has prior-session changes (webhook controller, bot user service, contract test, JaCoCo conventions)

**For each service:** run `git diff` first to understand the full scope of changes. Group related changes into logical commits. Do NOT blindly commit everything as a single commit — separate feature work from version bumps.

---

## Group 1: Metrics Instrumentation Gaps

### Branch: `feature/notifications-metrics` (in notifications-service repo)

---

### Task 1.1 — Add Delivery Success/Failure Counters to Email Channel (HIGH)

**Problem:** `EmailNotificationChannel.send()` catches `MailException` but does not record success or failure metrics. The `notification.sent` counter in `EventListenerService` increments before delivery is attempted, so it doesn't reflect actual delivery status.

**Current state:**
- `server/services/selection-committee-notifications-service/src/main/java/edu/kpi/fice/notifications_service/service/adapter/email/EmailNotificationChannel.java` — catches `MailException` at line 34, logs error, no metric
- `notification.sent` counter in `EventListenerService.java` increments at lines 64-68 (EMAIL), 82-86 (TELEGRAM), 109-113 (IN_APP) — BEFORE the channel `send()` completes

**Fix:**
1. Inject `MeterRegistry` into `EmailNotificationChannel`.
2. After `mailSender.send(mailMessage)` succeeds (line 32), increment:
   ```java
   Counter.builder("notification.delivery")
       .tag("channel", "EMAIL")
       .tag("status", "success")
       .register(meterRegistry)
       .increment();
   ```
3. In the `catch (MailException)` block, increment:
   ```java
   Counter.builder("notification.delivery")
       .tag("channel", "EMAIL")
       .tag("status", "failure")
       .register(meterRegistry)
       .increment();
   ```

**Tests:** Add unit test in `src/test/java/edu/kpi/fice/notifications_service/service/adapter/email/EmailNotificationChannelTest.java`:
- Test: successful send increments success counter
- Test: MailException increments failure counter

---

### Task 1.2 — Add Delivery Counter to InApp Channel (MEDIUM)

**Problem:** `InAppNotificationChannel.send()` persists the notification and sends via SSE, but has no metrics for success/failure.

**File:** `server/services/selection-committee-notifications-service/src/main/java/edu/kpi/fice/notifications_service/service/adapter/inapp/InAppNotificationChannel.java`

**Fix:**
1. Inject `MeterRegistry`.
2. After `inAppNotificationService.save(entity)` + `sseRegistry.sendToUser()` at line 36, increment success counter:
   ```java
   Counter.builder("notification.delivery")
       .tag("channel", "IN_APP")
       .tag("status", "success")
       .register(meterRegistry)
       .increment();
   ```
3. Wrap the save+send in a try/catch and increment failure counter on exception.

**Tests:** Unit test verifying counter increments on success and failure paths.

---

### Task 1.3 — Add Delivery Latency Timer (MEDIUM)

**Problem:** No metric tracks end-to-end notification delivery latency (time from RabbitMQ event received to SSE sent or email dispatched).

**File:** `server/services/selection-committee-notifications-service/src/main/java/edu/kpi/fice/notifications_service/service/events/EventListenerService.java`

**Fix:**
1. At the start of `handleEvent()` (line 39), record `Instant start = Instant.now();`
2. After each channel's `notificationService.send()` call, record a timer sample:
   ```java
   Timer.builder("notification.delivery.duration")
       .tag("channel", channelType.name())
       .tag("template", event.templateType().name())
       .register(meterRegistry)
       .record(Duration.between(start, Instant.now()));
   ```
3. This measures from event receipt to send completion per channel.

**Tests:** Unit test verifying timer is recorded after send.

---

### Task 1.4 — Add Cleanup Scheduler Counters (LOW)

**Problem:** `NotificationCleanupScheduler` archives and deletes notifications on a cron schedule but has no metrics.

**File:** `server/services/selection-committee-notifications-service/src/main/java/edu/kpi/fice/notifications_service/scheduler/NotificationCleanupScheduler.java`

**Current state (lines 28-41):**
```java
public void archiveOldNotifications() {
    int archived = notificationRepository.archiveReadNotificationsOlderThan(archiveBefore);
    log.info("Archived {} read notifications older than {} days", archived, archiveAfterDays);
}
public void deleteArchivedNotifications() {
    int deleted = notificationRepository.deleteArchivedNotificationsOlderThan(deleteBefore);
    log.info("Deleted {} archived notifications older than {} days", deleted, deleteAfterDays);
}
```

**Fix:**
1. Inject `MeterRegistry`.
2. After `archiveOldNotifications()` completes, increment:
   ```java
   meterRegistry.counter("notifications.cleanup.archived").increment(archived);
   ```
3. After `deleteArchivedNotifications()` completes, increment:
   ```java
   meterRegistry.counter("notifications.cleanup.deleted").increment(deleted);
   ```

**Tests:** Unit test verifying counters increment with the correct values.

---

### Task 1.5 — Update Grafana Dashboard with New Metrics (LOW)

**File:** `infra/grafana/dashboards/notifications.json` (in parent repo)

**Add panels:**
1. **Email Delivery Status** — stacked bar chart showing `notification.delivery` counter with `channel="EMAIL"` split by `status` tag (success/failure)
2. **Delivery Latency p95** — timeseries showing `notification.delivery.duration` histogram percentile, split by channel
3. **Cleanup Scheduler** — stat panels showing `notifications.cleanup.archived` and `notifications.cleanup.deleted` counters

---

## Group 2: BuildSrc Drift Sync

### Branch: `chore/buildsrc-sync` (in each affected service repo)

---

### Task 2.1 — Sync Per-Service buildSrc with Root

**Problem:** 6 of 7 service `buildSrc/src/main/kotlin/sc.spring-boot-service.gradle.kts` files use hard-coded Maven coordinates instead of version catalog lookups. The gateway's copy matches root. This means catalog version changes won't propagate to 6 services.

**Root version** (`server/buildSrc/src/main/kotlin/sc.spring-boot-service.gradle.kts`):
```kotlin
implementation(catalog.findLibrary("spring-boot-starter-web").get())
implementation(catalog.findLibrary("spring-boot-starter-validation").get())
implementation(catalog.findLibrary("spring-boot-starter-log4j2").get())
implementation(catalog.findLibrary("spring-boot-starter-actuator").get())
// ...
implementation(catalog.findLibrary("micrometer-tracing-brave").get())
implementation(catalog.findLibrary("zipkin-reporter-brave").get())
```

**Drifted service version** (6 services):
```kotlin
implementation("org.springframework.boot:spring-boot-starter-web")
implementation("org.springframework.boot:spring-boot-starter-validation")
implementation("org.springframework.boot:spring-boot-starter-log4j2")
implementation("org.springframework.boot:spring-boot-starter-actuator")
// ...
implementation("io.micrometer:micrometer-tracing-bridge-brave")
implementation("io.zipkin.reporter2:zipkin-reporter-brave")
```

**Fix:**
1. Copy `server/buildSrc/src/main/kotlin/sc.spring-boot-service.gradle.kts` to each of the 6 drifted service `buildSrc/` directories:
   - `selection-committee-admission-service/buildSrc/src/main/kotlin/`
   - `selection-committee-documents-service/buildSrc/src/main/kotlin/`
   - `selection-committee-environment-service/buildSrc/src/main/kotlin/`
   - `selection-committee-identity-service/buildSrc/src/main/kotlin/`
   - `selection-committee-notifications-service/buildSrc/src/main/kotlin/`
   - `selection-committee-telegram-bot-service/buildSrc/src/main/kotlin/`
2. Also check `sc.java-conventions.gradle.kts` and `sc.testing-conventions.gradle.kts` for minor whitespace/encoding differences and normalize them.
3. Verify each service still builds after the sync: `./gradlew build` from each service directory.

**Verification:** All 7 services build successfully. `diff` between root and each service buildSrc shows no differences for the synced files.

---

## Group 3: Prometheus Gateway Scrape Fix

### Branch: `hotfix/prometheus-gateway-port` (in infra repo)

---

### Task 3.1 — Fix Gateway Prometheus Scrape Target

**Problem:** `infra/prometheus/prometheus.yml` scrapes the gateway at port `8080` (the application port), but the gateway's actuator is on management port `8079`.

**Current state:**
- `infra/prometheus/prometheus.yml` line 12: `targets: ['gateway:8080']`
- `server/services/selection-committee-gateway/src/main/resources/application.yml` line 210: `management.server.port: 8079`

**Fix already applied in working directory** — `prometheus.yml` was edited to `gateway:8079` in the previous session, but it's in the infra repo (gitignored by parent).

**Action:** Commit the change within the infra repo:
```bash
cd infra && git add prometheus/prometheus.yml && git commit -m "fix: correct gateway Prometheus scrape to management port 8079"
```

**Verification:** After stack restart, verify `curl http://localhost:9090/api/v1/targets` shows the gateway target as UP.

---

## Group 4: Docker Runtime Hardening — Manual Verification

### Branch: none (verification only, no code changes)

---

### Task 4.1 — Full Stack Health Check Verification

**Steps:**
1. From `infra/` directory, start the full stack:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --build
   ```
2. Wait 2 minutes for all services to initialize.
3. Verify all services are healthy:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.services.yml ps
   ```
   All services should show `(healthy)`. If any show `(health: starting)` after 2 minutes, investigate logs.
4. Verify Prometheus can scrape all targets:
   ```bash
   curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | {job: .labels.job, health: .health}'
   ```
   All 7 targets should show `"health": "up"`.

### Task 4.2 — Service Failure and Recovery Test

**Steps:**
1. Kill the identity-service:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.services.yml stop identity-service
   ```
2. Verify dependent services report errors in their logs (Feign calls fail, circuit breakers open):
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.services.yml logs --since=30s admission-service environment-service documents-service notifications-service
   ```
3. Check Grafana circuit breaker dashboard at `http://localhost:3001` — verify circuit breakers show OPEN state.
4. Check Prometheus alerts at `http://localhost:9090/alerts` — verify `ServiceDown` fires for identity.
5. Restart identity-service:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.services.yml start identity-service
   ```
6. Wait 30 seconds, then verify:
   - Identity service returns to healthy
   - Circuit breakers close (CLOSED state in Grafana)
   - Dependent services resume normal operation

### Task 4.3 — SSE Stream Verification

**Steps:**
1. Log in to the frontend at `http://localhost:3000`.
2. Open browser DevTools → Network → filter by EventStream.
3. Verify the SSE connection to `/api/v1/notifications/stream` is established.
4. Verify no "response already committed" errors in notifications-service logs:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.services.yml logs --since=60s notifications-service | grep -i "committed\|error"
   ```
5. Verify the Grafana Notification Center dashboard at `http://localhost:3001` shows active SSE connections.

---

## Group 5: Computer Vision Service Scaffold (Optional)

### Branch: `feature/computer-vision-scaffold`

**Skip this group if the service is not yet needed.** The directory `server/services/selection-committee-computer-vision/` exists but is empty/not started.

If proceeding:

1. Scaffold Spring Boot 3.5.6 service with:
   - `build.gradle` importing sc-bom (1.3.1), sc-auth-starter, sc-observability-starter, sc-common
   - `settings.gradle` (Groovy format, standalone — NOT referencing root settings)
   - `application.yml` with actuator/prometheus/zipkin config matching other services
   - `Dockerfile` (multi-stage, matching existing service Dockerfiles in `server/services/`)
   - Flyway migration baseline: `V1__baseline.sql`
   - `buildSrc/` copied from root `server/buildSrc/`
   - `gradle/libs.versions.toml` matching other services with sc-libs = "1.3.1"
2. Add to `infra/docker-compose.services.yml` with health check, `depends_on` postgres + identity-service.
3. Add to `infra/prometheus/prometheus.yml` scrape targets (use the service's management port or default app port).
4. Add to gateway routes in `server/services/selection-committee-gateway/src/main/resources/application.yml`.
5. Add to `.github/workflows/service-ci.yml` and `.github/workflows/service-docker.yml` change detection filters.

---

## Execution Order

```
0. Commit uncommitted sub-repo changes (Steps 0.1-0.3) — MUST do first
1. Group 1 (Tasks 1.1-1.5) — Metrics instrumentation in notifications-service
2. Group 2 (Task 2.1) — BuildSrc sync across 6 services
3. Group 3 (Task 3.1) — Prometheus gateway port fix (infra repo commit)
4. Group 4 (Tasks 4.1-4.3) — Manual Docker verification (after all code changes)
5. Group 5 — Optional, only if needed
```

---

## Completion Criteria

**From this prompt:**
- [ ] All sub-repo changes committed in their respective repos
- [ ] Email, InApp delivery success/failure counters instrumented
- [ ] Delivery latency timer instrumented per channel
- [ ] Cleanup scheduler counters instrumented
- [ ] Grafana notifications dashboard updated with new metric panels
- [ ] All 7 service buildSrc directories in sync with root
- [ ] Prometheus gateway scrape target committed in infra repo
- [ ] Full Docker stack starts with all services healthy
- [ ] SSE stream works without "response already committed" errors
- [ ] Circuit breakers open/close correctly on service failure/recovery
- [ ] Prometheus scrapes all 7 services successfully

**Carried forward from prior sessions (already complete — verify only):**
- [x] AuthFilter skips public paths via shouldNotFilter()
- [x] SseEmitterRegistry is thread-safe (computeIfPresent)
- [x] Notification settings persist on quick popover close (fetch keepalive)
- [x] SSE 401 triggers token clear + toast + redirect
- [x] middleware.ts renamed to proxy.ts (Next.js 16)
- [x] All services on sc-libs 1.3.1
- [x] Notification Center Grafana dashboard exists
