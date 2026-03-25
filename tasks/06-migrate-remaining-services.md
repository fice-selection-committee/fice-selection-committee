# Phase 1.3b — Migrate Remaining Services

**Priority:** P1 — Completes build unification
**Status:** Pending
**Blocked by:** `03-auth-starter-and-pilot-migration.md`

## Goal

Migrate identity, admission, documents, environment, gateway, and e2e-tests services to use convention plugins.

## Per-Service Changes

### identity-service (217 → ~45 lines)
- Apply: `sc.spring-boot-service`, `sc.testing-conventions`, `sc.jacoco-conventions`
- Keep service-specific: jjwt, spring-data-redis, spring-data-jpa, spring-amqp, spring-retry, flyway, spring-security-test
- Remove: `ext { versions = [...] }`, duplicated spotless/errorprone/integrationTest/jacoco blocks
- Add: `implementation project(':libs:sc-auth-starter')`, `implementation project(':libs:sc-common')`, `implementation project(':libs:sc-event-contracts')`

### admission-service (281 → ~40 lines)
- Apply: `sc.spring-boot-service`, `sc.testing-conventions`, `sc.jacoco-conventions`
- Keep service-specific: resilience4j, openhtmltopdf, thymeleaf, spring-amqp
- Remove: GitHub Packages repository block, `ext { versions = [...] }`, duplicated blocks
- Add: `implementation project(':libs:sc-auth-starter')`, `implementation project(':libs:sc-common')`, `implementation project(':libs:sc-event-contracts')`

### documents-service (261 → ~45 lines)
- Apply: `sc.spring-boot-service`, `sc.testing-conventions`, `sc.jacoco-conventions`
- Keep service-specific: AWS SDK (s3, auth, regions), openhtmltopdf, zxing (QR), thymeleaf, jackson-databind
- Remove: GitHub Packages repository block, `ext { versions = [...] }`, duplicated blocks
- Add: `implementation project(':libs:sc-auth-starter')`, `implementation project(':libs:sc-common')`

### environment-service
- Apply: `sc.spring-boot-service`, `sc.testing-conventions`, `sc.jacoco-conventions`
- Keep service-specific deps
- Add: `implementation project(':libs:sc-common')`, `implementation project(':libs:sc-event-contracts')`

### gateway
- Apply: `sc.java-conventions`, `sc.testing-conventions` (NOT sc.spring-boot-service — gateway uses WebFlux)
- Keep: spring-cloud-starter-gateway, spring-boot-starter-webflux, redis reactive
- Special handling: gateway excludes spring-boot-starter-web (uses webflux instead)

### e2e-tests
- Apply: `sc.testing-conventions`
- Keep: test-specific dependencies

## Cleanup Per Service

Delete from each service directory:
- `settings.gradle`
- `gradle/wrapper/` directory
- `gradlew`, `gradlew.bat`
- `gradle/jacoco.gradle` (if present)
- `gradle/test.gradle` (if present)

## Verification
- `./gradlew build` from server/ root — ALL modules compile
- `./gradlew test` — all unit tests pass
- `./gradlew spotlessCheck` — formatting passes
- Each service boots individually
