# Phase 2.3-2.5 — Remaining Shared Libraries

**Priority:** P2 — Quality improvements
**Status:** Pending
**Blocked by:** `06-migrate-remaining-services.md`

## Goal

Create sc-test-common, sc-observability-starter, and sc-s3-starter shared libraries.

## 2.3 — sc-test-common

**Path:** `server/libs/sc-test-common/`
**Package:** `edu.kpi.fice.sc.test`

Composable Testcontainer configurations:
- `PostgresContainerConfig` — PostgreSQLContainer<>("postgres:16-alpine")
- `RabbitContainerConfig` — RabbitMQContainer("rabbitmq:3.13-management-alpine")
- `RedisContainerConfig` — GenericContainer<>("redis:7-alpine")
- `MinioContainerConfig` — for documents-service
- `BaseServiceIntegrationTest` — composable via @Import

Services compose containers via `@Import`:
```java
@Import({PostgresContainerConfig.class, RabbitContainerConfig.class})
public abstract class AbstractIntegrationTest extends BaseServiceIntegrationTest { }
```

**Deps:** testcontainers BOM, spring-boot-testcontainers, JUnit 5

## 2.4 — sc-observability-starter

**Path:** `server/libs/sc-observability-starter/`
**Package:** `edu.kpi.fice.sc.observability`

Auto-configures identical observability blocks from every service:
- Actuator endpoint exposure (health, info, prometheus)
- Prometheus metrics registry
- Micrometer tracing (Brave + Zipkin)
- Health probes (liveness + readiness)
- Common Micrometer meter binders

Spring Boot auto-configuration via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

**Deps:** sc-common, micrometer-registry-prometheus, micrometer-tracing-bridge-brave, zipkin-reporter-brave

## 2.5 — sc-s3-starter

**Path:** `server/libs/sc-s3-starter/`
**Package:** `edu.kpi.fice.sc.s3`

Extract from documents-service:
- `S3Config` — S3Client + S3Presigner bean factory
- `S3Properties` — @ConfigurationProperties for endpoint, credentials, region
- `S3BucketInitializer` — bucket creation on startup

**Deps:** sc-common, AWS SDK (s3, auth, regions)

## Verification
- `./gradlew :libs:sc-test-common:build` succeeds
- `./gradlew :libs:sc-observability-starter:build` succeeds
- `./gradlew :libs:sc-s3-starter:build` succeeds
- Integration tests still pass using shared container configs
