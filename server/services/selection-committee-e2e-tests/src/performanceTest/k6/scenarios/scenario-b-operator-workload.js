/**
 * Scenario B — Operator Workload
 * ================================
 * Simulates 20 concurrent operators processing their review queues:
 *
 *   login → list assigned applications (paginated) → for each application:
 *     fetch details → review → accept → (repeat)
 *
 * Pre-condition: the database must already contain at least 500 applications
 * in "submitted" status so that operators have work to pick up.
 * Use the seed script (db/seed-submitted-applications.sql) before running.
 *
 * Auto-assignment scheduler fires every 30 s and moves submitted apps to
 * reviewing. Scenario B exercises the manual review/accept path that
 * operators take on already-assigned applications.
 *
 * Target metrics:
 *   - p95 list query  ≤ 600 ms
 *   - p95 accept      ≤ 800 ms
 *   - error rate      <   1 %
 *
 * Run:
 *   k6 run \
 *     -e BASE_URL=http://localhost:8080 \
 *     -e OPERATOR_EMAIL=operator@fice-perf.test \
 *     -e OPERATOR_PASSWORD=OperatorPerf1! \
 *     src/performanceTest/k6/scenarios/scenario-b-operator-workload.js
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate } from 'k6/metrics';

import { loginAndGetToken, authHeaders } from '../lib/auth.js';
import { SCENARIO_B } from '../lib/thresholds.js';
import { OPERATOR_CREDS } from '../lib/data.js';

export const options = {
  scenarios: {
    operator_workload: {
      executor: 'constant-vus',
      vus: 20,
      duration: '10m',
    },
  },
  thresholds: SCENARIO_B,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

const acceptedCount = new Counter('operator_accepted_total');
const emptyQueue    = new Counter('operator_empty_queue_count');
const reviewErrors  = new Rate('operator_review_error_rate');

const BASE_URL        = __ENV.BASE_URL || 'http://localhost:8080';
const ADMISSIONS_BASE = `${BASE_URL}/api/v1/admissions`;

// Each VU logs in once and reuses the token for the entire 10-minute run.
// The token is stored in a module-level variable scoped to the VU.
let operatorToken;

export function setup() {
  // Verify at least one operator can log in before the test starts
  const token = loginAndGetToken(OPERATOR_CREDS.email, OPERATOR_CREDS.password);
  return { tokenSample: token };
}

export default function () {
  // Lazy initialisation — log in once per VU
  if (!operatorToken) {
    operatorToken = loginAndGetToken(OPERATOR_CREDS.email, OPERATOR_CREDS.password);
  }

  // ------------------------------------------------------------------
  // Step 1 — List reviewing applications assigned to this operator
  //          Use page size 10 to mimic a realistic UI table view
  // ------------------------------------------------------------------
  let appIds = [];

  group('list_applications', () => {
    const res = http.get(
      `${ADMISSIONS_BASE}?size=10&page=0&sort=createdAt,asc`,
      { headers: authHeaders(operatorToken), tags: { name: 'list_applications', scenario: 'operator_workload' } }
    );

    const ok = check(res, {
      'list: status 200': (r) => r.status === 200,
      'list: content array': (r) => {
        try { return Array.isArray(JSON.parse(r.body).content); } catch { return false; }
      },
    });

    if (ok) {
      const body  = JSON.parse(res.body);
      const items = body.content || [];
      // Filter only reviewing-status apps to simulate a real operator queue filter
      appIds = items
        .filter((a) => a.status === 'reviewing' || a.status === 'submitted')
        .map((a) => a.id)
        .slice(0, 3); // process at most 3 per iteration to stay within SLA
    }
  });

  if (appIds.length === 0) {
    emptyQueue.add(1);
    sleep(5); // back-off when queue is empty
    return;
  }

  // ------------------------------------------------------------------
  // Step 2 — For each picked application: fetch details then accept
  // ------------------------------------------------------------------
  for (const id of appIds) {
    group('fetch_application_detail', () => {
      const res = http.get(
        `${ADMISSIONS_BASE}/${id}`,
        { headers: authHeaders(operatorToken), tags: { name: 'get_application', scenario: 'operator_workload' } }
      );
      check(res, { 'get detail: status 200': (r) => r.status === 200 });
    });

    sleep(0.3); // simulate brief review time

    group('accept_application', () => {
      // Move from reviewing → accepted
      const res = http.put(
        `${ADMISSIONS_BASE}/${id}/accept`,
        null,
        { headers: authHeaders(operatorToken), tags: { name: 'accept_application', scenario: 'operator_workload' } }
      );

      const ok = check(res, {
        // 204 = accepted, 422/409 = already accepted/wrong status from parallel operator — both acceptable
        'accept: 204 or 422': (r) => r.status === 204 || r.status === 422 || r.status === 409,
      });

      if (res.status === 204) {
        acceptedCount.add(1);
      }
      if (!ok) {
        reviewErrors.add(1);
      }
    });

    sleep(0.5);
  }

  sleep(2);
}
