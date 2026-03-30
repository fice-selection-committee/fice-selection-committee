# Phase 2: Backend — Onboarding API Endpoints

## Overview
Create DTOs and extend ProfileController with step-specific onboarding endpoints.

## Steps

### 2.1 Create AcceptTermsRequest DTO
- **File (new):** `server/.../user/api/dto/AcceptTermsRequest.java`
- Record with single field: `@NotNull Boolean termsAccepted`

### 2.2 Create DateOfBirthRequest DTO
- **File (new):** `server/.../user/api/dto/DateOfBirthRequest.java`
- Record with single field: `@NotNull LocalDate dateOfBirth`

### 2.3 Add Terms Endpoint to ProfileController
- **File:** `server/.../user/api/controller/ProfileController.java`
- `PATCH /api/v1/identity/profile/terms`
- Accepts `AcceptTermsRequest`, requires authentication
- Sets `user.termsAcceptedAt = Instant.now()` (server-side timestamp, legally defensible)
- Saves user, evicts cache, returns updated UserDto
- Add `@AuditLog` annotation for compliance

### 2.4 Add Date of Birth Endpoint to ProfileController
- `PATCH /api/v1/identity/profile/dob`
- Accepts `DateOfBirthRequest`, requires authentication
- Validates age >= 16 using `Period.between(dob, LocalDate.now()).getYears() >= 16`
- Returns 400 Bad Request with message if too young
- Sets `user.dateOfBirth`, saves, evicts cache, returns UserDto
- Add `@AuditLog` annotation

### 2.5 Modify Existing Profile Update (Name Endpoint)
- **File:** `server/.../user/api/controller/ProfileController.java`
- Existing `PATCH /api/v1/identity/profile` already handles firstName, middleName, lastName
- After saving name fields, check: if `termsAcceptedAt != null && dateOfBirth != null && !"-".equals(firstName)` → set `onboardingCompleted = true`
- Save and return updated UserDto

### 2.6 Security — Verify No Changes Needed
- `/api/v1/identity/profile/**` is already protected (not in whitelist)
- New sub-paths `/terms` and `/dob` inherit authentication requirement
- Confirm with a quick test or code review

## Acceptance Criteria
- [ ] `PATCH /profile/terms` sets termsAcceptedAt with server timestamp
- [ ] `PATCH /profile/dob` validates age >= 16 and returns 400 if too young
- [ ] `PATCH /profile` (name) auto-sets `onboardingCompleted = true` when all fields present
- [ ] All endpoints require valid Bearer token
- [ ] All endpoints have `@AuditLog` annotation
- [ ] UserDto response includes all onboarding fields
