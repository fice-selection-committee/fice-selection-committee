# Phase 2.6 + 1.3a — Migrate sc-auth-starter & Pilot Service

**Priority:** P0 — Validates new build structure
**Status:** Pending
**Blocked by:** `02-buildsrc-convention-plugins.md`

## Goal

Convert sc-auth-starter from GitHub Packages to local module. Migrate notifications-service as pilot to validate the new build structure works end-to-end.

## Deliverables

### sc-auth-starter Migration
- Replace `build.gradle` (60 lines → ~15 lines)
- Apply `sc.library-conventions`
- Remove `maven-publish` GitHub Packages publishing block
- Dependencies use version catalog references

### notifications-service Migration (Pilot)
- Replace `build.gradle` (170 lines → ~30 lines)
- Apply `sc.spring-boot-service`, `sc.testing-conventions`, `sc.jacoco-conventions`
- Change `implementation 'edu.kpi.fice:sc-auth-starter:1.0.0'` → not applicable (notifications doesn't use auth-starter)
- Remove `ext { versions = [...] }` block
- Remove duplicated integrationTest sourceset config (now in convention plugin)
- Remove duplicated spotless/errorprone/allure config
- Delete: `settings.gradle`, `gradle/wrapper/`, `gradlew`, `gradlew.bat`
- Keep: `gradle/jacoco.gradle` → remove (now in convention plugin), `gradle/test.gradle` → remove

## Verification
- `./gradlew :services:selection-committee-notifications-service:build` succeeds
- `./gradlew :services:selection-committee-notifications-service:test` passes
- `./gradlew :libs:sc-auth-starter:build` succeeds
- No GitHub Packages credentials required
