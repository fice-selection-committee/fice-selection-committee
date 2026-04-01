# RELEASE REPORT — FULL PROJECT HISTORY

| Field | Value |
|---|---|
| **Document Number** | FICE-REL-2026-003 |
| **Document Title** | Cumulative Release Report -- FICE Selection Committee Platform v1.0.0 through v1.2.0 |
| **Version / Revision** | Rev 2.0 |
| **Classification** | Internal |
| **Date of Issue** | 2026-03-30 |
| **Prepared By** | FICE Selection Committee Engineering Team |
| **Approved By** | _Pending approval_ |
| **Distribution** | Engineering, DevOps, QA, Project Management |

---

## Revision History

| Rev | Date | Author | Description |
|---|---|---|---|
| 1.0 | 2026-03-30 | Engineering Team | Initial release report for v1.2.0 |
| 2.0 | 2026-03-30 | Engineering Team | Rewritten as comprehensive full-history report covering v1.0.0 through v1.2.0 |

---

## Table of Contents

1. [Purpose and Scope](#1-purpose-and-scope)
2. [Definitions and Abbreviations](#2-definitions-and-abbreviations)
3. [Project Overview](#3-project-overview)
4. [Release History Summary](#4-release-history-summary)
5. [Version 1.0.0 — Project Foundation (2026-03-25 to 2026-03-26)](#5-version-100--project-foundation-2026-03-25-to-2026-03-26)
6. [Version 1.1.0 — Infrastructure and Testing (2026-03-26 to 2026-03-28)](#6-version-110--infrastructure-and-testing-2026-03-26-to-2026-03-28)
7. [Version 1.2.0 — Docker Maturity and Expansion (2026-03-30)](#7-version-120--docker-maturity-and-expansion-2026-03-30)
8. [Breaking Changes and Migration Guide](#8-breaking-changes-and-migration-guide)
9. [Cumulative Validation Status](#9-cumulative-validation-status)
10. [Current System Architecture](#10-current-system-architecture)
11. [Remaining Risks and Follow-up Items](#11-remaining-risks-and-follow-up-items)
12. [Document Control](#12-document-control)
13. [Appendix A: Approval Sign-off](#appendix-a-approval-sign-off)
14. [Appendix B: Full Commit Log](#appendix-b-full-commit-log)

---

## 1. Purpose and Scope

This document constitutes the authoritative cumulative release report for the FICE Selection Committee platform, covering the complete project history from inception through version 1.2.0. It SHALL serve as the single reference for all changes, validations, architectural decisions, risks, and migration requirements across every released version of the platform.

The scope of this report encompasses:

- A complete inventory of all changes delivered across versions 1.0.0, 1.1.0, and 1.2.0.
- The architectural evolution of the platform from initial monorepo establishment through Docker maturity.
- Identification and documentation of all breaking changes introduced in each version.
- Validation status of all automated and manual quality gates at each release boundary.
- Migration procedures required by consuming services and deployment environments.
- Outstanding risks, follow-up items, and their assigned priority levels.
- The full commit log of all 39 non-merge commits comprising the project's history.

This report supersedes the previous single-version release report (FICE-REL-2026-003 Rev 1.0) and MUST be maintained as the living record of the platform's release history.

---

## 2. Definitions and Abbreviations

| Term | Definition |
|---|---|
| **CI/CD** | Continuous Integration / Continuous Delivery -- the automated build, test, and deployment pipeline. |
| **Feign** | A declarative HTTP client framework used for inter-service communication within the platform. |
| **Flyway** | A database migration tool used to manage versioned schema changes across all service databases. |
| **Trivy** | An open-source vulnerability scanner for container images, file systems, and code repositories. |
| **SARIF** | Static Analysis Results Interchange Format -- a standardized output format for static analysis tools. |
| **RabbitMQ** | The message broker used for asynchronous inter-service event communication. |
| **MinIO** | An S3-compatible object storage service used for file storage within the platform. |
| **Grafana** | An observability and dashboarding platform used for monitoring service and infrastructure metrics. |
| **Prometheus** | A time-series metrics collection and alerting system integrated with Grafana. |
| **Testcontainers** | A library providing lightweight, disposable containers for integration testing. |
| **i18n** | Internationalization -- the design practice of enabling software to support multiple languages. |
| **POM** | Page Object Model -- a design pattern used in end-to-end test automation. |
| **JRE** | Java Runtime Environment. |
| **OpenAPI** | A specification for machine-readable interface definitions for HTTP APIs. |
| **Gradle** | The build automation tool used across all backend services and shared libraries. |
| **mavenLocal** | The local Maven repository used for publishing and resolving shared library artifacts during development. |
| **N/A** | Not Applicable. |

---

## 3. Project Overview

### 3.1 Repository Information

| Property | Value |
|---|---|
| **Repository** | `git@github.com:fice-selection-committee/fice-selection-committee.git` |
| **Default Branch** | `main` |
| **Structure** | Gradle multi-module monorepo |
| **License** | Internal / Proprietary |

### 3.2 Technology Stack

| Layer | Technologies |
|---|---|
| **Backend Services** | Java 21, Spring Boot 3.x, Gradle 8.14 |
| **Frontend** | Next.js 16+, TypeScript, React, Tailwind CSS v4, shadcn/ui |
| **Shared Libraries** | sc-auth-starter, sc-test-common, sc-observability-starter, sc-s3-starter |
| **Databases** | PostgreSQL 16 (per-service databases, Flyway-managed migrations) |
| **Messaging** | RabbitMQ 3 |
| **Object Storage** | MinIO (S3-compatible) |
| **Caching** | Redis 7 |
| **API Gateway** | Spring Cloud Gateway |
| **Observability** | Zipkin (tracing), Prometheus + Grafana (metrics and dashboards) |
| **Email (Dev)** | Mailpit |
| **CI/CD** | GitHub Actions |
| **Containerization** | Docker, Docker Compose |
| **Package Registry** | GitHub Packages (shared libraries) |

### 3.3 Service Inventory (as of v1.2.0)

| Service | Domain | Introduced |
|---|---|---|
| `selection-committee-identity-service` | User identity, authentication, magic link login | v1.0.0 |
| `selection-committee-admission-service` | Admission workflows and applicant management | v1.0.0 |
| `selection-committee-documents-service` | Document storage and retrieval | v1.0.0 |
| `selection-committee-environment-service` | Feature flags and environment configuration | v1.0.0 |
| `selection-committee-notifications-service` | Email and notification delivery | v1.0.0 |
| `selection-committee-gateway` | API gateway (port 8080, sole external entry point) | v1.0.0 |
| `selection-committee-telegram-bot-service` | Telegram-based feature flag management | v1.2.0 |

### 3.4 Team

| Role | Attribution |
|---|---|
| **Prepared By** | FICE Selection Committee Engineering Team |

---

## 4. Release History Summary

| Version | Tag Ref | Date Range | Key Theme | Non-Merge Commits | Files Changed | Insertions | Deletions | PRs |
|---|---|---|---|---|---|---|---|---|
| **v1.0.0** | `30f2b86` | 2026-03-25 to 2026-03-26 | Project Foundation | 9 | 67 | 17,080 | — | #1 – #5 |
| **v1.1.0** | `ebf103c` | 2026-03-26 to 2026-03-28 | Infrastructure and Testing | 18 | 55 | 7,817 | — | #6 – #24 |
| **v1.2.0** | `2415707` | 2026-03-30 | Docker Maturity and Expansion | 12 | 90 | 14,354 | 5,309 | #25 – #29 |
| **Cumulative** | — | 2026-03-25 to 2026-03-30 | — | **39** | — | **39,251** | — | **#1 – #29** |

---

## 5. Version 1.0.0 — Project Foundation (2026-03-25 to 2026-03-26)

### 5.1 Scope and Objectives

Version 1.0.0 established the FICE Selection Committee platform as a Gradle multi-module monorepo. The primary objectives were:

1. Consolidate six pre-existing microservices (identity, admission, documents, environment, notifications, and the API gateway) from their separate repositories into a single monorepo.
2. Create shared libraries to eliminate duplicated cross-cutting concerns across services.
3. Establish a build infrastructure capable of publishing shared libraries to GitHub Packages.
4. Provide a local development startup mechanism for the engineering team.

This version represents the transition from a distributed multi-repository architecture to a unified monorepo, establishing the foundation upon which all subsequent versions build.

### 5.2 Changes Delivered

| Seq. | Commit Ref. | Category | Description |
|---|---|---|---|
| 1 | `71b7789` | `feat` | **Add Gradle multi-module monorepo build infrastructure.** Established the root Gradle project with multi-module structure, `buildSrc` conventions, and dependency management for all services and libraries. |
| 2 | `8a8c205` | `fix` | **Add execute permission to gradlew.** Corrected file permissions on the Gradle wrapper script to enable execution on Unix-based systems. |
| 3 | `6db6b73` | `feat` | **Merge sc-auth-starter into monorepo.** Migrated the shared authentication starter library into the monorepo structure, including JWT handling, security filters, and Feign-based identity service client. |
| 4 | `046d46b` | `fix` | **Include sc-auth-starter client package and scope gitignore paths.** Corrected missing package inclusion in the auth starter and scoped `.gitignore` patterns to the monorepo directory structure. |
| 5 | `6701695` | `fix` | **Add unauthorized entry point and normalize role authority names.** Introduced a proper unauthorized entry point for Spring Security and standardized role authority naming conventions across the authentication library. |
| 6 | `999dc4c` | `chore` | **Add local development startup script and project logo.** Provided a convenience script for bootstrapping the local development environment and added the project's visual identity asset. |
| 7 | `d6f74b1` | `feat` | **Implement shared libraries (sc-test-common, sc-observability-starter, sc-s3-starter).** Created three additional shared libraries: test utilities with Testcontainers support, distributed tracing and observability auto-configuration, and S3/MinIO client auto-configuration. PR #3. |
| 8 | `bdde5d4` | `fix(sc-s3-starter)` | **Register S3BucketInitializer as bean in auto-configuration.** Corrected the auto-configuration to properly register the S3 bucket initializer as a Spring bean, enabling automatic bucket creation on startup. PR #4. |
| 9 | `30f2b86` | `feat` | **Add GitHub Packages publishing for shared libraries v1.0.0.** Configured Gradle publishing plugins and GitHub Packages repository settings for all four shared libraries. PR #5. |

### 5.3 Architecture Established

The v1.0.0 release established the following architectural patterns:

- **Monorepo layout**: `server/` root containing `libs/` (shared libraries) and `services/` (microservices), with a shared `buildSrc/` for Gradle convention plugins.
- **Bounded contexts**: Each service owns its database, schema, and domain. Cross-service communication is exclusively through Feign HTTP clients or RabbitMQ events.
- **Gateway pattern**: The `selection-committee-gateway` service serves as the sole external entry point on port 8080. No service exposes endpoints directly to consumers.
- **Authentication flow**: Magic link-based login managed by the identity service, with JWT tokens propagated through the gateway via `sc-auth-starter`.

### 5.4 Shared Libraries Introduced

| Library | Purpose | Key Capabilities |
|---|---|---|
| `sc-auth-starter` | Authentication and authorization | JWT filter chain, `IdentityServiceClient` Feign interface, role authority normalization, unauthorized entry point |
| `sc-test-common` | Test infrastructure | Testcontainers base classes, shared test fixtures, integration test utilities |
| `sc-observability-starter` | Distributed tracing and metrics | Zipkin auto-configuration, trace propagation, observability defaults |
| `sc-s3-starter` | Object storage client | MinIO/S3 client auto-configuration, automatic bucket initialization |

All libraries are published to GitHub Packages and resolved as Gradle dependencies by consuming services. Local development requires `./gradlew publishToMavenLocal` from `server/` before building dependent services.

---

## 6. Version 1.1.0 — Infrastructure and Testing (2026-03-26 to 2026-03-28)

### 6.1 Scope and Objectives

Version 1.1.0 focused on operationalizing the monorepo established in v1.0.0. The primary objectives were:

1. Create a Docker Compose overlay enabling full-stack local deployment of all services and infrastructure.
2. Establish CI/CD pipelines for automated building, testing, and Docker image publishing.
3. Introduce contract tests for inter-service API boundaries and end-to-end tests for critical user flows.
4. Add OpenAPI-based TypeScript client generation for frontend integration.
5. Provision Grafana dashboards for service health, JVM metrics, HTTP metrics, and circuit breaker monitoring.
6. Introduce the magic link authentication template for the notification service.

### 6.2 Changes Delivered

| Seq. | Commit Ref. | Category | Description |
|---|---|---|---|
| 1 | `ecd4c7a` | `fix(buildSrc)` | **Resolve JaCoCo classDirectories finalization error on Gradle 8.14.** Fixed a compatibility issue where JaCoCo task configuration conflicted with Gradle 8.14's stricter task finalization rules. PR #6. |
| 2 | `df37ce3` | `chore` | **Add .kotlin/ to .gitignore and remove migration script.** Excluded Kotlin compiler cache directory from version control and removed a one-time migration script that was no longer needed. PR #8. |
| 3 | `187bd6b` | `feat(openapi)` | **Add TypeScript client generation tooling.** Introduced OpenAPI-based code generation that produces TypeScript client types and API functions from backend service specifications. |
| 4 | `47b0912` | `fix(docker)` | **Correct service ports and add Gateway Dockerfile.** Fixed port mapping inconsistencies in Docker configuration and created the missing Dockerfile for the gateway service. |
| 5 | `0465686` | `feat(docker)` | **Add services Docker Compose overlay for full-stack local deployment.** Created `docker-compose.services.yml` enabling developers to run all microservices alongside infrastructure containers on the `sc-net` network. |
| 6 | `f5c49a0` | `ci` | **Add service build/test and Docker image workflows.** Introduced GitHub Actions workflows for building and testing all services, and for publishing Docker images to the container registry. |
| 7 | `7bdc266` | `test(contracts)` | **Add consumer and provider contract tests for inter-service APIs.** Established contract testing patterns using consumer-driven contract verification for Feign client interfaces. PR implicit. |
| 8 | `94b20d0` | `feat(grafana)` | **Add provisioned dashboards for service health, JVM, HTTP, and circuit breakers.** Introduced Grafana provisioning configuration with four pre-built dashboards for monitoring platform health. |
| 9 | `24167b2` | `test(e2e)` | **Add environment, notification delivery, and negative flow E2E tests.** Created Playwright-based end-to-end tests covering environment service operations, notification delivery verification, and negative (failure) scenarios. |
| 10 | `a3e5cb5` | `feat(infra)` | **Add frontend task plans and web service to docker-compose.** Integrated the Next.js frontend application into the Docker Compose services overlay and added planning documentation for frontend development. PR #16. |
| 11 | `08ccfb9` | `fix(ci)` | **Skip service builds when source code is not in monorepo.** Added a conditional check to avoid CI failures when attempting to build services whose source code had not yet been migrated into the monorepo structure. |
| 12 | `aa34a1a` | `fix(ci)` | **Add workflow_dispatch trigger for manual re-runs.** Enabled manual triggering of CI workflows to support re-execution after transient failures without requiring a new commit. |
| 13 | `09740c9` | `fix(ci)` | **Use dynamic matrix to avoid expression parse errors.** Replaced static matrix definitions with dynamically generated matrices to resolve GitHub Actions expression parsing limitations. |
| 14 | `20720f6` | `fix(ci)` | **Use compact jq output for GITHUB_OUTPUT compatibility.** Corrected jq output formatting to ensure compatibility with GitHub Actions' `GITHUB_OUTPUT` environment file mechanism. |
| 15 | `5df3fc5` | `fix(ci)` | **Check for src directory in Docker build availability check.** Added validation that the `src` directory exists before attempting Docker builds, preventing build failures for services without source code in the expected location. |
| 16 | `1091f12` | `fix(ci)` | **Check for src/main instead of src/ in Docker availability check.** Refined the availability check to target `src/main` specifically, avoiding false positives from test-only source directories. |
| 17 | `8b089d5` | `feat(events)` | **Add MAGIC_LINK template type for magic link auth.** Introduced the `MAGIC_LINK` notification template type to support the magic link authentication flow in the notification service. PR #23. |
| 18 | `ebf103c` | `release` | **Bump version to 1.1.0.** Updated all version references across the monorepo to 1.1.0 in preparation for the release tag. PR #24. |

### 6.3 Docker Compose and Services

The `docker-compose.services.yml` overlay introduced in v1.1.0 provided the first complete local deployment capability for the platform. Key characteristics:

- All six microservices configured with build contexts, port mappings, and environment variable injection.
- The Next.js frontend web service added to the compose topology.
- Network configuration on `sc-net` for inter-service communication.
- Service-to-infrastructure dependency declarations (PostgreSQL, Redis, RabbitMQ, MinIO).

**Limitation (addressed in v1.2.0):** The initial compose configuration used `service_started` dependency conditions rather than health-based coordination, which could result in services starting before their dependencies were fully ready.

### 6.4 CI/CD Pipeline

The CI/CD pipeline introduced in v1.1.0 comprised two primary workflows:

1. **Service Build and Test Workflow**: Triggered on pushes and pull requests affecting service source code. Builds all services, executes unit and integration tests, and reports results.
2. **Docker Image Workflow**: Builds and publishes Docker images to the container registry upon successful merge to `main`.

**CI Hotfix Sequence:** The initial CI implementation required six successive hotfixes (commits 11-16 in the table above) to resolve issues with dynamic matrix generation, GitHub Actions output formatting, and Docker build availability detection. This sequence illustrates the iterative nature of CI pipeline development when targeting a heterogeneous monorepo structure.

### 6.5 Testing Infrastructure

Version 1.1.0 established two critical testing layers:

- **Contract Tests**: Consumer and provider contract tests for Feign client interfaces, ensuring that inter-service API contracts remain consistent as services evolve independently. Located in `src/integrationTest/**/contract/`.
- **End-to-End Tests (Playwright)**: Browser-based E2E tests covering environment service workflows, notification delivery verification, and negative flow scenarios. Used Page Object Model patterns for maintainability.

### 6.6 OpenAPI and Frontend Integration

The OpenAPI TypeScript client generation tooling established the bridge between backend API specifications and the frontend application. This tooling generates TypeScript types and API client functions from OpenAPI specifications produced by backend services, ensuring type safety across the full stack.

---

## 7. Version 1.2.0 — Docker Maturity and Expansion (2026-03-30)

### 7.1 Scope and Objectives

Version 1.2.0 represents a significant maturation of the platform's operational posture. The primary objectives were:

1. Rewrite all service Dockerfiles to employ multi-stage builds with embedded health checks.
2. Overhaul the Docker Compose services manifest with health-based dependency coordination, corrected environment variable mappings, and monitoring integration.
3. Correct the breaking `getCurrentUser` HTTP method from POST to GET in the shared authentication library.
4. Introduce the Telegram Bot Service for feature flag management via Telegram.
5. Enhance the CI/CD pipeline with tag-based triggers, Trivy vulnerability scanning, and an expanded build matrix.
6. Establish engineering documentation (CLAUDE.md and supporting reference files) as the project's operational governance framework.
7. Move end-to-end tests to a separate repository for isolation.

### 7.2 Changes Delivered

| Seq. | Commit Ref. | Category | Description |
|---|---|---|---|
| 1 | `2b9deaf` | `chore` | **Update .gitignore and remove obsolete files.** Added `node_modules/`, `screenshots/`, and temporary file patterns. Removed `start-all.sh` and 14 legacy task plan files. |
| 2 | `3692b7a` | `feat(docker)` | **Rewrite service Dockerfiles to multi-stage builds.** All existing Dockerfiles rewritten to a consistent 3-stage pattern: `lib-builder` → `service-builder` → `runtime` (JRE 21 Alpine). `HEALTHCHECK` directives added. `server/.dockerignore` created. |
| 3 | `8706dd1` | `fix(auth)` | **Change getCurrentUser from POST to GET and update contract tests.** Corrected `@PostMapping` to `@GetMapping` in `IdentityServiceClient.java`. `ProviderContractIT` rewritten. `IdentityClientContractIT` updated. `EnvClientContractIT` removed as obsolete. |
| 4 | `bfad1f2` | `feat(infra)` | **Overhaul docker-compose with health checks, telegram bot, and monitoring.** Major `docker-compose.services.yml` rewrite: health-based dependency coordination, build context relocation, JWT secret volume mounts, Telegram bot containers (behind profile), env var renaming (`RABBIT_*` → `RABBITMQ_*`), Grafana provisioning, `.env.example` creation. |
| 5 | `38ff1db` | `feat(ci)` | **Enhance Docker CI with tag triggers, Trivy scanning, and expanded matrix.** Semver tag triggers, shared library/buildSrc change triggers, expanded matrix (web frontend + telegram bot), Trivy SARIF scanning, dynamic context resolution, 30-minute timeout. |
| 6 | `211f2cb` | `feat(telegram)` | **Add Telegram Bot Service for feature flag management.** New Spring Boot service (79 source files): webhook-based Telegram integration, Feign client to environment service, RabbitMQ subscriptions, chat-based auth, rate limiting, i18n (Ukrainian + English), Flyway migrations, unit test suite. |
| 7 | `ca722e2` | `docs` | **Add engineering standards, Claude Code references, and review report.** Introduced `CLAUDE.md`, `docs/claude/` (10 reference files), `docs/analysis/`, and `REVIEW_REPORT.md` (48-finding codebase audit). |
| 8 | `e6a333f` | `chore` | **Add telegram buildSrc, updated task plans, and root tooling deps.** Gradle `buildSrc` conventions for Telegram Bot Service, 19 new task plan files, root `package.json` with shadcn CLI dependency. |
| 9 | `7e33af9` | `release` | **Bump version to 1.2.0.** Updated all version references across the monorepo. |
| 10 | `2d2aa65` | `fix(telegram)` | **Update shared library version to 1.2.0.** Aligned the Telegram Bot Service's shared library dependency versions with the 1.2.0 release. |
| 11 | `fb69309` | `fix(repo)` | **Remove force-committed service files, fix .gitignore, add release docs.** Cleaned up files that had been committed despite `.gitignore` rules and corrected the ignore patterns. Added release documentation. |
| 12 | `01a3708` | `fix(repo)` | **Move e2e tests to separate repo, add to .gitignore.** Relocated Playwright end-to-end tests to a dedicated repository for independent lifecycle management. Updated `.gitignore` to exclude the E2E directory. |

### 7.3 Multi-stage Docker Builds

All service Dockerfiles were rewritten to a consistent 3-stage build pattern:

| Stage | Name | Purpose |
|---|---|---|
| 1 | `lib-builder` | Copies shared libraries source and executes `publishToMavenLocal` to make them available for service compilation. |
| 2 | `service-builder` | Copies the target service source, resolves dependencies from the mavenLocal cache, and produces the application JAR. |
| 3 | `runtime` | Uses JRE 21 Alpine as the minimal runtime base. Copies only the built JAR. Includes a `HEALTHCHECK` directive. |

This pattern ensures that Docker images are self-contained (no external dependency on a pre-published library state) while remaining minimal in size (JRE-only runtime layer). A `server/.dockerignore` was added to exclude unnecessary files from the build context.

### 7.4 Auth Contract Fix (POST to GET)

**Affected Component:** `sc-auth-starter` shared library (`IdentityServiceClient.java`)

The `getCurrentUser` endpoint in the Feign client interface was incorrectly annotated with `@PostMapping`. The identity service implementation expected a GET request. This mismatch was masked in environments where the endpoint was invoked through the gateway (which may have performed method translation) but would cause HTTP 405 errors in direct Feign invocations.

**Correction:** `@PostMapping` changed to `@GetMapping`. Contract tests updated to verify the correct HTTP method. The obsolete `EnvClientContractIT` was removed during this cleanup.

**Impact:** This is a breaking change for any service binary compiled against the previous library version. See Section 8 for migration procedures.

### 7.5 Docker Compose Overhaul

The `docker-compose.services.yml` manifest was comprehensively overhauled:

| Change | Before (v1.1.0) | After (v1.2.0) |
|---|---|---|
| Dependency coordination | `service_started` | `service_healthy` for Redis, RabbitMQ, PostgreSQL |
| Build context | Individual service directories | `server/` root (required for multi-stage builds) |
| JWT secrets | Hardcoded or environment-injected | Volume-mounted from host |
| Environment variables | `RABBIT_*` prefix | `RABBITMQ_*` prefix (consistency) |
| Gateway health check | Incorrect port | Corrected to actual management port |
| Telegram bot | Not present | Added behind `telegram` profile (with ngrok and webhook-init) |
| Monitoring | Basic | Grafana provisioning with 4 dashboards |
| Environment template | Not present | `infra/.env.example` created |

### 7.6 CI/CD Enhancements

The Docker CI workflow (`service-docker.yml`) was expanded with:

- **Tag-based triggers**: Semver tag patterns (`v*.*.*`) trigger Docker image builds, enabling release-driven publishing.
- **Expanded trigger paths**: Changes to shared libraries (`server/libs/`) and `buildSrc/` now trigger rebuilds of dependent services.
- **Expanded build matrix**: The matrix now includes the web frontend and Telegram Bot Service in addition to the original six backend services.
- **Trivy vulnerability scanning**: Container images are scanned for known vulnerabilities and results are uploaded in SARIF format for GitHub Security integration.
- **Dynamic context resolution**: Each service's Dockerfile and build context are resolved dynamically per matrix entry.
- **Job timeout**: A 30-minute timeout was configured to prevent runaway builds.

### 7.7 Telegram Bot Service

A new Spring Boot microservice was introduced: `selection-committee-telegram-bot-service` (79 source files).

| Capability | Description |
|---|---|
| **Telegram webhook integration** | Receives and processes Telegram Bot API webhook events |
| **Feature flag CRUD** | Create, read, update, and delete feature flags via chat commands |
| **Environment service communication** | Feign client interface to the environment service for flag operations |
| **RabbitMQ subscriptions** | Subscribes to platform events for real-time notification of flag changes |
| **Chat-based authentication** | Authenticates Telegram users against the platform's identity system |
| **Rate limiting** | Per-user rate limiting to prevent abuse |
| **Internationalization** | Full i18n support for Ukrainian and English languages |
| **Database migrations** | Flyway-managed schema for chat-to-user mappings and bot state |
| **Unit test suite** | Comprehensive unit tests included with the service |

The service is deployed behind the `telegram` Docker Compose profile and requires additional containers (ngrok for webhook tunneling, webhook-init for registration).

### 7.8 Monitoring and Observability

Grafana provisioning was introduced with automatic datasource and dashboard configuration:

| Dashboard | Scope |
|---|---|
| Feature Flags | Environment service feature flag metrics and usage |
| PostgreSQL | Database connection pool, query performance, and health metrics |
| RabbitMQ | Queue depths, message rates, consumer counts, and connection health |
| Services Overview | HTTP request rates, error rates, JVM metrics, and circuit breaker states |

Provisioning files are located in `infra/grafana/provisioning/` with dashboard JSON definitions in `infra/grafana/dashboards/`.

### 7.9 Engineering Documentation

Version 1.2.0 introduced a comprehensive engineering documentation framework:

| File / Directory | Description |
|---|---|
| `CLAUDE.md` | Engineering operating standard governing Claude Code behavior. Covers agents, skills, operational rules, decision framework, failure-aware thinking, change proposal format, and output expectations. |
| `docs/claude/agents.md` | Agent role specifications (Solution Architect, Backend/Frontend Engineer, QA, DevOps, et al.) |
| `docs/claude/skills.md` | Skill activation procedures and verification outputs |
| `docs/claude/architecture.md` | Service topology, shared libraries, infrastructure, compose topology |
| `docs/claude/docker.md` | Docker startup, known gaps, developer workflows, improvement mandate |
| `docs/claude/testing.md` | TDD protocol, bug fix protocol, test infrastructure, layer mapping |
| `docs/claude/build.md` | Build commands, dependency chain, code style, CI/CD reference |
| `docs/claude/interaction-standards.md` | Hover, focus, disabled, loading, cursor, transition, color-state rules |
| `docs/claude/ios-atmosphere.md` | Typography scale, button system, motion tokens, elevation, color rules |
| `docs/analysis/` | eCampus investigation and analysis files |
| `REVIEW_REPORT.md` | 48-finding codebase audit with categorized issues and recommendations |

---

## 8. Breaking Changes and Migration Guide

### 8.1 Version 1.0.0 — No Breaking Changes

Version 1.0.0 was the initial release. No pre-existing consumers were affected.

### 8.2 Version 1.1.0 — No Breaking Changes

Version 1.1.0 introduced additive infrastructure (Docker Compose, CI/CD, tests, dashboards) without modifying existing service contracts or APIs. The magic link template type (`MAGIC_LINK`) was an addition to the notification service's template enum and did not alter existing behavior.

### 8.3 Version 1.2.0 — Three Breaking Changes

#### 8.3.1 getCurrentUser HTTP Method (POST to GET)

| Field | Value |
|---|---|
| **Affected Component** | `sc-auth-starter` shared library (`IdentityServiceClient.java`) |
| **Impact** | All services consuming `sc-auth-starter` that invoke `getCurrentUser` |
| **Severity** | High — causes HTTP 405 Method Not Allowed if mismatched |

**Migration Procedure:**

1. Execute `./gradlew publishToMavenLocal` from the `server/` directory to publish the updated `sc-auth-starter`.
2. Rebuild all dependent services to incorporate the corrected HTTP method.
3. No source code changes are required in consuming services — the correction resides in the shared library annotation.

**Risk:** If a service is rebuilt against the previous library version while the identity service expects GET, authentication calls SHALL fail with HTTP 405.

#### 8.3.2 Docker Build Context Relocation

| Field | Value |
|---|---|
| **Affected Component** | All service Dockerfiles and `docker-compose.services.yml` |
| **Impact** | Docker build commands and CI pipelines |
| **Severity** | Medium — previous standalone build commands no longer function |

**Migration Procedure:**

1. Pull the latest `docker-compose.services.yml`.
2. Copy `infra/.env.example` to `infra/.env` and populate with the required values.
3. Execute `docker compose -f infra/docker-compose.services.yml build`.
4. Previous standalone `docker build` commands targeting individual service directories SHALL no longer function. Use compose or specify `--file` and context explicitly.

#### 8.3.3 Environment Variable Renaming (RABBIT_* to RABBITMQ_*)

| Field | Value |
|---|---|
| **Affected Component** | `docker-compose.services.yml` |
| **Impact** | Any existing `.env` files or deployment configurations using the previous naming convention |
| **Severity** | Medium — services will fail to connect to RabbitMQ if old variable names are used |

**Migration Procedure:**

1. Update the local `infra/.env` file to use `RABBITMQ_`-prefixed variable names.
2. Reference `infra/.env.example` for the authoritative variable names.
3. Update any deployment scripts or CI secrets that reference the previous naming convention.

---

## 9. Cumulative Validation Status

### 9.1 Build and Test Validation

| Version | Validation | Status | Notes |
|---|---|---|---|
| v1.0.0 | Gradle build | Passed | All services and libraries build successfully |
| v1.0.0 | GitHub Packages publishing | Passed | All four shared libraries published |
| v1.1.0 | CI pipeline (build/test) | Passed | All services pass unit and integration tests |
| v1.1.0 | Docker image builds | Passed | Images built and published via CI |
| v1.1.0 | Contract tests | Passed | Consumer and provider contracts verified |
| v1.1.0 | E2E tests (Playwright) | Passed | Environment, notification, and negative flow tests |
| v1.2.0 | Multi-stage Docker builds | Locally Verified | All services build with the new 3-stage pattern |
| v1.2.0 | Docker Compose startup | Locally Verified | Health-based dependency ordering validated |
| v1.2.0 | Contract tests (updated) | Updated | `ProviderContractIT` rewritten; `IdentityClientContractIT` updated; `EnvClientContractIT` removed |
| v1.2.0 | Telegram Bot unit tests | Included | Comprehensive unit test suite committed with the service |
| v1.2.0 | CI pipeline (post-merge) | Passed | CI executed successfully on `main` after all PRs merged |
| v1.2.0 | Trivy scanning | Established | Introduced in this version; baseline to be reviewed |

### 9.2 Quality Gate Summary

| Gate | v1.0.0 | v1.1.0 | v1.2.0 |
|---|---|---|---|
| Unit Tests | N/A (library-only) | Pass | Pass |
| Integration Tests | N/A | Pass | Pass |
| Contract Tests | N/A | Pass | Updated/Pass |
| E2E Tests | N/A | Pass | Relocated to separate repo |
| Docker Builds | N/A | Pass | Pass (multi-stage) |
| Vulnerability Scan | N/A | N/A | Established |
| Code Review | PR-based | PR-based | PR-based |

---

## 10. Current System Architecture

### 10.1 Post-v1.2.0 Service Topology

The platform comprises 7 Spring Boot microservices, 1 Next.js frontend application, and supporting infrastructure, all orchestrated via Docker Compose on the `sc-net` network.

```
                          ┌─────────────────────┐
                          │     External         │
                          │     Consumers        │
                          └─────────┬────────────┘
                                    │ :8080
                          ┌─────────▼────────────┐
                          │       Gateway         │
                          │  (Spring Cloud GW)    │
                          └─────────┬────────────┘
                                    │
            ┌───────────┬───────────┼───────────┬───────────┬───────────┐
            │           │           │           │           │           │
     ┌──────▼──┐  ┌─────▼───┐ ┌────▼────┐ ┌────▼────┐ ┌────▼────┐ ┌───▼──────┐
     │Identity │  │Admission│ │Documents│ │Environm.│ │Notific. │ │Telegram  │
     │Service  │  │Service  │ │Service  │ │Service  │ │Service  │ │Bot Svc   │
     └────┬────┘  └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘ └────┬─────┘
          │            │           │            │           │            │
     ┌────▼────┐  ┌────▼────┐ ┌────▼────┐ ┌────▼────┐ ┌────▼────┐ ┌────▼─────┐
     │  PG DB  │  │  PG DB  │ │  PG DB  │ │  PG DB  │ │  PG DB  │ │  PG DB   │
     └─────────┘  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └──────────┘

     Infrastructure: Redis 7 │ RabbitMQ 3 │ MinIO │ Mailpit │ Zipkin │ Prometheus │ Grafana
```

### 10.2 Shared Library Dependencies

```
sc-auth-starter ──────────► identity-service, admission-service, documents-service,
                             environment-service, notifications-service, telegram-bot-service

sc-test-common ───────────► All services (test scope only)

sc-observability-starter ─► All services

sc-s3-starter ────────────► documents-service
```

### 10.3 Inter-service Communication

| Source | Target | Mechanism | Purpose |
|---|---|---|---|
| All services | Identity Service | Feign (via sc-auth-starter) | `getCurrentUser` authentication |
| Telegram Bot | Environment Service | Feign | Feature flag CRUD operations |
| Environment Service | RabbitMQ | Event publish | Feature flag change notifications |
| Telegram Bot | RabbitMQ | Event subscribe | Real-time flag change updates |
| Notifications Service | RabbitMQ | Event subscribe | Notification delivery triggers |
| Documents Service | MinIO | S3 API | File storage and retrieval |

### 10.4 Docker Compose Profiles

| Profile | Containers | Purpose |
|---|---|---|
| (default) | All infrastructure + 6 core services + web frontend | Standard development |
| `telegram` | ngrok, webhook-init, telegram-bot-service | Telegram bot development (additive) |

---

## 11. Remaining Risks and Follow-up Items

### 11.1 High Priority

| Seq. | Risk / Item | Version Introduced | Description | Recommended Action |
|---|---|---|---|---|
| 1 | **Shared library rebuild required** | v1.2.0 | The `sc-auth-starter` POST-to-GET correction requires all services to rebuild against the updated library. | Ensure `publishToMavenLocal` is executed before any service build; verify in CI. |
| 2 | **Telegram Bot secrets provisioning** | v1.2.0 | Telegram bot token, ngrok authentication token, and related secrets MUST be provisioned. | Document required secrets in the deployment runbook; verify `.env.example` completeness. |
| 3 | **Trivy baseline establishment** | v1.2.0 | The initial Trivy scan may surface pre-existing vulnerabilities in base images. | Review initial SARIF results and establish a suppression baseline for known acceptable findings. |

### 11.2 Medium Priority

| Seq. | Risk / Item | Version Introduced | Description | Recommended Action |
|---|---|---|---|---|
| 4 | **Contract test coverage gap** | v1.2.0 | `EnvClientContractIT` was removed; verification is required that no untested contract surface remains. | Audit remaining Feign clients for contract test coverage. |
| 5 | **Grafana dashboard accuracy** | v1.2.0 | Four dashboards were added but may require tuning for actual metric names and service labels. | Validate dashboards against live Prometheus metrics after deployment. |
| 6 | **Telegram Bot integration tests** | v1.2.0 | Unit tests are comprehensive; integration and contract tests (Feign to environment-service) are absent. | Add `EnvironmentClientContractIT` for the Telegram Bot's Feign client. |
| 7 | **Health check interval tuning** | v1.2.0 | `HEALTHCHECK` directives in Dockerfiles use default intervals, which may be insufficient for slow-starting services. | Monitor container health transition times and adjust intervals as needed. |
| 8 | **E2E test separation** | v1.2.0 | E2E tests moved to a separate repository; cross-repo coordination required for regression coverage. | Establish CI triggers or documented procedures for running E2E tests against the main repository's changes. |

### 11.3 Low Priority

| Seq. | Risk / Item | Version Introduced | Description | Recommended Action |
|---|---|---|---|---|
| 9 | **Task plan freshness** | v1.2.0 | 19 new task plan files were added; these are planning artifacts and SHALL be reviewed for continued relevance. | Prune completed or superseded task plans in the next release cycle. |
| 10 | **Root package.json** | v1.2.0 | Introduced for shadcn CLI tooling; `node_modules/` at the repository root may cause confusion. | Document the purpose of root-level Node.js tooling in the developer setup guide. |
| 11 | **Ngrok dependency** | v1.2.0 | Telegram webhook setup depends on ngrok for local development; the ngrok free tier imposes session limits. | Document ngrok alternatives for long-running development sessions. |
| 12 | **Redundant .gitkeep** | v1.2.0 | `infra/grafana/dashboards/.gitkeep` is redundant now that dashboard JSON files exist. | Remove `.gitkeep` in a follow-up cleanup commit. |
| 13 | **CI hotfix debt** | v1.1.0 | Six successive CI hotfixes suggest the pipeline may benefit from a local validation mechanism. | Introduce `act` or equivalent for local GitHub Actions testing. |

---

## 12. Document Control

| Field | Value |
|---|---|
| **Document Number** | FICE-REL-2026-003 |
| **Classification** | Internal |
| **Retention Period** | Indefinite (retained with release artifacts) |
| **Storage Location** | Repository root: `RELEASE_REPORT_v1.2.0.md` |
| **Owner** | FICE Selection Committee Engineering Team |
| **Last Modified** | 2026-03-30 |
| **Next Review** | Upon release of v1.3.0 or as required |

This document SHALL be maintained under version control alongside the source code it describes. Any amendments MUST be recorded in the Revision History table and the revision number incremented accordingly.

---

## Appendix A: Approval Sign-off

The undersigned confirm that this release report has been reviewed and that the cumulative release history is accurate and complete through version 1.2.0.

| Role | Name | Signature | Date |
|---|---|---|---|
| Engineering Lead | __________________ | __________________ | ____/____/________ |
| QA Lead | __________________ | __________________ | ____/____/________ |
| DevOps Lead | __________________ | __________________ | ____/____/________ |
| Project Manager | __________________ | __________________ | ____/____/________ |

---

## Appendix B: Full Commit Log

The following table contains all 39 non-merge commits from project inception through version 1.2.0, presented in chronological order.

| Seq. | Commit Ref. | Date | Version | Category | Description |
|---|---|---|---|---|---|
| 1 | `71b7789` | 2026-03-25 | v1.0.0 | `feat` | Add Gradle multi-module monorepo build infrastructure |
| 2 | `8a8c205` | 2026-03-25 | v1.0.0 | `fix` | Add execute permission to gradlew |
| 3 | `6db6b73` | 2026-03-25 | v1.0.0 | `feat` | Merge sc-auth-starter into monorepo |
| 4 | `046d46b` | 2026-03-25 | v1.0.0 | `fix` | Include sc-auth-starter client package and scope gitignore paths |
| 5 | `6701695` | 2026-03-25 | v1.0.0 | `fix` | Add unauthorized entry point and normalize role authority names |
| 6 | `999dc4c` | 2026-03-25 | v1.0.0 | `chore` | Add local development startup script and project logo |
| 7 | `d6f74b1` | 2026-03-25 | v1.0.0 | `feat` | Implement shared libraries (sc-test-common, sc-observability-starter, sc-s3-starter) |
| 8 | `bdde5d4` | 2026-03-25 | v1.0.0 | `fix(sc-s3-starter)` | Register S3BucketInitializer as bean in auto-configuration |
| 9 | `30f2b86` | 2026-03-26 | v1.0.0 | `feat` | Add GitHub Packages publishing for shared libraries v1.0.0 |
| 10 | `ecd4c7a` | 2026-03-26 | v1.1.0 | `fix(buildSrc)` | Resolve JaCoCo classDirectories finalization error on Gradle 8.14 |
| 11 | `df37ce3` | 2026-03-27 | v1.1.0 | `chore` | Add .kotlin/ to .gitignore and remove migration script |
| 12 | `187bd6b` | 2026-03-27 | v1.1.0 | `feat(openapi)` | Add TypeScript client generation tooling |
| 13 | `47b0912` | 2026-03-27 | v1.1.0 | `fix(docker)` | Correct service ports and add Gateway Dockerfile |
| 14 | `0465686` | 2026-03-27 | v1.1.0 | `feat(docker)` | Add services Docker Compose overlay for full-stack local deployment |
| 15 | `f5c49a0` | 2026-03-27 | v1.1.0 | `ci` | Add service build/test and Docker image workflows |
| 16 | `7bdc266` | 2026-03-27 | v1.1.0 | `test(contracts)` | Add consumer and provider contract tests for inter-service APIs |
| 17 | `94b20d0` | 2026-03-27 | v1.1.0 | `feat(grafana)` | Add provisioned dashboards for service health, JVM, HTTP, and circuit breakers |
| 18 | `24167b2` | 2026-03-27 | v1.1.0 | `test(e2e)` | Add environment, notification delivery, and negative flow E2E tests |
| 19 | `a3e5cb5` | 2026-03-28 | v1.1.0 | `feat(infra)` | Add frontend task plans and web service to docker-compose |
| 20 | `08ccfb9` | 2026-03-28 | v1.1.0 | `fix(ci)` | Skip service builds when source code is not in monorepo |
| 21 | `aa34a1a` | 2026-03-28 | v1.1.0 | `fix(ci)` | Add workflow_dispatch trigger for manual re-runs |
| 22 | `09740c9` | 2026-03-28 | v1.1.0 | `fix(ci)` | Use dynamic matrix to avoid expression parse errors |
| 23 | `20720f6` | 2026-03-28 | v1.1.0 | `fix(ci)` | Use compact jq output for GITHUB_OUTPUT compatibility |
| 24 | `5df3fc5` | 2026-03-28 | v1.1.0 | `fix(ci)` | Check for src directory in Docker build availability check |
| 25 | `1091f12` | 2026-03-28 | v1.1.0 | `fix(ci)` | Check for src/main instead of src/ in Docker availability check |
| 26 | `8b089d5` | 2026-03-28 | v1.1.0 | `feat(events)` | Add MAGIC_LINK template type for magic link auth |
| 27 | `ebf103c` | 2026-03-28 | v1.1.0 | `release` | Bump version to 1.1.0 |
| 28 | `2b9deaf` | 2026-03-30 | v1.2.0 | `chore` | Update .gitignore and remove obsolete files |
| 29 | `3692b7a` | 2026-03-30 | v1.2.0 | `feat(docker)` | Rewrite service Dockerfiles to multi-stage builds |
| 30 | `8706dd1` | 2026-03-30 | v1.2.0 | `fix(auth)` | Change getCurrentUser from POST to GET and update contract tests |
| 31 | `bfad1f2` | 2026-03-30 | v1.2.0 | `feat(infra)` | Overhaul docker-compose with health checks, telegram bot, and monitoring |
| 32 | `38ff1db` | 2026-03-30 | v1.2.0 | `feat(ci)` | Enhance Docker CI with tag triggers, Trivy scanning, and expanded matrix |
| 33 | `211f2cb` | 2026-03-30 | v1.2.0 | `feat(telegram)` | Add Telegram Bot Service for feature flag management |
| 34 | `ca722e2` | 2026-03-30 | v1.2.0 | `docs` | Add engineering standards, Claude Code references, and review report |
| 35 | `e6a333f` | 2026-03-30 | v1.2.0 | `chore` | Add telegram buildSrc, updated task plans, and root tooling deps |
| 36 | `7e33af9` | 2026-03-30 | v1.2.0 | `release` | Bump version to 1.2.0 |
| 37 | `2d2aa65` | 2026-03-30 | v1.2.0 | `fix(telegram)` | Update shared library version to 1.2.0 |
| 38 | `fb69309` | 2026-03-30 | v1.2.0 | `fix(repo)` | Remove force-committed service files, fix .gitignore, add release docs |
| 39 | `01a3708` | 2026-03-30 | v1.2.0 | `fix(repo)` | Move e2e tests to separate repo, add to .gitignore |

---

*End of Document -- FICE-REL-2026-003 Rev 2.0*
