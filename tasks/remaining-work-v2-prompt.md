# Remaining Work — Implementation Prompt (v3)

## Context

v1.3.0 shipped (Notification Center). Two audit rounds + remaining-work-v1 completed.
This prompt covers **separately tracked items** that were deferred from v1.3.0 scope:

1. **Sonner toast theme bug** — `next-themes` import in Zustand-based theme system
2. **Frontend proxy migration** — eliminate direct cross-origin API calls via Next.js rewrites
3. **Telegram Bot Phases 2-4** — separate repo, UX/security/reliability improvements
4. **Backend E2E test maintenance** — separate repo, kept in sync with service changes
5. **Visual regression testing** — infrastructure for catching CSS/theme regressions
6. **Theme toggle E2E test** — blocked on visual regression infra

---

## Items Verified as DONE (do NOT reimplement)

Everything from `remaining-work-prompt.md` (v1) is complete:
- Security hardening (CORS, actuator, AuthFilter delegation, magic link referrer, CSP headers)
- UX fixes (sidebar active state, useIsMobile hydration, refreshUser error handling)
- Accessibility (ARIA, focus management, skip-to-content, prefers-reduced-motion)
- Infrastructure (Trivy scanning, Grafana alerts, health check tuning)
- Test fixes (role-guard.test.tsx, AuthFilterTest for admission/documents)
- E2E specs (error handling, admin/operator dashboards, DataTable)
- Cleanup (task files pruned, .gitkeep removed)

---

## Phase 1: Sonner Toast Theme Bug (Quick Fix)

### 1.1 Fix `next-themes` Import in Sonner Component

**Problem:** `client/web/src/components/ui/sonner.tsx:10` imports `useTheme` from `next-themes`, but the app uses a custom Zustand-based theme system (`src/stores/theme-store.ts` + `src/providers/theme-provider.tsx`). The `next-themes` package is installed but has no `<ThemeProvider>` wrapping the app, so `useTheme()` always returns `{ theme: "system" }`. This means toasts never match the actual dark/light theme.

**Current code:**
```typescript
import { useTheme } from "next-themes";

const Toaster = ({ ...props }: ToasterProps) => {
  const { theme = "system" } = useTheme();
  // theme is ALWAYS "system" — bug
```

**Fix:** Replace `next-themes` import with the Zustand theme store:
```typescript
import { useThemeStore } from "@/stores/theme-store";

const Toaster = ({ ...props }: ToasterProps) => {
  const { theme } = useThemeStore();
  // theme is "light" or "dark" — correct
```

**After fix, evaluate:** Can `next-themes` be removed from `package.json`? Search for any other imports first. If sonner.tsx was the only consumer, uninstall it:
```bash
cd client/web && pnpm remove next-themes
```

**File:** `client/web/src/components/ui/sonner.tsx`

**Verification:**
- `npx tsc --noEmit` — no type errors
- Toggle to dark mode → trigger a toast → toast should use dark theme colors
- Toggle to light mode → trigger a toast → toast should use light theme colors

---

## Phase 2: Frontend Proxy Migration

### 2.1 Problem Statement

The frontend at `http://localhost:3000` makes direct cross-origin API calls to the gateway at `http://localhost:8080` via Axios. This requires:
- `withCredentials: true` on every request (CORS cookie handling)
- CORS configuration on the gateway allowing the frontend origin
- `Access-Control-Allow-Credentials: true` header from gateway
- Browser preflight requests on every non-simple request

This architecture has several drawbacks:
- CORS complexity — every new header or method needs gateway CORS config update
- Cookie scope limitations — cookies set by the gateway have a different origin
- Preflight overhead — OPTIONS request before every mutating API call
- Environment coupling — `NEXT_PUBLIC_API_BASE` must change per environment

### 2.2 Target Architecture

Use Next.js `rewrites` in `next.config.ts` to proxy API calls through the frontend server:

```
Browser → http://localhost:3000/api/** → Next.js rewrite → http://gateway:8080/api/**
```

Benefits:
- Same-origin requests — no CORS, no preflight, simpler cookie handling
- Gateway only needs to accept requests from the Next.js server (internal network)
- One URL to configure (gateway internal address), not exposed to browser
- Eliminates `NEXT_PUBLIC_API_BASE` (API base becomes `/api`)

### 2.3 Implementation Plan

**Step 1: Add rewrites to `next.config.ts`**

```typescript
async rewrites() {
  return [
    {
      source: "/api/:path*",
      destination: `${process.env.API_PROXY_TARGET ?? "http://localhost:8080"}/api/:path*`,
    },
  ];
},
```

Note: `API_PROXY_TARGET` is a server-side env var (no `NEXT_PUBLIC_` prefix) — never exposed to browser.

**Step 2: Update Axios client base URL**

In `client/web/src/lib/api/client.ts`:
- Change `baseURL: API_BASE` (which reads `NEXT_PUBLIC_API_BASE`) to empty string or `/`
- Remove or deprecate `NEXT_PUBLIC_API_BASE` from `src/lib/constants.ts`
- Since all API paths already start with `/api/`, Axios will call same-origin

**Step 3: Simplify CORS on gateway**

In `server/services/selection-committee-gateway/`:
- CORS is configured in `SecurityConfig.java` — can be simplified to only allow internal origins or disabled entirely if all traffic routes through Next.js proxy
- Keep CORS for Swagger UI if needed, but restrict to development profile

**Step 4: Update environment files**
- Remove `NEXT_PUBLIC_API_BASE` from `client/web/.env.local`
- Add `API_PROXY_TARGET=http://localhost:8080` to `client/web/.env.local`
- Update `infra/.env.example` with new var
- Update Docker compose for web service if it exists

**Step 5: Update middleware to skip `/api` routes**

Current middleware already skips `/api` routes:
```typescript
if (pathname.startsWith("/_next") || pathname.startsWith("/api") || hasFileExtension) {
  return NextResponse.next();
}
```
This is correct — rewritten `/api` requests should pass through to Next.js rewrite handler.

**Step 6: Update E2E test configuration**

E2E tests use `E2E_API_BASE_URL` (defaults to `http://localhost:8080`) for:
- Direct API calls in helpers (mailpit.ts, auth.fixture.ts, notifications.ts)
- Route interception patterns in specs

These helpers make server-side HTTP calls (not browser requests), so they should continue targeting the gateway directly. No change needed for test helpers.

However, Playwright route interception in specs (`page.route()`) should be updated:
- Old: `page.route('http://localhost:8080/api/**', ...)`
- New: `page.route('**/api/**', ...)` (same-origin pattern)

### 2.4 Risks & Considerations

| Risk | Mitigation |
|---|---|
| SSE (Server-Sent Events) for notifications may not proxy correctly | Test SSE through rewrite; Next.js rewrites should handle streaming — verify with notification bell |
| File uploads to MinIO presigned URLs bypass gateway | Presigned URLs go directly to MinIO, not through `/api/` — unaffected |
| WebSocket connections (if any) | Currently none — SSE is used for real-time |
| Next.js rewrite timeout | Default is 30s — sufficient for all current endpoints; increase if needed |
| Cookie domain changes | Same-origin means cookies "just work" — simplifies session cookie handling |
| Breaking change for existing deployments | Requires coordinated deploy: frontend + gateway config |

### 2.5 Migration Verification Checklist

- [ ] `pnpm dev` starts without errors
- [ ] Login flow works (magic link → verify → dashboard)
- [ ] API calls in Network tab show same-origin (no CORS preflight)
- [ ] Token refresh works (401 → retry)
- [ ] Notification SSE connection works (bell shows count)
- [ ] File upload to documents works
- [ ] Dark mode toast displays correctly
- [ ] All E2E smoke tests pass
- [ ] No `NEXT_PUBLIC_API_BASE` references remain (except migration notes)

---

## Phase 3: Telegram Bot Phases 2-4 (Separate Repo)

> **Repository:** `server/services/selection-committee-telegram-bot-service/`
> **Phase 1 is COMPLETE** — i18n, database, inline keyboards, callbacks, 21 tests passing.

### 3.1 Phase 2: Core UX Improvements

#### 3.1.1 Fix FlagsCallbackHandler Toggle Button Label (Quick Fix)

**Problem:** `FlagsCallbackHandler.handleView()` has a broken `.replace()` call that always shows "Toggle" instead of localized text.

**Fix:** Add i18n key `flags.view.btn.toggle` with EN="Toggle" / UK="Перемкнути" and use it directly instead of the `.replace()` chain.

#### 3.1.2 NotificationsCallbackHandler Enhancement

**Current:** Shows a static toggle button regardless of state.
**Target:** Show current subscription status with appropriate button:
- Subscribed: "🔔 Subscribed ✓" + unsubscribe button
- Not subscribed: "🔕 Not subscribed" + subscribe button
- Requires reading `BotUser.subscribed` from `BotUserService`

#### 3.1.3 Command Menu Registration

Create `CommandMenuRegistrar.java` with `@EventListener(ApplicationReadyEvent.class)`:
- Call Telegram's `setMyCommands` API on startup
- Register: `/start`, `/flags`, `/flag`, `/search`, `/toggle`, `/settings`, `/help`
- i18n: register both EN and UK command descriptions

#### 3.1.4 RBAC Implementation

Create `security/BotAuthorizationService.java`:
- `canToggleFlags(userId)` — requires `BotUser.role` = `operator` or `admin`
- Wire into `FlagsCallbackHandler.handleTogglePrompt()` and `ToggleFlagHandler`
- Return `flags.toggle.unauthorized` message if denied
- i18n keys already exist in message files

#### 3.1.5 Rate Limiting

Create `security/RateLimitService.java`:
- Per-chat: 5 req/sec (config at `telegram.rate-limit.max-requests-per-second`)
- Per-user write: 1 toggle/minute
- `ConcurrentHashMap<Long, Deque<Long>>` for sliding window
- Wire into `TelegramWebhookController` — silently drop excess (return 200)

#### 3.1.6 Deep Link Support

Handle `/start flag_<key>` deep links:
- In `BotCommandDispatcher`, parse payload from `/start` command args
- If payload matches `flag_*`, dispatch to `FlagsCallbackHandler.handleView()`

#### 3.1.7 Navigation State Manager

Create `service/NavigationStateManager.java`:
- Per-chat breadcrumb stack (max 10) via `ConcurrentHashMap<Long, Deque<String>>`
- `push(chatId, callbackData)`, `pop(chatId)`, `reset(chatId)`
- Wire `nav:back` callback to `pop()` instead of hardcoded targets
- `nav:home` calls `reset()` and renders main menu

### 3.2 Phase 3: Operations & Observability

#### 3.2.1 Micrometer Metrics

Add counters via `MeterRegistry` (already available via `sc-observability-starter`):
- `bot.commands.total` (tags: command, status)
- `bot.callbacks.total` (tags: action)
- `bot.users.active` gauge (from DB count)

#### 3.2.2 Audit Logging

Structured SLF4J logging for write operations:
- Flag toggle: userId, flagKey, oldState → newState, timestamp
- Subscription change: userId, action (subscribe/unsubscribe)
- Use MDC for structured fields

#### 3.2.3 Circuit Breaker for Feign Client

`ResilientEnvironmentServiceClient.java` already exists with basic error handling. Enhance:
- Add Resilience4j circuit breaker (config already in `application.yml`)
- Fallback: cached last-known flag list for reads, error message for writes
- Check if `resilience4j-spring-boot3` is already a dependency

### 3.3 Phase 4: Polish & Reliability

#### 3.3.1 Migrate NotificationChatRegistry to DB

Replace file-based `data/subscriptions.json` with DB-backed `BotUser.subscribed`:
- `FlagChangeNotificationListener` already queries `botUserService.getSubscribedUsers()`
- Migrate `SubscribeHandler` and `UnsubscribeHandler` to use `BotUserService.toggleSubscription()`
- Remove `NotificationChatRegistry` if fully replaced (or keep as group chat fallback)

#### 3.3.2 Health Handler Improvements

Current `HealthCallbackHandler` has hardcoded service URLs:
- Make configurable via `application.yml` properties
- Add 2-second timeout per health check
- Show response time per service
- Add "last checked" timestamp

#### 3.3.3 Custom Telegram Health Indicator

`health/TelegramHealthIndicator.java` already exists. Verify:
- Calls `getMe` API to verify token validity
- Returns UP/DOWN for Spring Actuator

#### 3.3.4 Error Recovery in TelegramApiClient

- Retry logic for 429 (Telegram rate limit): exponential backoff, 3 attempts
- Specific error logging for 403 (forbidden), network timeout
- Message length safety: truncate at 4096 chars or split into multiple messages

### 3.4 Execution Order

```
3.1.1 → 3.1.2 → 3.1.3 → 3.1.4 → 3.1.5 (security first)
    → 3.2.1 → 3.2.2 → 3.2.3
    → 3.3.1 → 3.1.6 → 3.1.7
    → 3.3.2 → 3.3.3 → 3.3.4
```

### 3.5 Build & Test

```bash
# From server/ (only if shared libs changed):
./gradlew publishToMavenLocal

# From server/services/selection-committee-telegram-bot-service/:
./gradlew build          # Full: spotless + compile + test + coverage
./gradlew test           # Unit tests only
./gradlew spotlessApply  # Auto-format
```

---

## Phase 4: Backend E2E Test Maintenance (Separate Repo)

> **Repository:** `server/services/selection-committee-e2e-tests/`
> **Moved from monorepo** in PR #29 (commit 2415707, 2026-03-30).
> **Ignored in `.gitignore`** — managed as a separate checkout.

### 4.1 Current State

8 E2E test flows exist:
- `AuthFlowE2ETest` — magic link login, token refresh, logout
- `FullAdmissionFlowE2ETest` — complete application lifecycle
- `DocumentRejectionFlowE2ETest` — document upload and rejection
- `ContractOrderE2ETest` — contract management workflow
- `EnvironmentServiceE2ETest` — feature flag CRUD
- `NegativeFlowE2ETest` — error handling and edge cases
- `NotificationDeliveryE2ETest` — notification lifecycle
- `RbacMatrixE2ETest` — role-based access control verification

**Stack:** JUnit 5, REST Assured, Testcontainers, Awaitility, Allure reporting.

### 4.2 Maintenance Tasks

#### 4.2.1 Sync with Notification Center Changes (v1.3.0)

v1.3.0 added SSE notifications, notification preferences, and sound settings. The E2E tests may need:
- **NotificationDeliveryE2ETest** — verify SSE delivery path (not just REST polling)
- **New test:** Notification preferences API (mute/unmute channels)
- **New test:** Mark-as-read and bulk-dismiss endpoints

#### 4.2.2 Sync with AuthFilter Delegation Changes

Both admission-service and documents-service AuthFilters were updated to delegate auth failures to Spring Security instead of returning 401 directly. Verify:
- `AuthFlowE2ETest` still passes with new auth behavior
- `NegativeFlowE2ETest` — 401 responses may have different body shape if Spring Security formats them

#### 4.2.3 Sync with CORS Restriction

Gateway CORS was restricted from `*` to specific origins. If E2E tests make cross-origin requests, they may need the test origin added to the allowed list, or use the proxy approach from Phase 2.

#### 4.2.4 CI Integration

The E2E repo has its own CI workflows:
- `ci.yml` — standard CI
- `nightly.yml` — nightly regression runs
- `regression.yml` — on-demand regression suite

Verify these workflows still work against the current service stack. May need Docker compose file updates if service configurations changed.

### 4.3 Verification

```bash
# Start full stack
cd infra && docker compose -f docker-compose.yml -f docker-compose.services.yml up -d

# Run E2E tests
cd server/services/selection-committee-e2e-tests
./gradlew test
```

---

## Phase 5: Visual Regression Testing Infrastructure

### 5.1 Problem Statement

Theme changes, CSS variable updates, and component styling modifications have zero automated verification. The dark mode toggle, color tokens, shadows, and responsive layouts can regress silently. Current Playwright setup only captures screenshots on test failure — no baseline comparison.

### 5.2 Approach: Playwright Visual Comparisons

Playwright has built-in `toHaveScreenshot()` for visual regression. No external service needed.

**Setup:**
1. Configure `expect.toHaveScreenshot` in `playwright.config.ts`:
```typescript
expect: {
  toHaveScreenshot: {
    maxDiffPixels: 100,
    threshold: 0.2,
  },
},
```

2. Create baseline screenshots on first run (committed to repo).
3. CI updates baselines on `--update-snapshots` flag.

### 5.3 Implementation

#### 5.3.1 Visual Regression Spec

**File:** `client/web/tests/e2e/regression/visual/theme-snapshots.spec.ts`

**Tests:**
- Dashboard in light mode → screenshot comparison
- Dashboard in dark mode → screenshot comparison
- Login page in light mode → screenshot comparison
- Login page in dark mode → screenshot comparison
- Toast notification in light mode → screenshot comparison
- Toast notification in dark mode → screenshot comparison

**Pattern:**
```typescript
test("dashboard light mode", async ({ page }) => {
  // Auth + navigate to dashboard
  await expect(page).toHaveScreenshot("dashboard-light.png", {
    fullPage: false,
    mask: [page.locator("[data-testid='dynamic-content']")], // mask dynamic data
  });
});

test("dashboard dark mode", async ({ page }) => {
  // Auth + navigate to dashboard + toggle theme
  await page.evaluate(() => {
    document.documentElement.classList.add("dark");
  });
  await expect(page).toHaveScreenshot("dashboard-dark.png", {
    fullPage: false,
    mask: [page.locator("[data-testid='dynamic-content']")],
  });
});
```

#### 5.3.2 Theme Toggle E2E Test

**File:** `client/web/tests/e2e/regression/visual/theme-toggle.spec.ts`

**Tests:**
- Click theme toggle → verify `.dark` class toggles on `<html>`
- Theme persists across page reload (localStorage)
- Toast theme matches current theme (verifies Phase 1.1 Sonner fix)
- No FOUC (flash of unstyled content) on page load in dark mode

### 5.4 Considerations

- Screenshots are platform-dependent (fonts, rendering) — use Docker for CI consistency
- Mask dynamic content (timestamps, counts, user names) to reduce flakiness
- Start with a small set of critical pages, expand gradually
- Baseline updates require manual review (`pnpm exec playwright test --update-snapshots`)

---

## Dependency Graph

```
Phase 1 (Sonner fix)           ── independent, quick fix, do first
Phase 2 (Proxy migration)      ── independent, significant refactor
Phase 3 (Telegram bot)         ── independent, separate repo
Phase 4 (Backend E2E)          ── depends on Phase 2 (CORS changes affect E2E)
Phase 5 (Visual regression)    ── depends on Phase 1 (Sonner fix), Phase 5.3.2 depends on Phase 5.3.1
```

**Recommended execution order:**
1. Phase 1 (Sonner fix) — 15 minutes
2. Phase 5.3.1 (Visual regression infra) — enables theme testing
3. Phase 5.3.2 (Theme toggle E2E) — uses visual regression
4. Phase 2 (Proxy migration) — significant, test thoroughly
5. Phase 4 (Backend E2E sync) — after proxy migration settles
6. Phase 3 (Telegram bot) — independent, can be parallel with 2-5

---

## Verification Checklist

### Phase 1
- [ ] `sonner.tsx` imports from `@/stores/theme-store`, not `next-themes`
- [ ] `pnpm remove next-themes` succeeds (no other consumers)
- [ ] `npx tsc --noEmit` passes
- [ ] Dark mode toast renders with dark colors

### Phase 2
- [ ] `next.config.ts` has `rewrites` proxying `/api/**`
- [ ] `client.ts` uses empty baseURL (same-origin)
- [ ] `NEXT_PUBLIC_API_BASE` removed from `.env.local` and constants
- [ ] Login flow works end-to-end
- [ ] SSE notifications work through proxy
- [ ] No CORS preflight in browser Network tab
- [ ] E2E smoke tests pass

### Phase 3
- [ ] `./gradlew build` passes from telegram bot service directory
- [ ] Each sub-phase: tests written first, then implementation
- [ ] JaCoCo coverage ≥ 80%
- [ ] Spotless formatting clean

### Phase 4
- [ ] `./gradlew test` passes in e2e-tests repo against current stack
- [ ] NotificationDeliveryE2ETest covers SSE
- [ ] AuthFlowE2ETest works with new AuthFilter behavior

### Phase 5
- [ ] `playwright.config.ts` has `toHaveScreenshot` config
- [ ] Baseline screenshots committed for light + dark mode
- [ ] Theme toggle test verifies persistence and FOUC prevention
- [ ] CI can update baselines with `--update-snapshots`

---

## What is NOT in this prompt (and why)

| Item | Reason |
|---|---|
| Additional service AuthFilters | Only admission-service and documents-service have AuthFilter.java — others use different auth mechanisms |
| Grafana dashboard improvements | 8 dashboards + 7 alert rules already sufficient for current scale |
| Performance testing infrastructure | K6/JMH scripts exist in E2E repo — maintenance only, not new development |
| Mobile-specific E2E tests | Current Pixel 7 project in Playwright covers responsive — no new mobile-specific gaps identified |
| Next.js 17 migration | Not released yet — monitor for breaking changes |
