# Comprehensive Stabilization & Versioning Prompt

Execute all tasks below in order. Each task includes exact files, current state, and expected outcome. Follow TDD where applicable. Create feature/hotfix branches per logical group.

---

## Prerequisites

### Current State Summary

**Parent repo** (`D:\develop\fice-selection-committee`):
- Branch: `hotfix/notification-center-fixes`
- Uncommitted: modified `infra/grafana/dashboards/notifications.json`, deleted old task files, untracked new task files
- `server/version.properties` = `1.3.1`

**Sub-repo commit status** (all committed unless noted):
- admission-service: CLEAN
- documents-service: CLEAN
- environment-service: CLEAN
- gateway: CLEAN
- identity-service: untracked `buildSrc/.kotlin/` (build cache, ignore)
- notifications-service: CLEAN
- telegram-bot-service: untracked `buildSrc/main/` (build cache, ignore)
- client/web: test artifacts in working tree (videos, reports)
- infra: CLEAN

---

## Group 0: Versioning Standardization — Remove SNAPSHOT Convention

### Branch: `chore/versioning-standardization` (parent repo + all service repos)

---

### Problem Statement

The current versioning uses a dual-mode pattern in `server/build.gradle.kts`:

```kotlin
val isRelease = System.getenv("GITHUB_REF_TYPE") == "tag"
val effectiveVersion = if (isRelease) versionFromFile else "$versionFromFile-SNAPSHOT"
```

This means:
- Local builds always produce `1.3.1-SNAPSHOT` artifacts
- CI tagged builds produce `1.3.1` artifacts
- 6 of 7 service `gradle/libs.versions.toml` reference `sc-libs = "1.3.1-SNAPSHOT"` to match local builds
- Gateway references `sc-libs = "1.3.1"` (inconsistent)
- This creates confusion: you must remember to use SNAPSHOT locally and release version in CI

**Target state**: All versions are plain `X.Y.Z`. No SNAPSHOT suffix anywhere. Local `publishToMavenLocal` publishes `1.3.1`, services consume `1.3.1`. CI publish also publishes `1.3.1`.

---

### Task 0.1 — Remove SNAPSHOT Logic from Root Build

**File:** `server/build.gradle.kts`

**Current (lines 1-17):**
```kotlin
val versionFromFile = file("version.properties")
    .readLines()
    .first { it.startsWith("version=") }
    .substringAfter("=")
    .trim()

val isRelease = System.getenv("GITHUB_REF_TYPE") == "tag"
val effectiveVersion = if (isRelease) versionFromFile else "$versionFromFile-SNAPSHOT"

allprojects {
    group = "edu.kpi.fice"
    version = effectiveVersion

    repositories {
        mavenCentral()
    }
}
```

**Replace with:**
```kotlin
val versionFromFile = file("version.properties")
    .readLines()
    .first { it.startsWith("version=") }
    .substringAfter("=")
    .trim()

allprojects {
    group = "edu.kpi.fice"
    version = versionFromFile

    repositories {
        mavenCentral()
    }
}
```

**Verification:** Run `cd server && ./gradlew properties | grep "^version:"` — should show `1.3.1` (no SNAPSHOT).

---

### Task 0.2 — Update All Service Version Catalogs to Plain Version

For each of the 6 services that have `sc-libs = "1.3.1-SNAPSHOT"`, change to `sc-libs = "1.3.1"`:

| Service | File | Current | Target |
|---|---|---|---|
| admission-service | `gradle/libs.versions.toml` line 70 | `sc-libs = "1.3.1-SNAPSHOT"` | `sc-libs = "1.3.1"` |
| documents-service | `gradle/libs.versions.toml` line 70 | `sc-libs = "1.3.1-SNAPSHOT"` | `sc-libs = "1.3.1"` |
| environment-service | `gradle/libs.versions.toml` line 70 | `sc-libs = "1.3.1-SNAPSHOT"` | `sc-libs = "1.3.1"` |
| identity-service | `gradle/libs.versions.toml` line 70 | `sc-libs = "1.3.1-SNAPSHOT"` | `sc-libs = "1.3.1"` |
| notifications-service | `gradle/libs.versions.toml` line 70 | `sc-libs = "1.3.1-SNAPSHOT"` | `sc-libs = "1.3.1"` |
| telegram-bot-service | `gradle/libs.versions.toml` line 70 | `sc-libs = "1.3.1-SNAPSHOT"` | `sc-libs = "1.3.1"` |

Gateway already has `sc-libs = "1.3.1"` — no change needed.

**For each service repo:**
1. Edit `gradle/libs.versions.toml`
2. Commit: `chore: standardize sc-libs version to 1.3.1 (remove SNAPSHOT)`

---

### Task 0.3 — Republish Libraries Without SNAPSHOT

After Task 0.1, republish from `server/`:

```bash
cd server
./gradlew clean publishToMavenLocal
```

**Verification:** Check `~/.m2/repository/edu/kpi/fice/sc-bom/1.3.1/sc-bom-1.3.1.pom` exists (NOT `1.3.1-SNAPSHOT`).

---

### Task 0.4 — Verify All Services Build Against Plain Version

For each of the 7 services, verify they resolve `1.3.1` (not SNAPSHOT):

```bash
cd server/services/selection-committee-{service}
./gradlew dependencies --configuration runtimeClasspath | grep "edu.kpi.fice"
```

All should resolve to `1.3.1` with no SNAPSHOT references.

---

### Task 0.5 — Commit Parent Repo Changes

In the parent repo (`D:\develop\fice-selection-committee`):

```bash
git add server/build.gradle.kts
git commit -m "chore: remove SNAPSHOT version convention, use plain X.Y.Z versioning"
```

---

## Group 1: BuildSrc Drift Sync

### Branch: `chore/buildsrc-sync` (in each affected service repo)

---

### Problem Statement

All 7 services have identical copies of buildSrc convention plugins that must stay in sync with the root `server/buildSrc/`. Currently all copies ARE identical (confirmed by audit), but this is fragile. This task establishes a verification baseline and ensures no drift exists.

---

### Task 1.1 — Verify and Sync Per-Service buildSrc

**Source of truth:** `server/buildSrc/src/main/kotlin/`

**Files to sync** (4 convention plugins):
- `sc.spring-boot-service.gradle.kts` (35 lines — uses version catalog lookups)
- `sc.java-conventions.gradle.kts` (48 lines)
- `sc.testing-conventions.gradle.kts` (73 lines)
- `sc.jacoco-conventions.gradle.kts` (96 lines)

**Note:** `sc.library-conventions.gradle.kts` exists only in root (used by libs, not services).

**Also sync:** `buildSrc/build.gradle.kts` (plugin dependency declarations).

**For each of the 7 services:**
1. Diff each file against the root version:
   ```bash
   diff server/buildSrc/src/main/kotlin/sc.spring-boot-service.gradle.kts \
        server/services/selection-committee-{service}/buildSrc/src/main/kotlin/sc.spring-boot-service.gradle.kts
   ```
2. If any difference found, copy from root to service.
3. Also diff `buildSrc/build.gradle.kts` and `buildSrc/settings.gradle.kts`.
4. Verify the service still builds: `./gradlew build` from the service directory.

**Services:**
- `selection-committee-admission-service`
- `selection-committee-documents-service`
- `selection-committee-environment-service`
- `selection-committee-gateway`
- `selection-committee-identity-service`
- `selection-committee-notifications-service`
- `selection-committee-telegram-bot-service`

**For each service where changes were made, commit within the service repo:**
```
chore: sync buildSrc with root convention plugins
```

**Verification:** `diff -r server/buildSrc/ server/services/selection-committee-{service}/buildSrc/` shows no differences for synced files (excluding `sc.library-conventions.gradle.kts` which should NOT exist in services).

---

## Group 2: Parent Repo Cleanup & Merge

### Branch: `hotfix/notification-center-fixes` (current, continue)

---

### Task 2.1 — Commit Pending Parent Repo Changes

**Current uncommitted state:**
- Modified: `infra/grafana/dashboards/notifications.json` (new panels added)
- Deleted: `tasks/remaining-work-prompt.md`, `tasks/remaining-work-v2-prompt.md`, `tasks/remaining-work-v3-prompt.md`
- Untracked: `tasks/remaining-work-v4-prompt.md`, `tasks/stabilization-and-improvements.md`, `tasks/comprehensive-stabilization-prompt.md`

**Steps:**
1. Stage and commit the Grafana dashboard update:
   ```bash
   git add infra/grafana/dashboards/notifications.json
   git commit -m "feat(grafana): update notifications dashboard with delivery and cleanup panels"
   ```

2. Stage and commit task file cleanup:
   ```bash
   git rm tasks/remaining-work-prompt.md tasks/remaining-work-v2-prompt.md tasks/remaining-work-v3-prompt.md
   git add tasks/remaining-work-v4-prompt.md tasks/stabilization-and-improvements.md tasks/comprehensive-stabilization-prompt.md
   git commit -m "chore: consolidate task prompts into comprehensive stabilization prompt"
   ```

---

### Task 2.2 — Merge hotfix Branch to Main

After all changes on `hotfix/notification-center-fixes` are committed:

```bash
git checkout main
git merge hotfix/notification-center-fixes
git push origin main
git branch -d hotfix/notification-center-fixes
git push origin --delete hotfix/notification-center-fixes
```

---

## Group 3: Client/Web Cleanup

### Branch: `chore/test-artifacts-cleanup` (in client/web repo)

---

### Task 3.1 — Add Test Artifacts to .gitignore

**File:** `client/web/.gitignore`

**Add these entries** (if not already present):
```gitignore
# Playwright test artifacts
playwright-report/
test-results/
```

**Then clean the working tree:**
```bash
cd client/web
git rm -r --cached playwright-report/ test-results/ 2>/dev/null
git add .gitignore
git commit -m "chore: gitignore Playwright test artifacts"
```

---

### Task 3.2 — Clean Untracked Test Artifacts

After Task 3.1, the untracked videos and test results will be ignored. Verify:
```bash
cd client/web
git status
```

Should show a clean working tree (no test-results or playwright-report entries).

---

## Group 4: Docker Full Stack Verification

### Branch: none (verification only, no code changes)

---

### Task 4.1 — Republish Libraries and Build All Services

This is a prerequisite for Docker verification. After Group 0 (versioning) and Group 1 (buildSrc sync):

```bash
cd server
./gradlew clean publishToMavenLocal
```

Then build each service (can be parallelized):
```bash
for svc in admission documents environment identity notifications telegram-bot; do
  (cd server/services/selection-committee-${svc}-service && ./gradlew build) &
done
(cd server/services/selection-committee-gateway && ./gradlew build) &
wait
```

All 7 services must build successfully.

---

### Task 4.2 — Full Stack Health Check

**Steps:**
1. Start the full stack from `infra/`:
   ```bash
   cd infra
   docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --build
   ```

2. Wait for startup (up to 3 minutes for all services including notifications-service 45s start period).

3. Verify all services are healthy:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.services.yml ps
   ```
   All services should show `(healthy)`.

4. Verify Prometheus scrapes all 7 targets:
   ```bash
   curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | {job: .labels.job, health: .health}'
   ```
   Expected: all 7 jobs show `"health": "up"`:
   - sc-gateway (gateway:8079)
   - sc-identity (identity-service:8081)
   - sc-admission (admission-service:8083)
   - sc-documents (documents-service:8084)
   - sc-environment (environment-service:8085)
   - sc-notifications (notifications-service:8086)
   - sc-telegram-bot (telegram-bot-service:8087)

---

### Task 4.3 — SSE Stream Verification

**Steps:**
1. Open `http://localhost:3000` and log in.
2. Open browser DevTools → Network → filter EventStream.
3. Verify SSE connection to `/api/v1/notifications/stream` is established.
4. Check notifications-service logs for errors:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.services.yml logs --since=60s notifications-service | grep -i "committed\|error\|exception"
   ```
   Expected: no "response already committed" errors.

5. Verify Grafana Notification Center dashboard at `http://localhost:3001`:
   - SSE Connections panel shows active connections
   - Delivery Status panel shows data when a notification is triggered

---

### Task 4.4 — Service Failure and Recovery Test

**Steps:**
1. Stop identity-service:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.services.yml stop identity-service
   ```

2. Wait 30s. Check dependent services logs for circuit breaker activation:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.services.yml logs --since=60s admission-service environment-service documents-service notifications-service | grep -i "circuit\|fallback\|timeout"
   ```

3. Check Prometheus alert fires:
   ```bash
   curl -s http://localhost:9090/api/v1/alerts | jq '.data.alerts[] | {alertname: .labels.alertname, state: .state}'
   ```
   Expected: `ServiceDown` alert in `firing` state for identity.

4. Verify Grafana circuit breaker dashboard at `http://localhost:3001` — `idCircuit` should show OPEN state.

5. Restart identity-service:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.services.yml start identity-service
   ```

6. Wait 45s. Verify:
   - Identity service returns to healthy
   - Circuit breakers close (check Grafana)
   - Prometheus alert resolves

---

### Task 4.5 — E2E Test Verification

Run the full Playwright E2E suite against the running Docker stack:

```bash
cd client/web
pnpm exec playwright test --reporter=list
```

All existing tests should pass:
- Smoke tests (app-health, login-flow, navigation)
- Regression tests (main-flow, auth, notifications, navigation, accessibility)

If any tests fail, document the failures. Do NOT modify tests to make them pass — diagnose the root cause.

---

## Group 5: Metrics Instrumentation Gaps (if not already done)

### Branch: `feature/notifications-metrics` (in notifications-service repo)

**Check first:** The notifications-service already has commit `f48a722 feat(metrics): add delivery counters, latency timer, cleanup metrics`. Verify the following metrics are actually instrumented by reading the source files:

---

### Task 5.1 — Verify Existing Metrics Instrumentation

**Read and verify these files exist and contain the expected instrumentation:**

1. **EmailNotificationChannel** — should have `notification.delivery` counter with `channel=EMAIL`, `status=success/failure` tags
2. **InAppNotificationChannel** — should have `notification.delivery` counter with `channel=IN_APP`, `status=success/failure` tags
3. **EventListenerService** — should have `notification.delivery.duration` timer per channel
4. **NotificationCleanupScheduler** — should have `notifications.cleanup.archived` and `notifications.cleanup.deleted` counters
5. **SseEmitterRegistry** — should have `sse.connections.active` gauge

**If any are missing**, implement them following the patterns described in `tasks/remaining-work-v4-prompt.md` Group 1.

---

### Task 5.2 — Verify Grafana Dashboard References Correct Metric Names

**File:** `infra/grafana/dashboards/notifications.json`

Cross-reference the metric names in the dashboard panels against the actual metric names in the Java source code. Micrometer converts metric names: Java `notification.delivery` becomes Prometheus `notification_delivery_total`.

Verify these panels query the correct metrics:
- SSE Connections → `sse_connections_active`
- Delivery Status → `notification_delivery_total{channel=..., status=...}`
- Delivery Latency → `notification_delivery_duration_seconds_bucket`
- Cleanup Archived → `notifications_cleanup_archived_total`
- Cleanup Deleted → `notifications_cleanup_deleted_total`
- Notifications Sent → `notification_sent_total{channel=...}`

Fix any mismatches between dashboard queries and actual metric names.

---

## Execution Order

```
0. Group 0 (Tasks 0.1-0.5) — Remove SNAPSHOT versioning, republish libs
   └── 0.1 first (root build.gradle.kts)
   └── 0.3 (republish to mavenLocal with plain version)
   └── 0.2 (update all service catalogs) — can parallel after 0.3
   └── 0.4 (verify all services build)
   └── 0.5 (commit parent repo)

1. Group 1 (Task 1.1) — BuildSrc sync (can run in parallel with Group 0 service updates)

2. Group 2 (Tasks 2.1-2.2) — Parent repo cleanup and merge to main

3. Group 3 (Tasks 3.1-3.2) — Client/web test artifact cleanup

4. Group 4 (Tasks 4.1-4.5) — Full Docker stack verification
   └── 4.1 (rebuild all) — blocks all verification tasks
   └── 4.2, 4.3, 4.4, 4.5 — sequential (require running stack)

5. Group 5 (Tasks 5.1-5.2) — Metrics verification (can run during Group 4)
```

---

## Completion Criteria

**Versioning:**
- [ ] `server/build.gradle.kts` has no SNAPSHOT logic — version is always `version.properties` value
- [ ] All 7 service `gradle/libs.versions.toml` reference `sc-libs = "1.3.1"` (no SNAPSHOT)
- [ ] `publishToMavenLocal` produces `1.3.1` artifacts (verify in `~/.m2/`)
- [ ] All 7 services build and resolve `edu.kpi.fice` dependencies at `1.3.1`

**BuildSrc:**
- [ ] All 7 service `buildSrc/` directories are identical to root `server/buildSrc/` (excluding `sc.library-conventions.gradle.kts`)
- [ ] All services build successfully after sync

**Git State:**
- [ ] Parent repo merged to `main`, `hotfix/notification-center-fixes` branch deleted
- [ ] All sub-repos have clean working trees (no uncommitted changes besides build caches)
- [ ] Client/web `.gitignore` covers test artifacts

**Docker Runtime:**
- [ ] All 7 services + infrastructure start and report healthy
- [ ] Prometheus scrapes all 7 service targets successfully
- [ ] SSE stream connects without "response already committed" errors
- [ ] Circuit breakers open on service failure and close on recovery
- [ ] Prometheus `ServiceDown` alert fires and resolves correctly

**E2E:**
- [ ] All Playwright tests pass against running Docker stack

**Metrics:**
- [ ] Delivery counters, latency timer, cleanup counters, SSE gauge instrumented
- [ ] Grafana notifications dashboard panels reference correct Prometheus metric names

---

## Version Bump Procedure (Reference for Future)

When bumping the sc-libs version (e.g., `1.3.1` → `1.4.0`):

1. Update `server/version.properties` to `version=1.4.0`
2. Run `cd server && ./gradlew clean publishToMavenLocal`
3. Update `sc-libs = "1.4.0"` in all 7 service `gradle/libs.versions.toml` files
4. Verify each service builds: `./gradlew build`
5. Commit in each service repo: `chore: bump sc-libs to 1.4.0`
6. Commit in parent repo: `chore: bump library version to 1.4.0`
7. Tag and push: `git tag v1.4.0 && git push origin v1.4.0` (triggers CI publish)
