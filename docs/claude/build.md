# Build, Code Style & CI/CD Reference

This file contains the build dependency chain, service build instructions, code style conventions, and CI/CD workflow reference. Claude MUST read this file before executing build-related tasks or modifying build configuration.

---

## 1. Build Dependency Chain

```
1. server/libs/*  →  ./gradlew publishToMavenLocal  (from server/)
2. Each service   →  ./gradlew build                (from service directory)
3. Docker images  →  docker compose up --build      (from infra/)
```

**Critical rule**: Skipping step 1 when shared libraries have changed causes service builds to use stale library versions. The build will succeed but produce incorrect behavior or fail in CI.

**When to run step 1**: Any time a file in `server/libs/` has been modified. Claude MUST check this before building any service.

---

## 2. Shared Library Build

```bash
cd server
./gradlew build                 # Build all libraries + run tests
./gradlew publishToMavenLocal   # Publish to local Maven repository — REQUIRED before service builds
```

**Library source**: `server/libs/` (7 modules: sc-bom, sc-auth-starter, sc-common, sc-event-contracts, sc-test-common, sc-observability-starter, sc-s3-starter)

**Root build**: Controlled by `server/settings.gradle.kts` (Kotlin DSL). Only includes libraries, NOT services.

**Version**: Tracked in `server/version.properties`. Bump after any library change.

---

## 3. Backend Service Build

Each service is an independent Gradle project. Built from its own directory. Has its own `settings.gradle` (Groovy format, NOT `.kts`). Does NOT reference the root `server/settings.gradle.kts`.

```bash
cd server/services/<service-name>

# Build + unit tests:
./gradlew build

# Integration tests (requires Docker for Testcontainers):
./gradlew integrationTest

# Single test class:
./gradlew test --tests "*.ClassName"

# Tests by JUnit tag:
./gradlew test -PincludeTags=tagName
./gradlew integrationTest -PincludeTags=tagName

# Format check:
./gradlew spotlessCheck

# Auto-format:
./gradlew spotlessApply

# Coverage report:
./gradlew jacocoTestReport
```

**Service directories**:
| Service | Directory |
|---|---|
| Identity | `server/services/selection-committee-identity-service` |
| Admission | `server/services/selection-committee-admission-service` |
| Documents | `server/services/selection-committee-documents-service` |
| Environment | `server/services/selection-committee-environment-service` |
| Notifications | `server/services/selection-committee-notifications-service` |
| Gateway | `server/services/selection-committee-gateway` |
| Computer Vision | `server/services/selection-committee-computer-vision` (scaffolded) |
| E2E Tests | `server/services/selection-committee-e2e-tests` |

---

## 4. Frontend Build

```bash
cd client/web

# Install dependencies:
pnpm install

# Dev server (Turbopack, port 3000):
pnpm dev

# Production build (standalone output):
pnpm build

# Lint check (Biome):
pnpm lint

# Auto-fix lint issues:
pnpm lint:fix

# Unit tests (Vitest):
pnpm test

# E2E tests (Playwright — requires running backend):
pnpm test:e2e

# Unit test coverage:
pnpm test:coverage
```

---

## 5. Code Style — Backend (Java)

| Aspect | Convention |
|---|---|
| **Java version** | 21 |
| **Spring Boot** | 3.5.6 |
| **Spring Cloud** | 2025.0.0 |
| **Formatter** | Google Java Format via Spotless (`./gradlew spotlessApply`) |
| **Static analysis** | Error Prone (compile-time warnings and errors) |
| **Code generation** | Lombok (annotations: `@Data`, `@Builder`, `@RequiredArgsConstructor`, etc.) |
| **Object mapping** | MapStruct (compile-time generated mappers, not manual mapping) |
| **Coverage** | JaCoCo 80% minimum per file |
| **Dependency management** | `server/gradle/libs.versions.toml` — all versions centralized |

**Convention plugins** (in `server/buildSrc/src/main/kotlin/`):
| Plugin | Purpose |
|---|---|
| `sc.java-conventions` | Java 21, Spotless, Error Prone, Lombok configuration |
| `sc.spring-boot-service` | Spring Boot service configuration, dependency management |
| `sc.testing-conventions` | JUnit 5, Testcontainers, integration test source set, tag filtering |
| `sc.jacoco-conventions` | JaCoCo coverage thresholds and reporting |
| `sc.library-conventions` | Shared library build and publish configuration |

**Rules**:
- Do NOT add dependency versions directly in service `build.gradle` — use `libs.versions.toml`
- Do NOT bypass Error Prone warnings without documented justification
- Do NOT use manual object mapping when MapStruct can generate it
- Do NOT modify `server/settings.gradle.kts` to include services

---

## 6. Code Style — Frontend (TypeScript/React)

| Aspect | Convention |
|---|---|
| **Formatter/Linter** | Biome |
| **Indent** | 2 spaces |
| **Quotes** | Double |
| **Semicolons** | Required |
| **Trailing commas** | Required |
| **Line width** | 100 characters |
| **Path alias** | `@/` maps to `src/` |
| **Pre-commit** | Husky + lint-staged runs Biome on staged files |

**Rules**:
- All imports from `src/` must use the `@/` alias
- No `any` types for API responses — define types in `src/types/`
- Use existing shadcn/ui components before creating new ones
- Use TanStack Query for server state, Zustand for client state
- Use React Hook Form + Zod for form validation

---

## 7. CI/CD Workflows

Located in `.github/workflows/`. All workflows use Java 21 Temurin.

| Workflow | File | Trigger | Purpose |
|---|---|---|---|
| **Library CI** | `ci.yml` | PR/push to `main` when `server/libs/**` or `server/buildSrc/**` changes | Build and test all shared libraries, verify publishable |
| **Service CI** | `service-ci.yml` | PR/push to `main` when `server/services/**` changes | Matrix build — only affected services. Uses `dorny/paths-filter` for smart change detection |
| **Docker Build** | `service-docker.yml` | Push to `main` when `server/services/**` changes | Build and push Docker images to `ghcr.io` as `sc-<name>:latest` and `sc-<name>:<sha>` |
| **Publish** | `publish.yml` | Push tag `v*.*.*` | Publish libraries to GitHub Packages, create GitHub Release. Verifies tag matches `version.properties` |

**CI behavior**:
- Library CI triggers on library or buildSrc changes — always builds all libraries
- Service CI uses path filtering — only builds/tests services whose files changed in the PR
- Service CI also runs `integrationTest` (Testcontainers works in GitHub Actions runners)
- Docker Build pushes images to `ghcr.io` with repository-scoped naming
- Publish workflow requires tag to match version in `server/version.properties`

**When modifying CI**:
- Understand the change detection logic in `service-ci.yml` before editing
- Ensure new services are added to the path filter matrix
- Ensure new libraries are included in the library CI build
- Do not add steps that assume host-specific tooling not available in CI runners
- Verify the library publish → service build dependency chain is preserved
