# Phase 1.2 — buildSrc Convention Plugins

**Priority:** P0 — Foundation (blocks service migration)
**Status:** Pending
**Blocked by:** `01-gradle-root-infrastructure.md`

## Goal

Create 5 convention plugins in `server/buildSrc/` to eliminate ~150 lines of duplicated build logic per service.

## Deliverables

### `server/buildSrc/build.gradle.kts`
Kotlin DSL build file with Gradle plugin portal and Spring Boot/Spotless/ErrorProne/Allure plugin dependencies.

### Convention Plugins

#### 1. `sc.java-conventions.gradle.kts`
Extracts from every service:
- Java 21 toolchain
- `compileOnly { extendsFrom annotationProcessor }`
- Lombok annotation processor setup
- ErrorProne: `-Xep:UnusedVariable:WARN`, `-Xep:EqualsHashCode:ERROR`
- Spotless: Google Java Format 1.17.0, target `src/**/*.java`

#### 2. `sc.spring-boot-service.gradle.kts`
Applies `sc.java-conventions` plus:
- `org.springframework.boot` + `io.spring.dependency-management`
- Spring Cloud BOM import
- Exclude `spring-boot-starter-logging`
- Common deps: web, validation, log4j2, actuator, prometheus, tracing, springdoc, dotenv

#### 3. `sc.testing-conventions.gradle.kts`
Extracts from every service (lines 165-199 pattern):
- `integrationTest` source set
- `processIntegrationTestResources { duplicatesStrategy = EXCLUDE }`
- JUnit Platform with tag-based inclusion/exclusion
- Allure plugin config (version 2.30.0, autoconfigure=true)
- Common test deps: spring-boot-starter-test, rest-assured, junit-platform-launcher

#### 4. `sc.jacoco-conventions.gradle.kts`
Consolidates two JaCoCo patterns:
- `gradle/jacoco.gradle` (used by identity, notifications, environment, gateway)
- Inline JaCoCo (admission, documents)
- Parameterized: `ext.minimumCoveragePerFile`, `ext.filesExcludedFromCoverage`

#### 5. `sc.library-conventions.gradle.kts`
For shared library modules:
- `java-library` plugin
- No bootJar (skip Spring Boot fat jar)
- `sc.java-conventions` applied
- `maven-publish` for local composite builds

## Verification
- `./gradlew buildSrc:build` compiles without errors
- Convention plugins are accessible in service build files
