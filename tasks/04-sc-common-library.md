# Phase 2.1 — sc-common Shared Library

**Priority:** P1 — Eliminates code duplication
**Status:** Pending
**Blocked by:** `02-buildsrc-convention-plugins.md`

## Goal

Extract duplicated cross-cutting code into `server/libs/sc-common/`.

## Classes to Extract

| Class | Source Services | Copies |
|-------|---------------|--------|
| `ErrorResponse` | admission, identity, documents, environment | 4 |
| `ResourceNotFoundException` | identity, documents, environment | 3 |
| `ValidationException` | identity, documents, environment | 3 |
| `RequestResponseLogFilter` | identity (`fliter/`), documents, environment | 3 |
| `BaseGlobalExceptionHandler` (NEW) | Extract common logic from 4 GlobalExceptionHandlers | 4 |

## Package Structure

```
edu.kpi.fice.sc.common/
├── dto/
│   └── ErrorResponse.java
├── exception/
│   ├── ResourceNotFoundException.java
│   ├── ValidationException.java
│   └── BaseGlobalExceptionHandler.java
└── filter/
    └── RequestResponseLogFilter.java
```

## BaseGlobalExceptionHandler Design

Abstract `@RestControllerAdvice` handling:
- `MethodArgumentNotValidException` → 400
- `ConstraintViolationException` → 400
- `HttpMessageNotReadableException` → 400
- `MissingServletRequestParameterException` → 400
- `HttpRequestMethodNotSupportedException` → 405
- `AccessDeniedException` → 403
- `ResourceNotFoundException` → 404
- `Exception` (catch-all) → 500

Each service subclasses to add domain-specific handlers.

## Build File

`libs/sc-common/build.gradle`:
- Apply `sc.library-conventions`
- `api`: spring-boot-starter-web, spring-boot-starter-validation
- `compileOnly`: lombok

## Service Updates

After extraction, update each service to:
1. Add `implementation project(':libs:sc-common')`
2. Delete local copies of ErrorResponse, ResourceNotFoundException, ValidationException, RequestResponseLogFilter
3. Update GlobalExceptionHandler to extend BaseGlobalExceptionHandler
4. Update imports throughout

## Verification
- `./gradlew :libs:sc-common:build` succeeds
- All services compile with shared classes
- All existing tests pass
