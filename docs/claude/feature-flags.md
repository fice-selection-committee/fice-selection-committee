# Feature Flag Platform: Architecture & Integration Plan

This document governs how the feature flag system is designed, implemented, and operated in the FICE Selection Committee repository. Every instruction is binding for Claude Code when working with feature flags.

---

## 1. Current-State Analysis

### 1.1 What Exists

**Backend — Environment Service** (source of truth):
- `FeatureFlag` entity at `server/services/selection-committee-environment-service/.../features/persistence/entity/FeatureFlag.java` with: `key`, `enabled`, `description`, `targetEnvironments`, `targetScopes`, `targetUserIds`, `targetRoleIds`, `requiredPermissionIds`, `rolloutPercentage`, `startDate`, `endDate`, `targetingStrategy` (AND/OR), audit fields
- `FeatureFlagService.java` — CRUD + evaluation with Redis caching (5min TTL), Micrometer metrics
- `FeatureFlagServiceUtils.java` — deterministic evaluation: enable gate -> date gate -> global check -> environment/user/role/permission/rollout targeting
- `FeatureFlagController.java` — REST API: GET/POST/PUT/DELETE with `@PreAuthorize("hasAuthority('ADMIN')")` on writes
- Flyway migration `V1__create_feature_flags.sql` — schema with element collection tables for targeting
- Rollout hashing: `(Objects.hash(flag.getKey(), userId) & 0x7FFFFFFF) % 100`

**Backend — Identity Service** (consumer):
- `FeatureFlagCacheManager.java` — TTL-based local cache with circuit breaker (Resilience4j), rate-limited retries, stale-cache fallback
- `EnvServiceClient.java` — Feign client calling `GET /flags?scope=identity`
- `FeatureFlags.java` — Spring `@ConfigurationProperties` holder
- `PropertySourceImpl.java` — integrates flags into Spring property resolution

**Gateway**: Routes `/api/v1/feature-flags/**` to environment-service:8085, circuit breaker + rate limiter (20 req/s, 40 burst)

**Frontend**:
- `client/web/src/lib/api/environment.ts` — `getFeatureFlags()`, `toggleFlag()`
- `client/web/src/app/(dashboard)/admin/settings/page.tsx` — admin-only toggle UI
- `queryKeys.featureFlags.all()` in query-keys.ts
- **No** `useFeatureFlag` hook, no `FeatureFlagProvider`, no component-level consumption pattern

**Tests**:
- `FeatureFlagCacheIT.java`, `FeatureFlagControllerIT.java` in environment-service
- No frontend feature-flag test utilities
- No E2E flag override mechanism

### 1.2 Gaps

| Gap | Severity | Impact |
|-----|----------|--------|
| No frontend consumption pattern (hook, provider, context) | Critical | Components cannot react to flags |
| No local override mechanism for dev/QA | High | Forces shared env mutations for testing |
| No flag type classification (release, ops, experiment) | Medium | No lifecycle governance |
| No ownership/lifecycle fields (owner, type, status, expiresAt) | Medium | No accountability or cleanup |
| Weak rollout hash (`Objects.hash`) | Medium | Java-version-dependent, poor distribution |
| No experiment/variant support | Medium | Cannot run A/B tests |
| No audit trail for evaluations beyond counter metric | Low | Cannot debug individual evaluations |
| Frontend FeatureFlag type is incomplete | Low | Admin UI limited |

### 1.3 Strengths

- Centralized source of truth (environment-service) with proper service boundary
- Rich targeting (user, role, permission, environment, scope, rollout, date)
- Circuit breaker + stale-cache fallback in identity-service consumer
- Redis caching with full eviction on writes
- Gateway rate limiting
- Audit events infrastructure exists (V2 migration, RabbitMQ ingest)

---

## 2. Conceptual Model and Boundaries

### 2.1 Flag Type Taxonomy

| Type | Purpose | Evaluation Owner | Frontend-Safe? | Lifespan |
|------|---------|-----------------|----------------|----------|
| **RELEASE** | Gate unfinished features | Backend + Frontend | Yes | Temporary |
| **OPS** | Emergency disable / kill-switch | Backend (authoritative) | Frontend reads, backend enforces | Permanent |
| **EXPERIMENT** | A/B test with variants | Backend (assignment), Frontend (rendering) | Yes (variant) | Temporary |
| **PERMISSION_ADJACENT** | Role/permission-gated access | Backend (authoritative) | Frontend reads for UX | Semi-permanent |

### 2.2 Separation Rules

```
Feature Flags != Permissions
  Permissions = "is the user allowed?"     (identity-service)
  Flags       = "is this feature available?" (environment-service)

Feature Flags != Configuration
  Config = static per-environment values (DB URL, timeouts)
  Flags  = dynamic, targetable, lifecycle-managed gates

Feature Flags != Experiments
  Flag = boolean (on/off) with targeting
  Experiment = multi-variant with assignment + exposure tracking (built ON TOP of flags)
```

### 2.3 Override Hierarchy

```
Priority  Layer                    Scope              Persistence
1 (high)  URL parameter            Single pageview    Ephemeral
2         localStorage override    Single browser     localStorage
3         Test fixture override    Single test run    Test setup/teardown
4         Per-user DB targeting    Specific user      DB
5         Per-role DB targeting    Specific role      DB
6         Rollout percentage       % of users         DB
7         Environment targeting    Specific env       DB
8         Date range gate          Time window        DB
9 (low)   Global enabled/disabled  All users          DB
```

---

## 3. ABSmartly Adaptation

### 3.1 Adoption Decisions

| Concept | Decision | Rationale |
|---------|----------|-----------|
| Murmur3 deterministic hashing with seed isolation | **Adopt** | Current `Objects.hash` is weak |
| Separate traffic seed from variant seed | **Adopt later** | Only for multi-variant experiments |
| `fullOnVariant` concept | **Adapt** | Map to `FULL_ON` lifecycle status |
| Exposure tracking | **Adopt for experiments only** | Release flags don't need it |
| Override/Custom distinction | **Adopt** | `localOverride` (dev) vs `adminTargeting` (tracked) |
| Audience JSON DSL | **Avoid** | Our attribute targeting is sufficient |
| Multi-unit-type hashing | **Avoid** | Single unit type (userId) |

### 3.2 Improved Rollout Hash

```java
// Target: Murmur3-based, deterministic, well-distributed
int hash = Murmur3.hash32(flag.getKey() + ":" + userId, SEED);
int bucket = (hash & 0x7FFFFFFF) % 10000; // 0.01% granularity
return bucket < (percentage * 100);
```

---

## 4. Target Architecture

### 4.1 Overview

```
Frontend (Next.js)
  FeatureFlagProvider → useFeatureFlag → FeatureGate
  Local Override Layer (localStorage / URL params / DevTools)
       │
       │ GET /api/v1/feature-flags (TanStack Query, stale: 60s, poll: 300s)
       ↓
  Gateway (:8080) — circuit breaker, rate limit
       ↓
  Environment Service (:8085)
    Flag CRUD (admin) │ Evaluation Engine │ Audit Publisher
    Redis Cache (5m)  │ PostgreSQL (SoT)
       ↑
  Backend Services (Feign + circuit breaker + stale cache)
```

### 4.2 New Components

| Component | Location | Purpose |
|-----------|----------|---------|
| `POST /evaluate-batch` | environment-service | Batch evaluate for user context |
| `FeatureFlagProvider` | `client/web/src/providers/feature-flag-provider.tsx` | React context |
| `useFeatureFlag(key)` | `client/web/src/hooks/use-feature-flag.ts` | Flag consumption hook |
| `FeatureGate` | `client/web/src/components/shared/feature-gate.tsx` | Declarative guard |
| `useFeatureFlagOverrides` | `client/web/src/hooks/use-feature-flag-overrides.ts` | Dev override management |
| `FeatureFlagDevTools` | `client/web/src/components/dev/feature-flag-devtools.tsx` | Dev panel |
| V3 migration | environment-service | type, owner, status, expires_at |
| Murmur3 hash | `FeatureFlagServiceUtils.java` | Improved rollout assignment |

### 4.3 Failure Handling

| Failure | Behavior |
|---------|----------|
| Environment-service down | Backend: circuit breaker -> stale cache. Frontend: stale query cache |
| Redis down | Falls through to PostgreSQL |
| Flag not found | Returns `false` (safe default) |
| Network partition | Both layers serve stale data |

---

## 5. Frontend Integration Design

### 5.1 File Structure

```
providers/feature-flag-provider.tsx    — Context + TanStack Query
hooks/use-feature-flag.ts             — Per-flag hook with override support
hooks/use-feature-flag-overrides.ts   — Local override management
components/shared/feature-gate.tsx    — Declarative guard
components/dev/feature-flag-devtools.tsx — Dev override panel
lib/feature-flags/keys.ts            — Flag key registry
types/feature-flag.ts                 — Types
```

### 5.2 Core Abstractions

```typescript
// Provider context shape
interface FeatureFlagContextValue {
  flags: Record<string, boolean>;
  isLoading: boolean;
  overrides: Record<string, boolean>;
  setOverride: (key: string, value: boolean | null) => void;
  clearOverrides: () => void;
}

// Hook API
function useFeatureFlag(key: string): { enabled: boolean; isLoading: boolean }

// Guard component
<FeatureGate flag="key" fallback={<Fallback />}>
  <Feature />
</FeatureGate>
```

### 5.3 Anti-Patterns (Forbidden)

1. Never import `environmentApi` directly in components — use `useFeatureFlag`
2. Never use flag keys as magic strings — use `FeatureFlags` registry
3. Never nest `FeatureGate` more than 2 levels
4. Never use flags for authorization — use `RoleGuard` + `@PreAuthorize`
5. Never use `any` for flag state

---

## 6. Testing Integration

### 6.1 Backend

```java
// Unit: Mock service
@MockitoBean FeatureFlagService featureFlagService;
when(featureFlagService.isEnabled("key")).thenReturn(true);

// Integration: DB insert
@Sql("INSERT INTO environment.feature_flags ...")
```

### 6.2 Frontend Unit (Vitest)

```typescript
// Mock hook
vi.mock("@/hooks/use-feature-flag", () => ({
  useFeatureFlag: (key) => ({ enabled: key === "test", isLoading: false }),
}));

// Or use test provider
<FeatureFlagTestProvider flags={{ "key": true }}>
  <Component />
</FeatureFlagTestProvider>
```

### 6.3 E2E (Playwright)

```typescript
// Preferred: localStorage override (no backend mutation)
await page.addInitScript(() => {
  localStorage.setItem("__ff_overrides_v1", JSON.stringify({ "key": true }));
});
```

### 6.4 Scenario Matrix

Every flag-dependent feature must test: flag ON, flag OFF, flag missing (=OFF).

---

## 7. Management and Governance

### 7.1 Required Metadata

key, type, owner, description, status, enabled, rolloutPercentage, targetingStrategy, targeting sets, startDate/endDate, expiresAt, audit fields.

### 7.2 Lifecycle States

```
DRAFT → ACTIVE → FULL_ON → DEPRECATED → ARCHIVED
               ↗ FULL_OFF ↗
```

### 7.3 Cleanup Rules

1. Expired + ACTIVE → auto DEPRECATED
2. DEPRECATED > 30d → logged warning
3. DEPRECATED > 90d → auto ARCHIVED
4. ARCHIVED excluded from API responses

---

## 8. Security Architecture

### 8.1 Key Rules

- Backend is authoritative; frontend is projection only
- All management requires `administration` permission
- Frontend receives evaluated booleans, not raw targeting rules
- Local overrides only active in non-production or for admins
- All mutations produce audit events
- OPS flags require explicit confirmation header

### 8.2 Trust Boundaries

```
UNTRUSTED: Browser, Telegram bot
TRUSTED: Gateway (authn), Environment-service (authz + evaluation), PostgreSQL, Redis
```

---

## 9. Telegram Bot Decision

**Recommendation: Build read-only first.**

| Phase | Capability |
|-------|-----------|
| Phase 1 | List/search/view flags, notifications on changes |
| Phase 2 | Toggle with confirmation + identity verification |
| Never | Create/delete flags, change rollout percentage |

Security: bot token in secrets, identity mapping required, rate limited, goes through gateway.

---

## 10. Data Model (V3 Migration)

```sql
ALTER TABLE environment.feature_flags
  ADD COLUMN flag_type VARCHAR(30) DEFAULT 'RELEASE' NOT NULL,
  ADD COLUMN owner VARCHAR(100),
  ADD COLUMN status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
  ADD COLUMN expires_at TIMESTAMP;
```

---

## 11. Implementation Phases

### Phase 1: Foundation (Backend)
- V3 migration, entity updates, status gate in evaluation, Murmur3 hash, evaluate-batch endpoint

### Phase 2: Frontend Integration
- Provider, hook, gate, override system, DevTools, test provider, key registry

### Phase 3: Testing
- Playwright fixture, backend test helpers, unit/integration tests

### Phase 4: Governance & Operations
- Enhanced admin UI, stale flag detection, Grafana dashboard, audit events

### Phase 5: Telegram Bot (Separate Track)
- Bot service scaffold, read-only commands, user mapping, notifications

### Phase 6: Experimentation (Future)
- Variants, ABSmartly-style hashing, exposure tracking, frontend variant API
