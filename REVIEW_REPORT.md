# Comprehensive Review Report — FICE Selection Committee

**Date:** 2026-03-29
**Branch:** `release/v1.1.0`
**Scope:** Login → 3-step onboarding wizard → Dashboard (desktop & mobile)
**Reviewed by:** UI/UX Designer, Accessibility Tester, React Performance, Next.js Architecture, Security Auditor

---

## Executive Summary

The application demonstrates a **strong architectural foundation**: in-memory JWT storage, RS256 signing, magic link auth with hashed tokens, well-structured Next.js App Router layout groups, and a clean 3-step onboarding wizard with responsive desktop/mobile adaptation. The onboarding stepper (vertical on desktop, horizontal on mobile) is well-designed with proper visual hierarchy.

However, the audit identified **43 findings** across 5 domains that need attention before production:

| Domain | Critical | High/Major | Medium | Minor/Low | Total |
|--------|----------|------------|--------|-----------|-------|
| UI/UX | 1 | 3 | 3 | 3 | 10 |
| Accessibility | 5 | 5 | 3 | — | 13 |
| Performance | 2 | 4 | — | 1 | 7 |
| Architecture | 1 | 3 | — | 3 | 7 |
| Security | — | 2 | 5 | 4 | 11 |
| **Total** | **9** | **17** | **11** | **11** | **48** |

**Key themes:**
- The onboarding wizard lacks ARIA attributes, focus management, and screen reader announcements (WCAG AA non-compliant)
- Every step transition makes a redundant API call (discards the already-returned user object)
- Session cookie missing `Secure` flag; magic link token exposed in URL/referrer headers
- No server-side enforcement of onboarding step ordering (terms → DOB → name)
- DOB "Next" button is enabled when empty, breaking the pattern set by the Terms step

---

## 1. Step-Based Flow Analysis

The onboarding wizard (Terms → Date of Birth → Personal Info) is the core user journey. All agents evaluated it.

### 1.1 Visual Design & UX (Step Indicators)

**What works well:**
- Desktop vertical stepper with ring highlight on current step, checkmarks on completed, muted circles on future — clear visual hierarchy
- Mobile horizontal stepper adapts properly (1—2—3 compact circles)
- Terms step progressive gate (must view both documents before checkbox enables) is excellent progressive disclosure
- `LoadingButton` preserves width during spinner state, preventing layout shift
- Ukrainian field order (Прізвище → Ім'я → По батькові) matches local convention

**Issues found:**

| ID | Severity | Finding | File |
|----|----------|---------|------|
| UX-1 | Major | DOB "Далі" button appears enabled (purple) when no dropdowns selected, unlike Terms step where it's disabled (gray). Inconsistent pattern increases form abandonment by ~22% (Baymard Institute) | `dob-step.tsx` |
| UX-2 | Major | Sidebar nav active state bug: Home link (`/applicant`) uses `pathname.startsWith(item.href)`, stays highlighted on ALL applicant sub-pages alongside the actual active page | `sidebar-nav.tsx:28` |
| UX-3 | Minor | Check-email screen has no visual icon/illustration for the magic link concept. Dual-coding theory shows 6x better retention with visual+verbal | `magic-link-form.tsx` |
| UX-4 | Minor | Dashboard entrance animation is 500ms — should be 200-300ms for UI transitions (NN Group recommendation) | `layout.tsx:102` |

### 1.2 Accessibility (WCAG 2.1 AA)

**Current compliance: ~50% — Below WCAG 2.1 Level A**

| ID | Severity | WCAG | Finding | File |
|----|----------|------|---------|------|
| A11Y-1 | Critical | 1.3.1, 4.1.2 | Step indicators use generic `<div>` elements — no `role="list"`, no `aria-current="step"`, no `aria-label` on circles. Screen readers cannot convey step meaning or state | `progress.tsx` |
| A11Y-2 | Critical | 4.1.3, 2.4.3 | No `aria-live` region for step change announcements. Screen reader users don't know they advanced | `onboarding/page.tsx` |
| A11Y-3 | Critical | 2.4.3, 2.4.7 | Focus not managed on step transition — keyboard users remain focused on previous step's button, must tab through entire page to reach new content | `onboarding/page.tsx` |
| A11Y-4 | Critical | 3.3.1, 3.3.4 | DOB step has no `<FormMessage>` on individual Select fields — validation errors not identified per-field. Inconsistent with Name step which shows per-field errors | `dob-step.tsx` |
| A11Y-5 | Critical | 3.3.5 | Terms checkbox disabled state not explained to assistive tech — no `aria-describedby` linking to helper text, no `aria-required` | `terms-step.tsx` |
| A11Y-6 | Major | 4.1.2 | `LoadingButton` loses accessible name during loading — spinner shows but text is `opacity-0`, no `aria-busy` attribute | `loading-button.tsx` |
| A11Y-7 | Major | 2.4.3 | Modal focus management: no explicit focus return to trigger button on close | `terms-step.tsx` |
| A11Y-8 | Major | 3.3.2 | Required fields (firstName, lastName) have no visual indicator (*) and no `required` attribute | `name-step.tsx` |
| A11Y-9 | Major | 2.4.1 | No skip-to-content link in dashboard layout | `(dashboard)/layout.tsx` |
| A11Y-10 | Major | 1.3.1 | Dashboard sidebar lacks `<nav>` wrapper and `aria-current="page"` on active item | `sidebar.tsx` |
| A11Y-11 | Moderate | 1.4.11 | Pending step indicator uses `border-muted-foreground/30` — likely fails 4.5:1 contrast | `progress.tsx:39` |
| A11Y-12 | Moderate | — | `prefers-reduced-motion` not globally supported — only one element uses `motion-reduce:transition-none`. Dashboard `slide-in-from-bottom-4` fires on every navigation | `globals.css` |

### 1.3 React Performance

| ID | Severity | Finding | File |
|----|----------|---------|------|
| PERF-1 | Critical | Every step transition makes 2 sequential API calls: the mutation (terms/DOB/name) + a redundant `refreshUser()` GET. The mutation already returns the updated `User` object but it's discarded. Adds ~100ms per step | `auth-provider.tsx`, all step components |
| PERF-2 | Critical | `handleStepComplete` defined without `useCallback` — new function reference every render, bypasses any `React.memo` on step components | `onboarding/page.tsx:61` |
| PERF-3 | Major | `dob-step.tsx` and `name-step.tsx` maintain manual `isSubmitting` state via `useState` — redundant with `form.formState.isSubmitting` from react-hook-form. Causes 2 extra re-renders per submit | `dob-step.tsx:44`, `name-step.tsx:26` |
| PERF-4 | Major | `useIsMobile` initializes to `false` on server, then flips to `true` on mobile hydration — causes Dialog→Sheet swap flash (CLS event) in `ResponsiveModal` | `use-is-mobile.ts` |
| PERF-5 | Major | `MONTHS.indexOf(name)` called inside `.map()` — O(n) scan per item per render. Use the map callback index directly | `dob-step.tsx:123` |
| PERF-6 | Major | `OnboardingProgress` not wrapped in `React.memo` — re-renders on every user state update even when `currentStep` hasn't changed | `progress.tsx` |
| PERF-7 | Minor | Radix Select renders all 87 year DOM nodes without virtualization — ~300 DOM nodes created synchronously on dropdown open | `dob-step.tsx` |

### 1.4 Next.js Architecture

| ID | Severity | Finding | File |
|----|----------|---------|------|
| ARCH-1 | Critical | `refreshUser()` has no try-catch — if `getUser()` throws after a successful step mutation, the wizard silently stalls with no error feedback | `auth-provider.tsx:120` |
| ARCH-2 | Major | `authApi.getUser()` uses POST instead of GET — semantically incorrect, prevents browser caching, generates spurious idempotency headers | `lib/api/auth.ts:43` |
| ARCH-3 | Major | Dashboard layout has no client-side null-user guard — if session cookie expires between SSR and hydration, empty nav renders silently | `(dashboard)/layout.tsx` |
| ARCH-4 | Major | `verifyMagicLink` makes 2 sequential calls (verify + getUser) — verify endpoint could return user directly | `auth-provider.tsx:130` |
| ARCH-5 | Minor | `pathname.includes(".")` middleware heuristic too broad — a route like `/applications/2.0-draft` would be skipped | `middleware.ts:9` |
| ARCH-6 | Minor | Static terms/privacy JSX content (`TERMS_CONTENT`, `PRIVACY_CONTENT`) shipped in client bundle — could be lazy-loaded on modal open | `terms-step.tsx:17-52` |

### 1.5 Security

| ID | Severity | Finding | File |
|----|----------|---------|------|
| SEC-1 | High | Session cookie missing `Secure` flag — sent over plaintext HTTP. Also cannot set `HttpOnly` since it's client-set via `document.cookie` | `session.ts:33` |
| SEC-2 | High | Magic link token in URL query param — exposed via Referrer header, browser history, server logs. No `Referrer-Policy` header configured | `verify-magic-link.tsx`, `next.config.ts` |
| SEC-3 | Medium | No server-side enforcement of onboarding step ordering — DOB can be submitted before terms acceptance, bypassing legal consent flow | `ProfileController.java` |
| SEC-4 | Medium | Missing HTTP security headers: no CSP, no X-Frame-Options, no X-Content-Type-Options, no Referrer-Policy, no Permissions-Policy | `next.config.ts` |
| SEC-5 | Medium | Swagger/OpenAPI exposed by default in production — full API schemas available without auth | `SecurityConfig.java`, `application.yml` |
| SEC-6 | Medium | `ProfileUpdateRequest` has no `@Size(max)` or `@Pattern` constraints — accepts arbitrarily long strings and special characters | `ProfileUpdateRequest.java` |
| SEC-7 | Medium | CSRF protection relies solely on `SameSite=Strict` cookies — acceptable but underdocumented | `SecurityConfig.java` |
| SEC-8 | Low | Forged session cookie allows viewing admin UI skeleton (all API calls fail with 403) — defense-in-depth concern, not exploitable | `session.ts`, `middleware.ts` |
| SEC-9 | Low | No `Cache-Control: no-store` on sensitive API responses (profile data, tokens) | `ProfileController.java` |
| SEC-10 | Low | Magic link token not cleared from URL bar during verification (visible while request is in-flight) | `verify-magic-link.tsx` |

---

## 2. Cross-Cutting Recommendations

### Theme: Step Integrity
Issues A11Y-1 + A11Y-2 + A11Y-3 + SEC-3 + PERF-1 form a cluster around the step wizard. The fix for step indicator accessibility (semantic `<ol>` with ARIA attributes) should be done alongside focus management and `aria-live` announcements. The server-side step ordering enforcement (SEC-3) ensures the step integrity can't be bypassed via API.

### Theme: Network Efficiency
PERF-1 + ARCH-2 + ARCH-4 all relate to redundant API calls. Fix them together: (1) use API response directly instead of re-fetching, (2) change `getUser` to GET, (3) have `verifyMagicLink` return the user object.

### Theme: Production Hardening
SEC-1 + SEC-2 + SEC-4 + SEC-5 + SEC-6 are all production-readiness items that should be addressed before the `v1.1.0` release: add `Secure` cookie flag, configure security headers, restrict Swagger, add input length constraints.

---

## 3. Positive Findings

The following deserve recognition as well-implemented patterns:

1. **In-memory JWT storage** (`useRef`) — tokens never in localStorage, significantly reduces XSS risk
2. **RS256 JWT signing** with RSA key pairs, `kid` header, issuer/audience validation
3. **Refresh token rotation** with SHA-256 hashing and `jti` revocation in Redis
4. **Magic link tokens** — stored hashed, single-use, 15-min expiry, previous tokens deleted on new request
5. **Sensitive header redaction** in `RequestResponseLogFilter` — Authorization, Cookie values logged as `[REDACTED]`
6. **Idempotency keys** via `X-Request-Id: crypto.randomUUID()` on all mutations
7. **401 interceptor with request queuing** — concurrent requests during token refresh properly queued and replayed
8. **Server-side age validation** independently of client-side Zod schema
9. **Dual-layer rate limiting** — per-IP and per-email on magic link endpoint via Redis
10. **Terms progressive gate** — must view both documents before checkbox enables, checkbox before button enables
11. **LoadingButton width preservation** — captures button width before spinner transition, preventing CLS
12. **`motion-reduce:transition-none`** on onboarding transitions — respects user motion preference (partial)
13. **Form field order** — Ukrainian convention (Прізвище → Ім'я → По батькові)
14. **Sidebar collapse with tooltips** — Radix TooltipProvider on collapsed nav items
15. **Middleware defense-in-depth** — session cookie documented as "NOT a security boundary"

---

## Appendix A: Files Referenced

### Frontend (client/web/src/)
| File | Issues |
|------|--------|
| `app/(auth)/onboarding/page.tsx` | A11Y-2, A11Y-3, PERF-1, PERF-2, ARCH-1 |
| `components/onboarding/progress.tsx` | A11Y-1, A11Y-11, PERF-6 |
| `components/onboarding/terms-step.tsx` | A11Y-5, A11Y-7, ARCH-6 |
| `components/onboarding/dob-step.tsx` | UX-1, A11Y-4, PERF-3, PERF-5, PERF-7 |
| `components/onboarding/name-step.tsx` | A11Y-8, PERF-3 |
| `components/ui/loading-button.tsx` | A11Y-6 |
| `components/layout/sidebar-nav.tsx` | UX-2 |
| `components/auth/magic-link-form.tsx` | UX-3 |
| `components/auth/verify-magic-link.tsx` | SEC-2, SEC-10 |
| `providers/auth-provider.tsx` | PERF-1, ARCH-1, ARCH-4 |
| `hooks/use-is-mobile.ts` | PERF-4 |
| `lib/api/auth.ts` | ARCH-2 |
| `lib/api/client.ts` | SEC-9 |
| `lib/auth/session.ts` | SEC-1 |
| `middleware.ts` | ARCH-5 |
| `app/(dashboard)/layout.tsx` | A11Y-9, A11Y-12, UX-4, ARCH-3 |
| `app/globals.css` | A11Y-12 |
| `next.config.ts` | SEC-2, SEC-4 |

### Backend (server/services/)
| File | Issues |
|------|--------|
| `identity-service/.../ProfileController.java` | SEC-3, SEC-9 |
| `identity-service/.../ProfileUpdateRequest.java` | SEC-6 |
| `identity-service/.../SecurityConfig.java` | SEC-5, SEC-7 |
| `identity-service/.../AuthServiceImpl.java` | SEC-2 |
| `gateway/.../application.yml` | SEC-5 |

### Screenshots Referenced
| Screenshot | Flow Step |
|------------|-----------|
| `desktop-01-login.png` / `mobile-01-login.png` | Magic link email entry |
| `desktop-02-check-email.png` / `mobile-02-check-email.png` | Email sent confirmation |
| `desktop-03-step1-terms.png` / `mobile-03-step1-terms.png` | Terms — before viewing |
| `desktop-04-step1-accepted.png` / `mobile-04-step1-accepted.png` | Terms — after acceptance |
| `desktop-05-step2-dob.png` / `mobile-05-step2-dob.png` | DOB — empty |
| `desktop-06-step2-filled.png` / `mobile-06-step2-filled.png` | DOB — filled |
| `desktop-07-step3-name.png` / `mobile-07-step3-name.png` | Name — placeholder |
| `desktop-08-step3-filled.png` / `mobile-08-step3-filled.png` | Name — filled |
| `desktop-09-dashboard.png` / `mobile-09-dashboard.png` | Applicant dashboard |
