# Phase 11: Admin Pages

**Depends on**: Phase 4, 6
**Blocks**: None

## Checklist

- [ ] `src/app/(dashboard)/admin/page.tsx` — Admin dashboard:
  - System overview cards: total users, active sessions, pending applications
  - Recent admin actions (audit log preview)
  - Quick links to user management, roles, settings

- [ ] `src/app/(dashboard)/admin/users/page.tsx` — User management:
  - Data table with columns: name, email, role, status (active/locked), last login
  - Search by name or email
  - Filters: role, status
  - Inline actions: lock/unlock toggle
  - Click row → user detail

- [ ] `src/app/(dashboard)/admin/users/[id]/page.tsx` — User detail:
  - User profile info (read-only or editable by admin)
  - Current role display
  - Role change select + confirm dialog
  - Lock/unlock button
  - Activity log for this user
  - API: `/api/v1/identity/admin/users/{id}`

- [ ] `src/app/(dashboard)/admin/roles/page.tsx` — Role management:
  - Table of all roles
  - Columns: role name, description, user count, permissions count
  - Click row → role detail

- [ ] `src/app/(dashboard)/admin/roles/[id]/page.tsx` — Role detail:
  - Role metadata
  - Permission matrix (checkbox grid)
  - Add/remove permissions
  - Save changes button
  - API: `/api/v1/identity/admin/roles/{id}`

- [ ] `src/app/(dashboard)/admin/settings/page.tsx` — Feature flags:
  - List of feature flags from environment-service
  - Toggle switches per flag
  - Scope management (if applicable)
  - API: `/api/v1/feature-flags/*`

## Deliverables
- Admin can manage users (CRUD, lock/unlock, role assignment)
- Admin can manage roles and permissions
- Feature flag management via environment-service
- All admin API calls go through `/api/v1/identity/admin/**`
