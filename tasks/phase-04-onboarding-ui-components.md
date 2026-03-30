# Phase 4: Frontend — Onboarding UI Components

## Overview
Build all onboarding step components and the orchestrator page, following design reference patterns.

## Design Direction (from references)

### Key Patterns:
1. **Vertical step checklist** (Ref 4) — shows all steps with purple circle indicators; completed steps get checkmarks, current is highlighted, future is muted
2. **Full-width rounded CTA buttons** at bottom of each step (primary/dark color)
3. **Centered card layout** with generous padding (~24-32px), clean white/dark card
4. **Large centered headings** per step with muted subtitle text
5. **Purple/lavender accent** for active indicators and buttons (existing theme)
6. **Animated transitions** between steps (subtle slide/fade)

### Layout Structure (Desktop):
```
┌─────────────────────────────────────────────┐
│              ФІОТ                           │
│         Приймальна комісія            🌙    │
├─────────────────────────────────────────────┤
│                                             │
│  ┌──────────────┐  ┌─────────────────────┐  │
│  │ ✓ Умови      │  │                     │  │
│  │ ● Дата       │  │  [Current Step      │  │
│  │ ○ Дані       │  │   Content Card]     │  │
│  │              │  │                     │  │
│  └──────────────┘  │  [Full-width CTA]   │  │
│                    └─────────────────────┘  │
│                                             │
└─────────────────────────────────────────────┘
```

### Layout Structure (Mobile):
```
┌───────────────────┐
│     ФІОТ      🌙  │
├───────────────────┤
│ ✓──●──○           │
│ (compact dots)    │
├───────────────────┤
│                   │
│ [Step Heading]    │
│ [Step Subtitle]   │
│                   │
│ [Form Fields]     │
│                   │
│ [Full-width CTA]  │
│                   │
└───────────────────┘
```

## Steps

### 4.1 Create OnboardingProgress Component
- **File (new):** `client/web/src/components/onboarding/progress.tsx`
- **Desktop (≥1025px):** Vertical step list in a sidebar-like column
  - Each step: circle icon (number or checkmark) + step label
  - Completed: purple filled circle with white checkmark + strikethrough or muted label
  - Current: purple filled circle with number + bold label
  - Future: gray outlined circle with number + muted label
  - Connecting lines between steps (vertical divider)
- **Tablet/Mobile (≤1024px):** Horizontal compact indicator
  - Three dots or small circles with connecting lines
  - Current dot is larger/filled with primary color
- Props: `currentStep: OnboardingStep`, `completedSteps: OnboardingStep[]`
- Step labels: "Умови та політика", "Дата народження", "Особисті дані"

### 4.2 Create TermsStep Component
- **File (new):** `client/web/src/components/onboarding/terms-step.tsx`
- **Heading:** "Умови та політика" (large, centered, semibold)
- **Subtitle:** "Будь ласка, ознайомтесь з умовами та погодьтесь для продовження" (muted)
- **Content:** Two expandable sections using shadcn `Collapsible` or custom Accordion:
  - Section 1: "Умови користування" — 2-3 paragraphs of placeholder Ukrainian text
  - Section 2: "Політика конфіденційності" — 2-3 paragraphs of placeholder Ukrainian text
  - Each section has a chevron icon and expand/collapse animation
  - Scrollable content area with `max-h-48 overflow-y-auto`
- **Checkbox:** "Я погоджуюсь з умовами користування та політикою конфіденційності"
  - Uses shadcn `Checkbox` + label
- **CTA:** Full-width rounded "Далі" button (`w-full rounded-xl`)
  - Disabled (muted) until checkbox is checked
  - Loading spinner during API call
- **On submit:** `authApi.acceptTerms()` → `onComplete()` callback
- Error handling with toast

### 4.3 Create DateOfBirthStep Component
- **File (new):** `client/web/src/components/onboarding/dob-step.tsx`
- **Heading:** "Дата народження" (large, centered, semibold)
- **Subtitle:** "Вкажіть вашу дату народження для продовження" (muted)
- **Inputs:** Three `<Select>` dropdowns (shadcn Select component):
  - **День:** 1-31 (numeric)
  - **Місяць:** Ukrainian month names (Січень, Лютий, Березень, Квітень, Травень, Червень, Липень, Серпень, Вересень, Жовтень, Листопад, Грудень)
  - **Рік:** from (currentYear - 100) to (currentYear - 14), descending order
  - Layout: `flex gap-3` on desktop, `flex-col gap-3 sm:flex-row` for mobile stacking
- **Validation:**
  - Invalid date (e.g., Feb 30): "Невірна дата"
  - Age < 16: "Вам має бути щонайменше 16 років"
  - Show error message below selects (red text)
- **CTA:** Full-width rounded "Далі" button
- **On submit:** Format as "YYYY-MM-DD", call `authApi.submitDateOfBirth()`, then `onComplete()`
- Use react-hook-form + zod resolver

### 4.4 Create NameStep Component
- **File (new):** `client/web/src/components/onboarding/name-step.tsx`
- **Heading:** "Особисті дані" (large, centered, semibold)
- **Subtitle:** "Вкажіть ваше прізвище, ім'я та по батькові" (muted)
- **Inputs:** Three text inputs (shadcn Input):
  - Прізвище (lastName) — required, placeholder "Іванов"
  - Ім'я (firstName) — required, placeholder "Іван"
  - По-батькові (middleName) — optional, placeholder "Іванович", label includes "(необов'язково)"
- **CTA:** Full-width rounded "Зберегти" button
- **On submit:** `authApi.updateProfile()` → `onComplete()`
- Reuse `profileCompleteSchema` from existing validators
- Same react-hook-form pattern as existing `ProfileCompletePage`

### 4.5 Create WelcomeScreen Component
- **File (new):** `client/web/src/components/onboarding/welcome-screen.tsx`
- **Layout:** Full card, centered content
- **Animation sequence:**
  1. Animated checkmark circle (scale from 0→1 + opacity 0→1, CSS keyframes `@keyframes checkmark-pop`)
  2. Purple/primary filled circle with white checkmark icon inside
  3. After 0.5s delay: heading fades in "Ласкаво просимо, {firstName} {lastName}!"
  4. Subtitle: "Переходимо до вашого кабінету..."
- **Auto-redirect:** `setTimeout` 2.5s → `router.push(getRoleDefaultRoute(user.role))`
- Uses `getRoleDefaultRoute` from `lib/auth/roles.ts`

### 4.6 Create Onboarding Page (Orchestrator)
- **File (new):** `client/web/src/app/(auth)/onboarding/page.tsx`
- `"use client"` component with `export const metadata` NOT possible (use `<title>` via next/head or document)
- **Behavior:**
  - Uses `useAuth()` to get `{ user, isLoading, refreshUser }`
  - If `isLoading` → render loading skeleton (card shimmer)
  - If `!user` → should not happen (middleware redirects), but safety: `router.push("/login")`
  - If `user.onboardingCompleted` → safety redirect to dashboard
  - Calls `getOnboardingStep(user)` → determines current step
- **Layout:**
  - Desktop: two-column layout inside max-width container
    - Left column (narrow): `OnboardingProgress`
    - Right column (wide): Current step card
  - Mobile: single column — progress dots on top, step card below
- **Step transitions:** Each step component calls `onComplete` which:
  1. Calls the relevant API
  2. Calls `refreshUser()` to get updated user with new fields
  3. Component re-renders → `getOnboardingStep()` returns next step
  4. Optionally: animate transition with CSS `transition-all` or Framer Motion
- **Card wrapper:** `<Card>` with consistent padding, rounded corners (`rounded-xl`), subtle shadow

## Acceptance Criteria
- [ ] Vertical step list shows completed/current/future states correctly
- [ ] Terms expandable sections open/close with animation
- [ ] Terms checkbox properly gates the CTA button
- [ ] DOB selects show Ukrainian month names
- [ ] DOB validates date and age correctly with Ukrainian error messages
- [ ] Name fields validate required fields
- [ ] Welcome animation plays smoothly and auto-redirects
- [ ] Responsive at mobile (≤640px), tablet (641-1024px), desktop (≥1025px)
- [ ] All steps use existing shadcn components (Card, Button, Input, Checkbox, Select)
- [ ] Loading and error states handled for all API calls
- [ ] Step transitions feel smooth (no jarring re-renders)
- [ ] Design matches reference patterns: purple accents, full-width CTAs, centered headings
