# Phase 5: Frontend — Routing, Middleware & Auth Provider

## Overview
Enforce the onboarding flow via middleware and auth provider changes. Make it impossible to skip steps.

## Steps

### 5.1 Update Session Cookie Structure
- **File:** `client/web/src/lib/auth/session.ts`
- Add `onboardingCompleted: boolean` to session cookie data
- Update `setSessionCookie()` to accept and store the new field
- Update `parseSessionCookie()` to extract the new field
- Default `onboardingCompleted` to `false` for backwards compatibility

### 5.2 Update Auth Provider
- **File:** `client/web/src/providers/auth-provider.tsx`
- **verifyMagicLink()**: Change redirect logic from `firstName === "-"` check to `!userData.onboardingCompleted ? "/onboarding" : getRoleDefaultRoute(userData.role)`
- **restoreSession()**: After fetching user data, if `!userData.onboardingCompleted`, consider routing to `/onboarding`
- **setSessionCookie() calls**: Pass `onboardingCompleted` from user data (extracted from JWT payload or user object)
- All `setSessionCookie()` calls must be updated (in `scheduleRefresh`, `restoreSession`, `verifyMagicLink`)

### 5.3 Update Middleware
- **File:** `client/web/src/middleware.ts`
- Add onboarding enforcement rules (BEFORE role-based routing):
  1. `session && !session.onboardingCompleted && !isPublicRoute && pathname !== "/onboarding"` → redirect to `/onboarding`
  2. `session && session.onboardingCompleted && pathname === "/onboarding"` → redirect to role dashboard
  3. `!session && pathname === "/onboarding"` → redirect to `/login`
- This ensures:
  - Users with incomplete onboarding CANNOT access any dashboard route
  - Users with complete onboarding CANNOT go back to onboarding
  - Unauthenticated users CANNOT access onboarding

### 5.4 Update Route Roles
- **File:** `client/web/src/lib/auth/roles.ts`
- `/onboarding` should NOT be in `PUBLIC_ROUTES` (requires auth)
- `/onboarding` should NOT be in `ROUTE_ROLES` (all roles allowed)
- The middleware handles it as a special case before role checks

### 5.5 Remove Old Profile Complete Page
- **File:** `client/web/src/app/(dashboard)/applicant/profile/complete/page.tsx` — Delete
- Remove any references to `/applicant/profile/complete` in the codebase

## Acceptance Criteria
- [ ] Authenticated user with `onboardingCompleted=false` is ALWAYS redirected to `/onboarding` regardless of URL typed
- [ ] Authenticated user with `onboardingCompleted=true` is redirected AWAY from `/onboarding` to their dashboard
- [ ] Unauthenticated user on `/onboarding` goes to `/login`
- [ ] Page refresh during onboarding returns to correct step
- [ ] After magic link verification, new users go to `/onboarding`
- [ ] After completing onboarding, user cannot return to onboarding page
- [ ] Old profile/complete route no longer exists
