# Phase 1: Foundation — CSS Tokens, Animations, Typography, Safe Areas

## Objective
Establish the design system foundation that all subsequent phases build upon. No visual changes to pages yet — only CSS and config updates.

---

## Step 1.1: Extended Color Tokens

### File: `client/web/src/app/globals.css`

Add new surface/glow tokens to `:root` and `.dark` blocks:

```css
:root {
  /* Existing tokens stay unchanged */

  /* NEW: Extended surface tokens */
  --surface-elevated: oklch(0.97 0.008 293);
  --surface-sunken: oklch(0.965 0.01 293);
  --primary-subtle: oklch(0.95 0.03 293);
  --primary-glow: oklch(0.726 0.126 293 / 15%);
}

.dark {
  /* Existing tokens stay unchanged */

  /* NEW: Extended surface tokens */
  --surface-elevated: oklch(0.24 0.018 285);
  --surface-sunken: oklch(0.16 0.012 285);
  --primary-subtle: oklch(0.25 0.04 293);
  --primary-glow: oklch(0.726 0.126 293 / 20%);
}
```

Register in the `@theme inline` block:
```css
--color-surface-elevated: var(--surface-elevated);
--color-surface-sunken: var(--surface-sunken);
--color-primary-subtle: var(--primary-subtle);
--color-primary-glow: var(--primary-glow);
```

### Verification
- `bg-surface-elevated`, `bg-primary-subtle`, `bg-primary-glow` should work as Tailwind classes
- Both light and dark mode must render distinct surfaces

---

## Step 1.2: New Keyframe Animations

### File: `client/web/src/app/globals.css`

Add below existing `@keyframes` blocks:

```css
@keyframes slide-in-right {
  from {
    opacity: 0;
    transform: translateX(24px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@keyframes slide-out-left {
  from {
    opacity: 1;
    transform: translateX(0);
  }
  to {
    opacity: 0;
    transform: translateX(-24px);
  }
}

@keyframes stagger-in {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes gentle-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}

@keyframes pulse-ring {
  0% {
    box-shadow: 0 0 0 0 oklch(0.726 0.126 293 / 40%);
  }
  70% {
    box-shadow: 0 0 0 12px oklch(0.726 0.126 293 / 0%);
  }
  100% {
    box-shadow: 0 0 0 0 oklch(0.726 0.126 293 / 0%);
  }
}

@keyframes progress-fill {
  from { width: var(--progress-from, 0%); }
  to { width: var(--progress-to, 100%); }
}
```

All animations are already covered by the existing `prefers-reduced-motion: reduce` blanket override in globals.css — no additional a11y work needed here.

---

## Step 1.3: Typography Scale Updates

### File: `client/web/src/app/globals.css`

Update the `@layer base` block:

```css
@layer base {
  * {
    @apply border-border outline-ring/50;
  }
  body {
    @apply bg-background text-foreground;
    -webkit-font-smoothing: antialiased;
    -moz-osx-font-smoothing: grayscale;
    letter-spacing: 0.01em;
  }
  h1, h2, h3, h4 {
    letter-spacing: -0.015em;
  }
  /* NEW: Prevent iOS zoom on form inputs */
  input, select, textarea {
    font-size: 16px;
  }
  /* NEW: Disable overscroll for native feel */
  html {
    overscroll-behavior: none;
  }
  /* NEW: Tap highlight removal for native feel */
  button, [role="button"], a {
    -webkit-tap-highlight-color: transparent;
  }
}
```

### Typography Reference (for use in subsequent phases)

| Element | Mobile | Desktop | Weight | Tracking |
|---------|--------|---------|--------|----------|
| Hero heading | text-3xl (30px) | text-5xl (48px) | font-bold (700) | tracking-tight |
| Step heading | text-2xl (24px) | text-2xl (24px) | font-bold (700) | tracking-tight |
| Body text | text-base (16px) | text-base (16px) | font-normal (400) | default |
| Supporting text | text-sm (14px) | text-sm (14px) | font-normal (400) | default |
| Labels | text-sm (14px) | text-sm (14px) | font-medium (500) | default |
| Legal/footnote | text-xs (12px) | text-xs (12px) | font-normal (400) | default |

---

## Step 1.4: Safe Area & Viewport Meta

### File: `client/web/src/app/layout.tsx`

Ensure the viewport meta includes `viewport-fit=cover` for notched devices:

```tsx
export const metadata: Metadata = {
  // existing metadata...
  other: {
    "viewport": "width=device-width, initial-scale=1, viewport-fit=cover",
    "apple-mobile-web-app-capable": "yes",
    "apple-mobile-web-app-status-bar-style": "default",
  },
};
```

**Note**: Check Next.js 16 API for `viewport` export. If separate `viewport` export exists, use that instead.

### File: `client/web/src/app/globals.css`

Add safe area padding support (utility classes for use in components):

```css
@layer utilities {
  .safe-bottom {
    padding-bottom: env(safe-area-inset-bottom, 0px);
  }
  .safe-top {
    padding-top: env(safe-area-inset-top, 0px);
  }
}
```

---

## Step 1.5: Hero Background CSS Classes

### File: `client/web/src/app/globals.css`

Add the hero/branding panel backgrounds:

```css
.hero-bg {
  background:
    repeating-linear-gradient(
      90deg,
      oklch(0.726 0.126 293 / 5%) 0px,
      oklch(0.726 0.126 293 / 5%) 1px,
      transparent 1px,
      transparent 60px
    ),
    repeating-linear-gradient(
      0deg,
      oklch(0.726 0.126 293 / 5%) 0px,
      oklch(0.726 0.126 293 / 5%) 1px,
      transparent 1px,
      transparent 60px
    ),
    radial-gradient(
      ellipse at 30% 50%,
      oklch(0.726 0.126 293 / 15%) 0%,
      transparent 70%
    ),
    linear-gradient(
      135deg,
      oklch(0.95 0.03 293) 0%,
      oklch(0.97 0.015 280) 50%,
      oklch(0.98 0.008 300) 100%
    );
}

.dark .hero-bg {
  background:
    repeating-linear-gradient(
      90deg,
      oklch(0.726 0.126 293 / 8%) 0px,
      oklch(0.726 0.126 293 / 8%) 1px,
      transparent 1px,
      transparent 60px
    ),
    repeating-linear-gradient(
      0deg,
      oklch(0.726 0.126 293 / 8%) 0px,
      oklch(0.726 0.126 293 / 8%) 1px,
      transparent 1px,
      transparent 60px
    ),
    radial-gradient(
      ellipse at 30% 50%,
      oklch(0.726 0.126 293 / 20%) 0%,
      transparent 70%
    ),
    linear-gradient(
      135deg,
      oklch(0.2 0.03 293) 0%,
      oklch(0.18 0.02 285) 50%,
      oklch(0.16 0.015 290) 100%
    );
}
```

---

## Step 1.6: Component Size Standards

### Reference for subsequent phases (no changes yet)

| Component | Height | Radius | Font |
|-----------|--------|--------|------|
| Primary CTA button | h-12 (48px) | rounded-xl (12px) | text-base font-semibold |
| Secondary button | h-11 (44px) | rounded-xl | text-sm font-medium |
| Ghost/tertiary button | h-10 (40px) | rounded-lg | text-sm |
| Text input | h-12 (48px) | rounded-xl | text-base |
| Select trigger | h-12 (48px) | rounded-xl | text-base |
| Touch target minimum | min-h-11 min-w-11 (44px) | — | — |

---

## Verification Checklist
- [ ] New Tailwind color classes work: `bg-surface-elevated`, `bg-primary-subtle`, `bg-primary-glow`
- [ ] Both light and dark mode render correctly with new tokens
- [ ] New keyframe animations are defined and accessible via `animate-[name_...]` syntax
- [ ] `prefers-reduced-motion` still suppresses all animations
- [ ] Input font-size is 16px (verify no iOS zoom on focus)
- [ ] `overscroll-behavior: none` active on `<html>`
- [ ] Safe area env() values applied correctly on iOS simulator
- [ ] Hero background renders in both light and dark mode
- [ ] No existing UI breaks from foundation changes
