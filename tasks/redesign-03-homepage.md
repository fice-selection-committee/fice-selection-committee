# Phase 3: Homepage Redesign — Hero, CTA, Branding

## Objective
Transform the homepage from 3 lines of centered text into a premium landing page that communicates institutional credibility in < 50ms (Lindgaard et al., 2006). Keep only existing functionality: entrance to login.

**Depends on**: Phase 1 (CSS tokens, animations), Phase 2 (not directly, but homepage has its own layout)

---

## Current State

### File: `client/web/src/app/page.tsx`
```tsx
// 18 lines total — h1, p, and a Link to /login
<main className="flex min-h-screen flex-col items-center justify-center gap-6 p-8">
  <h1>Приймальна комісія ФІОТ</h1>
  <p>Система електронного вступу...</p>
  <Link href="/login">Увійти</Link>
</main>
```

**Problem**: Empty on desktop. No visual identity. No trust signals. 50ms impression = "unfinished."

---

## Step 3.1: Homepage Layout Structure

### File: `client/web/src/app/page.tsx`

The homepage does NOT use the auth layout. It has its own full-page design.

**Mobile layout** (stacked, full-viewport):
```
┌─────────────────────────────┐
│ [Logo] ФІОТ          [Theme]│  ← header
├─────────────────────────────┤
│                             │
│     [Mascot 120px]          │  ← hero illustration
│     animate-gentle-float    │
│                             │
│   Вступ до ФІОТ             │  ← heading, text-3xl bold
│   починається тут            │
│                             │
│   Система електронного       │  ← subtitle, text-base muted
│   вступу ФІОТ КПІ           │
│                             │
│                             │
│  ┌─────────────────────────┐│
│  │   Розпочати вступ       ││  ← primary CTA, h-12 rounded-xl
│  └─────────────────────────┘│
│                             │
│   Вже маєте акаунт? Увійти  │  ← secondary link
│                             │
│       © КПІ ім. Сікорського │  ← footer
└─────────────────────────────┘
```

**Desktop layout** (split):
```
┌──────────────────────┬──────────────────┐
│                      │          [Theme] │
│    hero-bg           │                  │
│                      │  [Mascot 80px]   │
│  [Large ФІОТ         │                  │
│   wordmark]          │  Вступ до ФІОТ   │
│                      │  починається тут  │
│  [Decorative grid    │                  │
│   + gradient]        │  Subtitle text    │
│                      │                  │
│                      │  [Primary CTA]   │
│                      │  [Secondary]     │
│                      │                  │
│    © КПІ             │                  │
└──────────────────────┴──────────────────┘
      55%                    45%
```

---

## Step 3.2: Implementation

### File: `client/web/src/app/page.tsx`

```tsx
import Image from "next/image";
import Link from "next/link";
import { ThemeToggle } from "@/components/auth/theme-toggle";

export default function HomePage() {
  return (
    <div className="flex min-h-screen">
      {/* Left branding panel — desktop only */}
      <div className="hidden lg:flex lg:w-[55%] hero-bg relative flex-col items-center justify-center p-12">
        <div className="text-center">
          <h1 className="text-7xl font-bold tracking-tighter text-foreground/90">ФІОТ</h1>
          <p className="mt-3 text-xl text-muted-foreground">Приймальна комісія</p>
        </div>
        <p className="absolute bottom-8 text-xs text-muted-foreground/60">
          КПІ ім. Ігоря Сікорського
        </p>
      </div>

      {/* Right content panel */}
      <div className="flex flex-1 flex-col min-h-screen lg:min-h-0">
        {/* Header */}
        <header className="flex items-center justify-between p-4">
          <div className="flex items-center gap-2 lg:invisible">
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

        {/* Main content — centered */}
        <main className="flex flex-1 flex-col items-center justify-center px-5 pb-8 lg:px-12">
          <div className="w-full max-w-[420px] space-y-8">
            {/* Mascot */}
            <div
              className="flex justify-center animate-[stagger-in_0.5s_ease-out_both] motion-reduce:animate-none"
              style={{ animationDelay: "0ms" }}
            >
              <Image
                src="/frog-logo.png"
                alt=""
                width={120}
                height={120}
                className="rounded-3xl animate-[gentle-float_3s_ease-in-out_infinite] motion-reduce:animate-none lg:w-20 lg:h-20"
                aria-hidden="true"
                unoptimized
              />
            </div>

            {/* Text content */}
            <div
              className="text-center space-y-3 animate-[stagger-in_0.5s_ease-out_both] motion-reduce:animate-none"
              style={{ animationDelay: "80ms" }}
            >
              <h2 className="text-3xl font-bold tracking-tight lg:text-4xl">
                Вступ до ФІОТ{" "}
                <span className="block lg:inline">починається тут</span>
              </h2>
              <p className="text-base text-muted-foreground leading-relaxed">
                Система електронного вступу факультету інформатики та обчислювальної техніки КПІ
              </p>
            </div>

            {/* Actions */}
            <div
              className="space-y-3 animate-[stagger-in_0.5s_ease-out_both] motion-reduce:animate-none"
              style={{ animationDelay: "160ms" }}
            >
              <Link
                href="/login"
                className="flex h-12 w-full items-center justify-center rounded-xl bg-primary text-base font-semibold text-primary-foreground shadow-sm transition-all hover:shadow-lg hover:shadow-primary/25 hover:scale-[1.02] active:scale-[0.98] motion-reduce:hover:scale-100 motion-reduce:active:scale-100"
              >
                Розпочати
              </Link>
              <p className="text-center text-sm text-muted-foreground">
                Вхід або створення нового акаунту
              </p>
            </div>
          </div>
        </main>

        {/* Footer — mobile only */}
        <footer className="p-4 text-center lg:hidden">
          <p className="text-xs text-muted-foreground/60">
            КПІ ім. Ігоря Сікорського
          </p>
        </footer>
      </div>
    </div>
  );
}
```

---

## Step 3.3: Design Details

### CTA Button
- Height: 48px (`h-12`)
- Radius: 12px (`rounded-xl`)
- Font: 16px semibold (`text-base font-semibold`)
- Hover: shadow + slight scale up (`hover:scale-[1.02]`)
- Active: scale down (`active:scale-[0.98]`)
- Reduced motion: no scale transforms

### Staggered Entrance Animation
- 3 groups stagger at 0ms, 80ms, 160ms
- Each uses `stagger-in` keyframe (Phase 1): translateY(12px) → 0 with opacity
- Duration: 500ms each, ease-out
- Respected by `motion-reduce:animate-none`

### Desktop Left Panel
- Reuses `hero-bg` class from Phase 1
- Large ФІОТ wordmark at `text-7xl` (72px)
- Subtle footer "КПІ ім. Ігоря Сікорського"

### Mobile
- Larger mascot (120px) with gentle-float animation
- Full-width CTA
- Compact header with logo + theme toggle

---

## Step 3.4: Accessibility Considerations

- `h1` is "ФІОТ" in the left panel (desktop) — the visual hierarchy heading
- `h2` is the action-oriented heading in the right panel
- On mobile where left panel is hidden, consider making "Вступ до ФІОТ починається тут" an `h1` instead
- **Solution**: Use `lg:hidden` / `hidden lg:block` to render appropriate heading levels:
  - Mobile: `<h1>Вступ до ФІОТ починається тут</h1>` (visible)
  - Desktop: `<h1>ФІОТ</h1>` in left panel + `<h2>` in right panel

- Images are decorative: `alt=""` + `aria-hidden="true"`
- Link uses semantic `<Link>` with clear text "Розпочати"
- Stagger animations respect `prefers-reduced-motion`

---

## Verification Checklist
- [ ] Desktop (1440px): Left panel with hero-bg + large ФІОТ, right panel with centered content
- [ ] Mobile (375px): Full-width, mascot prominent, full-width CTA
- [ ] Stagger animation plays on first load (3 groups, 80ms apart)
- [ ] Reduced motion: no animations, content renders immediately
- [ ] Dark mode: hero-bg dark variant, all text readable
- [ ] CTA hover effect: shadow + scale
- [ ] CTA tap/click navigates to /login
- [ ] Screen reader: heading hierarchy is logical (h1 → content)
- [ ] No horizontal scroll on any viewport
- [ ] Mascot float animation is smooth (3s loop)
- [ ] Playwright: navigate to /, verify CTA visible, click navigates to /login
