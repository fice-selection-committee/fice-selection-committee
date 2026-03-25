# Phase 1.1 — Root Gradle Build Infrastructure

**Priority:** P0 — Foundation (blocks everything else)
**Status:** Pending

## Goal

Create the root Gradle multi-module build files that unify all services under a single build.

## Deliverables

### 1. `server/settings.gradle.kts`
- `rootProject.name = "fice-selection-committee"`
- Version catalog from `gradle/libs.versions.toml`
- Include all 6 shared libs and 7 services

### 2. `server/build.gradle.kts`
- `group = "edu.kpi.fice"` for all subprojects
- `mavenCentral()` repository for all

### 3. `server/gradle/libs.versions.toml`
Unified version catalog fixing all drift:

| Dependency | Unified Version | Current Drift |
|-----------|----------------|---------------|
| spring-boot | 3.5.6 | None |
| spring-cloud | 2025.0.0 | None |
| springdoc | 2.8.13 | 2.5.0–2.8.13 |
| lombok | 1.18.38 | 1.18.34–1.18.38 |
| testcontainers | 1.20.6 | 1.19.1–1.20.6 |
| postgresql | 42.7.5 | 42.7.3–managed |
| mapstruct | 1.5.5.Final | None |
| errorprone | 2.29.2 | None |
| spotless | 6.25.0 | None |
| rest-assured | 5.5.0 | None |
| allure-plugin | 2.12.0 | None |
| allure-adapter | 2.30.0 | None |
| resilience4j | 2.2.0 | None |
| aws-sdk | 2.25.38 | None |
| micrometer-prometheus | 1.13.1 | None |
| jjwt | 0.13.0 | None |
| openhtmltopdf | 1.0.10 | None |
| dotenv | 4.0.0 | None |

## Verification
- `ls server/settings.gradle.kts server/build.gradle.kts server/gradle/libs.versions.toml` — all exist
- No build errors when Gradle syncs (may need empty placeholder modules initially)
