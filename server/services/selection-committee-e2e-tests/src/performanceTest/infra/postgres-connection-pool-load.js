/**
 * PostgreSQL Connection Pool Under Load
 * =======================================
 * Admission-service and identity-service share the same PostgreSQL instance
 * but use separate schemas (admission, identity). Each service has its own
 * HikariCP pool (Spring Boot default: max-pool-size=10).
 *
 * This test:
 *   1. Sends 30 concurrent requests per service (3× the default pool size)
 *   2. Each request involves a DB query (not just a cache hit)
 *   3. Monitors HikariCP pool metrics via Actuator /actuator/prometheus
 *
 * Metrics to watch:
 *   hikaricp_connections_pending{pool=HikariPool-1}  — queued acquisition requests
 *   hikaricp_connections_timeout_total               — pool timeout count
 *   hikaricp_connections_active                      — currently in-use connections
 *   hikaricp_connection_acquire_seconds_*            — acquisition latency distribution
 *
 * Success criteria:
 *   - hikaricp_connections_pending never exceeds 10
 *   - hikaricp_connections_timeout_total stays at 0
 *   - p99 DB response (via API) < 2 000 ms
 *   - No DataAccessException in application logs
 *
 * Run:
 *   k6 run \
 *     -e BASE_URL=http://localhost:8080 \
 *     -e ADMISSION_ACTUATOR=http://localhost:8083/actuator \
 *     -e IDENTITY_ACTUATOR=http://localhost:8081/actuator \
 *     src/performanceTest/infra/postgres-connection-pool-load.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Gauge, Counter } from 'k6/metrics';

import { loginAndGetToken, authHeaders } from '../k6/lib/auth.js';
import { ADMIN_CREDS, OPERATOR_CREDS } from '../k6/lib/data.js';

export const options = {
  scenarios: {
    pg_pool_load: {
      executor: 'constant-vus',
      vus: 30,        // 3× HikariCP default max-pool-size
      duration: '5m',
    },
  },
  thresholds: {
    http_req_failed:                         [{ threshold: 'rate<0.02', abortOnFail: false }],
    'http_req_duration{name:list_apps_db}':  [{ threshold: 'p(99)<2000', abortOnFail: false }],
    'http_req_duration{name:get_users_db}':  [{ threshold: 'p(99)<2000', abortOnFail: false }],
    hikaricp_pending_connections:            [{ threshold: 'value<10', abortOnFail: false }],
  },
};

const hikariPending  = new Gauge('hikaricp_pending_connections');
const hikariTimeouts = new Counter('hikaricp_pool_timeouts_total');

const BASE_URL           = __ENV.BASE_URL              || 'http://localhost:8080';
const ADMISSION_ACTUATOR = __ENV.ADMISSION_ACTUATOR    || 'http://localhost:8083/actuator';
const IDENTITY_ACTUATOR  = __ENV.IDENTITY_ACTUATOR     || 'http://localhost:8081/actuator';
const ADMISSIONS_BASE    = `${BASE_URL}/api/v1/admissions`;
const IDENTITY_BASE      = `${BASE_URL}/api/v1/identity/users`;

let adminToken;
let promScrapeIteration = 0;

// Scrape Prometheus metrics and extract HikariCP pool stats
function scrapeHikariMetrics(actuatorUrl) {
  const res = http.get(`${actuatorUrl}/prometheus`, { tags: { name: 'prometheus_scrape' } });
  if (res.status !== 200) return;

  const lines = res.body.split('\n');
  for (const line of lines) {
    if (line.startsWith('hikaricp_connections_pending{')) {
      const value = parseFloat(line.split(' ')[1]);
      if (!isNaN(value)) {
        hikariPending.add(value);
        if (value > 5) {
          console.warn(`HikariCP pending connections HIGH: ${value} (${actuatorUrl})`);
        }
      }
    }
    if (line.startsWith('hikaricp_connections_timeout_total{')) {
      const value = parseFloat(line.split(' ')[1]);
      if (!isNaN(value) && value > 0) {
        hikariTimeouts.add(value);
        console.error(`HikariCP pool timeout detected: ${value} total timeouts`);
      }
    }
  }
}

export default function () {
  if (!adminToken) {
    adminToken = loginAndGetToken(ADMIN_CREDS.email, ADMIN_CREDS.password);
  }

  // Primary DB load: paginated list query (always hits DB — no Redis cache)
  // Use search endpoint which uses ApplicationSpecificationService (more complex query)
  const appsRes = http.post(
    `${ADMISSIONS_BASE}/search?size=20&sort=createdAt,desc`,
    JSON.stringify({ applicationStatus: 'reviewing' }),
    { headers: authHeaders(adminToken), tags: { name: 'list_apps_db' } }
  );
  check(appsRes, { 'list apps: 200': (r) => r.status === 200 });

  // Secondary DB load: identity user list (different schema, different pool)
  const usersRes = http.get(
    `${IDENTITY_BASE}?size=20`,
    { headers: authHeaders(adminToken), tags: { name: 'get_users_db' } }
  );
  check(usersRes, { 'list users: 200': (r) => r.status === 200 });

  // Scrape HikariCP metrics every ~10 iterations from VU 1 only
  promScrapeIteration++;
  if (__VU === 1 && promScrapeIteration % 10 === 0) {
    scrapeHikariMetrics(ADMISSION_ACTUATOR);
    scrapeHikariMetrics(IDENTITY_ACTUATOR);
  }

  // No sleep — maximum concurrent DB pressure
}
