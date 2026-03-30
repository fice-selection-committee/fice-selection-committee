/**
 * Scenario A — Admission Peak Load
 * =================================
 * Simulates the busiest period of the admission campaign:
 * 200 concurrent applicants each completing the full self-service journey:
 *
 *   register → login → create application → upload passport doc (metadata + presign)
 *   → upload ipn doc (metadata + presign) → submit application
 *
 * The "upload" step in this scenario only calls createMetadata + presign —
 * it does NOT push the binary payload to MinIO (that is Scenario D).
 * This keeps the scenario focused on the admission-service and identity-service
 * response times rather than MinIO / network throughput.
 *
 * Target metrics (see thresholds.js):
 *   - p95 full journey ≤ 3 000 ms
 *   - p95 login       ≤   500 ms
 *   - p95 create app  ≤ 1 000 ms
 *   - p95 submit      ≤ 2 000 ms
 *   - error rate      <     1 %
 *
 * Run:
 *   k6 run \
 *     -e BASE_URL=http://localhost:8080 \
 *     -e EDUCATIONAL_PROGRAM_ID=1 \
 *     src/performanceTest/k6/scenarios/scenario-a-admission-peak.js
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Counter } from 'k6/metrics';

import { registerAndLogin, authHeaders } from '../lib/auth.js';
import { SCENARIO_A } from '../lib/thresholds.js';
import {
  applicantEmail,
  createApplicationPayload,
  documentMetadataPayload,
  APPLICANT_PASSWORD,
} from '../lib/data.js';

// ---------------------------------------------------------------------------
// k6 options
// ---------------------------------------------------------------------------
export const options = {
  scenarios: {
    admission_peak: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 50  },  // ramp up to 50 VUs over 2 min
        { duration: '3m', target: 200 },  // ramp up to 200 VUs over 3 min
        { duration: '5m', target: 200 },  // sustain 200 VUs for 5 min
        { duration: '2m', target: 0   },  // ramp down
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: SCENARIO_A,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

// ---------------------------------------------------------------------------
// Custom metrics
// ---------------------------------------------------------------------------
const journeyDuration  = new Trend('applicant_journey_duration_ms', true);
const submittedApps    = new Counter('submitted_applications_total');
const failedJourneys   = new Counter('failed_journeys_total');

const BASE_URL             = __ENV.BASE_URL || 'http://localhost:8080';
const EDUCATIONAL_PROGRAM_ID = parseInt(__ENV.EDUCATIONAL_PROGRAM_ID || '1', 10);
const DOCS_BASE            = `${BASE_URL}/api/v1/documents`;
const ADMISSIONS_BASE      = `${BASE_URL}/api/v1/admissions`;

// ---------------------------------------------------------------------------
// Default function (one full journey per VU iteration)
// ---------------------------------------------------------------------------
export default function () {
  const email    = applicantEmail(__VU, __ITER);
  const start    = Date.now();
  let   token;
  let   appId;

  // ------------------------------------------------------------------
  // Step 1 — Register + Login
  // ------------------------------------------------------------------
  group('auth', () => {
    token = registerAndLogin(email, APPLICANT_PASSWORD);
  });

  if (!token) {
    failedJourneys.add(1);
    return;
  }

  sleep(0.5);

  // ------------------------------------------------------------------
  // Step 2 — Create application (draft)
  // ------------------------------------------------------------------
  group('create_application', () => {
    const res = http.post(
      ADMISSIONS_BASE,
      JSON.stringify(createApplicationPayload(EDUCATIONAL_PROGRAM_ID)),
      { headers: authHeaders(token), tags: { name: 'create_application', scenario: 'admission_peak' } }
    );

    const ok = check(res, {
      'create_app: status 201': (r) => r.status === 201,
      'create_app: id present': (r) => {
        try { return JSON.parse(r.body).id > 0; } catch { return false; }
      },
    });

    if (ok) {
      appId = JSON.parse(res.body).id;
    } else {
      failedJourneys.add(1);
    }
  });

  if (!appId) return;
  sleep(1);

  // ------------------------------------------------------------------
  // Step 3 — Upload passport document metadata + retrieve presign URL
  // ------------------------------------------------------------------
  group('upload_passport', () => {
    const metaRes = http.post(
      `${DOCS_BASE}/metadata`,
      JSON.stringify(documentMetadataPayload(5 * 1024 * 1024, 'passport')),  // 5 MB
      { headers: authHeaders(token), tags: { name: 'presign_upload', scenario: 'admission_peak' } }
    );
    check(metaRes, { 'passport metadata: 200/201': (r) => r.status === 200 || r.status === 201 });
  });

  sleep(0.5);

  // ------------------------------------------------------------------
  // Step 4 — Upload IPN document metadata
  // ------------------------------------------------------------------
  group('upload_ipn', () => {
    const metaRes = http.post(
      `${DOCS_BASE}/metadata`,
      JSON.stringify(documentMetadataPayload(1 * 1024 * 1024, 'ipn')),  // 1 MB
      { headers: authHeaders(token), tags: { name: 'presign_upload', scenario: 'admission_peak' } }
    );
    check(metaRes, { 'ipn metadata: 200/201': (r) => r.status === 200 || r.status === 201 });
  });

  sleep(1);

  // ------------------------------------------------------------------
  // Step 5 — Submit application (Feign call: admission → documents)
  // ------------------------------------------------------------------
  group('submit_application', () => {
    const res = http.put(
      `${ADMISSIONS_BASE}/${appId}/submit`,
      null,
      { headers: authHeaders(token), tags: { name: 'submit_application', scenario: 'admission_peak' } }
    );

    const ok = check(res, {
      'submit: status 204': (r) => r.status === 204,
    });

    if (ok) {
      submittedApps.add(1);
    } else {
      failedJourneys.add(1);
    }
  });

  // Record total wall-clock time for the full journey
  journeyDuration.add(Date.now() - start);
  sleep(2);
}
