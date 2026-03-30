# Phase 7: Testing & Edge Cases

## Overview
Manual and automated verification of all onboarding scenarios.

## Manual Test Scenarios

### 7.1 New User Flow
1. Enter new email → request magic link
2. Verify via magic link → redirected to `/onboarding`
3. Terms step: expand both sections, check checkbox, click "Далі"
4. DOB step: select valid date (age >= 16), click "Далі"
5. Name step: fill Прізвище + Ім'я (+ optional По-батькові), click "Зберегти"
6. Welcome animation plays → auto-redirect to dashboard
7. Verify DB: `terms_accepted_at`, `date_of_birth`, `onboarding_completed=true`

### 7.2 Returning User — Incomplete Onboarding
1. Complete only Terms step, then logout
2. Re-login with same email
3. Verify: lands on DOB step (not terms, not start)
4. Complete DOB, logout again
5. Re-login → lands on Name step
6. Complete Name → welcome → dashboard

### 7.3 Returning User — Complete Onboarding
1. User who completed all steps logs in
2. Verify: goes directly to dashboard (no onboarding screens)

### 7.4 Page Refresh at Each Step
1. At Terms step: refresh → still on Terms step
2. At DOB step: refresh → still on DOB step
3. At Name step: refresh → still on Name step

### 7.5 URL Manipulation
1. While at Terms step, manually navigate to `/applicant` → must redirect to `/onboarding`
2. While at DOB step, navigate to `/admin` → must redirect to `/onboarding`
3. After completion, navigate to `/onboarding` → must redirect to dashboard

### 7.6 Invalid Input — DOB
1. Enter date making user 15 years old → error "Вам має бути щонайменше 16 років"
2. Enter invalid date (Feb 30) → error "Невірна дата"
3. Cannot proceed until valid date entered

### 7.7 Invalid Input — Name
1. Leave Прізвище empty, try to submit → validation error
2. Leave Ім'я empty, try to submit → validation error
3. По-батькові empty is OK → can proceed

### 7.8 Terms Checkbox
1. Do not check checkbox → "Далі" button is disabled
2. Check checkbox → button becomes enabled

### 7.9 Theme Toggle
1. Toggle to dark mode on login page → persists on refresh
2. Complete onboarding → dark mode persists to dashboard
3. Toggle back to light → persists

### 7.10 Responsive
1. Test all steps at 375px (mobile) — inputs stack, tap targets adequate
2. Test at 768px (tablet) — comfortable margins
3. Test at 1440px (desktop) — centered card, max-width ~560px

## Automated Tests (if time permits)

### Unit Tests
- `getOnboardingStep()` — all state combinations
- Validation schemas — valid/invalid dates, age boundary, empty names

### Integration Tests
- Backend: `PATCH /profile/terms` sets timestamp
- Backend: `PATCH /profile/dob` rejects age < 16
- Backend: `PATCH /profile` sets `onboardingCompleted` when all fields present

## Acceptance Criteria
- [ ] All 10 manual scenarios pass
- [ ] No way to reach dashboard without completing all steps
- [ ] Progress survives logout + re-login
- [ ] Progress survives page refresh
- [ ] Responsive layout works at all breakpoints
- [ ] Dark/light theme works throughout the flow
