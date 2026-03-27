# Phase 12: Testing

**Depends on**: Phase 1-11
**Blocks**: Phase 13 (deployment confidence)

## Unit Tests (Vitest + React Testing Library + MSW)

### Hooks
- [ ] `tests/unit/hooks/use-auth.test.ts`:
  - Login: sets user, isAuthenticated
  - Logout: clears user, redirects
  - Refresh: updates token, maintains session
  - Role checking: correct role from JWT
- [ ] `tests/unit/hooks/use-debounce.test.ts`:
  - Value updates after delay
  - Cancels previous timeout on new value
- [ ] `tests/unit/hooks/use-file-upload.test.ts`:
  - 4-step flow: metadata → presign → upload → confirm
  - Error handling per step
  - Progress tracking

### API Client
- [ ] `tests/unit/lib/api-client.test.ts`:
  - Attaches Authorization header
  - Attaches X-Request-Id on POST/PUT/PATCH
  - Retries on 401 with refresh
  - Redirects to login on refresh failure
  - Normalizes error responses

### Components
- [ ] `tests/unit/components/login-form.test.tsx`:
  - Renders email and password fields
  - Validates required fields
  - Validates email format
  - Submits with correct payload
  - Shows error on failed login
- [ ] `tests/unit/components/document-upload.test.tsx`:
  - Renders dropzone
  - Validates file type
  - Validates file size
  - Shows progress during upload
  - Shows success/error states
- [ ] `tests/unit/components/data-table.test.tsx`:
  - Renders columns and data
  - Pagination controls work
  - Sorting toggles
  - Empty state shown when no data
  - Loading skeleton shown
- [ ] `tests/unit/components/role-guard.test.tsx`:
  - Renders children when role matches
  - Renders nothing when role doesn't match
  - Handles multiple allowed roles

## E2E Tests (Playwright)

- [ ] `tests/e2e/auth.spec.ts`:
  - Register → verify email → login → see dashboard → logout
  - Login with wrong password → error shown
  - Access protected route without login → redirected to /login
- [ ] `tests/e2e/applicant-flow.spec.ts`:
  - Login as applicant
  - Upload a document → see it in list
  - Fill application form (select faculty, cathedra, program)
  - Submit application → status changes
- [ ] `tests/e2e/operator-review.spec.ts`:
  - Login as operator
  - See assigned application queue
  - Open application → review documents
  - Accept application → status updates
  - Return application with reason → notification sent
- [ ] `tests/e2e/admin-users.spec.ts`:
  - Login as admin
  - View user list
  - Change user role → confirm → verify change
  - Lock/unlock user

## Coverage Targets
- `src/lib/`: 70%+
- `src/components/`: 50%+
- `src/hooks/`: 70%+

## MSW Setup
- [ ] `tests/mocks/handlers.ts` — API mock handlers matching gateway routes
- [ ] `tests/mocks/server.ts` — MSW server setup for Vitest
- [ ] `tests/mocks/browser.ts` — MSW browser setup for development (optional)

## Deliverables
- Unit test suite passing with `pnpm test`
- E2E test suite passing with `pnpm test:e2e`
- Coverage reports generated
- MSW mocks for all critical API endpoints
