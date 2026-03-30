# Phase 2: Auth Layout — Split-Panel Desktop, Mobile Full-Screen

## Objective
Replace the current centered-column auth layout with a split-panel design: branded left panel on desktop, full-screen mobile experience. This is the highest-impact single change — it transforms every auth and onboarding screen.

**Depends on**: Phase 1 (foundation CSS tokens, hero-bg class)

---

## Current State

### File: `client/web/src/app/(auth)/layout.tsx`
- Centered flex column with `max-w-2xl`
- Logo (frog-logo.png 40x40) + "ФІОТ" text + "Приймальна комісія" subtitle
- ThemeToggle absolute-positioned top-right
- `pt-[12vh] sm:pt-[15vh]` pushes content down

**Problem**: On desktop, a narrow column floating in white space. Mobile is acceptable but feels empty.

---

## Step 2.1: Auth Layout — Desktop Split Panel

### File: `client/web/src/app/(auth)/layout.tsx`

Replace the entire layout with:

```tsx
import Image from "next/image";
import type { ReactNode } from "react";
import { ThemeToggle } from "@/components/auth/theme-toggle";

export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen">
      {/* Left branding panel — desktop only */}
      <div className="hidden lg:flex lg:w-[55%] hero-bg relative flex-col items-center justify-center p-12">
        {/* Frog mascot — large */}
        <div className="flex flex-col items-center gap-6">
          <Image
            src="/frog-logo.png"
            alt=""
            width={160}
            height={160}
            className="rounded-3xl"
            aria-hidden="true"
            unoptimized
          />
          <div className="text-center">
            <h1 className="text-4xl font-bold tracking-tight text-foreground">ФІОТ</h1>
            <p className="mt-2 text-lg text-muted-foreground">Приймальна комісія</p>
          </div>
        </div>
        {/* Footer in left panel */}
        <p className="absolute bottom-8 text-xs text-muted-foreground/60">
          КПІ ім. Ігоря Сікорського
        </p>
      </div>

      {/* Right form panel */}
      <div className="flex flex-1 flex-col min-h-screen lg:min-h-0">
        {/* Mobile header */}
        <header className="flex items-center justify-between p-4 lg:hidden">
          <div className="flex items-center gap-2">
            <Image
              src="/frog-logo.png"
              alt=""
              width={32}
              height={32}
              className="rounded-lg"
              aria-hidden="true"
              unoptimized
            />
            <span className="text-lg font-semibold text-primary">ФІОТ</span>
          </div>
          <ThemeToggle />
        </header>

        {/* Desktop theme toggle */}
        <div className="hidden lg:flex justify-end p-4">
          <ThemeToggle />
        </div>

        {/* Form content — centered */}
        <main className="flex flex-1 flex-col items-center justify-center px-5 pb-6 lg:px-12">
          <div className="w-full max-w-[420px]">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
```

### Key Changes
1. **Desktop**: 55/45 split. Left has `hero-bg` class (from Phase 1), large mascot (160px), university name, footer
2. **Mobile**: Full-width right panel only. Compact header with small logo (32px) + theme toggle
3. **Theme toggle**: No longer absolute-positioned. In mobile header row / desktop top-right
4. **Content area**: `max-w-[420px]` for focused forms, vertically centered with `flex-1 justify-center`
5. **Image accessibility**: `alt=""` + `aria-hidden="true"` for decorative mascot images

---

## Step 2.2: Theme Toggle — Touch Target Fix

### File: `client/web/src/components/auth/theme-toggle.tsx`

Update to remove absolute positioning and ensure 44px touch target:

```tsx
"use client";

import { Moon, Sun } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useThemeStore } from "@/stores/theme-store";

export function ThemeToggle() {
  const { theme, toggle } = useThemeStore();

  return (
    <Button
      variant="ghost"
      size="icon"
      onClick={toggle}
      className="min-h-11 min-w-11"
      aria-label={theme === "dark" ? "Увімкнути світлу тему" : "Увімкнути темну тему"}
    >
      {theme === "dark" ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}
    </Button>
  );
}
```

### Changes
- Removed `absolute right-4 top-4` — positioning now handled by parent layout
- Added `min-h-11 min-w-11` — ensures 44px minimum touch target (WCAG 2.5.8)
- Improved `aria-label` — describes the action, not current state

---

## Step 2.3: Left Panel — Onboarding Step Illustration Support

### File: `client/web/src/app/(auth)/layout.tsx`

The left panel should eventually show step-specific illustrations. For now, add a data attribute or context that child pages can use to control the left panel content.

**Option A (Simple — Phase 2)**: Static branding only. Step illustrations added in Phase 5.

**Option B (Extensible)**: Create a `BrandingPanel` client component that accepts an `illustration` prop via context:

```tsx
// client/web/src/components/auth/branding-panel.tsx
"use client";

import Image from "next/image";

interface BrandingPanelProps {
  illustration?: "shield" | "calendar" | "person" | "celebration";
}

export function BrandingPanel({ illustration }: BrandingPanelProps) {
  return (
    <div className="hidden lg:flex lg:w-[55%] hero-bg relative flex-col items-center justify-center p-12">
      <div className="flex flex-col items-center gap-6">
        <Image
          src="/frog-logo.png"
          alt=""
          width={160}
          height={160}
          className="rounded-3xl animate-[gentle-float_3s_ease-in-out_infinite] motion-reduce:animate-none"
          aria-hidden="true"
          unoptimized
        />
        <div className="text-center">
          <h1 className="text-4xl font-bold tracking-tight text-foreground">ФІОТ</h1>
          <p className="mt-2 text-lg text-muted-foreground">Приймальна комісія</p>
        </div>
      </div>
      <p className="absolute bottom-8 text-xs text-muted-foreground/60">
        КПІ ім. Ігоря Сікорського
      </p>
    </div>
  );
}
```

**Recommendation**: Start with Option A (static), refactor to Option B in Phase 5 if step illustrations are added.

---

## Step 2.4: Verify Mobile Responsiveness

### Mobile Layout Structure (< 1024px)
```
┌─────────────────────────────┐
│ [Logo 32px] ФІОТ    [Theme]│  ← header, 56px
├─────────────────────────────┤
│                             │
│        {children}           │  ← flex-1, centered
│     max-w-[420px]           │
│                             │
└─────────────────────────────┘
```

### Desktop Layout Structure (>= 1024px)
```
┌──────────────────────┬──────────────────┐
│                      │          [Theme] │
│    hero-bg           │                  │
│                      │    {children}    │
│  [Mascot 160px]      │  max-w-[420px]   │
│    ФІОТ              │                  │
│  Приймальна комісія  │                  │
│                      │                  │
│    © КПІ             │                  │
└──────────────────────┴──────────────────┘
      55%                    45%
```

---

## Verification Checklist
- [ ] Desktop (1440px): Left panel with hero-bg visible, right panel centered form
- [ ] Tablet (768px): Full-width, mobile header, no left panel
- [ ] Mobile (375px): Compact header, full-width form, no horizontal scroll
- [ ] Dark mode: hero-bg renders correctly in dark variant
- [ ] Theme toggle: 44px touch target on all viewports
- [ ] Theme toggle: Correct aria-label that describes action
- [ ] No layout shift when navigating between login and onboarding
- [ ] Logo image not announcing to screen readers (decorative)
- [ ] Keyboard navigation: Tab order is logical (theme toggle → form fields)
- [ ] Safe area padding works on notched devices (iOS Safari)
