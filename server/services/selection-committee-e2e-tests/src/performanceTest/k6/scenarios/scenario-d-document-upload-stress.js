/**
 * Scenario D — Document Upload Stress Test
 * ==========================================
 * 50 concurrent VUs each uploading a 10 MB file through the full presign flow:
 *
 *   login → POST /api/v1/documents/metadata (creates DB record, returns presign URL)
 *         → PUT <minio-presign-url>          (transfers the actual binary payload)
 *         → PATCH /api/v1/documents/{id}/confirm (marks document as uploaded in DB)
 *
 * The 10 MB payload is generated once in setup() and shared across all VUs
 * to avoid per-VU memory allocation of 10 MB × 50 = 500 MB.
 *
 * This scenario stresses:
 *   - documents-service: concurrent DB writes + S3 presign generation
 *   - MinIO: 50 parallel 10 MB PUT requests (~500 MB/s peak)
 *   - Gateway: RequestSize=20MB filter under sustained multipart load
 *   - PostgreSQL connection pool on documents-service
 *
 * Target metrics:
 *   - p95 createMetadata ≤ 1 000 ms
 *   - p95 upload to MinIO ≤ 30 000 ms   (infra-dependent; 10 MB / ~330 KB/s)
 *   - error rate          <      3 %
 *
 * Run:
 *   k6 run \
 *     -e BASE_URL=http://localhost:8080 \
 *     -e MINIO_DIRECT_URL=http://localhost:9000 \
 *     src/performanceTest/k6/scenarios/scenario-d-document-upload-stress.js
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Trend } from 'k6/metrics';

import { registerAndLogin, authHeaders } from '../lib/auth.js';
import { SCENARIO_D } from '../lib/thresholds.js';
import {
  applicantEmail,
  documentMetadataPayload,
  generateBinaryPayload,
  APPLICANT_PASSWORD,
} from '../lib/data.js';

export const options = {
  scenarios: {
    document_upload_stress: {
      executor: 'constant-vus',
      vus: 50,
      duration: '8m',
    },
  },
  thresholds: SCENARIO_D,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

const uploadedBytes    = new Counter('uploaded_bytes_total');
const failedUploads    = new Counter('failed_uploads_total');
const minioUploadTime  = new Trend('minio_upload_duration_ms', true);

const BASE_URL        = __ENV.BASE_URL || 'http://localhost:8080';
const DOCS_BASE       = `${BASE_URL}/api/v1/documents`;
const FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

// setup() runs once and creates the shared binary payload
export function setup() {
  console.log(`Generating ${FILE_SIZE_BYTES / (1024 * 1024)} MB binary payload…`);
  const payload = generateBinaryPayload(FILE_SIZE_BYTES);
  console.log('Binary payload ready.');
  return { payload };
}

export default function (data) {
  const email = applicantEmail(__VU, __ITER);
  let token;

  // ------------------------------------------------------------------
  // Step 1 — Auth (lazy per VU)
  // ------------------------------------------------------------------
  group('auth', () => {
    token = registerAndLogin(email, APPLICANT_PASSWORD);
  });

  if (!token) { failedUploads.add(1); return; }

  // ------------------------------------------------------------------
  // Step 2 — Create document metadata record
  //          Response contains the pre-signed PUT URL for MinIO
  // ------------------------------------------------------------------
  let presignUrl;
  let docId;

  group('create_metadata', () => {
    const res = http.post(
      `${DOCS_BASE}/metadata`,
      JSON.stringify(documentMetadataPayload(FILE_SIZE_BYTES, 'passport')),
      { headers: authHeaders(token), tags: { name: 'create_metadata', scenario: 'document_upload_stress' } }
    );

    const ok = check(res, {
      'metadata: status 200/201': (r) => r.status === 200 || r.status === 201,
    });

    if (ok) {
      try {
        const body = JSON.parse(res.body);
        docId = body.id || body;
      } catch {
        failedUploads.add(1);
      }
    } else {
      failedUploads.add(1);
    }
  });

  if (!docId) return;

  // ------------------------------------------------------------------
  // Step 3 — Retrieve the pre-signed upload URL
  // ------------------------------------------------------------------
  group('get_presign_url', () => {
    const res = http.get(
      `${DOCS_BASE}/${docId}/presign-upload`,
      { headers: authHeaders(token), tags: { name: 'presign_upload', scenario: 'document_upload_stress' } }
    );

    const ok = check(res, {
      'presign: status 200': (r) => r.status === 200,
      'presign: url in body': (r) => r.body && r.body.length > 10,
    });

    if (ok) {
      presignUrl = res.body.replace(/"/g, '').trim();
    } else {
      failedUploads.add(1);
    }
  });

  if (!presignUrl) return;

  // ------------------------------------------------------------------
  // Step 4 — PUT binary payload to MinIO pre-signed URL
  //          The pre-signed URL bypasses the gateway (direct MinIO access)
  // ------------------------------------------------------------------
  group('upload_to_minio', () => {
    const t0  = Date.now();
    const res = http.put(
      presignUrl,
      data.payload.buffer,
      {
        headers: { 'Content-Type': 'application/pdf' },
        tags:    { name: 'upload_to_minio', scenario: 'document_upload_stress' },
        timeout: '60s',
      }
    );

    const elapsed = Date.now() - t0;
    minioUploadTime.add(elapsed);

    const ok = check(res, {
      // MinIO returns 200 for presign PUT
      'minio upload: status 200': (r) => r.status === 200 || r.status === 204,
    });

    if (ok) {
      uploadedBytes.add(FILE_SIZE_BYTES);
    } else {
      failedUploads.add(1);
    }
  });

  // ------------------------------------------------------------------
  // Step 5 — Webhook confirm (marks document status as uploaded in DB)
  // ------------------------------------------------------------------
  group('confirm_upload', () => {
    const res = http.post(
      `${DOCS_BASE}/${docId}/webhook`,
      JSON.stringify({ status: 'COMPLETED' }),
      { headers: authHeaders(token), tags: { name: 'confirm_upload', scenario: 'document_upload_stress' } }
    );
    check(res, { 'confirm: 200/204': (r) => r.status === 200 || r.status === 204 });
  });

  sleep(1);
}
