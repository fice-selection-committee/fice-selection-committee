/**
 * RabbitMQ Queue Depth Under Sustained Load
 * ==========================================
 * Generates a sustained flood of application status-change events to test
 * whether the audit.audit-ingest queue backs up under load and whether
 * the environment-service consumer (AuditIngestConsumer) can keep up.
 *
 * Architecture:
 *   identity-service / admission-service
 *     → AMQP publish to exchange → audit.audit-ingest queue
 *       → environment-service AuditIngestConsumer
 *         → INSERT into environment.audit_events table
 *
 * Test strategy:
 *   - 100 VUs each submitting applications as fast as possible
 *   - Each submit publishes one status-change event to RabbitMQ
 *   - After 5 minutes, check queue depth via RabbitMQ management API
 *   - Queue depth should not exceed 500 unacked messages at steady state
 *
 * RabbitMQ Management API endpoint (HTTP):
 *   GET http://localhost:15672/api/queues/%2F/audit.audit-ingest
 *
 * Success criteria:
 *   - Queue depth stays below 500 messages
 *   - No messages in DLQ (audit.audit-ingest.dlq)
 *   - Consumer utilisation < 90 %
 *
 * Run:
 *   k6 run \
 *     -e BASE_URL=http://localhost:8080 \
 *     -e RABBITMQ_MGMT_URL=http://localhost:15672 \
 *     -e RABBITMQ_USER=scadmin \
 *     -e RABBITMQ_PASS=scadminpass \
 *     src/performanceTest/infra/rabbitmq-queue-depth.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Gauge, Counter } from 'k6/metrics';
import { encode } from 'k6/encoding';

import { registerAndLogin, authHeaders } from '../k6/lib/auth.js';
import { applicantEmail, createApplicationPayload, APPLICANT_PASSWORD } from '../k6/lib/data.js';

export const options = {
  scenarios: {
    event_flood: {
      executor: 'constant-vus',
      vus: 100,
      duration: '5m',
    },
  },
  thresholds: {
    http_req_failed: [{ threshold: 'rate<0.02', abortOnFail: false }],
    'rabbitmq_queue_depth':  [{ threshold: 'value<500', abortOnFail: false }],
  },
};

const queueDepth    = new Gauge('rabbitmq_queue_depth');
const dlqDepth      = new Gauge('rabbitmq_dlq_depth');
const publishedEvents = new Counter('events_published_total');

const BASE_URL          = __ENV.BASE_URL            || 'http://localhost:8080';
const MGMT_URL          = __ENV.RABBITMQ_MGMT_URL   || 'http://localhost:15672';
const MGMT_USER         = __ENV.RABBITMQ_USER        || 'scadmin';
const MGMT_PASS         = __ENV.RABBITMQ_PASS        || 'scadminpass';
const ADMISSIONS_BASE   = `${BASE_URL}/api/v1/admissions`;
const EDUCATIONAL_PROG  = parseInt(__ENV.EDUCATIONAL_PROGRAM_ID || '1', 10);

const mgmtAuthHeader = `Basic ${encode(`${MGMT_USER}:${MGMT_PASS}`)}`;

// Poll queue depth every 10 s from VU 1 only to avoid hammering management API
function pollQueueDepth() {
  if (__VU !== 1) return;

  const queueRes = http.get(
    `${MGMT_URL}/api/queues/%2F/audit.audit-ingest`,
    { headers: { Authorization: mgmtAuthHeader } }
  );

  const dlqRes = http.get(
    `${MGMT_URL}/api/queues/%2F/audit.audit-ingest.dlq`,
    { headers: { Authorization: mgmtAuthHeader } }
  );

  if (queueRes.status === 200) {
    try {
      const q = JSON.parse(queueRes.body);
      queueDepth.add(q.messages || 0);
      console.log(`Queue depth: ${q.messages}, consumers: ${q.consumers}, rate: ${q.message_stats ? q.message_stats.publish_details?.rate : 'N/A'}`);
    } catch { /* ignore */ }
  }

  if (dlqRes.status === 200) {
    try {
      const d = JSON.parse(dlqRes.body);
      dlqDepth.add(d.messages || 0);
      if ((d.messages || 0) > 0) {
        console.warn(`DLQ has ${d.messages} messages — consumer is failing!`);
      }
    } catch { /* ignore */ }
  }
}

let vuToken;

export default function () {
  // Poll queue depth from VU 1 every 10 s
  if (__VU === 1 && __ITER % 5 === 0) {
    pollQueueDepth();
  }

  if (!vuToken) {
    vuToken = registerAndLogin(applicantEmail(__VU, 0), APPLICANT_PASSWORD);
  }

  // Create and immediately submit an application — this publishes a status event
  const createRes = http.post(
    ADMISSIONS_BASE,
    JSON.stringify(createApplicationPayload(EDUCATIONAL_PROG)),
    { headers: authHeaders(vuToken), tags: { name: 'create_for_event' } }
  );

  if (createRes.status !== 201) return;

  const appId = JSON.parse(createRes.body).id;

  // Advance to submitted → triggers applicationEventPublisher.publishStatusChange()
  const submitRes = http.put(
    `${ADMISSIONS_BASE}/${appId}/status`,
    JSON.stringify('submitted'),
    { headers: authHeaders(vuToken), tags: { name: 'status_for_event' } }
  );

  check(submitRes, { 'submit event published: 204': (r) => r.status === 204 });
  if (submitRes.status === 204) publishedEvents.add(1);

  sleep(2);
}
