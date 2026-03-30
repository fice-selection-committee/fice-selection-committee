# Phase 7: Polish, Animations & Playwright Verification

## Objective
Final polish pass: ensure all animations are smooth, dark mode is consistent, responsive breakpoints work, and verify the complete flow with Playwright E2E tests.

**Depends on**: Phases 1-6 complete

---

## Step 7.1: Animation Polish

### Entrance Animations Audit
Verify each page has staggered entrance:

| Page | Group 1 (0ms) | Group 2 (80ms) | Group 3 (160ms) |
|------|--------------|----------------|-----------------|
| Homepage | Mascot | Heading + subtitle | CTA + helper text |
| Login (email) | Heading + subtitle | Form (input + button) | Helper text |
| Login (check-email) | Mail icon | Heading + email chip | Actions |
| Onboarding steps | Heading + subtitle | Form content | CTA button |

All animations use `stagger-in` keyframe (500ms, ease-out) with `motion-reduce:animate-none`.

### Transition Quality Check
- Step transitions: 250ms slide (24px translateX), ease-out
- Progress bar fill: 500ms, ease-out
- Button hover: 150ms scale(1.02)
- Button active: 100ms scale(0.98)
- Mascot float: 3s gentle-float loop, ease-in-out
- Check-email pulse: 2s pulse-ring loop

### Known Edge Cases to Test
- [ ] Rapid step completion (terms → DOB → name in quick succession): transitions should queue, not overlap
- [ ] Browser back button during onboarding: should not break layout
- [ ] Page refresh during onboarding: should restore correct step
- [ ] Theme toggle during animation: should not cause flash or broken state

---

## Step 7.2: Dark Mode Consistency

### Checklist
- [ ] Homepage hero-bg dark variant renders correctly (darker gradient, purple tints)
- [ ] Left branding panel: mascot image has sufficient contrast against hero-bg
- [ ] All form inputs have visible borders in dark mode (`--input` token)
- [ ] Progress bar track visible against dark background (`bg-muted`)
- [ ] Sticky CTA backdrop blur works in dark mode (`bg-background/80 backdrop-blur-lg`)
- [ ] Document rows (terms step): border visible in dark mode
- [ ] Check-email pulse ring visible in dark mode
- [ ] Email chip (`bg-primary/10`): text readable in dark mode
- [ ] Ghost buttons: sufficient contrast in dark mode
- [ ] Success checkmark (welcome screen): green circle visible on dark background

### Specific Dark Mode Values to Verify
```
--surface-elevated (dark): oklch(0.24 0.018 285) → should be distinguishable from --background
--primary-subtle (dark): oklch(0.25 0.04 293) → should have visible purple tint
--primary-glow (dark): oklch(0.726 0.126 293 / 20%) → should be visible but subtle
```

---

## Step 7.3: Responsive Breakpoint Testing

### Viewports to Test

| Viewport | Width | Key Behavior |
|----------|-------|-------------|
| iPhone SE | 375px | Smallest supported mobile |
| iPhone 14 | 390px | Standard mobile |
| iPhone 14 Pro Max | 430px | Large mobile |
| iPad Mini | 768px | Tablet (still mobile layout) |
| iPad Pro | 1024px | Breakpoint — split-panel appears |
| Laptop | 1280px | Standard desktop |
| Desktop | 1440px | Wide desktop |
| Ultrawide | 1920px | Ensure left panel doesn't grow too wide |

### Breakpoint Behavior
- **< 1024px (lg)**: Full-width layout, mobile header, no left panel, sticky bottom CTA
- **>= 1024px (lg)**: Split-panel (55/45), left branding panel visible, CTA in normal flow

### Specific Layout Checks
- [ ] 375px: No horizontal overflow on any page
- [ ] 375px: CTA button has minimum 16px horizontal padding
- [ ] 375px: Select dropdowns don't overflow on DOB step
- [ ] 768px: Content doesn't float too high (comfortable vertical centering)
- [ ] 1024px: Clean transition to split-panel (no layout jump)
- [ ] 1440px: Right panel content doesn't stretch beyond 420px
- [ ] 1920px: Left panel doesn't exceed ~55% width

---

## Step 7.4: Performance Check

### Metrics to Monitor
- **LCP (Largest Contentful Paint)**: Homepage hero image / mascot should load quickly
  - Mascot is loaded with `unoptimized` and is relatively small (120-160px display size)
  - Consider adding `priority` prop to the mascot Image on homepage
- **CLS (Cumulative Layout Shift)**:
  - Sticky CTA must not cause layout shift
  - Loading skeletons must match final content dimensions
  - Font loading (`display: swap`) may cause shift — SF Pro local font should prevent this
- **FID (First Input Delay)**:
  - Homepage should be interactive immediately (minimal JS)
  - Auth pages: form should be interactive before entrance animation completes

### Actions
- Add `priority` to homepage mascot Image component
- Verify skeleton dimensions match final rendered content
- Test with Chrome DevTools Performance tab → Core Web Vitals

---

## Step 7.5: Playwright E2E Tests

### Test Structure
Create/update Playwright tests for the redesigned pages:

#### Test: Homepage
```typescript
test("homepage renders hero and navigates to login", async ({ page }) => {
  await page.goto("/");

  // Verify key elements
  await expect(page.getByRole("heading", { level: 2 })).toContainText("Вступ до ФІОТ");
  await expect(page.getByRole("link", { name: "Розпочати" })).toBeVisible();

  // Navigate to login
  await page.getByRole("link", { name: "Розпочати" }).click();
  await expect(page).toHaveURL("/login");
});

test("homepage dark mode toggle works", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: /тему/ }).click();
  await expect(page.locator("html")).toHaveClass(/dark/);
});

test("homepage responsive — no horizontal scroll on mobile", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto("/");

  const body = page.locator("body");
  const scrollWidth = await body.evaluate((el) => el.scrollWidth);
  const clientWidth = await body.evaluate((el) => el.clientWidth);
  expect(scrollWidth).toBeLessThanOrEqual(clientWidth);
});
```

#### Test: Login Flow
```typescript
test("login page shows email form", async ({ page }) => {
  await page.goto("/login");

  await expect(page.getByRole("heading")).toContainText("Вхід до системи");
  await expect(page.getByLabel("Електронна пошта")).toBeVisible();
  await expect(page.getByRole("button", { name: "Отримати посилання" })).toBeVisible();
});

test("login page — email input is 48px tall and prevents iOS zoom", async ({ page }) => {
  await page.goto("/login");

  const input = page.getByLabel("Електронна пошта");
  const fontSize = await input.evaluate((el) => window.getComputedStyle(el).fontSize);
  expect(parseInt(fontSize)).toBeGreaterThanOrEqual(16);
});
```

#### Test: Onboarding Flow (requires auth)
```typescript
test("onboarding flow — terms → DOB → name → welcome", async ({ authenticatedPage }) => {
  // Assumes test fixture with authenticated user needing onboarding
  await authenticatedPage.goto("/onboarding");

  // Step 1: Terms
  await expect(authenticatedPage.getByRole("heading")).toContainText("Умови та політика");
  // View terms + privacy
  await authenticatedPage.getByText("Умови користування").click();
  // ... close modal
  await authenticatedPage.getByText("Політика конфіденційності").click();
  // ... close modal
  // Accept
  await authenticatedPage.getByRole("checkbox").check();
  await authenticatedPage.getByRole("button", { name: "Далі" }).click();

  // Step 2: DOB
  await expect(authenticatedPage.getByRole("heading")).toContainText("Коли ви народились");
  // Select date
  // ...

  // Step 3: Name
  await expect(authenticatedPage.getByRole("heading")).toContainText("Як вас звати");
  // Fill name
  // ...

  // Step 4: Welcome
  await expect(authenticatedPage.getByRole("heading")).toContainText("Ласкаво просимо");
  await expect(authenticatedPage.getByRole("button", { name: "Перейти зараз" })).toBeVisible();
});
```

#### Test: Accessibility
```typescript
test("homepage passes axe-core", async ({ page }) => {
  await page.goto("/");
  const accessibilityScanResults = await new AxeBuilder({ page }).analyze();
  expect(accessibilityScanResults.violations).toEqual([]);
});

test("login page passes axe-core", async ({ page }) => {
  await page.goto("/login");
  const accessibilityScanResults = await new AxeBuilder({ page }).analyze();
  expect(accessibilityScanResults.violations).toEqual([]);
});
```

#### Test: Visual Regression (Optional)
```typescript
test("homepage visual snapshot — desktop", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/");
  // Wait for entrance animations to complete
  await page.waitForTimeout(600);
  await expect(page).toHaveScreenshot("homepage-desktop.png");
});

test("homepage visual snapshot — mobile", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto("/");
  await page.waitForTimeout(600);
  await expect(page).toHaveScreenshot("homepage-mobile.png");
});
```

---

## Step 7.6: Final Cross-Browser Check

### Browsers to Test
- Chrome (latest) — primary
- Safari (latest) — iOS and macOS
- Firefox (latest) — secondary
- Edge (latest) — secondary

### Safari-Specific Concerns
- `backdrop-filter: blur()` may not work with `-webkit-` prefix missing
- `env(safe-area-inset-bottom)` requires `viewport-fit=cover` meta tag
- OKLch colors: Safari 15.4+ supports them; verify no fallback needed for older versions
- `overscroll-behavior: none` may not work on Safari < 16

### Firefox-Specific Concerns
- `@property` CSS (for animated gradients) not supported in older Firefox
- Verify progress bar transition works

---

## Verification Checklist (Final)
- [ ] All entrance animations play smoothly (no jank)
- [ ] Dark mode: every page element is properly themed
- [ ] 375px mobile: no horizontal scroll, CTAs reachable
- [ ] 1440px desktop: split-panel renders, content centered
- [ ] Playwright: homepage E2E test passes
- [ ] Playwright: login flow E2E test passes
- [ ] Playwright: onboarding E2E test passes (if auth fixture available)
- [ ] Playwright: axe-core accessibility scan passes (0 violations)
- [ ] Performance: LCP < 2.5s, CLS < 0.1
- [ ] Cross-browser: Chrome, Safari, Firefox verified
- [ ] Reduced motion: all animations suppressed
- [ ] No console errors on any page
- [ ] Screenshot comparison: mobile and desktop look as designed
