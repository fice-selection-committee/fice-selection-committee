#!/usr/bin/env bash
# =============================================================================
# run-all-perf-tests.sh — Performance Test Suite Runner
# =============================================================================
# Executes all k6 performance scenarios in sequence and produces a consolidated
# report. Designed to run in CI/CD or locally after the full stack is up.
#
# Prerequisites:
#   - k6 installed (https://k6.io/docs/getting-started/installation/)
#   - All 6 Spring Boot services running (use docker-compose up)
#   - Database seeded with test data (see db/seed-*.sql scripts)
#   - Environment variables set (see .env.perf.template)
#
# Usage:
#   chmod +x run-all-perf-tests.sh
#   BASE_URL=http://localhost:8080 ./run-all-perf-tests.sh
#
# Exit codes:
#   0 — all thresholds passed
#   1 — one or more thresholds failed
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K6_DIR="${SCRIPT_DIR}/k6"
REPORT_DIR="${SCRIPT_DIR}/reports"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# ---------------------------------------------------------------------------
# Configuration defaults (override via environment)
# ---------------------------------------------------------------------------
BASE_URL="${BASE_URL:-http://localhost:8080}"
EDUCATIONAL_PROGRAM_ID="${EDUCATIONAL_PROGRAM_ID:-1}"
OPERATOR_EMAIL="${OPERATOR_EMAIL:-operator@fice-perf.test}"
OPERATOR_PASSWORD="${OPERATOR_PASSWORD:-OperatorPerf1!}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@fice-perf.test}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-AdminPerf1!}"
APPLICANT_PASSWORD="${APPLICANT_PASSWORD:-ApplicantPerf1!}"
MINIO_DIRECT_URL="${MINIO_DIRECT_URL:-http://localhost:9000}"
RABBITMQ_MGMT_URL="${RABBITMQ_MGMT_URL:-http://localhost:15672}"
RABBITMQ_USER="${RABBITMQ_USER:-scadmin}"
RABBITMQ_PASS="${RABBITMQ_PASS:-scadminpass}"
ADMISSION_ACTUATOR="${ADMISSION_ACTUATOR:-http://localhost:8083/actuator}"
IDENTITY_ACTUATOR="${IDENTITY_ACTUATOR:-http://localhost:8081/actuator}"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
mkdir -p "${REPORT_DIR}"

log() { echo "[$(date '+%H:%M:%S')] $*"; }
separator() { echo ""; echo "================================================================"; echo ""; }

FAILED_SCENARIOS=()

run_scenario() {
  local name="$1"
  local script="$2"
  shift 2
  local extra_args=("$@")

  separator
  log "Starting: ${name}"
  log "Script:   ${script}"

  local report_file="${REPORT_DIR}/${TIMESTAMP}_${name}.json"

  set +e
  k6 run \
    --out json="${report_file}" \
    -e BASE_URL="${BASE_URL}" \
    -e EDUCATIONAL_PROGRAM_ID="${EDUCATIONAL_PROGRAM_ID}" \
    -e OPERATOR_EMAIL="${OPERATOR_EMAIL}" \
    -e OPERATOR_PASSWORD="${OPERATOR_PASSWORD}" \
    -e ADMIN_EMAIL="${ADMIN_EMAIL}" \
    -e ADMIN_PASSWORD="${ADMIN_PASSWORD}" \
    -e APPLICANT_PASSWORD="${APPLICANT_PASSWORD}" \
    -e MINIO_DIRECT_URL="${MINIO_DIRECT_URL}" \
    -e RABBITMQ_MGMT_URL="${RABBITMQ_MGMT_URL}" \
    -e RABBITMQ_USER="${RABBITMQ_USER}" \
    -e RABBITMQ_PASS="${RABBITMQ_PASS}" \
    -e ADMISSION_ACTUATOR="${ADMISSION_ACTUATOR}" \
    -e IDENTITY_ACTUATOR="${IDENTITY_ACTUATOR}" \
    "${extra_args[@]}" \
    "${script}"
  local exit_code=$?
  set -e

  if [ ${exit_code} -ne 0 ]; then
    log "FAILED: ${name} (exit ${exit_code})"
    FAILED_SCENARIOS+=("${name}")
  else
    log "PASSED: ${name}"
  fi

  log "Report: ${report_file}"
}

# ---------------------------------------------------------------------------
# Smoke check — verify all services are reachable before running load tests
# ---------------------------------------------------------------------------
separator
log "Smoke check: verifying all services are reachable..."

services=(
  "${BASE_URL}/actuator/health"
  "http://localhost:8081/actuator/health"
  "http://localhost:8083/actuator/health"
  "http://localhost:8084/actuator/health"
  "http://localhost:8085/actuator/health"
  "http://localhost:8086/actuator/health"
)

for url in "${services[@]}"; do
  if curl -sf "${url}" > /dev/null 2>&1; then
    log "  UP: ${url}"
  else
    log "  DOWN: ${url} — aborting performance tests"
    exit 1
  fi
done
log "All services are up. Starting performance tests."

# ---------------------------------------------------------------------------
# Scenario A — Admission Peak
# ---------------------------------------------------------------------------
run_scenario "scenario-a-admission-peak" \
  "${K6_DIR}/scenarios/scenario-a-admission-peak.js"

# Wait 60 s between scenarios to let the system recover
sleep 60

# ---------------------------------------------------------------------------
# Scenario B — Operator Workload
# ---------------------------------------------------------------------------
run_scenario "scenario-b-operator-workload" \
  "${K6_DIR}/scenarios/scenario-b-operator-workload.js"

sleep 60

# ---------------------------------------------------------------------------
# Scenario C — Batch Order Signing
# ---------------------------------------------------------------------------
run_scenario "scenario-c-batch-order-signing" \
  "${K6_DIR}/scenarios/scenario-c-batch-order-signing.js"

sleep 60

# ---------------------------------------------------------------------------
# Scenario D — Document Upload Stress
# ---------------------------------------------------------------------------
run_scenario "scenario-d-document-upload-stress" \
  "${K6_DIR}/scenarios/scenario-d-document-upload-stress.js"

sleep 60

# ---------------------------------------------------------------------------
# Scenario E — Mixed Workload
# ---------------------------------------------------------------------------
run_scenario "scenario-e-mixed-workload" \
  "${K6_DIR}/scenarios/scenario-e-mixed-workload.js"

sleep 60

# ---------------------------------------------------------------------------
# Infrastructure stress tests (shorter runs — run only if previous passed)
# ---------------------------------------------------------------------------
if [ ${#FAILED_SCENARIOS[@]} -eq 0 ]; then
  run_scenario "infra-redis-pool" \
    "${SCRIPT_DIR}/infra/redis-connection-pool-stress.js"

  sleep 30

  run_scenario "infra-postgres-pool" \
    "${SCRIPT_DIR}/infra/postgres-connection-pool-load.js"

  sleep 30

  run_scenario "infra-rabbitmq-depth" \
    "${SCRIPT_DIR}/infra/rabbitmq-queue-depth.js"

  sleep 30

  run_scenario "infra-minio-uploads" \
    "${SCRIPT_DIR}/infra/minio-concurrent-uploads.js"
else
  log "Skipping infrastructure tests because ${#FAILED_SCENARIOS[@]} scenario(s) failed."
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
separator
log "Performance Test Suite Complete"
log "Reports saved to: ${REPORT_DIR}/"

if [ ${#FAILED_SCENARIOS[@]} -gt 0 ]; then
  log ""
  log "FAILED SCENARIOS:"
  for s in "${FAILED_SCENARIOS[@]}"; do
    log "  - ${s}"
  done
  log ""
  log "Exit code: 1 (threshold violations detected)"
  exit 1
else
  log "All scenarios passed all thresholds."
  exit 0
fi
