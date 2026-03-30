# Redesign Overview: Homepage & Onboarding UI/UX Overhaul

## Goal
Transform the homepage (entrance) and onboarding flow from a developer prototype into a polished, mobile-native-feeling product that matches premium app references — using only existing functionality.

## Design Principles (Derived from Reference Analysis)
1. **One screen, one purpose, one primary CTA**
2. **Bottom-anchored actions** on mobile (fixed bottom panel)
3. **Illustration-driven** top sections (40-50% of screen on mobile)
4. **Strict typography hierarchy** (bold 24px heading, regular 16px body)
5. **Generous whitespace** (32-48px between sections)
6. **Rounded everything** (12-16px button/input radius)
7. **Monochromatic + one accent** (purple primary from existing palette)
8. **Progressive disclosure** (each step feels like its own screen)
9. **Trust-building sequence** (warmth before forms)
10. **Dark mode as first-class** (not an afterthought)

## Architecture Decision: Desktop Split-Panel
- **Mobile (<1024px)**: Full-width, vertically stacked, bottom-anchored CTAs
- **Desktop (>=1024px)**: 55% left branding panel / 45% right form panel
- Left panel: animated gradient background, university branding, step illustrations
- Right panel: form content, max-w-[420px], vertically centered

## Scope
Only pages: homepage (`page.tsx`), auth layout, login, onboarding (terms, DOB, name, welcome), and shared components used by these.

**No new features. No backend changes. Only UI/UX improvements to existing functionality.**

## Phase Breakdown

| Phase | Description | Est. Effort |
|-------|------------|-------------|
| redesign-01 | Foundation: CSS tokens, animations, typography, safe areas | 2-3h |
| redesign-02 | Auth layout: split-panel desktop, mobile full-screen | 2-3h |
| redesign-03 | Homepage redesign: hero, CTA, branding | 2-3h |
| redesign-04 | Login page: form upgrade, check-email state | 2-3h |
| redesign-05 | Onboarding flow: all steps + progress indicator | 4-6h |
| redesign-06 | Accessibility fixes + WCAG compliance | 2-3h |
| redesign-07 | Polish: animations, transitions, Playwright verification | 2-3h |

**Total estimated: 16-24h**

## Files Affected
- `client/web/src/app/globals.css`
- `client/web/src/app/layout.tsx`
- `client/web/src/app/page.tsx`
- `client/web/src/app/(auth)/layout.tsx`
- `client/web/src/app/(auth)/login/page.tsx`
- `client/web/src/app/(auth)/onboarding/page.tsx`
- `client/web/src/components/auth/magic-link-form.tsx`
- `client/web/src/components/auth/theme-toggle.tsx`
- `client/web/src/components/onboarding/progress.tsx`
- `client/web/src/components/onboarding/terms-step.tsx`
- `client/web/src/components/onboarding/dob-step.tsx`
- `client/web/src/components/onboarding/name-step.tsx`
- `client/web/src/components/onboarding/welcome-screen.tsx`
- `client/web/src/components/ui/loading-button.tsx`

## No-Go Zones
- No changes to backend API contracts
- No changes to auth flow logic (magic link, verification, session)
- No changes to middleware routing
- No new npm dependencies (use existing: Tailwind v4, shadcn/ui, Lucide, Framer Motion if already installed)
- No new features beyond what exists today
