/**
 * data.js — test data generators for k6 VUs.
 *
 * All generators are pure functions so they can be called inside setup()
 * for pre-population or inside the default function for per-iteration data.
 */

/**
 * Generate a unique applicant email for a given VU.
 * Using __VU and __ITER prevents cross-VU collision across iterations.
 *
 * @param {number} vu    k6 __VU
 * @param {number} iter  k6 __ITER
 * @returns {string}
 */
export function applicantEmail(vu, iter) {
  return `perf.applicant.${vu}.${iter}@fice-perf.test`;
}

/**
 * Build a CreateApplicationDto payload.
 *
 * educationalProgramId must reference a row that actually exists in the DB.
 * The setup() function in each scenario seeds these rows and passes the ID
 * via scenario data.
 *
 * @param {number} educationalProgramId
 * @returns {object}
 */
export function createApplicationPayload(educationalProgramId) {
  return {
    sex: true,
    grade: 'bachelor',
    educationalProgramId,
  };
}

/**
 * Build a DocumentRequest metadata payload for the documents-service.
 * The actual binary upload goes directly to the pre-signed MinIO URL
 * returned by createMetadata; this payload only creates the DB record.
 *
 * @param {number} sizeBytes   Reported file size (must be < 20 MB = 20971520)
 * @param {string} type        One of: passport, ipn, certificate, photo, etc.
 * @returns {object}
 */
export function documentMetadataPayload(sizeBytes, type) {
  return {
    type,
    contentType: 'application/pdf',
    fileName: `${type}_perf_test.pdf`,
    sizeBytes,
    year: 2026,
    grade: 'bachelor',
    temp: false,
  };
}

/**
 * Generate a synthetic 10 MB ArrayBuffer filled with pseudo-random bytes.
 * k6 supports ArrayBuffer as an HTTP body out of the box.
 *
 * NOTE: Generating this inside the default function would be expensive;
 * call it once in setup() and share via scenario data.
 *
 * @param {number} sizeBytes
 * @returns {Uint8Array}
 */
export function generateBinaryPayload(sizeBytes) {
  const buf = new Uint8Array(sizeBytes);
  for (let i = 0; i < sizeBytes; i++) {
    buf[i] = i % 256;
  }
  return buf;
}

/** Operator credentials — loaded from environment to keep secrets out of source */
export const OPERATOR_CREDS = {
  email: __ENV.OPERATOR_EMAIL || 'operator@fice-perf.test',
  password: __ENV.OPERATOR_PASSWORD || 'OperatorPerf1!',
};

/** Admin credentials */
export const ADMIN_CREDS = {
  email: __ENV.ADMIN_EMAIL || 'admin@fice-perf.test',
  password: __ENV.ADMIN_PASSWORD || 'AdminPerf1!',
};

/** Default applicant password (shared across all synthetic users) */
export const APPLICANT_PASSWORD = __ENV.APPLICANT_PASSWORD || 'ApplicantPerf1!';
