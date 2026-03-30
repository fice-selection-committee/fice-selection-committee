# Phase 9: Major Improvements (3–5 days)

Architecture fixes, UX consistency, remaining accessibility, and performance majors.

---

## 9.1 UX: DOB Button Disabled State Consistency
**Issues:** UX-1
**File:** `client/web/src/components/onboarding/dob-step.tsx`
**Effort:** 15 min

### Substep 9.1.1: Add `mode: "onChange"` to useForm
```typescript
const form = useForm<DateOfBirthFormData>({
  resolver: zodResolver(dateOfBirthSchema),
  mode: "onChange", // enables real-time isValid tracking
});
```

### Substep 9.1.2: Disable button when form is invalid
```tsx
<LoadingButton
  type="submit"
  loading={isSubmitting}
  disabled={!form.formState.isValid}
  className="w-full rounded-xl"
  size="lg"
>
  Далі
</LoadingButton>
```

This matches the Terms step pattern where the button is gray/disabled until prerequisites are met.

---

## 9.2 UX: Fix Sidebar Nav Active State
**Issues:** UX-2
**File:** `client/web/src/components/layout/sidebar-nav.tsx:28`
**Effort:** 15 min

### Substep 9.2.1: Use exact match for root role routes
The current `pathname.startsWith(item.href)` causes Home (`/applicant`) to be highlighted on every sub-page. Fix:

```typescript
const isActive = pathname === item.href ||
  (item.href !== getRoleDefaultRoute(user.role) && pathname.startsWith(item.href));
```

Or add an `exact` property to home nav items and check:
```typescript
const isActive = item.exact ? pathname === item.href : pathname.startsWith(item.href);
```

---

## 9.3 Architecture: Change `getUser` from POST to GET
**Issues:** ARCH-2
**File:** `client/web/src/lib/api/auth.ts:43`
**Effort:** 30 min (frontend + backend coordination)

### Substep 9.3.1: Change API client call
```typescript
async getUser() {
  const response = await apiClient.get<Record<string, unknown>>(`${AUTH_BASE}/user`);
  return { ...response, data: normalizeUser(response.data) };
},
```

### Substep 9.3.2: Add GET mapping on backend controller
In `AuthController.java`, add `@GetMapping("/user")` alongside or replacing the existing `@PostMapping`. Ensure the `AccessTokenFilter` authenticates GET requests to this endpoint.

### Substep 9.3.3: Remove spurious idempotency header
The `X-Request-Id` interceptor in `client.ts` only applies to POST/PUT/PATCH — switching to GET automatically eliminates the unnecessary header.

---

## 9.4 Architecture: Dashboard Layout Null-User Guard
**Issues:** ARCH-3
**File:** `client/web/src/app/(dashboard)/layout.tsx`
**Effort:** 15 min

### Substep 9.4.1: Add redirect when user is null after loading
```typescript
const router = useRouter();

useEffect(() => {
  if (!isLoading && !user) {
    router.push("/login");
  }
}, [isLoading, user, router]);
```

This handles the edge case where the session cookie expires between SSR and hydration.

---

## 9.5 Architecture: Combine `verifyMagicLink` into Single Round-Trip
**Issues:** ARCH-4
**File:** `client/web/src/providers/auth-provider.tsx:130-151`, backend `AuthController.java`
**Effort:** 1 hour

### Substep 9.5.1: Backend — return user in verify response
Modify the magic link verification endpoint to return `{ accessToken, refreshToken, user }` instead of just tokens. The user data is already loaded during verification — include it in the response.

### Substep 9.5.2: Frontend — use returned user directly
In `auth-provider.tsx`, after `verifyMagicLink`, use the user from the response instead of making a second `getUser()` call:

```typescript
const verifyMagicLink = useCallback(async (token: string) => {
  const { data } = await authApi.verifyMagicLink(token);
  accessTokenRef.current = data.accessToken;
  const userData = data.user; // already in response
  setUser(userData);
  // ... rest of setup
}, []);
```

---

## 9.6 Accessibility: LoadingButton Improvements
**Issues:** A11Y-6
**File:** `client/web/src/components/ui/loading-button.tsx`
**Effort:** 15 min

### Substep 9.6.1: Add `aria-busy` attribute
```tsx
<Button
  ref={ref}
  disabled={disabled || loading}
  aria-busy={loading}
  {...props}
>
```

### Substep 9.6.2: Keep text in DOM for screen readers
Instead of fully hiding text with `opacity-0`, use `aria-hidden` on the spinner and keep the text readable to screen readers:

```tsx
<span className={cn("inline-flex items-center gap-2", loading && "invisible")}>
  {children}
</span>
{loading && (
  <span className="absolute inset-0 flex items-center justify-center" aria-hidden="true">
    <AppleSpinner className="size-5" />
  </span>
)}
```

The `invisible` class (vs `opacity-0`) still hides visually but keeps the text in the accessibility tree.

---

## 9.7 Accessibility: Required Field Indicators
**Issues:** A11Y-8
**File:** `client/web/src/components/onboarding/name-step.tsx`
**Effort:** 15 min

### Substep 9.7.1: Add asterisk to required field labels
```tsx
<FormLabel>
  Прізвище <span className="text-destructive" aria-label="обов'язкове поле">*</span>
</FormLabel>
```
Apply to `lastName` and `firstName` labels. `middleName` already has "(необов'язково)".

### Substep 9.7.2: Add `required` attribute to required inputs
```tsx
<Input placeholder="Іванов" required {...field} />
```

---

## 9.8 Accessibility: Skip-to-Content Link
**Issues:** A11Y-9
**File:** `client/web/src/app/(dashboard)/layout.tsx`
**Effort:** 15 min

### Substep 9.8.1: Add skip link before sidebar
```tsx
<a
  href="#main-content"
  className="sr-only focus:not-sr-only focus:fixed focus:top-4 focus:left-4 focus:z-50 focus:bg-primary focus:text-primary-foreground focus:px-4 focus:py-2 focus:rounded-lg"
>
  Перейти до основного вмісту
</a>
```

### Substep 9.8.2: Add `id="main-content"` to `<main>` element
```tsx
<main id="main-content" className="flex-1 p-4 lg:p-6 ...">
```

---

## 9.9 Accessibility: Sidebar Navigation Landmark
**Issues:** A11Y-10
**File:** `client/web/src/components/layout/sidebar.tsx` (or `sidebar-nav.tsx`)
**Effort:** 15 min

### Substep 9.9.1: Wrap nav items in `<nav>` element
```tsx
<nav aria-label="Основна навігація">
  {/* nav items */}
</nav>
```

### Substep 9.9.2: Add `aria-current="page"` to active nav item
```tsx
<Link
  href={item.href}
  aria-current={isActive ? "page" : undefined}
  className={cn(...)}
>
```

---

## 9.10 Performance: Remove Duplicate `isSubmitting` State
**Issues:** PERF-3
**Files:** `client/web/src/components/onboarding/dob-step.tsx`, `client/web/src/components/onboarding/name-step.tsx`
**Effort:** 15 min

### Substep 9.10.1: Remove `useState` for `isSubmitting` in DOB step
Delete `const [isSubmitting, setIsSubmitting] = useState(false)` and both `setIsSubmitting` calls. Replace `loading={isSubmitting}` with `loading={form.formState.isSubmitting}`.

**Important:** For `form.formState.isSubmitting` to work, the `onSubmit` function passed to `form.handleSubmit()` must be `async` and the promise must be awaited by react-hook-form. The current code already uses `async function onSubmit` — verify it works correctly.

### Substep 9.10.2: Same change in Name step
Apply identical removal in `name-step.tsx`.

---

## 9.11 Performance: Fix `useIsMobile` SSR Hydration Flash
**Issues:** PERF-4
**File:** `client/web/src/hooks/use-is-mobile.ts`
**Effort:** 15 min

### Substep 9.11.1: Use lazy initializer for correct initial value
```typescript
const [isMobile, setIsMobile] = useState(() => {
  if (typeof window === "undefined") return false;
  return window.matchMedia(`(max-width: ${MOBILE_BREAKPOINT - 1}px)`).matches;
});
```

This reads the correct value on first client render, eliminating the Dialog→Sheet flash on mobile.

---

## 9.12 Performance: Fix `MONTHS.indexOf` in DOB Step
**Issues:** PERF-5
**File:** `client/web/src/components/onboarding/dob-step.tsx:123`
**Effort:** 5 min

### Substep 9.12.1: Use map callback index
```tsx
{MONTHS.map((name, i) => (
  <SelectItem key={name} value={(i + 1).toString()}>
    {name}
  </SelectItem>
))}
```

---

## 9.13 Performance: Memoize `OnboardingProgress`
**Issues:** PERF-6
**File:** `client/web/src/components/onboarding/progress.tsx`
**Effort:** 5 min

### Substep 9.13.1: Wrap component in `React.memo`
```tsx
import { memo } from "react";

export const OnboardingProgress = memo(function OnboardingProgress({
  currentStep,
}: OnboardingProgressProps) {
  // ... unchanged body
});
```

---

## 9.14 Accessibility: Global `prefers-reduced-motion` Support
**Issues:** A11Y-12, UX-4 (related)
**File:** `client/web/src/app/globals.css`
**Effort:** 15 min

### Substep 9.14.1: Add global reduced-motion override
```css
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}
```

This is a WCAG A requirement (SC 2.3.1) and affects ~35% of adults over 40.

---

## 9.15 Security: Add Cache-Control Headers to Sensitive Endpoints
**Issues:** SEC-9
**Files:** Backend controllers, `client/web/src/lib/api/client.ts`
**Effort:** 30 min

### Substep 9.15.1: Add response filter on backend
Create a Spring `OncePerRequestFilter` that adds `Cache-Control: no-store, no-cache, must-revalidate` to all `/api/v1/auth/**` and `/api/v1/identity/profile/**` responses.

### Substep 9.15.2: Alternative — annotate controllers
Add `@ResponseHeader` or use `HttpServletResponse.setHeader()` in each sensitive controller method.

---

## Verification Checklist (Phase 9)
- [ ] DOB step: "Далі" button disabled until all 3 dropdowns selected
- [ ] Sidebar: only one nav item highlighted at a time on all routes
- [ ] `getUser` API call is now GET (check Network tab)
- [ ] Dashboard: redirects to /login if session expires during navigation
- [ ] Magic link verify: only 1 API call (not 2) during verification
- [ ] LoadingButton: screen reader announces button text during loading state
- [ ] Name step: asterisk visible on required fields
- [ ] Skip link: visible on Tab press in dashboard, jumps to main content
- [ ] Sidebar wrapped in `<nav>` element (check DOM inspector)
- [ ] DOB/Name steps: no manual `isSubmitting` state (check source)
- [ ] Mobile: no Dialog→Sheet flash when opening terms modal
- [ ] OnboardingProgress: no unnecessary re-renders (React DevTools profiler)
- [ ] `prefers-reduced-motion: reduce` disables all animations
