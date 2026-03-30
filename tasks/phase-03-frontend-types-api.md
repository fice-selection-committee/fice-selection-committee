# Phase 3: Frontend — Types, API Layer & Validators

## Overview
Update TypeScript types, API client methods, and validation schemas to support onboarding.

## Steps

### 3.1 Update User Type
- **File:** `client/web/src/types/auth.ts`
- Add `dateOfBirth?: string` (ISO date "YYYY-MM-DD")
- Add `termsAcceptedAt?: string` (ISO instant)
- Add `onboardingCompleted: boolean`

### 3.2 Add Onboarding API Methods
- **File:** `client/web/src/lib/api/auth.ts`
- Add `acceptTerms()` → `PATCH /api/v1/identity/profile/terms` with `{ termsAccepted: true }`
- Add `submitDateOfBirth(dateOfBirth: string)` → `PATCH /api/v1/identity/profile/dob` with `{ dateOfBirth }`
- Both methods return normalized `User` from response (reuse `normalizeUser`)
- Existing `updateProfile()` already handles name step

### 3.3 Add Validation Schemas
- **File:** `client/web/src/lib/validators/auth.ts`
- `termsSchema`: `z.object({ accepted: z.literal(true, { errorMap: () => ({ message: "Необхідно прийняти умови" }) }) })`
- `dateOfBirthSchema`: `z.object({ day: z.number().min(1).max(31), month: z.number().min(1).max(12), year: z.number().min(1900).max(currentYear) })` with `.refine()` for:
  - Valid date check (e.g., Feb 30 should fail)
  - Age >= 16 check using current date
  - Error messages in Ukrainian
- Reuse existing `profileCompleteSchema` for name step

### 3.4 Create Onboarding Step Helper
- **File (new):** `client/web/src/lib/auth/onboarding.ts`
- Export `OnboardingStep` type: `"terms" | "dob" | "name" | "complete"`
- Export `getOnboardingStep(user: User): OnboardingStep`
  - No `termsAcceptedAt` → `"terms"`
  - No `dateOfBirth` → `"dob"`
  - `firstName === "-"` → `"name"`
  - Otherwise → `"complete"`
- Export `isOnboardingRequired(user: User): boolean` → `!user.onboardingCompleted`

## Acceptance Criteria
- [ ] User type matches backend UserDto response shape
- [ ] API methods compile and follow existing patterns (normalizeUser, error handling)
- [ ] Validators correctly reject invalid DOBs and underage users
- [ ] Step derivation function correctly identifies each step from User state
