# Phase 4: Layout Shell & Navigation

**Depends on**: Phase 2, Phase 3
**Blocks**: Phase 5, 7-11

## Checklist

- [ ] `src/app/(auth)/layout.tsx` — Auth layout:
  - Centered content, max-width 480px
  - Logo at top (drawn-frog-logo.png)
  - Lavender background gradient (subtle, not heavy)
  - No sidebar

- [ ] `src/app/(dashboard)/layout.tsx` — Dashboard layout:
  - Sidebar + topbar + main content area
  - Responsive: sidebar hidden on mobile, Sheet-based nav instead
  - Main content area with padding, max-width container

- [ ] `src/components/layout/sidebar.tsx`:
  - Width: 260px expanded, 80px collapsed (CSS variables)
  - Toggle button (chevron icon)
  - Logo at top
  - Navigation items with icons (lucide-react)
  - Active state highlighting (primary color)
  - Role-based menu items (filter by current user role):
    | Role | Menu Items |
    |---|---|
    | APPLICANT | Dashboard, Documents, Application, Profile |
    | OPERATOR | Dashboard, Application Queue, Personal Files |
    | EXECUTIVE_SECRETARY / DEPUTY | Dashboard, Applications, Contracts, Orders, Documents |
    | CONTRACT_MANAGER | Dashboard, Contracts |
    | GROUP_ASSIGNMENT_MANAGER | Dashboard, Group Distribution |
    | ADMIN | Dashboard, Users, Roles, Settings |

- [ ] `src/components/layout/sidebar-nav.tsx`:
  - Nav item component with icon, label (hidden when collapsed), active indicator
  - Uses `next/link` for client-side transitions
  - `usePathname()` for active state

- [ ] `src/components/layout/topbar.tsx`:
  - User avatar (initials fallback)
  - Role badge
  - Notifications bell (placeholder)
  - Logout dropdown
  - Mobile menu trigger (hamburger)

- [ ] `src/components/layout/breadcrumbs.tsx`:
  - Auto-generated from `usePathname()`
  - Map route segments to Ukrainian labels
  - Clickable intermediate crumbs via `next/link`

- [ ] `src/components/layout/mobile-nav.tsx`:
  - Uses shadcn/ui `Sheet` component
  - Same nav items as sidebar
  - Triggered by hamburger in topbar

- [ ] `src/stores/sidebar-store.ts` — Zustand:
  ```ts
  interface SidebarStore {
    isCollapsed: boolean;
    toggle: () => void;
  }
  ```
  - Persisted to localStorage via zustand/middleware

- [ ] `src/app/(dashboard)/page.tsx` — Dashboard root:
  - Redirect to role-specific dashboard based on user role

## Deliverables
- Fully functional dashboard layout with sidebar + topbar
- Role-aware navigation
- Breadcrumbs auto-generated from route
- Responsive mobile navigation
- Sidebar collapse state persisted
