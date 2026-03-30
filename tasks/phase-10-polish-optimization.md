# Phase 10: Polish & Optimization (1–2 days)

Minor UX improvements, performance tuning, and visual polish.

---

## 10.1 UX: Add Email Icon to Check-Email Screen
**Issues:** UX-3
**File:** `client/web/src/components/auth/magic-link-form.tsx`
**Effort:** 15 min

### Substep 10.1.1: Add Mail icon above heading
```tsx
import { Mail } from "lucide-react";

// In the "check email" state render:
<div className="flex justify-center mb-4">
  <div className="rounded-full bg-primary/10 p-4">
    <Mail className="h-8 w-8 text-primary" />
  </div>
</div>
<h2 className="text-xl font-semibold text-center">Перевірте вашу пошту</h2>
```

This reinforces the magic link concept visually (dual-coding theory — 6x better retention).

---

## 10.2 UX: Reduce Dashboard Entrance Animation Duration
**Issues:** UX-4
**File:** `client/web/src/app/(dashboard)/layout.tsx:102`
**Effort:** 5 min

### Substep 10.2.1: Change animation duration from 500ms to 200ms
```tsx
// Change:
className="... animate-in fade-in slide-in-from-bottom-4 duration-500"
// To:
className="... animate-in fade-in slide-in-from-bottom-2 duration-200"
```

Also reduce `slide-in-from-bottom-4` to `slide-in-from-bottom-2` for a subtler entrance.

---

## 10.3 Accessibility: Step Indicator Contrast Fix
**Issues:** A11Y-11
**File:** `client/web/src/components/onboarding/progress.tsx:39`
**Effort:** 5 min

### Substep 10.3.1: Remove `/30` opacity from pending step border
```tsx
// Change:
"border-2 border-muted-foreground/30 text-muted-foreground"
// To:
"border-2 border-muted-foreground/50 text-muted-foreground"
```

Test with a contrast checker to ensure 4.5:1 ratio against the background.

---

## 10.4 Performance: Consider Native `<select>` for Year Dropdown
**Issues:** PERF-7
**File:** `client/web/src/components/onboarding/dob-step.tsx`
**Effort:** 30 min

### Substep 10.4.1: Evaluate replacing Radix Select with native `<select>` for Year
Radix Select renders all 87 year items as DOM nodes. A native `<select>` uses OS-native rendering with zero JS overhead:

```tsx
<select
  value={field.value?.toString() ?? ""}
  onChange={(e) => field.onChange(Number(e.target.value))}
  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 appearance-none"
>
  <option value="" disabled>Рік</option>
  {YEARS.map((y) => (
    <option key={y} value={y}>{y}</option>
  ))}
</select>
```

**Trade-off:** Native select has less visual customization but better mobile UX (OS-native scroll) and zero DOM overhead. Consider applying to all 3 dropdowns for consistency, or just the year dropdown.

### Substep 10.4.2: Alternative — keep Radix Select but optimize
If native select is rejected for visual consistency, no change needed — 87 items is acceptable for a one-time onboarding flow.

---

## 10.5 Architecture: Lazy-Load Terms/Privacy Content
**Issues:** ARCH-6
**File:** `client/web/src/components/onboarding/terms-step.tsx:17-52`
**Effort:** 30 min

### Substep 10.5.1: Move content to separate file
Create `client/web/src/components/onboarding/terms-content.tsx`:
```tsx
export const TERMS_CONTENT = (...);
export const PRIVACY_CONTENT = (...);
```

### Substep 10.5.2: Dynamically import on modal open
```tsx
const [termsContent, setTermsContent] = useState<ReactNode>(null);

async function loadTermsContent() {
  const { TERMS_CONTENT } = await import("./terms-content");
  setTermsContent(TERMS_CONTENT);
}

// On button click:
<button onClick={() => { loadTermsContent(); setActiveModal("terms"); }}>
```

**Note:** This is a minor optimization — the content is small. Only implement if bundle size is a concern.

---

## 10.6 Accessibility: Notification Bell Badge
**Issues:** Related to A11Y findings
**File:** `client/web/src/components/layout/topbar.tsx`
**Effort:** 10 min

### Substep 10.6.1: Add descriptive `aria-label` to bell button
```tsx
<Button
  variant="ghost"
  size="icon"
  aria-label={`Сповіщення${unreadCount > 0 ? `: ${unreadCount} нових` : ""}`}
>
  <Bell className="h-5 w-5" />
</Button>
```

---

## 10.7 UX: Strengthen Primary Color Saturation (Optional)
**Issues:** UI/UX aesthetic assessment
**File:** `client/web/src/app/globals.css`
**Effort:** 1 hour

### Substep 10.7.1: Evaluate stronger CTA color
The current primary (`oklch(0.726 0.126 293)`) renders as soft lavender. Consider increasing saturation for more confident CTAs:

```css
--primary: oklch(0.65 0.18 293); /* deeper purple, more saturated */
```

### Substep 10.7.2: Increase disabled vs enabled contrast
Ensure disabled button state is visually distinct from enabled (currently too close in perceived weight).

**Note:** This is subjective — implement only if the team agrees the current palette feels too muted.

---

## 10.8 Architecture: Middleware Extension Check Improvement
**Issues:** ARCH-5
**File:** `client/web/src/middleware.ts:9`
**Effort:** 10 min

### Substep 10.8.1: Use more specific file extension check
```typescript
// Change:
if (pathname.startsWith("/_next") || pathname.startsWith("/api") || pathname.includes(".")) {
// To:
const hasFileExtension = /\.\w{2,5}$/.test(pathname);
if (pathname.startsWith("/_next") || pathname.startsWith("/api") || hasFileExtension) {
```

This prevents false positives on routes containing dots (e.g., `/applications/2.0-draft`).

---

## 10.9 Security: CSRF Documentation
**Issues:** SEC-7
**Effort:** 15 min

### Substep 10.9.1: Document CSRF architecture decision
Add a section to `docs/` explaining:
- Bearer token auth provides CSRF protection for most endpoints (token not auto-attached by browser)
- Refresh endpoint uses `SameSite=Strict` cookie — prevents cross-site forged requests
- Client-set `__session` cookie is `SameSite=Lax`, UX-only, carries no auth material
- Older browsers without SameSite support are an accepted risk (< 1% of traffic)

---

## 10.10 UX: Mobile Bottom Tab Bar (Future Enhancement)
**Issues:** UX design recommendation
**Effort:** 2–3 hours (if implemented)

### Substep 10.10.1: Evaluate replacing hamburger menu with bottom tabs
For the applicant role with only 4 nav items (Головна, Документи, Заявка, Профіль), a persistent bottom tab bar would reduce navigation friction. Steven Hoober's thumb zone research shows bottom targets are the most reachable.

**Implementation:** Create `BottomNav` component shown on mobile only (`lg:hidden`). Hide sidebar hamburger on mobile. Use the same `NAV_ITEMS` config.

**Note:** This is a future enhancement, not a bug fix. Implement only after Phase 8–9 are complete.

---

## Verification Checklist (Phase 10)
- [ ] Check-email screen shows mail icon above heading
- [ ] Dashboard page transitions feel snappier (200ms vs 500ms)
- [ ] Pending step indicators pass 4.5:1 contrast ratio check
- [ ] Year dropdown renders efficiently (if native select applied)
- [ ] Notification bell announces count to screen readers
- [ ] Lighthouse accessibility score ≥ 90
- [ ] Lighthouse performance score ≥ 85
- [ ] All Playwright tests pass
- [ ] Manual walkthrough: login → onboarding → dashboard (desktop + mobile)
