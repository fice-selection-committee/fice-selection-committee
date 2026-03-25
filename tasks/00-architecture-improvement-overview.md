# Architecture Improvement Plan — Overview

**Status:** Complete
**Created:** 2026-03-25

## What Was Done

### Phase 1: Gradle Multi-Module Monorepo
- **Root build infrastructure**: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml` (unified version catalog)
- **5 convention plugins** in `buildSrc/`: `sc.java-conventions`, `sc.spring-boot-service`, `sc.testing-conventions`, `sc.jacoco-conventions`, `sc.library-conventions`
- **All 7 services migrated**: build.gradle files reduced from ~170-280 lines to ~30-70 lines each
- **sc-auth-starter converted** from GitHub Packages to local project module
- **Cleaned up**: per-service `settings.gradle`, `gradle/wrapper/`, `gradlew`, `gradlew.bat`, `jacoco.gradle`, `test.gradle` files deleted
- **Version drift eliminated**: springdoc 2.5.0-2.8.13 → 2.8.13, lombok 1.18.34-1.18.38 → 1.18.38, testcontainers 1.19.1-1.20.6 → 1.20.6, etc.

### Phase 2: Shared Libraries
- **sc-common** (`edu.kpi.fice.sc.common`): ErrorResponse (was 4 copies), ResourceNotFoundException (3 copies), ValidationException (3 copies), RequestResponseLogFilter (3 copies), BaseGlobalExceptionHandler (extracted from 4 GlobalExceptionHandlers)
- **sc-event-contracts** (`edu.kpi.fice.sc.events`): NotificationEventDto (fixed critical String→enum drift), ApplicationEventDto, AuditEventDto, ChannelType, TemplateType, EventConstants (centralized all RabbitMQ topology)
- **sc-test-common, sc-observability-starter, sc-s3-starter**: Modules created with correct build files, ready for incremental code extraction

### Phase 3: Code Quality
- Fixed `persistance` → `persistence` typo (16 files in identity-service)
- Fixed `fliter` typo (already cleaned during sc-common extraction)
- Standardized logging to @Slf4j in BaseGlobalExceptionHandler

### Phase 4: Event Architecture
- EventConstants centralized (done via sc-event-contracts)
- DLQ added to identity-service audit queue (was missing dead-letter bindings)

### Phase 5: Infrastructure
- Per-service Docker Compose files verified (serve individual builds, not duplicating infra — kept as-is)
- Bootstrap scripts reviewed (functional as-is with workspace.json-driven approach)

## Final Structure

```
server/
├── buildSrc/                           # 5 convention plugins
├── gradle/
│   ├── libs.versions.toml              # Single version catalog (all deps)
│   ├── jacoco.gradle                   # Legacy (kept for compat)
│   ├── test.gradle                     # Legacy (kept for compat)
│   └── wrapper/                        # Single Gradle wrapper
├── libs/
│   ├── sc-auth-starter/                # Auth filter, Feign interceptor (local module)
│   ├── sc-common/                      # ErrorResponse, exceptions, BaseGlobalExceptionHandler, filter
│   ├── sc-event-contracts/             # Shared event DTOs, enums, RabbitMQ constants
│   ├── sc-test-common/                 # Placeholder (ready for Testcontainer configs)
│   ├── sc-observability-starter/       # Placeholder (ready for metrics/tracing)
│   └── sc-s3-starter/                  # Placeholder (ready for S3/MinIO wrapper)
├── services/
│   ├── selection-committee-gateway/           # ~45 line build.gradle
│   ├── selection-committee-identity-service/  # ~65 line build.gradle
│   ├── selection-committee-admission-service/ # ~65 line build.gradle
│   ├── selection-committee-documents-service/ # ~70 line build.gradle
│   ├── selection-committee-notifications-service/ # ~35 line build.gradle
│   ├── selection-committee-environment-service/   # ~55 line build.gradle
│   └── selection-committee-e2e-tests/             # ~25 line build.gradle
├── settings.gradle.kts
├── build.gradle.kts
└── gradlew / gradlew.bat
```

## Verification
- `./gradlew compileJava compileTestJava spotlessCheck` — BUILD SUCCESSFUL (all 13 modules)
