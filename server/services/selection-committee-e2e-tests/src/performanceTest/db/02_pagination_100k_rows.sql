-- =============================================================================
-- Pagination Performance Test — 100K rows
-- =============================================================================
-- Measures query performance at realistic data volumes.
-- Run AFTER seeding 100K applications:
--   psql -U scadmin -d selection-committee -f 02_seed_100k_applications.sql
--   psql -U scadmin -d selection-committee -f 02_pagination_100k_rows.sql
-- =============================================================================

SET search_path = admission, public;

-- Ensure statistics are fresh
ANALYZE admission.applications;

\timing on

-- ---------------------------------------------------------------------------
-- Test P1: Offset-based pagination — early pages (JPA default)
-- ---------------------------------------------------------------------------
\echo '--- P1a: page 0 (OFFSET 0) ---'
SELECT id, status, grade, created_at
FROM   admission.applications
ORDER BY created_at DESC
LIMIT  20 OFFSET 0;

\echo '--- P1b: page 50 (OFFSET 1000) ---'
SELECT id, status, grade, created_at
FROM   admission.applications
ORDER BY created_at DESC
LIMIT  20 OFFSET 1000;

\echo '--- P1c: page 2500 (OFFSET 50000) deep-page problem ---'
SELECT id, status, grade, created_at
FROM   admission.applications
ORDER BY created_at DESC
LIMIT  20 OFFSET 50000;

\echo '--- P1d: last page (OFFSET 99980) ---'
SELECT id, status, grade, created_at
FROM   admission.applications
ORDER BY created_at DESC
LIMIT  20 OFFSET 99980;


-- ---------------------------------------------------------------------------
-- Test P2: Keyset / cursor pagination (recommended alternative)
--          Uses (created_at, id) as the cursor — no OFFSET penalty
-- ---------------------------------------------------------------------------
\echo '--- P2: keyset pagination (no OFFSET) ---'
-- Simulate the cursor pointing at the 1000th row
DO $$
DECLARE
    v_cursor_ts  TIMESTAMP;
    v_cursor_id  BIGINT;
BEGIN
    SELECT created_at, id
    INTO   v_cursor_ts, v_cursor_id
    FROM   admission.applications
    ORDER BY created_at DESC
    LIMIT  1 OFFSET 999;

    RAISE NOTICE 'Cursor: created_at=%, id=%', v_cursor_ts, v_cursor_id;
END$$;

-- Actual keyset query (replace values from cursor above)
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, status, grade, created_at
FROM   admission.applications
WHERE  (created_at, id) < ('2026-01-01 00:00:00', 50000)
ORDER BY created_at DESC, id DESC
LIMIT  20;


-- ---------------------------------------------------------------------------
-- Test P3: COUNT(*) for totalElements — the most expensive part of JPA paging
-- ---------------------------------------------------------------------------
\echo '--- P3: COUNT(*) on 100K rows ---'
EXPLAIN (ANALYZE, BUFFERS)
SELECT COUNT(*) FROM admission.applications;

\echo '--- P3b: COUNT(*) with status filter ---'
EXPLAIN (ANALYZE, BUFFERS)
SELECT COUNT(*) FROM admission.applications WHERE status = 'reviewing';

\echo '--- P3c: COUNT(*) with composite filter (spec query) ---'
EXPLAIN (ANALYZE, BUFFERS)
SELECT COUNT(DISTINCT id)
FROM   admission.applications
WHERE  status = 'reviewing'
  AND  operator_user_id = 7;


-- ---------------------------------------------------------------------------
-- Test P4: Concurrent write contention simulation (same table, different rows)
--          Run this section with pgbench or multiple psql sessions to detect
--          HOT update issues.
-- ---------------------------------------------------------------------------
\echo '--- P4: simulate concurrent status update (run concurrently in 2+ sessions) ---'
BEGIN;
SELECT id FROM admission.applications WHERE status = 'submitted' LIMIT 5 FOR UPDATE SKIP LOCKED;
-- In session 2: run the same SELECT — SKIP LOCKED ensures no blocking
ROLLBACK;


-- ---------------------------------------------------------------------------
-- Recommended indexes if any of the above plans show Seq Scan
-- ---------------------------------------------------------------------------
\echo '--- Recommended indexes (CREATE IF NOT EXISTS) ---'

-- Partial index for auto-assignment scheduler (Q1)
CREATE INDEX IF NOT EXISTS idx_apps_submitted_unassigned
ON admission.applications (created_at ASC)
WHERE operator_user_id IS NULL AND status = 'submitted';

-- Covering index for operator dashboard (avoids heap fetch for status/grade)
CREATE INDEX IF NOT EXISTS idx_apps_covering_list
ON admission.applications (created_at DESC, id, status, grade)
WHERE status NOT IN ('archived');

-- Composite index for spec query with status + operator filter
CREATE INDEX IF NOT EXISTS idx_apps_status_operator
ON admission.applications (status, operator_user_id, created_at DESC);

\echo 'Done. Check query plans with EXPLAIN ANALYZE after index creation.'
