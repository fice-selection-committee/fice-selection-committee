# Phase 2: shadcn/ui Components & Design System

**Depends on**: Phase 1
**Blocks**: Phase 4, 5, 6

## Checklist

- [ ] Install shadcn/ui base components:
  ```
  pnpm dlx shadcn@latest add button input label form select checkbox radio-group
  pnpm dlx shadcn@latest add dialog alert-dialog sheet dropdown-menu popover
  pnpm dlx shadcn@latest add table badge avatar separator scroll-area
  pnpm dlx shadcn@latest add sonner tabs card skeleton
  pnpm dlx shadcn@latest add command tooltip progress
  ```
- [ ] Create `src/lib/utils/cn.ts`:
  ```ts
  import { clsx, type ClassValue } from "clsx";
  import { twMerge } from "tailwind-merge";
  export function cn(...inputs: ClassValue[]) {
    return twMerge(clsx(inputs));
  }
  ```
- [ ] Verify shadcn/ui components use CSS variables from globals.css (lavender theme applied)
- [ ] Enforce anti-patterns in component defaults:
  - Buttons: `rounded-md` (not `rounded-full`)
  - No gradient backgrounds on surfaces
  - No `backdrop-blur` on cards
- [ ] Test: render a sample page with Button, Card, Input, Dialog to verify theme

## Design System Reference
- Primary: `#BC9BF3` (HSL 268 83% 78%)
- Primary dark: `#9B7BCF` (HSL 268 40% 65%) — use for text on white backgrounds
- Background: `#FAF9FE`
- Success: `#8FC93A`, Error: `#F25E5E`, Warning: `#FFC857`
- Font: Poppins (400, 500, 600, 700) with Cyrillic
- Border radius: `0.5rem` default
- Sidebar: 260px expanded, 80px collapsed

## Deliverables
- All shadcn/ui components installed and themed
- `cn()` utility available
- Visual verification of lavender theme
