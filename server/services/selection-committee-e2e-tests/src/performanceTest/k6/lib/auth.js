/**
 * auth.js — shared authentication helpers for all k6 scenarios.
 *
 * The identity-service issues RSA-signed JWTs. Every scenario that calls a
 * protected endpoint must first obtain a token via POST /api/v1/auth/login
 * and attach it as a Bearer token in the Authorization header.
 *
 * Token lifetime is managed per VU: one login per VU at scenario start, then
 * reused for the lifetime of that VU's iteration set. This matches real-world
 * browser behaviour and avoids flooding the identity-service with logins that
 * are irrelevant to the scenario under test.
 */

import http from 'k6/http';
import { check, fail } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

/**
 * Log in as a single user and return the Authorization header value.
 * Aborts the VU iteration on failure.
 *
 * @param {string} email
 * @param {string} password
 * @returns {string}  "Bearer <access_token>"
 */
export function loginAndGetToken(email, password) {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email, password }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'auth_login' } }
  );

  const ok = check(res, {
    'login: status 200': (r) => r.status === 200,
    'login: access_token present': (r) => {
      try {
        return JSON.parse(r.body).accessToken !== undefined;
      } catch {
        return false;
      }
    },
  });

  if (!ok) {
    fail(`Login failed for ${email} — status ${res.status}: ${res.body}`);
  }

  return `Bearer ${JSON.parse(res.body).accessToken}`;
}

/**
 * Register a new applicant user and immediately log in.
 *
 * @param {string} email
 * @param {string} password
 * @returns {string}  "Bearer <access_token>"
 */
export function registerAndLogin(email, password) {
  const regRes = http.post(
    `${BASE_URL}/api/v1/auth/register`,
    JSON.stringify({
      email,
      password,
      firstName: 'Perf',
      lastName: `User${__VU}`,
      middleName: 'Test',
    }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'auth_register' } }
  );

  // 201 Created or 409 Conflict (user already exists from a previous run)
  if (regRes.status !== 201 && regRes.status !== 409) {
    fail(`Registration failed for ${email} — status ${regRes.status}: ${regRes.body}`);
  }

  return loginAndGetToken(email, password);
}

/**
 * Build a standard JSON request header map carrying a Bearer token.
 *
 * @param {string} token  "Bearer <access_token>"
 * @returns {object}
 */
export function authHeaders(token) {
  return {
    'Content-Type': 'application/json',
    Authorization: token,
  };
}

/**
 * Build a multipart-form header map carrying a Bearer token.
 * k6 adds the correct Content-Type boundary automatically when a FormData
 * body is passed; we must NOT set Content-Type manually here.
 *
 * @param {string} token  "Bearer <access_token>"
 * @returns {object}
 */
export function authHeadersMultipart(token) {
  return { Authorization: token };
}
