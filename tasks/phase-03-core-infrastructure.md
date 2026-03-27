# Phase 3: Core Infrastructure — API Client, Auth, Providers

**Depends on**: Phase 1, Phase 2
**Blocks**: All page phases (4-11)

## 3A. Types (`src/types/`)

- [ ] `api.ts` — Generic types: `PaginatedResponse<T>` (matches Spring `Page<T>`: content, totalElements, totalPages, number, size), `ApiError`, `ApiResponse<T>`
- [ ] `auth.ts` — `User`, `Role` (enum matching backend), `LoginRequest`, `RegisterRequest`, `AuthResponse`, `TokenPayload`
- [ ] `admission.ts` — `Application`, `ApplicationStatus` (enum), `Faculty`, `Cathedra`, `EducationalProgram`, `Privilege`
- [ ] `document.ts` — `Document`, `DocumentType` (enum), `DocumentStatus` (enum), `PresignedUrlResponse`
- [ ] `contract.ts` — `Contract`, `ContractStatus` (enum)
- [ ] `order.ts` — `Order`, `OrderType` (enum: ENROLL, EXPEL), `OrderStatus` (enum)
- [ ] `group.ts` — `Group`, `GroupAssignment`, `DistributionProtocol`

## 3B. API Client (`src/lib/api/`)

- [ ] `client.ts` — Axios instance:
  - `baseURL`: `process.env.NEXT_PUBLIC_API_BASE` (gateway :8080)
  - `withCredentials: true`
  - Request interceptor: attach `Authorization: Bearer <token>` from `getAccessToken()` callback
  - Request interceptor for POST/PUT/PATCH: auto-attach `X-Request-Id: crypto.randomUUID()`
  - Response interceptor: on 401 → call refresh → retry original request once → if still 401 redirect to /login
  - Response interceptor: normalize errors to `ApiError` type
- [ ] `auth.ts` — `login()`, `register()`, `verifyEmail()`, `refresh()`, `logout()`, `getUser()`
  - Endpoints: `/api/v1/auth/login`, `/api/v1/auth/register`, `/api/v1/auth/verify`, `/api/v1/auth/refresh`, `/api/v1/auth/logout`, `/api/v1/auth/user`
- [ ] `admissions.ts` — `findAll()`, `findById()`, `create()`, `submit()`, `accept()`, `reject()`, `enroll()`, `archive()`
  - Endpoints: `/api/v1/applications/*`
- [ ] `documents.ts` — `list()`, `getById()`, `createMetadata()`, `getPresignedUrl()`, `confirmUpload()`, `getDownloadUrl()`
  - Endpoints: `/api/v1/documents/*`
- [ ] `contracts.ts` — `findAll()`, `findById()`, `register()`
  - Endpoints: `/api/v1/contracts/*`
- [ ] `orders.ts` — `findAll()`, `findById()`, `create()`, `generatePdf()`
  - Endpoints: `/api/v1/orders/*`
- [ ] `groups.ts` — `findAll()`, `findById()`, `autoDistribute()`, `assign()`
  - Endpoints: `/api/v1/groups/*`, `/api/v1/group-assignments/*`
- [ ] `users.ts` — `findAll()`, `findById()`, `updateRole()`, `lock()`, `unlock()`
  - Endpoints: `/api/v1/identity/admin/users/*`
- [ ] `roles.ts` — `findAll()`, `findById()`, `updatePermissions()`
  - Endpoints: `/api/v1/identity/admin/roles/*`
- [ ] `environment.ts` — `getFeatureFlags()`, `toggleFlag()`
  - Endpoints: `/api/v1/feature-flags/*`

## 3C. Authentication (`src/lib/auth/`, `src/providers/`, `src/middleware.ts`)

- [ ] `src/lib/auth/roles.ts` — Role constants + permission map + route-to-role mapping:
  ```ts
  export const ROLES = {
    APPLICANT: 'APPLICANT',
    OPERATOR: 'OPERATOR',
    EXECUTIVE_SECRETARY: 'EXECUTIVE_SECRETARY',
    DEPUTY_EXECUTIVE_SECRETARY: 'DEPUTY_EXECUTIVE_SECRETARY',
    CONTRACT_MANAGER: 'CONTRACT_MANAGER',
    GROUP_ASSIGNMENT_MANAGER: 'GROUP_ASSIGNMENT_MANAGER',
    ADMIN: 'ADMIN',
  } as const;

  export const ROUTE_ROLES: Record<string, Role[]> = {
    '/applicant': ['APPLICANT'],
    '/operator': ['OPERATOR'],
    '/secretary': ['EXECUTIVE_SECRETARY', 'DEPUTY_EXECUTIVE_SECRETARY'],
    '/contracts': ['CONTRACT_MANAGER', 'EXECUTIVE_SECRETARY', 'DEPUTY_EXECUTIVE_SECRETARY'],
    '/groups': ['GROUP_ASSIGNMENT_MANAGER'],
    '/admin': ['ADMIN'],
  };
  ```
- [ ] `src/providers/auth-provider.tsx` — AuthContext:
  - State: `user`, `isAuthenticated`, `isLoading`
  - Actions: `login(email, password)`, `logout()`, `refresh()`
  - On mount: attempt `refresh()` to restore session
  - Store accessToken in React ref (not localStorage)
  - After login/refresh: set `__session` cookie (non-httpOnly) with `{role, exp}` for middleware
  - Auto-refresh interval before token expiry (parse `exp` from JWT payload)
- [ ] `src/hooks/use-auth.ts` — `useContext(AuthContext)` wrapper with error if outside provider
- [ ] `src/middleware.ts` — Next.js edge middleware:
  - Read `__session` cookie → parse role + exp
  - Public routes (`/login`, `/register`, `/verify-email`, `/forgot-password`, `/reset-password`): redirect to dashboard if authenticated
  - Protected routes (`/(dashboard)/**`): redirect to `/login` if unauthenticated
  - Role check: match route prefix against `ROUTE_ROLES` map → redirect to dashboard root if unauthorized

## 3D. Providers & TanStack Query

- [ ] `src/providers/query-provider.tsx` — QueryClientProvider:
  ```ts
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { staleTime: 60_000, refetchOnWindowFocus: false, retry: 1 },
    },
  });
  ```
- [ ] `src/providers/toast-provider.tsx` — Sonner `<Toaster />` with lavender theme
- [ ] `src/app/layout.tsx` — compose all providers: QueryProvider > AuthProvider > ToastProvider > children
- [ ] `src/lib/queries/query-keys.ts` — centralized key factory:
  ```ts
  export const queryKeys = {
    auth: { user: () => ['auth', 'user'] as const },
    admissions: {
      all: () => ['admissions'] as const,
      list: (params) => ['admissions', 'list', params] as const,
      detail: (id) => ['admissions', 'detail', id] as const,
    },
    // ... per domain
  };
  ```
- [ ] Create per-domain query hooks in `src/lib/queries/`:
  - `admissions.queries.ts`, `documents.queries.ts`, `contracts.queries.ts`, `orders.queries.ts`, `groups.queries.ts`, `users.queries.ts`
  - Each with `useQuery` for reads, `useMutation` with optimistic updates for writes
  - Global `onError` shows toast notification

## Deliverables
- Typed API client with auth interceptors and X-Request-Id idempotency
- Working auth flow: login → store token → refresh → logout
- Middleware protecting routes by auth + role
- TanStack Query configured with global defaults
- All TypeScript types for backend entities
