# Phase 6: Accessibility Fixes & WCAG 2.1 AA Compliance

## Objective
Address all accessibility issues identified in the audit, ensure redesigned pages meet WCAG 2.1 Level AA, and add proper a11y patterns for new design elements (animations, sticky CTAs, progress bars, etc.).

**Depends on**: Phases 1-5 (most a11y fixes are integrated into those phases; this phase covers what remains)

---

## Current Accessibility Strengths (Already Good)
- Semantic HTML with proper heading hierarchy
- ARIA live regions for onboarding step transitions
- `aria-current="step"` on progress indicators
- `prefers-reduced-motion: reduce` blanket override in globals.css
- Form accessibility via React Hook Form + Zod
- Radix UI components with proper ARIA

---

## Critical Issues (Must Fix)

### Issue 1: Welcome Screen Auto-Redirect Without User Control
**WCAG**: 2.2.1 (Timing Adjustable)
**File**: `client/web/src/components/onboarding/welcome-screen.tsx`
**Status**: Fixed in Phase 5, Step 5.6

**Solution** (already in Phase 5):
- Added manual "Перейти зараз" button
- Extended timer to 5s for `prefers-reduced-motion` users
- Default timer remains 2.5s for standard users

### Issue 2: Decorative Images Not Properly Marked
**WCAG**: 1.1.1 (Non-text Content)
**Files**: Auth layout, welcome screen, homepage

**Solution** (already in Phases 2, 3, 5):
- All mascot images use `alt=""` + `aria-hidden="true"`
- Only decorative images in auth/onboarding flow

---

## Major Issues

### Issue 3: Focus Management Timing After Step Transition
**WCAG**: 2.4.3 (Focus Order)
**File**: `client/web/src/app/(auth)/onboarding/page.tsx`

**Current code** (line 46-49):
```tsx
requestAnimationFrame(() => {
  const heading = document.querySelector("[data-step-heading]");
  if (heading instanceof HTMLElement) heading.focus();
});
```

**Problem**: `requestAnimationFrame` fires before the new step component is rendered — the heading may not exist yet.

**Fix**: Use a double `requestAnimationFrame` or a `setTimeout(0)` to ensure the DOM has updated:

```tsx
// After setDisplayedStep and setIsTransitioning:
requestAnimationFrame(() => {
  requestAnimationFrame(() => {
    const heading = document.querySelector("[data-step-heading]");
    if (heading instanceof HTMLElement) heading.focus();
  });
});
```

### Issue 4: Loading Button Spinner Has No Text Alternative
**WCAG**: 1.3.1 (Info and Relationships)
**File**: `client/web/src/components/ui/loading-button.tsx`

**Current**: When loading, the button text becomes `invisible` and only a spinner shows. The spinner has `aria-hidden="true"` but `aria-busy="true"` is set on the button.

**Problem**: `aria-busy` tells screen readers the element is updating, but doesn't describe the current state.

**Fix**: Add a visually hidden loading text:

```tsx
{loading && (
  <>
    <span
      className="absolute inset-0 flex items-center justify-center animate-[fade-in_150ms_ease-out_both]"
      aria-hidden="true"
    >
      <AppleSpinner className="size-5" />
    </span>
    <span className="sr-only">Завантаження...</span>
  </>
)}
```

### Issue 5: Select Dropdowns Missing Accessible Description
**WCAG**: 3.3.2 (Labels or Instructions)
**File**: `client/web/src/components/onboarding/dob-step.tsx`

**Current**: Each select has a `FormLabel` but no additional instruction about expected format.

**Fix**: The placeholder text in `SelectValue` ("День", "Місяць", "Рік") serves as sufficient instruction alongside the form label. No change needed — current implementation is adequate.

### Issue 6: Terms Modal Missing Focus Trap Announcement
**WCAG**: 1.3.1 (Info and Relationships)
**File**: `client/web/src/components/onboarding/terms-step.tsx`

**Current**: Uses `ResponsiveModal` which is built on Radix Dialog — Radix handles focus trap and role="dialog" automatically.

**Status**: Already compliant via Radix UI. No change needed.

### Issue 7: Checkbox Enable State Not Announced
**WCAG**: 3.3.1 (Error Identification)
**File**: `client/web/src/components/onboarding/terms-step.tsx`

**Current**: When both documents are viewed, the checkbox becomes enabled. The `aria-describedby` hint disappears but no announcement happens.

**Fix**: Add a live region announcement when the checkbox becomes enabled:

```tsx
// Add state:
const [enableAnnouncement, setEnableAnnouncement] = useState("");

// In effect watching allViewed:
useEffect(() => {
  if (allViewed) {
    setEnableAnnouncement("Тепер ви можете прийняти умови");
  }
}, [allViewed]);

// In JSX:
<div role="status" aria-live="polite" className="sr-only">
  {enableAnnouncement}
</div>
```

### Issue 8: Form Validation Errors Not Re-Announced
**WCAG**: 3.3.1 (Error Identification)
**File**: `client/web/src/components/onboarding/dob-step.tsx`

**Current**: Error message has `role="alert"` which announces once, but doesn't re-announce if the same error occurs.

**Fix**: The `role="alert"` will re-announce when the content changes. If the same error text appears again, wrap it with a timestamp or counter to force re-announcement:

```tsx
{form.formState.errors.root && (
  <p className="text-sm text-destructive text-center" role="alert" key={Date.now()}>
    {form.formState.errors.root.message}
  </p>
)}
```

**Note**: Using `key={Date.now()}` forces React to remount the element, triggering a new announcement. Use sparingly — only on form submission errors.

---

## Redesign-Specific Accessibility Requirements

### Animated Background (hero-bg)
- **Requirement**: WCAG 2.3.1 (Three Flashes or Below Threshold)
- **Status**: The CSS gradient background is static (no flashing). The geometric grid overlay is subtle and non-animated. COMPLIANT.
- **If adding animation**: Must not flash more than 3 times per second. The `@property --hue-shift` animated gradient (if used) shifts at 20s cycle — well under the threshold.

### Sticky Bottom CTA
- **Requirement**: WCAG 2.4.3 (Focus Order)
- **Status**: `position: sticky` keeps the button in DOM order. Keyboard focus reaches it naturally after form fields. COMPLIANT.
- **Requirement**: Button must not obscure content when keyboard is open on mobile.
- **Fix**: On mobile, the sticky CTA should have `safe-bottom` padding (env(safe-area-inset-bottom)).

### Page Transition Animations
- **Requirement**: WCAG 2.3.3 (Animation from Interactions)
- **Status**: All transitions use `motion-reduce:transition-none` or `motion-reduce:animate-none`. COMPLIANT.

### Progress Bar (Mobile)
- **Requirement**: WCAG 1.3.1 (Info and Relationships), 4.1.2 (Name, Role, Value)
- **Status**: Uses `role="progressbar"` with `aria-valuenow`, `aria-valuemin`, `aria-valuemax`, and `aria-label`. COMPLIANT.

### Split-Panel Layout
- **Requirement**: WCAG 1.3.2 (Meaningful Sequence)
- **Status**: Left panel is decorative (hidden on mobile via `hidden lg:flex`). Right panel contains all meaningful content. Reading/focus order follows the right panel. COMPLIANT.

---

## Color Contrast Verification

### OKLch Tokens — Estimated Contrast Ratios

| Pair | Light Mode | Dark Mode | Requirement | Status |
|------|-----------|-----------|-------------|--------|
| foreground on background | ~17:1 | ~16:1 | 4.5:1 (AA) | PASS |
| muted-foreground on background | ~5.5:1 | ~5.2:1 | 4.5:1 (AA) | PASS |
| primary on white (buttons) | ~4.7:1 | N/A | 3:1 (large text) | PASS |
| primary-foreground on primary | ~6.5:1 | ~5.8:1 | 4.5:1 (AA) | PASS |
| muted-foreground/60 on background | ~3.2:1 | ~3.0:1 | 3:1 (large) | BORDERLINE |
| muted-foreground/70 on background | ~3.8:1 | ~3.5:1 | 4.5:1 (AA) | FAIL for small text |

### Action Required
- The `text-muted-foreground/60` used on step labels and footer text is borderline for small text (12-14px). Use `text-muted-foreground/70` minimum, or `text-muted-foreground` for anything that conveys information.
- All `text-xs text-muted-foreground/60` instances should be bumped to at least `text-muted-foreground/70`.
- Use browser dev tools or axe-core to verify exact contrast ratios with the OKLch values.

---

## Touch Target Audit

| Element | Current Size | Required | Action |
|---------|-------------|----------|--------|
| Theme toggle | ~36px (icon button) | 44px min | Fixed in Phase 2: min-h-11 min-w-11 |
| Mobile progress circles | 28px (h-7 w-7) | 44px min | Fixed in Phase 5: replaced with progress bar (not interactive) |
| Document rows (terms) | ~48px | 44px min | Fixed in Phase 5: min-h-[64px] |
| Checkbox | ~20px (shadcn default) | 24px min | Acceptable — checkbox has clickable label expanding target area |
| Primary CTA buttons | 48px (h-12) | 44px min | PASS |
| Secondary buttons | 44px (h-11) | 44px min | PASS |
| Ghost buttons | 40px (h-10) | 24px min (AA) | PASS |

---

## Verification Checklist
- [ ] axe-core: 0 critical/serious violations on homepage
- [ ] axe-core: 0 critical/serious violations on login page
- [ ] axe-core: 0 critical/serious violations on each onboarding step
- [ ] Keyboard: complete login flow with Tab/Enter only
- [ ] Keyboard: complete onboarding flow with Tab/Enter only
- [ ] Screen reader (NVDA or VoiceOver): step transitions announced
- [ ] Screen reader: form errors announced
- [ ] Screen reader: loading states announced
- [ ] Screen reader: welcome screen has manual continue option
- [ ] Reduced motion: no visible animations
- [ ] iOS Safari: no input zoom (text is 16px)
- [ ] iOS Safari: safe area padding on sticky CTA
- [ ] Contrast: all text meets AA ratio (4.5:1 normal, 3:1 large)
- [ ] Touch targets: all interactive elements >= 44px on mobile
