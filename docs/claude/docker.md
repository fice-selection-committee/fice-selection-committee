# Docker-First Platform Model

Docker is the default and preferred runtime for this system. Every workflow instruction, startup guide, and development recommendation MUST prefer Docker-based execution. "Works on my machine" risk is mitigated by containerized, reproducible environments.

Docker is not a convenience detail. It is part of the system design, operational model, and reliability boundary. Claude MUST treat "run everything in Docker" as an architectural concern.

---

## 1. Full Stack Startup

```bash
# From infra/ directory:

# First time setup:
cp .env.example .env                    # Copy and populate all variables
bash scripts/gen-jwt-keys.sh            # Generate RS256 key pair for JWT

# Infrastructure only:
docker compose up -d

# Full stack (infrastructure + all services + frontend):
docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --build

# Full stack with rebuild (after code changes):
docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --build

# Stop everything:
docker compose -f docker-compose.yml -f docker-compose.services.yml down

# Stop + reset all data (destructive):
docker compose -f docker-compose.yml -f docker-compose.services.yml down -v
```

Services build from source via multi-stage Dockerfiles during `docker compose up --build`. No pre-built JARs or manual `./gradlew build` required. The Dockerfiles contain a `lib-builder` stage that publishes shared libraries to mavenLocal, then a `service-builder` stage that compiles the service.

Pre-built images can be pulled from `ghcr.io` using the production override:
```bash
IMAGE_TAG=1.1.0 docker compose -f docker-compose.yml -f docker-compose.services.yml -f docker-compose.prod.yml up -d
```

**Goal state**: A developer clones the repo, copies `.env.example` to `.env`, generates JWT keys, and runs a single `docker compose up -d --build` to get the full platform running. This is now achievable.

---

## 2. Startup Dependency Order

Current actual dependency chain (from compose files):

```
PostgreSQL (healthy)   ──► Identity Service (healthy) ──► Admission Service
                       ──► Environment Service (healthy) ──► Gateway
                       ──► Documents Service (healthy) ──►
Redis (healthy)        ──► Identity, Environment, Admission, Gateway
RabbitMQ (healthy)     ──► Identity, Environment, Admission, Notifications, Telegram Bot
MinIO-Init (completed) ──► Documents Service
Mailpit (started)      ──► Identity, Notifications
All services (healthy) ──► Gateway (healthy) ──► Web Frontend
```

**Health check configuration** (all backend services — both in Dockerfile and compose):
```yaml
healthcheck:
  test: ["CMD", "wget", "-q", "--spider", "http://localhost:<port>/actuator/health"]
  interval: 15s
  timeout: 5s
  retries: 10
  start_period: 30s
```

**Infrastructure health checks**:
- PostgreSQL: `pg_isready` (5s interval, 20 retries)
- Redis: `redis-cli ping` (5s interval, 10 retries)
- RabbitMQ: `rabbitmq-diagnostics check_port_connectivity` (10s interval, 20 retries, 30s start_period)

---

## 3. Multi-Stage Docker Build Architecture

All Java service Dockerfiles use a 3-stage build:

1. **lib-builder** (`eclipse-temurin:21-jdk-alpine`): Copies root Gradle config + shared libraries from `server/`, runs `publishToMavenLocal`
2. **service-builder** (`eclipse-temurin:21-jdk-alpine`): Copies mavenLocal artifacts from lib-builder, copies service source, runs `bootJar`
3. **runtime** (`eclipse-temurin:21-jre-alpine`): Minimal image with only the executable JAR

Build context for all Java services is `server/` (not the service directory). This is configured in both `docker-compose.services.yml` and the CI workflow.

**Standardized across all services**:
- Non-root user: `spring` (UID 10000, GID 10001)
- GC: G1GC with 75% RAM allocation
- HEALTHCHECK directive in every Dockerfile
- `.dockerignore` in every service directory

---

## 4. Compose File Structure

| File | Scope | Usage |
|---|---|---|
| `infra/docker-compose.yml` | Infrastructure | PostgreSQL, Redis, RabbitMQ, MinIO (+init), Mailpit, Zipkin, Prometheus, Grafana |
| `infra/docker-compose.services.yml` | Application | Identity, Environment, Admission, Documents, Notifications, Gateway, Web, Telegram Bot |
| `infra/docker-compose.prod.yml` | Production override | Pulls pre-built images from ghcr.io instead of building locally |

**Removed**: Per-service compose files and `local_infra/` directory. The unified model above replaces all previous alternatives.

---

## 5. Developer Workflow Expectations

### 5.1 Default: Full Docker Stack
```bash
cd infra
docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --build

# View logs for a specific service:
docker compose -f docker-compose.yml -f docker-compose.services.yml logs -f identity-service

# Restart a single service after code change:
docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --build identity-service

# Check health of all services:
docker compose -f docker-compose.yml -f docker-compose.services.yml ps
```

### 5.2 Hybrid: Infrastructure in Docker, Service on Host
For active backend development, run infrastructure in Docker and the target service on the host JVM:

```bash
cd infra && docker compose up -d
cd server/services/selection-committee-identity-service
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

This is a deviation from the Docker-first model. State it explicitly when recommending this approach.

### 5.3 Frontend Development
```bash
cd infra
docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --build
cd client/web
pnpm dev    # Turbopack dev server on port 3000, proxies to gateway at localhost:8080
```

### 5.4 Database Operations
```bash
docker exec -it $(docker ps -qf name=sc-postgres) psql -U <user> -d <db_name>

# Reset all data (destructive):
cd infra
docker compose -f docker-compose.yml -f docker-compose.services.yml down -v
docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --build
```

### 5.5 Never Assume
- PostgreSQL is NOT on the host — it's in Docker.
- Redis is NOT on the host — it's in Docker.
- RabbitMQ is NOT on the host — it's in Docker.
- MinIO is NOT on the host — it's in Docker.
- Mailpit is NOT on the host — it's in Docker.

---

## 6. Docker Configuration Reference

### 6.1 Network
All services use the shared network `sc-net`. This enables inter-service communication using container names as hostnames.

### 6.2 Volumes (Persistent Data)
| Volume | Service | Purpose |
|---|---|---|
| `pgdata` | PostgreSQL | Database files |
| `redis-data` | Redis | Cache persistence |
| `rabbitmq-data` | RabbitMQ | Queue persistence |
| `minio-data` | MinIO | Object storage |
| `prometheus-data` | Prometheus | Metrics history |
| `grafana-data` | Grafana | Dashboard configuration |

### 6.3 Secrets
JWT keys mounted into Identity Service:
- `infra/secrets/jwtRS256.pk8.pem` → `/run/secrets/private_key.pem`
- `infra/secrets/jwtRS256.key.pub` → `/run/secrets/public_key.pem`

Generated via `bash infra/scripts/gen-jwt-keys.sh`.

### 6.4 Init Scripts
| Script | Purpose | Container |
|---|---|---|
| `infra/postgres/init/01-create-dbs.sql` | Creates per-service databases | PostgreSQL (entrypoint) |
| `infra/minio/init.sh` | Creates MinIO buckets, sets CORS | `minio-init` (one-shot) |
| `infra/rabbitmq/definitions.json` | Defines exchanges, queues, bindings | RabbitMQ (loaded via env) |
| `infra/scripts/gen-jwt-keys.sh` | Generates RS256 JWT key pair | Host (manual, first-time) |

---

## 7. CI/CD Integration

### 7.1 Docker Image Pipeline
The `service-docker.yml` workflow builds and pushes images to ghcr.io:
- **Trigger**: Push to main, version tags, workflow_dispatch
- **Build context**: `server/` (same as compose)
- **Dockerfile**: `server/services/<service>/Dockerfile` (multi-stage, self-contained)
- **Tags**: Git SHA + `latest` (branch), semver (tags)
- **Security**: Trivy vulnerability scanning on every image

### 7.2 Image Naming
```
ghcr.io/fice-selection-committee/sc-<short-name>:<tag>
```

---

## 8. Observability

### 8.1 Prometheus
- Scrapes all services via Docker-internal DNS (e.g., `identity-service:8081`)
- Alerting rules in `infra/prometheus/alerts.yml`:
  - ServiceDown, HighErrorRate, HighLatencyP95
  - CircuitBreakerOpen, CircuitBreakerHighFailureRate
  - HighJvmMemoryUsage, HikariPoolExhausted

### 8.2 Grafana
- 9 pre-provisioned dashboards covering service health, HTTP RED metrics, JVM, circuit breakers, feature flags, PostgreSQL, RabbitMQ

### 8.3 Zipkin
- Distributed tracing at 100% sampling (local dev)
- All services instrumented via `sc-observability-starter`

---

## 9. Environment Variable Conventions

All services use consistent naming:
- `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`
- `POSTGRES_HOST`, `PGPORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- `ZIPKIN_URL`

Complete reference in `infra/.env.example`.
