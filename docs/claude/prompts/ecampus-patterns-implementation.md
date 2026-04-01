# Prompt: eCampus Pattern Integration — Analysis & Implementation

<role>
You are a Senior Frontend Engineer and Product-Minded Implementor.
You are working on the FICE Selection Committee project — a Ukrainian university admissions system built with Next.js 16 + React 19 + TypeScript + Tailwind v4 (CSS-first) + shadcn/ui.

You have access to a detailed competitive analysis of https://ecampus.kpi.ua/ (KPI's electronic campus system) stored in `docs/analysis/ecampus-kpi-investigation-report.md` and screenshots in `screenshots/ecampus/`.

Your task is to analyze what patterns from that investigation are worth adopting, then implement the approved changes across the codebase.
</role>

---

## Phase 1: Gap Analysis & Implementation Plan

Before writing any code, you MUST:

### Step 1 — Read Source Materials
1. Read `docs/analysis/ecampus-kpi-investigation-report.md` (full report)
2. View all screenshots in `screenshots/ecampus/` (login desktop/mobile/tablet, curator search, password reset, SSO, carousel)
3. Read `CLAUDE.md` for project engineering standards

### Step 2 — Read Current Codebase State
Read these files to understand the current implementation:

**Auth & Login:**
- `client/web/src/app/(auth)/layout.tsx` — current auth layout (already has split-panel pattern)
- `client/web/src/app/(auth)/login/page.tsx` — login page
- `client/web/src/components/auth/magic-link-form.tsx` — magic link form component
- `client/web/src/components/auth/verify-magic-link.tsx` — verification component

**Layout & Navigation:**
- `client/web/src/app/layout.tsx` — root layout (fonts, metadata, providers)
- `client/web/src/app/(dashboard)/layout.tsx` — dashboard layout
- `client/web/src/components/layout/sidebar.tsx` — sidebar navigation
- `client/web/src/components/layout/topbar.tsx` — top bar
- `client/web/src/components/layout/breadcrumbs.tsx` — breadcrumbs

**Shared Components:**
- `client/web/src/components/shared/search-input.tsx` — existing search component
- `client/web/src/components/shared/data-table.tsx` — data table
- `client/web/src/components/shared/empty-state.tsx` — empty state
- `client/web/src/components/shared/page-header.tsx` — page header

**Design System:**
- `client/web/src/app/globals.css` — design tokens, colors, animations
- `client/web/src/components/ui/button.tsx` — button variants
- `client/web/src/components/ui/input.tsx` — input component
- `client/web/src/components/ui/checkbox.tsx` — checkbox component
- `client/web/src/components/ui/loading-button.tsx` — loading button

**Infrastructure:**
- `client/web/src/proxy.ts` — route protection (Next.js 16 proxy convention)
- `client/web/src/app/not-found.tsx` — 404 page
- `client/web/src/app/error.tsx` — error page
- `client/web/public/` — public assets (check what exists)

**Config:**
- `client/web/package.json` — dependencies
- `client/web/next.config.ts` — Next.js config

### Step 3 — Produce Gap Analysis

For each pattern from the ecampus report, evaluate against our CURRENT state:

| Pattern | eCampus Has | We Currently Have | Gap | Priority | Action |
|---|---|---|---|---|---|
| Split-layout login | Photo carousel right panel | Hero gradient left panel | Already have split layout, may need photo enhancement | Low | Compare quality |
| Real-time inline search | Keystroke results, no submit | Debounced SearchInput component | Have debounce; may lack inline result dropdown | Medium | Evaluate if search results appear inline or in separate table |
| Disabled CTA until valid | opacity-40 disabled button | LoadingButton with loading state | Check if disabled-until-valid is implemented on all forms | Medium | Audit forms |
| Back navigation | Consistent `< Back` top-left | Breadcrumbs component | Different pattern; evaluate if both are needed | Low | Compare approaches |
| Pre-auth support links | 3 support actions on login page | Unknown | Check login page | Medium | Add if missing |
| PWA manifest | Full manifest with maskable icons | `appleWebApp.capable = true` only | Missing manifest.json | High | Create manifest |
| Language switcher | URL-based i18n (/uk/, /en/) | Single language (uk), no i18n | Large gap but low priority | Low | Skip for now unless requested |
| Proper 404 page | Missing (they redirect everything) | Have `not-found.tsx` | We're already better | None | Verify quality |
| HTML lang attribute | Missing on their side | Set to "uk" in root layout | We're already better | None | Verify |
| Viewport meta | `maximum-scale=1` (bad) | Check our config | Must not have this | High | Audit and fix if present |
| Checkbox branding | Brand-colored when checked | shadcn default | May need brand color | Low | Check |
| Form validation feedback | None visible | Zod + RHF + inline errors | We're already better | None | Verify completeness |

**Output**: Present findings as a structured table with columns: Pattern, Current State, Gap Assessment, Recommended Action, Priority (P0-P3), Estimated Effort.

### Step 4 — Present Plan for Approval

Present ONLY the changes you recommend implementing, grouped by priority:

**P0 — Must Fix (accessibility/correctness):**
- List concrete issues found

**P1 — High Value, Low Effort:**
- List quick wins

**P2 — Medium Value, Medium Effort:**
- List enhancements worth doing

**P3 — Low Priority / Skip:**
- List items to defer with reasoning

Wait for user approval before proceeding to Phase 2.

---

## Phase 2: Implementation

After plan approval, implement changes following these rules:

### Implementation Constraints

1. **Read before write** — ALWAYS read the target file before modifying it
2. **Preserve existing patterns** — Do not refactor code you're not changing
3. **Match existing style** — Use the same conventions found in the codebase:
   - Ukrainian UI strings (no English unless i18n is being added)
   - OKLCH color values in globals.css
   - shadcn/ui component patterns
   - Tailwind v4 CSS-first approach
   - React Hook Form + Zod for forms
   - TanStack Query for server state
4. **No unnecessary dependencies** — Do not add npm packages unless absolutely required
5. **Accessibility first** — Every change must maintain or improve WCAG AA compliance
6. **Mobile-first responsive** — All UI changes must work on 390px+ viewports
7. **Dark mode compatible** — All color/style changes must respect the existing dark mode tokens
8. **No breaking changes** — Existing functionality must not regress

### Implementation Sequence

Follow this order strictly:

#### Block 1: Accessibility & Correctness Fixes
- Fix any viewport meta issues
- Fix any missing ARIA attributes
- Verify HTML lang is properly set
- Ensure all forms have proper validation feedback

#### Block 2: PWA Enhancement
- Create `client/web/public/manifest.json` (or `site.webmanifest`) with:
  - App name in Ukrainian
  - Brand colors from globals.css (convert OKLCH to hex for manifest)
  - Maskable icons (192x192, 512x512) — use existing logo or create placeholder reference
  - `display: standalone`
  - `start_url: /`
  - `scope: /`
- Add manifest link to root layout `<head>`
- Verify Apple Web App meta tags are complete

#### Block 3: Login Page Enhancement
- Compare current auth layout hero panel with ecampus carousel approach
- If current hero panel is a gradient/abstract pattern:
  - Consider adding institutional photo (document what photo assets are needed)
  - Add photo credit/attribution overlay with gradient (bottom-to-top)
  - Ensure graceful degradation on mobile (hide photo panel, show form only)
- Add pre-auth support section below magic-link form:
  - Contact/support link
  - FAQ or help link
  - Use icon + label pattern (consistent with existing UI components)
- Verify disabled state on submit button before email is valid

#### Block 4: Search Enhancement
- Audit existing `search-input.tsx` component
- If search results currently render in a separate data table below:
  - Consider adding an inline dropdown result mode for quick-lookup use cases
  - Create a `SearchWithResults` compound component that:
    - Wraps SearchInput
    - Shows results in a dropdown/popover below the input
    - Has loading, empty, and error states
    - Supports custom result row rendering
    - Is keyboard navigable (arrow keys, Enter to select, Escape to close)
- If this is too complex for current needs, document it as a future enhancement

#### Block 5: Navigation Enhancement
- Evaluate if a `BackButton` component is needed alongside breadcrumbs
- If useful for sub-pages (onboarding steps, document upload, profile edit):
  - Create `client/web/src/components/shared/back-button.tsx`
  - Props: `href` (destination), `label` (default "Назад")
  - Chevron-left icon + text
  - Consistent top-left placement
- Do NOT replace breadcrumbs — they serve different purpose

### Verification After Implementation

For each change made:

1. **Visual check** — Use Playwright to navigate to the changed page and take a screenshot
2. **Mobile check** — Resize to 390px and verify layout
3. **Dark mode check** — If theme toggle exists, verify dark mode appearance
4. **Accessibility check** — Verify ARIA attributes, focus management, keyboard navigation
5. **Build check** — Run `cd client/web && npm run build` to verify no build errors
6. **Type check** — Run `cd client/web && npx tsc --noEmit` to verify no type errors

### Output Format

For each implemented change, report:
```
## Change: [Name]
**Files Modified**: [list]
**Files Created**: [list]
**What Changed**: [1-2 sentence description]
**Screenshot**: [path if taken]
**Verification**: [build passed / types passed / visual verified]
```

---

## Anti-Patterns to Explicitly Avoid

Based on the ecampus investigation, do NOT:

1. ❌ Add `maximum-scale=1` or `user-scalable=no` to viewport meta
2. ❌ Redirect all unknown routes to login (we should show proper 404)
3. ❌ Create dead links (`href="#"`) — use plain text for "not assigned" states
4. ❌ Use external services (Google Forms) for support on login page
5. ❌ Pre-check "Remember me" by default on shared-computer-facing systems
6. ❌ Load 10+ carousel images on a single page without lazy loading
7. ❌ Skip HTML `lang` attribute
8. ❌ Add carousel without dot indicators, keyboard support, and pause controls
9. ❌ Copy their font (Exo 2) — we use SF Pro Display
10. ❌ Copy their color scheme — we have our own OKLCH-based design tokens

---

## Context References

- **Investigation Report**: `docs/analysis/ecampus-kpi-investigation-report.md`
- **Screenshots**: `screenshots/ecampus/01-login-page.png` through `09-carousel-slide-2.png`
- **Our Design Tokens**: `client/web/src/app/globals.css`
- **Our Auth Flow**: Magic link (not credentials) — `client/web/src/components/auth/magic-link-form.tsx`
- **Our Font**: SF Pro Display (local) — not Google Fonts
- **Our UI Library**: shadcn/ui — do not introduce alternative component libraries
- **Our Color Space**: OKLCH — do not use hex/rgb in CSS custom properties
- **Our Build**: `cd client/web && npm run build` (standalone Next.js output)

---

## Decision Authority

- **P0 changes**: Implement immediately without asking
- **P1 changes**: Implement after presenting the plan
- **P2 changes**: Present options, let user choose
- **P3 changes**: Document as future work, do not implement
- **New npm packages**: Always ask before adding
- **New files**: Acceptable for components, manifests, assets — not for documentation unless asked
- **Structural refactors**: Never do unless explicitly part of the approved plan
