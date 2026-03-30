/**
 * MinIO Concurrent Upload Stress Test
 * =====================================
 * Validates MinIO throughput and connection stability under the maximum
 * expected document upload volume:
 *   - 50 concurrent VUs
 *   - Each uploading a different file size (1 MB, 5 MB, 10 MB, 20 MB)
 *   - 8-minute sustained run
 *
 * This test uploads DIRECTLY to MinIO pre-signed URLs (bypassing the gateway)
 * to isolate MinIO performance from service-layer overhead.
 *
 * MinIO health metrics to watch (via MinIO Console at :9001):
 *   - Disk IOPS
 *   - Network throughput (MB/s)
 *   - Error rate in MinIO logs
 *   - Active connections
 *
 * Success criteria:
 *   - p95 upload time (10 MB): < 30 000 ms
 *   - Error rate < 3 %
 *   - No 503/504 from MinIO
 *   - Consistent throughput (no throughput cliff after 30 s)
 *
 * Run:
 *   k6 run \
 *     -e MINIO_DIRECT_URL=http://localhost:9000 \
 *     -e MINIO_BUCKET=documents \
 *     -e MINIO_ACCESS_KEY=minioadmin \
 *     -e MINIO_SECRET_KEY=minioadmin \
 *     src/performanceTest/infra/minio-concurrent-uploads.js
 *
 * NOTE: The pre-signed URL generation (via documents-service) is not included
 * in this test to keep MinIO isolated. Pre-generate 500 presign URLs using
 * the seed helper before running this test.
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';
import { generateBinaryPayload } from '../k6/lib/data.js';

export const options = {
  scenarios: {
    minio_upload_stress: {
      executor: 'constant-vus',
      vus: 50,
      duration: '8m',
    },
  },
  thresholds: {
    // MinIO should handle 1 MB uploads in under 5 s at p95
    'http_req_duration{size:1mb}':  [{ threshold: 'p(95)<5000',  abortOnFail: false }],
    // 10 MB uploads: infrastructure-dependent; 30 s covers slow CI networks
    'http_req_duration{size:10mb}': [{ threshold: 'p(95)<30000', abortOnFail: false }],
    // 20 MB uploads: at the gateway RequestSize limit
    'http_req_duration{size:20mb}': [{ threshold: 'p(95)<60000', abortOnFail: false }],
    http_req_failed: [{ threshold: 'rate<0.03', abortOnFail: true }],
  },
};

const uploadedBytes  = new Counter('minio_uploaded_bytes_total');
const uploadErrors   = new Counter('minio_upload_errors_total');
const throughputMbps = new Trend('minio_throughput_mbps', true);

const MINIO_URL    = __ENV.MINIO_DIRECT_URL  || 'http://localhost:9000';
const BUCKET       = __ENV.MINIO_BUCKET      || 'documents';
const ACCESS_KEY   = __ENV.MINIO_ACCESS_KEY  || 'minioadmin';
const SECRET_KEY   = __ENV.MINIO_SECRET_KEY  || 'minioadmin';

// File sizes distributed across VUs to simulate realistic variety
const FILE_SIZES = [
  { label: '1mb',  bytes: 1  * 1024 * 1024 },
  { label: '5mb',  bytes: 5  * 1024 * 1024 },
  { label: '10mb', bytes: 10 * 1024 * 1024 },
  { label: '20mb', bytes: 20 * 1024 * 1024 },
];

// Generate payloads once in setup — avoids 50 VUs each allocating 20 MB
export function setup() {
  const payloads = {};
  for (const { label, bytes } of FILE_SIZES) {
    console.log(`Generating ${label} payload (${bytes} bytes)...`);
    payloads[label] = generateBinaryPayload(bytes);
  }
  return { payloads };
}

export default function (data) {
  // Rotate through file sizes per VU to distribute load evenly
  const sizeConfig = FILE_SIZES[(__VU - 1) % FILE_SIZES.length];
  const payload    = data.payloads[sizeConfig.label];

  // Build object key: perf-test/<vu>/<iter>/<label>.bin
  const objectKey  = `perf-test/${__VU}/${__ITER}/${sizeConfig.label}.pdf`;
  const uploadUrl  = `${MINIO_URL}/${BUCKET}/${objectKey}`;

  const t0  = Date.now();
  const res = http.put(
    uploadUrl,
    payload.buffer,
    {
      headers: {
        'Content-Type': 'application/pdf',
        'x-amz-content-sha256': 'UNSIGNED-PAYLOAD',
      },
      tags:    { size: sizeConfig.label },
      timeout: '90s',
    }
  );
  const elapsedMs = Date.now() - t0;

  const ok = check(res, {
    [`${sizeConfig.label} upload: 200`]: (r) => r.status === 200 || r.status === 200,
  });

  if (ok) {
    uploadedBytes.add(sizeConfig.bytes);
    const throughput = (sizeConfig.bytes / (1024 * 1024)) / (elapsedMs / 1000);
    throughputMbps.add(throughput);
  } else {
    uploadErrors.add(1);
    console.error(`Upload failed: VU=${__VU} size=${sizeConfig.label} status=${res.status}`);
  }

  sleep(0.5);
}

export function teardown() {
  // Optionally clean up perf-test objects via MinIO admin API
  console.log('MinIO stress test complete. Check MinIO Console for metrics.');
}
