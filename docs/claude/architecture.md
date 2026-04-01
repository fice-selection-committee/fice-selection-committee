# Architecture Reference

This file contains the system topology, shared library catalog, infrastructure specifications, frontend architecture, and Docker compose topology. Claude MUST read this file when activating the Solution Architect, Docker Runtime, or Distributed Systems Risk Reviewer agents, or when performing Service Analysis, Distributed Flow Reasoning, or Runtime Topology Validation skills.

All facts in this file are derived from reading the repository. If any fact conflicts with current file contents, trust the file — this document may be stale.

---

## 1. Service Topology

| Service | Directory | Port | Responsibility | Key Dependencies |
|---|---|---|---|---|
| **Gateway** | `selection-committee-gateway` | 8080 | API routing, rate limiting, circuit breakers, CORS, JWT validation | Redis, all downstream services |
| **Identity Service** | `selection-committee-identity-service` | 8081 | User auth, JWT (RS256) issuance, RBAC, user management, magic link login | PostgreSQL, Redis, RabbitMQ, Environment Service |
| **Admission Service** | `selection-committee-admission-service` | 8083 | Application workflows, PDF generation, orders, group assignment | PostgreSQL, Redis, RabbitMQ, Identity Service, Documents Service |
| **Documents Service** | `selection-committee-documents-service` | 8084 | S3/MinIO file storage, document metadata | PostgreSQL, MinIO, Identity Service, Admission Service |
| **Environment Service** | `selection-committee-environment-service` | 8085 | Feature flags, cached configuration | PostgreSQL, Redis, RabbitMQ |
| **Notifications Service** | `selection-committee-notifications-service` | 8086 | Email delivery via RabbitMQ consumer | RabbitMQ, Mailpit (SMTP) |
| **Computer Vision** | `selection-committee-computer-vision` | — | (Scaffolded, not yet operational) | — |
| **E2E Tests** | `selection-committee-e2e-tests` | — | Cross-service integration and performance tests | Full service stack |

All service source is at `server/services/<directory>/`.

**Architectural rules**:
- The **Gateway** is the sole controlled entry point for external traffic. No service exposes endpoints directly to external consumers.
- The **Frontend** is a consumer of gateway-exposed APIs — never an authority on business logic, permissions, or data truth.
- Each service owns its own database schema via Flyway migrations. Cross-database access is forbidden.
- Inter-service communication uses Feign HTTP clients (synchronous) or RabbitMQ events (asynchronous). No other mechanism.

---

## 2. Shared Libraries

Located in `server/libs/`. Included in the root `server/settings.gradle.kts`. Published to Maven Local for local development and GitHub Packages for CI.

| Library | Directory | Purpose |
|---|---|---|
| `sc-bom` | `server/libs/sc-bom` | Bill of Materials — version alignment for all libraries |
| `sc-auth-starter` | `server/libs/sc-auth-starter` | JWT validation, RBAC, security filter chains, `IdentityServiceClient` Feign client |
| `sc-common` | `server/libs/sc-common` | Shared exceptions, DTOs, base response structures, utilities |
| `sc-event-contracts` | `server/libs/sc-event-contracts` | RabbitMQ event payload definitions (the contract surface for async messaging) |
| `sc-test-common` | `server/libs/sc-test-common` | Testcontainers helpers, test fixtures, base test classes |
| `sc-observability-starter` | `server/libs/sc-observability-starter` | Micrometer Prometheus metrics, Zipkin distributed tracing auto-configuration |
| `sc-s3-starter` | `server/libs/sc-s3-starter` | MinIO/S3 integration auto-configuration |

**Critical operational rule**: A library change affects every consumer. Always run `./gradlew publishToMavenLocal` from `server/` after any library modification, before building any consuming service. Failure to do this causes service builds to use stale library versions — builds may pass locally but fail in CI or produce incorrect behavior.

**Version management**: Library versions are tracked in `server/version.properties`. Bump the version after any library change.

---

## 3. Frontend Architecture

**Location**: `client/web/`

| Aspect | Detail |
|---|---|
| **Framework** | Next.js 16 (App Router, Turbopack) |
| **Language** | TypeScript (strict mode) |
| **Routing** | Role-based route groups: `(auth)` for login/register/onboarding, `(dashboard)` for protected pages (applicant, operator, secretary, admin) |
| **Proxy** | `src/proxy.ts` — guards routes by role, checks JWT validity and onboarding status (Next.js 16 proxy convention) |
| **Server state** | TanStack Query (query key factories in `src/lib/queries/`) |
| **Client state** | Zustand (stores in `src/stores/`) |
| **Forms** | React Hook Form + Zod validation |
| **UI components** | shadcn/ui in `src/components/ui/`, feature components alongside their routes |
| **API client** | Axios with JWT interceptor in `src/lib/api/` |
| **Types** | `src/types/` — must match backend contracts |
| **Formatting** | Biome (2-space indent, double quotes, semicolons, trailing commas, line width 100) |
| **Pre-commit** | Husky + lint-staged runs Biome on staged files |
| **Path alias** | `@/` maps to `src/` |

**Architectural constraints**:
- The frontend is a projection layer. All authorization, business rules, and data validation MUST be enforced on the backend.
- Frontend validation is UX convenience, not a security boundary.
- Types in `src/types/` must match backend DTOs. Never use `any` for API responses.
- Use existing Axios client with JWT interceptor — do not create alternative HTTP clients.

---

## 4. Infrastructure Components

All infrastructure runs in Docker via `infra/docker-compose.yml`. Never assume these are installed on the host.

| Component | Image | Purpose | Ports | Configuration |
|---|---|---|---|---|
| **PostgreSQL** | `postgres:16` | One database per service (created by init scripts) | `$PGPORT:5432` | `infra/postgres/init/01-create-dbs.sql` |
| **Redis** | `redis:7` | Session caching, rate limiting, feature flag caching | `$REDIS_PORT:6379` | Password via `$REDIS_PASSWORD` |
| **RabbitMQ** | `rabbitmq:3-management` | Topic exchange `events`; queues for notifications, CV tasks, admission events | `5672`, `15672` | `infra/rabbitmq/definitions.json` |
| **MinIO** | `minio/minio` | S3-compatible object storage for documents | `$MINIO_PORT:9000`, `$MINIO_CONSOLE_PORT:9001` | Init via `infra/minio/init.sh` |
| **Mailpit** | `axllent/mailpit` | Local SMTP server + web UI for email testing | `$MAILPIT_SMTP:1025`, `$MAILPIT_HTTP:8025` | — |
| **Zipkin** | `openzipkin/zipkin` | Distributed tracing | `$ZIPKIN_PORT:9411` | — |
| **Prometheus** | `prom/prometheus` | Metrics collection from all services | `$PROMETHEUS_PORT:9090` | `infra/prometheus/prometheus.yml` |
| **Grafana** | `grafana/grafana` | Metrics visualization dashboards | `$GRAFANA_PORT:3001` | `infra/grafana/provisioning/`, `infra/grafana/dashboards/` |

---

## 5. Docker Compose Topology

Two compose files define the platform:

| File | Scope | Contents |
|---|---|---|
| `infra/docker-compose.yml` | Infrastructure | PostgreSQL, Redis, RabbitMQ, MinIO (+init), Mailpit, Zipkin, Prometheus, Grafana |
| `infra/docker-compose.services.yml` | Application | Identity, Environment, Admission, Documents, Notifications, Gateway, Web Frontend |

All containers use the shared network `sc-net`. Every backend service has a Dockerfile at its service directory root. The frontend Dockerfile is at `client/web/docker/Dockerfile`.

**Startup command** (full stack):
```bash
cd infra
docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --build
```

**Startup dependency chain** (actual current state from compose files):

```
PostgreSQL (healthy)   ──► Identity Service ──► Admission Service
                       ──► Environment Service
                       ──► Documents Service
Redis (started)        ──► Identity, Environment, Admission, Gateway
RabbitMQ (started)     ──► Identity, Environment, Admission, Notifications
MinIO (started)        ──► Documents Service
Mailpit (started)      ──► Notifications Service
Identity (healthy)     ──► Admission, Documents, Gateway
Environment (healthy)  ──► Gateway
Admission (healthy)    ──► Gateway
Documents (healthy)    ──► Gateway
Notifications (healthy)──► Gateway
Gateway (healthy)      ──► Web Frontend
```

**JWT secrets**: `infra/secrets/jwtRS256.pk8.pem` and `infra/secrets/jwtRS256.key.pub` are mounted into Identity Service as Docker secrets. Generated via `infra/scripts/gen-jwt-keys.sh`.

---

## 6. Known Gaps and Issues

Claude MUST critically evaluate these rather than assume sufficiency. These are observed from the current compose files and service configuration:

### 6.1 Health Check Gaps
- **RabbitMQ**: Uses `condition: service_started` in all consumers. No health check defined in compose. Services may fail to connect on first startup if RabbitMQ takes time to initialize exchanges and queues from `definitions.json`.
- **Redis**: Same — `service_started` without health check. Services depending on Redis for caching/rate-limiting may fail if Redis isn't ready.

### 6.2 Init Race Conditions
- **MinIO bucket creation**: `minio-init` container depends on `minio: condition: service_started` and runs `init.sh` to create buckets. Documents Service also depends on `minio: condition: service_started` — no guarantee `minio-init` has completed before Documents Service starts.

### 6.3 Environment Variable Inconsistencies
- Identity Service, Admission Service, Notifications Service use: `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`
- Environment Service uses: `RABBIT_HOST`, `RABBIT_PORT`, `RABBIT_USER`, `RABBIT_PASS`
- This inconsistency must be normalized across all services.

### 6.4 `.env.example` Gaps
- `infra/.env.example` is nearly empty. It does not document the full set of environment variables required by both compose files. Variables like `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`, `REDIS_PASSWORD`, `RABBIT_USER`, `RABBIT_PASS`, `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`, `MINIO_BUCKET`, and port mappings are all required but undocumented.

### 6.5 Frontend API Base
- `NEXT_PUBLIC_API_BASE=http://gateway:8080` is the Docker-internal network address. Works for SSR (server-side rendering runs inside the Docker network). Does NOT work for client-side browser requests (browser is on the host, cannot resolve `gateway`).

### 6.6 Missing Services
- **Computer Vision**: Scaffolded at `server/services/selection-committee-computer-vision/` but has no Dockerfile, no compose entry, and appears to have no source code yet.

### 6.7 Compose Version
- Both compose files use `version: "3.9"`. The `version` key is deprecated in modern Docker Compose V2. Not a functional issue but should be cleaned up.
