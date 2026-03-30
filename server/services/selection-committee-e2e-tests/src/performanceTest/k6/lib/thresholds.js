/**
 * thresholds.js — centralised SLA definitions.
 *
 * Every scenario imports this file so that SLA changes are applied
 * everywhere without hunting through individual test files.
 *
 * Threshold semantics (k6 built-ins):
 *   http_req_duration          — total round-trip time (ms)
 *   http_req_failed            — fraction of requests that returned a
 *                                non-2xx / non-expected status
 *   http_reqs                  — request throughput (req/s)
 *   checks                     — fraction of check() assertions that passed
 *
 * Naming convention for scenario-scoped counters:
 *   http_req_duration{scenario:<name>}
 */

// ---------------------------------------------------------------------------
// Core SLA — applies to every scenario unless overridden
// ---------------------------------------------------------------------------

/** p95 ≤ 800 ms, p99 ≤ 1500 ms for any HTTP request */
export const CORE_DURATION = {
  'http_req_duration{expected_response:true}': [
    { threshold: 'p(95)<800',  abortOnFail: false },
    { threshold: 'p(99)<1500', abortOnFail: false },
  ],
};

/** Overall error rate must stay below 1 % */
export const CORE_ERROR_RATE = {
  http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: true }],
};

/** At least 95 % of k6 check() assertions must pass */
export const CORE_CHECKS = {
  checks: [{ threshold: 'rate>0.95', abortOnFail: false }],
};

// ---------------------------------------------------------------------------
// Scenario A — Admission peak (200 VU applicant flow)
// ---------------------------------------------------------------------------
export const SCENARIO_A = {
  ...CORE_ERROR_RATE,
  ...CORE_CHECKS,
  // Full applicant journey (register → login → create → upload → submit) p95 ≤ 3 s
  'http_req_duration{scenario:admission_peak}': [
    { threshold: 'p(95)<3000', abortOnFail: false },
    { threshold: 'p(99)<5000', abortOnFail: false },
  ],
  // Login endpoint alone must answer in ≤ 500 ms at p95
  'http_req_duration{name:auth_login}': [
    { threshold: 'p(95)<500', abortOnFail: false },
  ],
  // Application creation must stay under 1 s p95
  'http_req_duration{name:create_application}': [
    { threshold: 'p(95)<1000', abortOnFail: false },
  ],
  // Presign URL generation ≤ 300 ms (pure DB + S3 presign, no data transfer)
  'http_req_duration{name:presign_upload}': [
    { threshold: 'p(95)<300', abortOnFail: false },
  ],
  // Application submit (involves cross-service Feign call to documents-service)
  'http_req_duration{name:submit_application}': [
    { threshold: 'p(95)<2000', abortOnFail: false },
  ],
};

// ---------------------------------------------------------------------------
// Scenario B — Operator workload (20 VU operators reviewing applications)
// ---------------------------------------------------------------------------
export const SCENARIO_B = {
  ...CORE_ERROR_RATE,
  ...CORE_CHECKS,
  'http_req_duration{scenario:operator_workload}': [
    { threshold: 'p(95)<1000', abortOnFail: false },
    { threshold: 'p(99)<2000', abortOnFail: false },
  ],
  // Paginated list queries by operators must be fast even under load
  'http_req_duration{name:list_applications}': [
    { threshold: 'p(95)<600', abortOnFail: false },
  ],
  // Status transitions (review/accept) are transactional DB writes
  'http_req_duration{name:accept_application}': [
    { threshold: 'p(95)<800', abortOnFail: false },
  ],
};

// ---------------------------------------------------------------------------
// Scenario C — Batch enrollment order signing (50 applications per order)
// ---------------------------------------------------------------------------
export const SCENARIO_C = {
  ...CORE_ERROR_RATE,
  ...CORE_CHECKS,
  // Sign endpoint is a transactional batch update of 50 rows
  'http_req_duration{name:sign_order}': [
    { threshold: 'p(95)<5000', abortOnFail: false },
    { threshold: 'p(99)<8000', abortOnFail: false },
  ],
};

// ---------------------------------------------------------------------------
// Scenario D — Document upload stress (50 VU × 10 MB files)
// ---------------------------------------------------------------------------
export const SCENARIO_D = {
  ...CORE_CHECKS,
  // Higher error budget: network saturation may cause occasional failures
  http_req_failed: [{ threshold: 'rate<0.03', abortOnFail: true }],
  // createMetadata (DB + S3 list + delete + insert) — presign only
  'http_req_duration{name:create_metadata}': [
    { threshold: 'p(95)<1000', abortOnFail: false },
  ],
  // Actual PUT to MinIO pre-signed URL (includes 10 MB payload transfer)
  // SLA is relaxed because MinIO throughput is infra-dependent
  'http_req_duration{name:upload_to_minio}': [
    { threshold: 'p(95)<30000', abortOnFail: false },
  ],
};

// ---------------------------------------------------------------------------
// Scenario E — Mixed workload
// ---------------------------------------------------------------------------
export const SCENARIO_E = {
  ...CORE_ERROR_RATE,
  ...CORE_CHECKS,
  'http_req_duration{scenario:mixed_workload}': [
    { threshold: 'p(95)<2000', abortOnFail: false },
    { threshold: 'p(99)<4000', abortOnFail: false },
  ],
  // Feature flag endpoint is cached in Redis — must be very fast
  'http_req_duration{name:get_feature_flags}': [
    { threshold: 'p(95)<50', abortOnFail: false },
  ],
};
