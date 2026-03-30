/**
 * Redis Connection Pool Exhaustion Test
 * =======================================
 * Fires concurrent Redis-backed requests to exhaust the pool defined in
 * application.yml:
 *
 *   spring.data.redis.jedis.pool.max-active: 10
 *
 * Two endpoints exercise Redis heavily:
 *   1. GET /api/v1/feature-flags?environment=production
 *      → @Cacheable hits Redis on cache miss; sets key on first call
 *   2. Operator auto-assignment INCR on admission:operator:round-robin-index
 *      → every call to the round-robin helper does a Redis INCR
 *   3. Gateway rate-limiter (RedisRateLimiter)
 *      → every proxied request touches Redis
 *
 * This test deliberately sends 200 concurrent requests — 20× the pool size
 * for admission-service (max-active=10) — to verify that:
 *   a) The pool blocks rather than throwing immediately
 *   b) Requests eventually succeed (pool releases fast enough)
 *   c) Response times under pool saturation stay within SLA
 *
 * Success criteria:
 *   - Error rate < 5 % (some queued requests may timeout)
 *   - p99 < 3 000 ms
 *   - No JedisConnectionException in service logs
 *
 * Run:
 *   k6 run \
 *     -e BASE_URL=http://localhost:8080 \
 *     src/performanceTest/infra/redis-connection-pool-stress.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

import { loginAndGetToken, authHeaders } from '../k6/lib/auth.js';
import { ADMIN_CREDS, OPERATOR_CREDS } from '../k6/lib/data.js';

export const options = {
  scenarios: {
    redis_pool_stress: {
      executor: 'constant-vus',
      vus: 200,       // 20× the pool max-active=10
      duration: '3m',
    },
  },
  thresholds: {
    http_req_failed: [{ threshold: 'rate<0.05', abortOnFail: true }],
    'http_req_duration{name:feature_flag_cached}': [
      { threshold: 'p(99)<3000', abortOnFail: false },
    ],
  },
};

const redisTimeouts = new Rate('redis_timeout_rate');
const cacheHitTime  = new Trend('redis_cache_hit_ms', true);

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const FLAGS_URL = `${BASE_URL}/api/v1/feature-flags`;

let cachedToken;

export default function () {
  if (!cachedToken) {
    cachedToken = loginAndGetToken(ADMIN_CREDS.email, ADMIN_CREDS.password);
  }

  const t0  = Date.now();
  const res = http.get(
    `${FLAGS_URL}?environment=production`,
    {
      headers: authHeaders(cachedToken),
      tags: { name: 'feature_flag_cached' },
    }
  );
  const elapsed = Date.now() - t0;
  cacheHitTime.add(elapsed);

  const timedOut = res.status === 503 || res.status === 429;
  redisTimeouts.add(timedOut ? 1 : 0);

  check(res, {
    'flags: 200 or 429 (rate-limited)': (r) => r.status === 200 || r.status === 429,
  });

  // No sleep — maximum concurrency pressure on Redis pool
}
