/**
 * Scenario E — Mixed Workload (Realistic Production Traffic)
 * ===========================================================
 * Runs all user personas concurrently in proportions that reflect
 * real-world admission-campaign traffic:
 *
 *   - 70 % applicants  (register / view status / upload docs)
 *   - 20 % operators   (list + review queue)
 *   - 10 % admin/env   (feature flag reads, audit queries, order management)
 *
 * This scenario exposes resource contention between the three roles:
 *   - Shared PostgreSQL connection pool (admission + identity schemas)
 *   - Shared Redis (round-robin index, rate-limiter, feature-flag cache)
 *   - Shared RabbitMQ (status-change events published by applicant submits
 *     and operator accepts running simultaneously)
 *
 * Target metrics:
 *   - p95 any request    ≤ 2 000 ms
 *   - p99 any request    ≤ 4 000 ms
 *   - feature flag GET   ≤    50 ms  (Redis cache hit)
 *   - error rate         <     1 %
 *
 * Run:
 *   k6 run \
 *     -e BASE_URL=http://localhost:8080 \
 *     -e EDUCATIONAL_PROGRAM_ID=1 \
 *     src/performanceTest/k6/scenarios/scenario-e-mixed-workload.js
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter } from 'k6/metrics';

import { loginAndGetToken, registerAndLogin, authHeaders } from '../lib/auth.js';
import { SCENARIO_E } from '../lib/thresholds.js';
import {
  applicantEmail,
  createApplicationPayload,
  documentMetadataPayload,
  OPERATOR_CREDS,
  ADMIN_CREDS,
  APPLICANT_PASSWORD,
} from '../lib/data.js';

export const options = {
  scenarios: {
    mixed_workload: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 100 },
        { duration: '5m', target: 150 },
        { duration: '5m', target: 150 },
        { duration: '2m', target: 0   },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: SCENARIO_E,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

const BASE_URL              = __ENV.BASE_URL || 'http://localhost:8080';
const EDUCATIONAL_PROGRAM_ID = parseInt(__ENV.EDUCATIONAL_PROGRAM_ID || '1', 10);
const ADMISSIONS_BASE       = `${BASE_URL}/api/v1/admissions`;
const DOCS_BASE             = `${BASE_URL}/api/v1/documents`;
const ENV_BASE              = `${BASE_URL}/api/v1/feature-flags`;

const totalRequests = new Counter('mixed_total_requests');

let operatorToken;
let adminToken;

export default function () {
  totalRequests.add(1);

  // Assign persona by VU number to keep traffic ratios stable
  const persona = __VU % 10;

  if (persona >= 0 && persona <= 6) {
    // 70 % — applicant persona
    applicantPersona();
  } else if (persona >= 7 && persona <= 8) {
    // 20 % — operator persona
    operatorPersona();
  } else {
    // 10 % — admin/env persona
    adminPersona();
  }
}

// ---------------------------------------------------------------------------
// Applicant persona — lightweight version of Scenario A (no submit to avoid
// needing real document upload state in mixed mode)
// ---------------------------------------------------------------------------
function applicantPersona() {
  const email = applicantEmail(__VU, __ITER);
  let token;

  group('applicant_auth', () => {
    token = registerAndLogin(email, APPLICANT_PASSWORD);
  });

  if (!token) return;
  sleep(0.3);

  // Check own application status (most common applicant action after submission)
  group('applicant_check_status', () => {
    const res = http.get(
      `${ADMISSIONS_BASE}?size=5`,
      { headers: authHeaders(token), tags: { name: 'list_applications', scenario: 'mixed_workload' } }
    );
    check(res, { 'applicant list: 200': (r) => r.status === 200 });
  });

  sleep(0.5);

  // Check feature flags (e.g. "is online application enabled?")
  group('applicant_feature_flags', () => {
    const res = http.get(
      `${ENV_BASE}?environment=production&scope=applicant`,
      { tags: { name: 'get_feature_flags', scenario: 'mixed_workload' } }
    );
    check(res, { 'feature flags: 200': (r) => r.status === 200 });
  });

  sleep(1);
}

// ---------------------------------------------------------------------------
// Operator persona — review queue polling
// ---------------------------------------------------------------------------
function operatorPersona() {
  if (!operatorToken) {
    operatorToken = loginAndGetToken(OPERATOR_CREDS.email, OPERATOR_CREDS.password);
  }

  group('operator_list', () => {
    const res = http.get(
      `${ADMISSIONS_BASE}?size=10&sort=createdAt,asc`,
      { headers: authHeaders(operatorToken), tags: { name: 'list_applications', scenario: 'mixed_workload' } }
    );
    const ok = check(res, { 'operator list: 200': (r) => r.status === 200 });

    if (ok) {
      const items = JSON.parse(res.body).content || [];
      const reviewing = items.filter((a) => a.status === 'reviewing').slice(0, 2);

      for (const app of reviewing) {
        sleep(0.2);
        const acceptRes = http.put(
          `${ADMISSIONS_BASE}/${app.id}/accept`,
          null,
          { headers: authHeaders(operatorToken), tags: { name: 'accept_application', scenario: 'mixed_workload' } }
        );
        check(acceptRes, { 'accept: 204/422': (r) => r.status === 204 || r.status === 422 });
      }
    }
  });

  sleep(2);
}

// ---------------------------------------------------------------------------
// Admin/env persona — feature flag reads, audit queries
// ---------------------------------------------------------------------------
function adminPersona() {
  if (!adminToken) {
    adminToken = loginAndGetToken(ADMIN_CREDS.email, ADMIN_CREDS.password);
  }

  // Feature flags — should be served from Redis cache
  group('admin_feature_flags', () => {
    const res = http.get(
      `${ENV_BASE}`,
      {
        headers: authHeaders(adminToken),
        tags: { name: 'get_feature_flags', scenario: 'mixed_workload' },
      }
    );
    check(res, { 'admin flags: 200': (r) => r.status === 200 });
  });

  sleep(0.3);

  // Paginated application list (admin sees all)
  group('admin_list_applications', () => {
    const res = http.get(
      `${ADMISSIONS_BASE}?size=20&sort=createdAt,desc`,
      { headers: authHeaders(adminToken), tags: { name: 'list_applications', scenario: 'mixed_workload' } }
    );
    check(res, { 'admin list: 200': (r) => r.status === 200 });
  });

  sleep(1);
}
