# Phase 1: Backend — Database & Entity Changes

## Overview
Add onboarding fields to the User entity and database schema in the Identity Service.

## Steps

### 1.1 Create Flyway Migration V11
- **File:** `server/services/selection-committee-identity-service/src/main/resources/db/migration/V11__user_onboarding_fields.sql`
- Add `date_of_birth DATE` column (nullable)
- Add `terms_accepted_at TIMESTAMPTZ` column (nullable)
- Add `onboarding_completed BOOLEAN NOT NULL DEFAULT false` column

### 1.2 Update User Entity
- **File:** `server/.../user/persistence/entity/User.java`
- Add `LocalDate dateOfBirth` field with `@Column(name = "date_of_birth")`
- Add `Instant termsAcceptedAt` field with `@Column(name = "terms_accepted_at")`
- Add `boolean onboardingCompleted` field with `@Column(name = "onboarding_completed", nullable = false)`, default `false`
- Lombok `@Getter`/`@Setter` already on class — no extra annotations needed
- Update `@Builder` defaults if necessary

### 1.3 Update UserDto Record
- **File:** `server/.../user/api/dto/UserDto.java`
- Add `LocalDate dateOfBirth` to record parameters
- Add `Instant termsAcceptedAt` to record parameters
- Add `boolean onboardingCompleted` to record parameters

### 1.4 Verify MapStruct Mapper
- **File:** `server/.../user/shared/mapper/UserDtoMapper.java`
- Confirm auto-mapping works for new fields (field names match)
- Verify Lombok `isOnboardingCompleted()` getter is handled by MapStruct
- Check generated mapper code after compilation

## Acceptance Criteria
- [ ] Migration V11 runs successfully against identity schema
- [ ] User entity compiles with new fields
- [ ] UserDto includes new fields in JSON response
- [ ] Existing tests still pass
