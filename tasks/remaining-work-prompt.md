# Remaining Work — Implementation Prompt (v2)

## Context

v1.3.0 shipped (Notification Center). Two audit rounds completed:
- **2026-04-01 Audit #1:** Resolved CORS wildcard, actuator overexposure, contract test gaps, AuthFilter delegation (admission-service), TanStack Query migration, NotificationCard memoization.
- **2026-04-01 Audit #2:** Resolved magic link Referrer leak, `refreshUser()` error handling, sidebar active state bug, `useIsMobile` hydration flash, onboarding step contrast, documents-service AuthFilter alignment.

This prompt covers everything that **genuinely remains** after cross-referencing REVIEW_REPORT.md findings against actual code state as of 2026-04-01 evening.

---

## Items Verified as DONE (do NOT reimplement)

**Security (all done):**
- Security headers — gateway `SecurityHeadersFilter.java` (CSP, HSTS, X-Frame-Options, Referrer-Policy, Permissions-Policy)
- Session cookie `Secure` flag — conditional on HTTPS in `client/web/src/lib/auth/session.ts:34`
- Swagger restriction — `springdoc.api-docs.enabled` defaults to `false`
- Server-side onboarding validation — `ProfileController.java:39-42,79-82`
- Profile input constraints — `ProfileUpdateRequest.java` has `@Size`, `@Pattern`, `@NotNull`, `@NotEmpty`
- `authApi.getUser()` — already uses GET (not POST)
- `Cache-Control: no-store` — `ProfileController` already adds it
- CORS restricted origins — gateway `SecurityConfig.java`
- Actuator endpoint exposure restricted
- Magic link Referrer leak — `page.tsx` now has `referrer: "no-referrer"` metadata
- `refreshUser()` error handling — now wraps in try-catch, calls `handleAuthFailure()` on error
- AuthFilter delegation — both admission-service and documents-service now use `log.warn`, clear SecurityContext on missing Bearer, add role name to authorities

**UX (all done):**
- Sidebar active state bug — root path `/` now uses exact match only
- `useIsMobile` hydration flash — rewritten with `useSyncExternalStore`
- `verifyMagicLink` redundant call — already returns user from response
- Dashboard null-user guard — three-layer protection in place

**Accessibility (all done):**
- ARIA on onboarding — `progress.tsx` has `role="progressbar"`, `aria-current="step"`, `aria-live="polite"`
- Focus management on step transitions — `onboarding/page.tsx:46-49`
- Skip-to-content link — `(dashboard)/layout.tsx:99-104` with `#main-content`
- Sidebar `<nav>` landmark — `sidebar-nav.tsx:25` has `aria-label="Основна навігація"`
- `prefers-reduced-motion` — globally applied in `globals.css:457-468`
- `LoadingButton` a11y — has `aria-busy`, sr-only "Завантаження..." text
- `OnboardingProgress` memo — already wrapped
- Step indicator contrast — border increased to `/50`, label increased to full `text-muted-foreground`

**Infrastructure (partially done):**
- Trivy vulnerability scanning — already added to `service-docker.yml` (lines 178-193)
- Grafana alerting rules — `infra/prometheus/alerts.yml` already exists with 7 rules (ServiceDown, HighErrorRate, HighLatencyP95, CircuitBreakerOpen, CircuitBreakerHighFailureRate, HighJvmMemoryUsage, HikariPoolExhausted)
- Prometheus config — `prometheus.yml` already mounts `alerts.yml` via `rule_files`

**Tests (existing):**
- AuthFilterTest for admission-service — 4 tests including webhook bypass
- AuthFilterTest for documents-service — 4 tests including role prefix handling

---

## Phase 1: Pre-existing Test Fixes (Quick Wins)

### 1.1 Fix `role-guard.test.tsx` Missing `updateUser` Mock

**Problem:** `tests/unit/components/role-guard.test.tsx` has 4 test cases that all fail TypeScript compilation because the mock return value is missing the `updateUser` property added to `AuthContextValue`.

**Current code (lines 15-30, repeated in 3 more places):**
```typescript
mockUseAuth.mockReturnValue({
  user: { ... },
  isAuthenticated: true,
  isLoading: false,
  refreshUser: vi.fn(),
  verifyMagicLink: vi.fn(),
  logout: vi.fn(),
  // MISSING: updateUser: vi.fn(),
});
```

**Fix:** Add `updateUser: vi.fn()` to all 4 `mockReturnValue` calls (lines 15, 42, 69, 96).

**File:** `client/web/tests/unit/components/role-guard.test.tsx`

**Verification:** `npx tsc --noEmit` should no longer report errors for this file.

---

## Phase 2: Health Check Tuning (Infrastructure)

### 2.1 Notifications-Service Start Period

**Problem:** `notifications-service` has heavier startup than other services (Flyway migrations + RabbitMQ connection + event listener registration), but uses the same `start_period: 30s` as all others. Under load or slow Docker hosts, this causes false-negative health checks during startup.

**Current state:**
- Dockerfile (`selection-committee-notifications-service/Dockerfile:36`): `--start-period=30s`
- Compose (`infra/docker-compose.services.yml:239`): `start_period: 30s`

**Fix:** Increase `start_period` to `45s` in compose healthcheck override (compose takes precedence over Dockerfile):
```yaml
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:8086/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 10
      start_period: 45s
```

**File:** `infra/docker-compose.services.yml:239` — change `start_period: 30s` to `start_period: 45s`

**Verification:** `docker compose -f infra/docker-compose.services.yml config` should parse without errors. Monitor startup with `docker compose logs -f notifications-service`.

---

## Phase 3: E2E Test Gaps (New Specs)

### 3.1 Error States Spec

**Problem:** No E2E test verifies graceful degradation when APIs fail. If error handling regresses, users see blank screens or unhandled exceptions instead of friendly error messages.

**What to test:**
- Navigate to dashboard with API mocked to return 500 → verify error boundary or fallback UI renders
- Submit onboarding form with API mocked to return 422 → verify validation error displays
- Trigger network timeout → verify loading state doesn't hang indefinitely

**Approach:** Use Playwright route interception (`page.route()`) to mock API failures.

**File to create:** `client/web/tests/e2e/regression/error-handling/api-failures.spec.ts`

**Considerations:**
- Use existing page objects from `tests/e2e/pages/`
- Use existing helpers from `tests/e2e/helpers/`
- Follow existing patterns in `tests/e2e/regression/` for structure
- The app uses Axios with interceptors (`src/lib/api/client.ts`) — route mocking should target the gateway URL

---

### 3.2 Role-Specific Dashboard Specs

**Problem:** Only applicant dashboard has E2E coverage (`tests/e2e/regression/applicant/dashboard.spec.ts`). Admin, operator, and deputy-secretary dashboards are untested. RBAC bugs (showing wrong data, allowing wrong actions) would go undetected.

**What to test per role:**
- Login as role → verify correct dashboard renders
- Verify role-specific navigation items are present
- Verify data appropriate to the role is displayed (or empty state)
- Verify actions appropriate to the role are available

**Files to create:**
- `client/web/tests/e2e/regression/admin/dashboard.spec.ts`
- `client/web/tests/e2e/regression/operator/dashboard.spec.ts`

**Considerations:**
- Requires test accounts for each role (check `tests/e2e/fixtures/` for existing test data)
- Use existing `global-setup.ts` auth pattern
- Follow the same structure as `applicant/dashboard.spec.ts`

---

### 3.3 DataTable Interactions Spec

**Problem:** DataTable is a core shared component used across admin and operator views, but has zero E2E coverage. Sorting, pagination, and filtering regressions would be invisible.

**What to test:**
- Load a page with a DataTable → verify rows render
- Click column header → verify sort order changes (aria-sort attribute or visual indicator)
- Navigate pagination → verify page content changes
- Apply filter → verify rows are filtered

**File to create:** `client/web/tests/e2e/regression/shared/data-table.spec.ts`

**Considerations:**
- Requires a role with access to a list view (admin or operator)
- Target a specific page that uses DataTable (e.g., admissions list)
- Verify both the data content and the accessibility attributes of the table

---

## Phase 4: Cleanup

### 4.1 Task Plans Pruning

**Problem:** 19 task plan files in `tasks/` — phases 1-10, redesigns 00-07, plus this prompt and a telegram-bot prompt. Completed plans create confusion about what work is actually pending.

**Completed and safe to delete:**
- `tasks/phase-01-backend-database-entity.md` through `tasks/phase-07-testing-edge-cases.md` (original build phases — fully shipped)
- `tasks/phase-08-critical-fixes.md`, `tasks/phase-09-major-improvements.md`, `tasks/phase-10-polish-optimization.md` (post-launch fixes — fully shipped)
- `tasks/redesign-00-overview.md` through `tasks/redesign-07-polish-testing.md` (redesign — fully shipped)
- `tasks/telegram-bot-phase2-prompt.md` (telegram bot Phase 2 — separate repo, prompt served its purpose)

**Keep:**
- `tasks/remaining-work-prompt.md` (this file — active reference)

**Action:** Delete 19 files, keep 1.

---

### 4.2 Redundant `.gitkeep`

**Problem:** `infra/grafana/dashboards/.gitkeep` is redundant — the directory now contains 8 dashboard JSON files.

**File to delete:** `infra/grafana/dashboards/.gitkeep`

---

## Dependency Graph

```
Phase 1 (Test Fix)    ── independent, quick win
Phase 2 (Health Check) ── independent, infrastructure
Phase 3 (E2E Tests)    ── independent, parallel with 1-2
Phase 4 (Cleanup)      ── independent, anytime
```

All phases are independent — they can be done in any order or in parallel.

---

## Verification Checklist

- [ ] **Phase 1.1:** `cd client/web && npx tsc --noEmit` — no errors in `role-guard.test.tsx`
- [ ] **Phase 1.1:** `cd client/web && npx vitest run tests/unit/components/role-guard.test.tsx` — all 4 tests pass
- [ ] **Phase 2.1:** `docker compose -f infra/docker-compose.services.yml config` parses cleanly
- [ ] **Phase 3.1:** `npx playwright test tests/e2e/regression/error-handling/api-failures.spec.ts`
- [ ] **Phase 3.2:** `npx playwright test tests/e2e/regression/admin/dashboard.spec.ts`
- [ ] **Phase 3.3:** `npx playwright test tests/e2e/regression/shared/data-table.spec.ts`
- [ ] **Phase 4.1:** `ls tasks/` shows only `remaining-work-prompt.md`
- [ ] **Phase 4.2:** `ls infra/grafana/dashboards/.gitkeep` returns "not found"

---

## What is NOT in this prompt (and why)

| Item | Reason |
|---|---|
| Additional service AuthFilters | Only admission-service and documents-service have AuthFilter.java. The other 6 services don't use this pattern (gateway uses Spring Security directly, identity-service IS the auth provider, others may use shared-lib auth or different mechanisms). |
| Frontend middleware → proxy migration | Tracked separately — requires Next.js 16 breaking changes analysis |
| Telegram bot Phase 2 | Separate repository, separate prompt |
| Backend E2E tests | Moved to separate repo (`selection-committee-e2e-tests`) |
| Theme toggle E2E test | Low priority — theme is CSS-only, unlikely to regress without visual regression testing infrastructure |
| Grafana alerting improvements | Current `alerts.yml` has 7 comprehensive rules covering service health, error rates, latency, circuit breakers, JVM memory, and connection pools. Sufficient for current scale. |
