# Phase 5 — Docker Compose & Bootstrap Cleanup

**Priority:** P2 — Polish
**Status:** Pending
**Blocked by:** `06-migrate-remaining-services.md`

## Goal

Consolidate Docker Compose files and update bootstrap scripts for the new mono-module Gradle structure.

## Deliverables

### 5.1 — Docker Compose Consolidation

- Check for per-service `docker-compose.yml` files that duplicate `infra/docker-compose.yml`
- Remove duplicates, consolidate to single `infra/docker-compose.yml`
- Add Docker Compose profiles if needed for selective service startup

### 5.2 — Bootstrap Script Updates

Update `bootstrap/` PowerShell scripts:
- `bootstrap/steps/03-server.ps1` — change from per-service `./gradlew` to root `./gradlew` at `server/`
- Service start commands: `./gradlew :services:selection-committee-{name}:bootRun --args='--spring.profiles.active=local'`
- Remove references to per-service Gradle wrappers

### 5.3 — workspace.json Update

Update `workspace.json` to reflect new module structure if needed.

## Verification
- `docker compose -f infra/docker-compose.yml up -d` starts all infrastructure
- Bootstrap scripts work with mono-module structure
- Each service starts via root Gradle wrapper
