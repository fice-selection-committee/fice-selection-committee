# Phase 3 — Code Quality Fixes

**Priority:** P2 — Polish
**Status:** Pending
**Blocked by:** `04-sc-common-library.md`

## Goal

Fix package naming typos and standardize logging.

## Fixes

### 3.1 — Package Typos in identity-service

1. **`fliter` → `filter`**
   - Path: `server/services/selection-committee-identity-service/src/main/java/edu/kpi/fice/identity_service/common/exception/fliter/`
   - Contains: `RequestResponseLogFilter.java` (moves to sc-common anyway)
   - Update all imports referencing this package

2. **`persistance` → `persistence`**
   - Path: `server/services/selection-committee-identity-service/src/main/java/edu/kpi/fice/identity_service/web/auth/persistance/`
   - Contains: `entity/` and `repository/` subdirectories
   - Update all imports referencing this package

### 3.2 — Standardize Logging

- `BaseGlobalExceptionHandler` in sc-common uses `@Slf4j` (not manual `LoggerFactory.getLogger()`)
- Verify no remaining `@Log4j2` annotations (all should be `@Slf4j`)
- Log4j2 remains as implementation; SLF4J as facade via Lombok

### 3.3 — Environment Service Package Note

Document: `edu.kpi.fice.service.environment` differs from pattern `edu.kpi.fice.{name}_service`. Defer rename to avoid breaking changes.

## Verification
- `./gradlew :services:selection-committee-identity-service:build` succeeds
- All imports resolve correctly
- `grep -r "fliter\|persistance" server/services/` returns no results
