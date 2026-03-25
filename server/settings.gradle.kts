rootProject.name = "fice-selection-committee"

// Version catalog auto-discovered from gradle/libs.versions.toml

// ── Shared libraries ──────────────────────────────────────
include("libs:sc-auth-starter")
include("libs:sc-common")
include("libs:sc-event-contracts")
include("libs:sc-test-common")
include("libs:sc-observability-starter")
include("libs:sc-s3-starter")

// ── Services ──────────────────────────────────────────────
include("services:selection-committee-gateway")
include("services:selection-committee-identity-service")
include("services:selection-committee-admission-service")
include("services:selection-committee-documents-service")
include("services:selection-committee-notifications-service")
include("services:selection-committee-environment-service")
include("services:selection-committee-e2e-tests")
