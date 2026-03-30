# Phase 8: Critical Fixes (1–2 days)

Security hardening, step wizard accessibility foundation, and performance-critical issues.
These MUST be resolved before the `v1.1.0` release.

---

## 8.1 Security: Add `Secure` Flag to Session Cookie
**Issues:** SEC-1
**File:** `client/web/src/lib/auth/session.ts:33`
**Effort:** 15 min

### Substep 8.1.1: Add `Secure` flag conditionally for production
```typescript
// session.ts line 33 — append Secure flag when not localhost
const isSecure = typeof window !== "undefined" && window.location.protocol === "https:";
document.cookie = `${SESSION_COOKIE}=${encodeURIComponent(JSON.stringify(data))};path=/;max-age=${maxAge};SameSite=Lax${isSecure ? ";Secure" : ""}`;
```

### Substep 8.1.2: Consider upgrading to `SameSite=Strict`
The cookie is only consumed by same-origin middleware — `Strict` is appropriate.

---

## 8.2 Security: Configure HTTP Security Headers
**Issues:** SEC-2, SEC-4
**File:** `client/web/next.config.ts`
**Effort:** 30 min

### Substep 8.2.1: Add `headers()` to Next.js config
```typescript
async headers() {
  return [{
    source: "/(.*)",
    headers: [
      { key: "X-Frame-Options", value: "DENY" },
      { key: "X-Content-Type-Options", value: "nosniff" },
      { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
      { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
    ],
  }];
}
```

### Substep 8.2.2: Add stricter `Referrer-Policy` for `/auth/verify`
```typescript
{
  source: "/auth/verify",
  headers: [
    { key: "Referrer-Policy", value: "no-referrer" },
  ],
}
```

---

## 8.3 Security: Clear Magic Link Token from URL
**Issues:** SEC-2, SEC-10
**File:** `client/web/src/components/auth/verify-magic-link.tsx`
**Effort:** 15 min

### Substep 8.3.1: Strip token from URL immediately after reading
After reading token from `searchParams`, call:
```typescript
window.history.replaceState({}, "", "/auth/verify");
```
This removes the token from the URL bar and browser history while the verification request is in flight.

---

## 8.4 Security: Enforce Onboarding Step Ordering Server-Side
**Issues:** SEC-3
**File:** `server/services/selection-committee-identity-service/src/main/java/edu/kpi/fice/identity_service/user/api/controller/ProfileController.java`
**Effort:** 30 min

### Substep 8.4.1: Add guard to DOB endpoint
`PATCH /api/v1/identity/profile/dob` — verify `user.getTermsAcceptedAt() != null` before allowing submission. Return `400 Bad Request` with message if terms not accepted.

### Substep 8.4.2: Add guard to name/profile endpoint
`PATCH /api/v1/identity/profile` — verify `user.getDateOfBirth() != null` before allowing name update. Return `400 Bad Request` if DOB not set.

---

## 8.5 Security: Add Input Length Constraints
**Issues:** SEC-6
**File:** `server/services/selection-committee-identity-service/src/main/java/edu/kpi/fice/identity_service/user/api/dto/ProfileUpdateRequest.java`
**Effort:** 15 min

### Substep 8.5.1: Add `@Size` and `@Pattern` to name fields
```java
@NotNull @NotEmpty @Size(max = 100)
@Pattern(regexp = "^[\\p{L}\\s'-]+$", message = "Name must contain only letters, spaces, hyphens, and apostrophes")
String firstName,

@Size(max = 100)
@Pattern(regexp = "^[\\p{L}\\s'-]*$")
String middleName,

@NotNull @NotEmpty @Size(max = 100)
@Pattern(regexp = "^[\\p{L}\\s'-]+$")
String lastName
```

### Substep 8.5.2: Add matching frontend validation
In `client/web/src/lib/validators/auth.ts`, add `.max(100)` to firstName, lastName, middleName schemas.

---

## 8.6 Security: Disable Swagger in Production
**Issues:** SEC-5
**Files:** `server/services/selection-committee-gateway/src/main/resources/application.yml`, `identity-service/.../SecurityConfig.java`
**Effort:** 15 min

### Substep 8.6.1: Change default to disabled
In gateway `application.yml`, change `${SWAGGER_ENABLED:true}` to `${SWAGGER_ENABLED:false}`.

### Substep 8.6.2: Gate Swagger whitelist paths behind profile
In `SecurityConfig.java`, conditionally include Swagger paths in `WHITE_LIST` only when `swagger.enabled=true` (use `@ConditionalOnProperty` or `@Profile("!prod")`).

---

## 8.7 Accessibility: Step Indicator Semantic Structure
**Issues:** A11Y-1
**File:** `client/web/src/components/onboarding/progress.tsx`
**Effort:** 1 hour

### Substep 8.7.1: Convert desktop stepper to semantic `<ol>`
Replace outer `<div>` with `<ol role="list" aria-label="Процес реєстрації">`. Wrap each step in `<li>`. Add `aria-current="step"` to current step's circle. Add `aria-label` with status text (e.g., "Умови та політика: поточний крок").

### Substep 8.7.2: Add screen-reader-only status text to step circles
```tsx
<div
  className={cn(...)}
  role="img"
  aria-label={`Крок ${stepNum}: ${step.label} — ${isCompleted ? "завершено" : isCurrent ? "поточний" : "очікування"}`}
  aria-current={isCurrent ? "step" : undefined}
>
```

### Substep 8.7.3: Update mobile stepper similarly
Add `role="list"` and `aria-label` to mobile container. Each step circle gets `aria-label` with status.

---

## 8.8 Accessibility: Step Change Announcements
**Issues:** A11Y-2
**File:** `client/web/src/app/(auth)/onboarding/page.tsx`
**Effort:** 30 min

### Substep 8.8.1: Add `aria-live="polite"` announcement region
Add a visually hidden `<div role="status" aria-live="polite" aria-atomic="true">` that updates its text content when `displayedStep` changes.

```tsx
const [announcement, setAnnouncement] = useState("");

useEffect(() => {
  if (currentStep !== displayedStep) {
    setIsTransitioning(true);
    const stepLabel = STEPS.find(s => s.key === currentStep)?.label;
    setAnnouncement(`Перейшли на крок: ${stepLabel}`);
    const timer = setTimeout(() => {
      setDisplayedStep(currentStep);
      setIsTransitioning(false);
    }, 200);
    return () => clearTimeout(timer);
  }
}, [currentStep, displayedStep]);

// In JSX:
<div role="status" aria-live="polite" aria-atomic="true" className="sr-only">
  {announcement}
</div>
```

---

## 8.9 Accessibility: Focus Management on Step Transition
**Issues:** A11Y-3
**File:** `client/web/src/app/(auth)/onboarding/page.tsx`
**Effort:** 30 min

### Substep 8.9.1: Move focus to step heading after transition
After `setDisplayedStep(currentStep)` and `setIsTransitioning(false)`, use `requestAnimationFrame` to find and focus the step heading:

```tsx
requestAnimationFrame(() => {
  const heading = document.querySelector("[data-step-heading]");
  if (heading instanceof HTMLElement) heading.focus();
});
```

### Substep 8.9.2: Add `tabIndex={-1}` and `data-step-heading` to each step's `<h2>`
In `terms-step.tsx`, `dob-step.tsx`, `name-step.tsx`, add `tabIndex={-1}` and `data-step-heading` to the `<h2>` element so it's focusable programmatically but not in the tab order.

---

## 8.10 Accessibility: DOB Form Error Messages
**Issues:** A11Y-4
**File:** `client/web/src/components/onboarding/dob-step.tsx`
**Effort:** 15 min

### Substep 8.10.1: Add `<FormMessage />` to each Select field
Add `<FormMessage />` after each `<FormControl>` for day, month, and year fields — matching the pattern used in `name-step.tsx`.

### Substep 8.10.2: Add `role="alert"` to root error message
```tsx
{form.formState.errors.root && (
  <p className="text-sm text-destructive text-center" role="alert">
    {form.formState.errors.root.message}
  </p>
)}
```

---

## 8.11 Accessibility: Terms Checkbox Disabled State
**Issues:** A11Y-5
**File:** `client/web/src/components/onboarding/terms-step.tsx`
**Effort:** 15 min

### Substep 8.11.1: Add `aria-required` and `aria-describedby` to checkbox
```tsx
<Checkbox
  id="terms-accept"
  checked={accepted}
  disabled={!allViewed}
  onCheckedChange={(checked) => setAccepted(checked === true)}
  aria-required="true"
  aria-describedby={!allViewed ? "terms-help-text" : undefined}
/>
```

### Substep 8.11.2: Add `id` to helper text
```tsx
{!allViewed && (
  <p id="terms-help-text" className="text-xs text-muted-foreground/70">
    Спочатку ознайомтесь з обома документами
  </p>
)}
```

---

## 8.12 Performance: Eliminate Redundant API Call on Step Transition
**Issues:** PERF-1, ARCH-1
**Files:** `client/web/src/providers/auth-provider.tsx`, `client/web/src/app/(auth)/onboarding/page.tsx`, all step components
**Effort:** 1 hour

### Substep 8.12.1: Add `updateUser` method to AuthProvider
Expose a method that directly sets the user state from an already-returned API response, without fetching again:

```typescript
const updateUser = useCallback((userData: User) => {
  onboardingRef.current = userData.onboardingCompleted;
  const payload = accessTokenRef.current ? parseAccessToken(accessTokenRef.current) : null;
  if (payload) setSessionCookie(payload.role, payload.exp, userData.onboardingCompleted);
  setUser(userData);
}, []);
```

### Substep 8.12.2: Pass API response from step components to `onComplete`
Each step component calls `onComplete(updatedUser)` with the user returned from the mutation API:

```typescript
// terms-step.tsx
const { data: updatedUser } = await authApi.acceptTerms();
onComplete(updatedUser);
```

### Substep 8.12.3: Update `handleStepComplete` to use `updateUser`
```typescript
const handleStepComplete = useCallback((updatedUser: User) => {
  updateUser(updatedUser);
}, [updateUser]);
```

### Substep 8.12.4: Add error handling to `refreshUser`
Wrap `refreshUser` in try-catch so network failures are propagated:

```typescript
const refreshUser = useCallback(async () => {
  try {
    const { data: userData } = await authApi.getUser();
    // ... existing logic
  } catch (error) {
    throw error; // Let callers handle
  }
}, []);
```

---

## 8.13 Performance: Wrap `handleStepComplete` in `useCallback`
**Issues:** PERF-2
**File:** `client/web/src/app/(auth)/onboarding/page.tsx:61`
**Effort:** 5 min

After applying 8.12.3, `handleStepComplete` is already wrapped in `useCallback`. If 8.12 is deferred, at minimum wrap the existing function:

```typescript
const handleStepComplete = useCallback(async () => {
  await refreshUser();
}, [refreshUser]);
```

---

## Verification Checklist (Phase 8)
- [ ] `npm run build` succeeds with no errors
- [ ] Magic link flow works: login → email → verify → onboarding → dashboard
- [ ] All 3 onboarding steps complete successfully
- [ ] Screen reader (NVDA): step indicator announces current step and status
- [ ] Screen reader: step change is announced via aria-live region
- [ ] Keyboard-only: focus moves to new step heading after each transition
- [ ] DOB error messages appear per-field when submitting empty form
- [ ] Session cookie has `Secure` flag in HTTPS environment
- [ ] `curl -I` on app shows X-Frame-Options, X-Content-Type-Options headers
- [ ] Token is stripped from URL bar during magic link verification
- [ ] Backend: `PATCH /dob` returns 400 if terms not accepted (via API test)
- [ ] Backend: Profile name fields reject strings > 100 chars
- [ ] Swagger UI returns 401/404 in production profile
