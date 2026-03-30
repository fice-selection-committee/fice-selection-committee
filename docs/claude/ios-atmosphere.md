# iOS Atmosphere Design System

This document defines the design system evolution toward an iOS-inspired atmosphere. It is operational instruction — every rule is binding for frontend changes.

---

## 1. Core Principles

The goal is **not** to make the app look like iOS. The goal is:

- **Clarity**: legibility, hierarchy, distinct information layers
- **Deference**: content-first, minimal chrome, UI recedes behind content
- **Depth**: layered surfaces, frosted overlays, spring-physics motion
- **Responsiveness**: immediate feedback, every tap acknowledged
- **Predictability**: consistent patterns, no surprises

---

## 2. Typography Scale

SF Pro Display is the primary font, loaded locally with 4 weights.

| Token Class | Size | Weight | Tracking | Line-Height | Use |
|---|---|---|---|---|---|
| `.text-title1` | 28px (1.75rem) | 700 bold | -0.02em | 1.25 | Page titles (h1) via `PageHeader` |
| `.text-title2` | 22px (1.375rem) | 600 semibold | -0.015em | 1.3 | Section headings, auth/onboarding (h2) |
| `.text-title3` | 20px (1.25rem) | 600 semibold | -0.015em | 1.3 | Card titles, subsections (h3) |
| `.text-headline` | 17px (1.0625rem) | 600 semibold | -0.01em | 1.4 | Emphasized body text |
| `.text-body-primary` | 17px (1.0625rem) | 400 regular | 0.01em | 1.5 | Primary body text |
| `.text-callout` | 16px (1rem) | 400 regular | 0.01em | 1.45 | Secondary body text |
| `.text-subheadline` | 15px (0.9375rem) | 400 regular | 0.01em | 1.45 | Subheadlines, captions |
| `.text-footnote` | 13px (0.8125rem) | 400 regular | 0.015em | 1.4 | Timestamps, metadata |
| `.text-caption1` | 12px (0.75rem) | 500 medium | 0.02em | 1.35 | Badges, labels |
| `.text-caption2` | 11px (0.6875rem) | 400 regular | 0.02em | 1.35 | Fine print |

### Font Weight Rules

| Weight | Usage |
|---|---|
| 400 (regular) | Body text, descriptions, form inputs |
| 500 (medium) | Labels, badges, captions, nav items |
| 600 (semibold) | Section titles, card titles |
| 700 (bold) | Page titles only |

### Rules

- Page titles: use `.text-title1` (via `PageHeader` component)
- Auth/onboarding headings: use `.text-title2`
- Card titles: use `.text-title3` or existing `CardTitle` (font-semibold)
- Body text: prefer `font-normal` (400). Reserve `font-medium` for labels/emphasis.
- Stat numbers in dashboard cards: `text-2xl font-semibold` is acceptable (display numbers, not headings)

---

## 3. Button System

### Variants

| Variant | Appearance | Use Case |
|---|---|---|
| `default` (filled) | `bg-primary`, white text | Primary CTA — one per section |
| `tinted` | `bg-primary/10 text-primary` | Secondary action alongside primary (iOS tinted button pattern) |
| `destructive` | `bg-destructive`, white text | Irreversible/dangerous actions |
| `outline` | `border`, transparent bg, `hover:bg-muted` | Neutral non-primary actions |
| `secondary` | `bg-secondary`, white text | Secondary emphasis (darker) |
| `ghost` | No bg, `hover:bg-muted` | Toolbar buttons, icon buttons, inline actions |
| `link` | `text-primary`, underline on hover | Inline text links |

### Sizes

| Size | Height | Use |
|---|---|---|
| `default` | h-11 (44px) | Standard buttons — meets Apple HIG 44pt minimum |
| `sm` | h-10 (40px) | Compact areas, table actions |
| `xs` | h-6 (24px) | Tight inline actions (icon-only acceptable) |
| `lg` | h-12 (48px) | Prominent CTAs |
| `icon` | size-11 (44px square) | Icon-only buttons |
| `icon-sm` | size-10 (40px square) | Compact icon buttons |
| `icon-xs` | size-6 (24px square) | Inline icon actions |
| `icon-lg` | size-12 (48px square) | Prominent icon buttons |

### Visual Properties

- Border radius: `rounded-lg` (8px) — base class
- Transition: `transition-all duration-150`
- Press feedback: `active:scale-[0.98] motion-reduce:active:scale-100`
- Disabled: `opacity-50 pointer-events-none`

### Rules

- Ghost and outline hover: `bg-muted` (subtle gray), NOT `bg-accent` (purple)
- Use `tinted` variant for secondary actions that should feel connected to primary
- `LoadingButton` is mandatory for async actions (see `interaction-standards.md`)
- Never use raw `<button>` — always use `<Button>` or `<LoadingButton>`

---

## 4. Link System

### `AppLink` Component (`components/ui/app-link.tsx`)

| Variant | Styling | Use |
|---|---|---|
| `default` | `text-primary hover:underline` | Standard navigation links |
| `muted` | `text-muted-foreground hover:text-foreground` | Secondary links, breadcrumbs |
| `destructive` | `text-destructive hover:underline` | Destructive action links |

### Rules

- External links: set `external` prop — adds trailing `ArrowUpRight` icon + `target="_blank"`
- Focus ring: `focus-visible:ring-[3px] ring-ring/50` (same as buttons)
- Transition: `duration-150`
- Use `AppLink` for styled links. Use Next.js `Link` directly only inside components that provide their own styling (nav items, etc.)

---

## 5. Motion Timing System

### CSS Custom Properties

```css
--duration-fast: 150ms;     /* Hover, focus, micro-interactions */
--duration-normal: 200ms;   /* Modal open/close, content swaps */
--duration-slow: 300ms;     /* Sidebar collapse, sheet slide, step transitions */
--duration-decorative: 500ms; /* Carousel, progress bars */

--ease-spring: cubic-bezier(0.2, 0.8, 0.2, 1);   /* Entrance animations (iOS spring-like) */
--ease-decelerate: cubic-bezier(0, 0, 0.2, 1);    /* Elements entering view */
--ease-accelerate: cubic-bezier(0.4, 0, 1, 1);    /* Elements leaving view */
```

### Allowed Durations (per `interaction-standards.md`)

| Duration | Use |
|---|---|
| Default (150ms) | Hover, focus changes |
| `duration-200` | Modal open/close, content swaps |
| `duration-300` | Sidebar collapse, sheet slide, step transitions |
| `duration-500` | Decorative only (carousel, progress bar) |

### Animation Principles

- Entrances: use `--ease-spring` for physics-like feel
- Exits: faster than entrances (300ms → 200ms)
- Translate distances: subtle (6-16px), not dramatic
- Stagger increments: 50-80ms between elements
- Overlays: `backdrop-blur-sm` for frosted glass depth
- Always respect `prefers-reduced-motion` (global override in `globals.css`)

### Keyframe Animations Available

| Animation | Duration | Use |
|---|---|---|
| `checkmark-pop` | 0.5s | Success checkmark feedback |
| `fade-in` | Variable | Scale-based entrance |
| `fade-in-up` | Variable | Upward slide entrance (6px) |
| `slide-in-right` | Variable | Right-to-left entrance (16px) |
| `slide-out-left` | Variable | Left slide exit (16px) |
| `stagger-in` | Variable | Sequential list entrance (8px) |
| `gentle-float` | Infinite | Decorative floating motion |
| `pulse-ring` | 2s infinite | Focus attention ring |
| `progress-fill` | Variable | Progress bar fill |

---

## 6. Interaction State Model

| State | Visual | Timing |
|---|---|---|
| Default | Base styling | — |
| Hover | `bg-muted` (ghost/outline), variant opacity shift (filled) | 150ms ease-out |
| Focus-visible | `ring-[3px] ring-ring/50` | Instant |
| Active/Pressed | `scale-[0.98]` | 150ms ease-out |
| Selected | `bg-primary/10 text-primary` + `aria-current="page"` | 150ms |
| Disabled | `opacity-50 pointer-events-none` | — |
| Loading | Content invisible, spinner centered, width-pinned | 300ms ease-out |

### Rules

- All interactive elements MUST have hover + focus-visible + active states
- Sidebar nav items: `active:scale-[0.98]` for press feedback
- Navigation links: `transition-colors duration-150`
- Ghost/outline hover: `bg-muted` (subtle gray), never `bg-accent` (purple)
- See `interaction-standards.md` for full specification

---

## 7. Elevation System

### Shadow Tokens

| Token | Shadow | Use |
|---|---|---|
| `--shadow-card` | Subtle 1px shadow | Cards, raised surfaces |
| `--shadow-dropdown` | Medium 4px shadow | Dropdowns, popovers, select menus |
| `--shadow-overlay` | Pronounced 10px shadow | Dialogs, sheets, modals |

### Overlay Pattern

- Dialog/Sheet/AlertDialog overlays: `bg-black/50 backdrop-blur-sm`
- Creates frosted-glass depth effect
- Content behind overlay is visible but muted

### Surface Tokens

| Token | Use |
|---|---|
| `--surface-elevated` | Raised elements (light mode) |
| `--surface-sunken` | Inset/depressed surfaces |
| `--primary-subtle` | Light primary tint background |
| `--primary-glow` | Glowing primary accent (15% opacity) |

---

## 8. Color Rules

### Key Token Relationships

| Token | Value | Note |
|---|---|---|
| `--primary` | `oklch(0.726 0.126 293)` | Purple — actions, CTAs, focus rings |
| `--accent` | `oklch(0.726 0.126 293)` | Same as primary (used by Radix internals) |
| `--muted` | `oklch(0.955 0.012 290)` | Subtle gray — ghost hover, backgrounds |
| `--muted-foreground` | `oklch(0.55 0.025 290)` | Secondary text (bumped for readability) |
| `--destructive-foreground` | `oklch(1 0 0)` | White (text ON destructive background) |

### Rules

- Ghost/outline button hover: use `bg-muted`, not `bg-accent`
- `--accent` remains equal to `--primary` for Radix component compatibility
- `--destructive-foreground` is white (for text on red backgrounds)
- Use `text-destructive` for red text labels (not `text-destructive-foreground`)
- All colors use OKLch color space for perceptual uniformity
- Raw Tailwind palette colors (`blue-100`, `green-800`) are forbidden — use tokens

---

## 9. Spacing Guidelines

### Base Unit: 4px

| Token | Value | Use |
|---|---|---|
| `gap-1` | 4px | Icon gaps, tight inline |
| `gap-2` | 8px | Related elements (label → input) |
| `gap-3` | 12px | Items in a group |
| `gap-4` | 16px | Between groups |
| `gap-5` | 20px | Section padding, form fields |
| `gap-6` | 24px | Between sections |
| `gap-8` | 32px | Major layout breaks |

### Component Spacing

| Component | Padding |
|---|---|
| Card | `py-6 px-6` (24px) |
| Dialog content | `p-6` (24px) |
| Topbar | `h-16 px-4 lg:px-6` |
| Sidebar items | `px-3 py-2.5` |
| Buttons (default) | `px-4 py-2` |

---

## 10. Border Radius Scale

| Token | Value | Use |
|---|---|---|
| `rounded-sm` | 4px | Small inline elements |
| `rounded-md` | 6px | Checkboxes, icon-xs buttons |
| `rounded-lg` | 8px | Buttons, inputs, nav items, dropdowns |
| `rounded-xl` | 12px | Cards, containers |
| `rounded-2xl` | 16px | Mobile sheet top corners |
| `rounded-full` | 50% | Badges, avatars, switches |

### Rules

- Buttons: `rounded-lg` (base class)
- Cards: `rounded-xl`
- Nav items: `rounded-lg`
- Back button: `rounded-md`
- Sidebar toggle button: `rounded-md`
- Maintain radius hierarchy: smaller nested elements use smaller radius

---

## 11. Component Quick Reference

| Pattern | Component | Key Classes |
|---|---|---|
| Page title | `PageHeader` | `.text-title1` |
| Section heading | `<h2>` | `.text-title2` |
| Primary action | `<Button>` or `<LoadingButton>` | `variant="default"` |
| Secondary action | `<Button variant="tinted">` | `bg-primary/10` |
| Cancel/neutral | `<Button variant="outline">` | `hover:bg-muted` |
| Inline action | `<Button variant="ghost">` | `hover:bg-muted` |
| Navigation link | `<AppLink>` | `text-primary hover:underline` |
| External link | `<AppLink external>` | `+ ArrowUpRight icon` |
| Back navigation | `<BackButton>` | `text-muted-foreground` |
| Modal overlay | `Dialog`/`Sheet`/`AlertDialog` | `backdrop-blur-sm` |
| Loading state | `<LoadingButton loading>` | Apple spinner, width-pinned |

---

## 12. Migration Notes

### From Previous System

| Before | After | Reason |
|---|---|---|
| `text-2xl font-bold tracking-tight` (headings) | `.text-title2` | Unified typography scale |
| `text-2xl font-semibold tracking-tight` (page h1) | `.text-title1` | Proper page-level hierarchy |
| Ghost `hover:bg-accent` | `hover:bg-muted` | Content-deferring, iOS-like subtle hover |
| Outline `hover:bg-accent` | `hover:bg-muted` | Consistent with ghost pattern |
| Button `h-9` default | `h-11` default | Meets Apple HIG 44pt minimum touch target |
| Button `rounded-md` | `rounded-lg` | Softer, iOS-aligned corners |
| Sheet open `duration-500` | `duration-300` | Per interaction-standards, snappier feel |
| Dialog overlay (solid) | `backdrop-blur-sm` | Frosted glass depth |
| `shadow-sm` on cards | `shadow-[var(--shadow-card)]` | Semantic elevation token |
| `--destructive-foreground` = red | = white | Correct: text ON destructive bg |
| `--muted-foreground` oklch(0.5) | oklch(0.55) | Better readability |
