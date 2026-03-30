/**
 * Scenario C — Batch Enrollment Order Signing
 * =============================================
 * Signs enrollment orders each covering 50 applications.
 *
 * The signOrder() service method executes a transactional batch:
 *   - Load order + items (N+1 guarded by JPA join-fetch)
 *   - For each of 50 OrderItems: update Application.status → enrolled
 *   - Publish 50 RabbitMQ status-change events
 *   - Persist Order.status → signed
 *
 * This scenario validates that the system can handle end-of-campaign batch
 * processing (dozens of simultaneous order signings by secretaries) without
 * deadlocks, connection pool exhaustion, or unacceptable latency.
 *
 * Pre-condition: seed the DB with draft orders each containing 50 accepted
 * applications via db/seed-orders-for-signing.sql before running.
 *
 * Target metrics:
 *   - p95 sign order ≤ 5 000 ms (batch TX with 50 rows + 50 events)
 *   - p99 sign order ≤ 8 000 ms
 *   - error rate     <     1 %
 *
 * Run:
 *   k6 run \
 *     -e BASE_URL=http://localhost:8080 \
 *     -e ADMIN_EMAIL=admin@fice-perf.test \
 *     -e ADMIN_PASSWORD=AdminPerf1! \
 *     src/performanceTest/k6/scenarios/scenario-c-batch-order-signing.js
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter } from 'k6/metrics';

import { loginAndGetToken, authHeaders } from '../lib/auth.js';
import { SCENARIO_C } from '../lib/thresholds.js';
import { ADMIN_CREDS } from '../lib/data.js';

export const options = {
  scenarios: {
    batch_order_signing: {
      executor: 'ramping-arrival-rate',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: 15,
      maxVUs: 30,
      stages: [
        { duration: '1m', target: 5  },   // 5 sign-requests/s for 1 min
        { duration: '3m', target: 10 },   // 10 sign-requests/s for 3 min
        { duration: '1m', target: 0  },   // ramp down
      ],
    },
  },
  thresholds: SCENARIO_C,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

const signedOrders  = new Counter('signed_orders_total');
const conflictCount = new Counter('sign_conflict_count'); // already signed by another VU

const BASE_URL    = __ENV.BASE_URL || 'http://localhost:8080';
const ORDERS_BASE = `${BASE_URL}/api/v1/orders`;

let adminToken;

// setup() returns the list of draft order IDs available for signing.
// In a real run these come from the seed script. Here we retrieve them via API.
export function setup() {
  const token = loginAndGetToken(ADMIN_CREDS.email, ADMIN_CREDS.password);
  const res   = http.get(
    `${ORDERS_BASE}?status=draft&size=500`,
    { headers: authHeaders(token) }
  );

  let orderIds = [];
  if (res.status === 200) {
    const body = JSON.parse(res.body);
    orderIds = (body.content || []).map((o) => o.id);
  }

  console.log(`setup: found ${orderIds.length} draft orders to sign`);
  return { orderIds, token };
}

export default function (data) {
  if (!adminToken) {
    adminToken = loginAndGetToken(ADMIN_CREDS.email, ADMIN_CREDS.password);
  }

  if (!data.orderIds || data.orderIds.length === 0) {
    sleep(5);
    return;
  }

  // Pick a random draft order from the pre-fetched list
  const orderId = data.orderIds[Math.floor(Math.random() * data.orderIds.length)];

  group('sign_order', () => {
    const res = http.put(
      `${ORDERS_BASE}/${orderId}/sign`,
      null,
      {
        headers: authHeaders(adminToken),
        tags: { name: 'sign_order', scenario: 'batch_order_signing' },
        timeout: '15s',
      }
    );

    const ok = check(res, {
      // 200 = signed, 422 = already signed (race condition between VUs — acceptable)
      'sign: 200 or 422': (r) => r.status === 200 || r.status === 422,
    });

    if (res.status === 200)       signedOrders.add(1);
    if (res.status === 422)       conflictCount.add(1);
  });

  sleep(1);
}
