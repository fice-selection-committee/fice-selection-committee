-- =============================================================================
-- Database Performance Tests — EXPLAIN ANALYZE for Critical Queries
-- =============================================================================
-- Run against a populated database (use seed scripts to load realistic data):
--   psql -U scadmin -d selection-committee -f 01_explain_analyze_critical_queries.sql
--
-- Prerequisites:
--   - pg_stat_statements extension enabled
--   - At least 10 000 applications rows
--   - autovacuum has run (ANALYZE tables before running)
-- =============================================================================

SET search_path = admission, identity, public;

-- ---------------------------------------------------------------------------
-- Q1: Auto-assignment scheduler query
--     OperatorAssignmentService.autoAssignPendingApplications()
--     Runs every 30 s. Must be fast even at 1 000+ submitted rows.
-- ---------------------------------------------------------------------------
\echo '=== Q1: submitted applications without operator (auto-assign) ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT id, applicant_user_id, operator_user_id, status, created_at
FROM   admission.applications
WHERE  status = 'submitted'
  AND  operator_user_id IS NULL
ORDER BY created_at ASC;

-- Expected: Index Scan using idx_applications_operator_user_id + status filter.
-- If Seq Scan appears: add the partial index below.
-- Recommended index (run once if missing):
-- CREATE INDEX CONCURRENTLY idx_applications_submitted_unassigned
--   ON admission.applications (status, created_at ASC)
--   WHERE operator_user_id IS NULL AND status = 'submitted';


-- ---------------------------------------------------------------------------
-- Q2: Paginated application list for operator (page 0, size 20, sorted DESC)
--     ApplicationService.findAll(pageable) called on every dashboard load
-- ---------------------------------------------------------------------------
\echo '=== Q2: paginated list all applications (operator view, page 0) ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT id, applicant_user_id, operator_user_id, status, grade, created_at
FROM   admission.applications
ORDER BY created_at DESC
LIMIT  20 OFFSET 0;


-- ---------------------------------------------------------------------------
-- Q3: Paginated list with status filter (specification query)
--     ApplicationService.findAll(ApplicationFilter{status=reviewing}, pageable)
-- ---------------------------------------------------------------------------
\echo '=== Q3: filtered applications by status (spec query) ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT DISTINCT id, applicant_user_id, operator_user_id, status, grade, created_at
FROM   admission.applications
WHERE  status = 'reviewing'
ORDER BY created_at DESC
LIMIT  20 OFFSET 0;


-- ---------------------------------------------------------------------------
-- Q4: Applicant own applications (role-scoped view)
-- ---------------------------------------------------------------------------
\echo '=== Q4: applicant own applications ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT id, applicant_user_id, operator_user_id, status, grade, created_at
FROM   admission.applications
WHERE  applicant_user_id = 1001
ORDER BY created_at DESC
LIMIT  20 OFFSET 0;


-- ---------------------------------------------------------------------------
-- Q5: Contract number uniqueness check before insert
-- ---------------------------------------------------------------------------
\echo '=== Q5: contract number uniqueness lookup ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT id, contract_number, status
FROM   admission.contracts
WHERE  contract_number = '26б-0042';


-- ---------------------------------------------------------------------------
-- Q6: Contracts for application (validation before order signing)
--     OrderService.validateEnrollmentApplications() — called per app in batch
-- ---------------------------------------------------------------------------
\echo '=== Q6: contracts by application_id (order validation inner loop) ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT id, contract_number, status, contract_type
FROM   admission.contracts
WHERE  application_id = 500;


-- ---------------------------------------------------------------------------
-- Q7: Order with items join (signOrder loads all OrderItems)
-- ---------------------------------------------------------------------------
\echo '=== Q7: order with items join (sign order batch) ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT o.id, o.order_number, o.status, o.order_type,
       oi.id AS item_id, oi.application_id
FROM   admission.orders o
JOIN   admission.order_items oi ON oi.order_id = o.id
WHERE  o.id = 1
  AND  o.status = 'draft';


-- ---------------------------------------------------------------------------
-- Q8: Feature flag lookup by key (cache miss path)
-- ---------------------------------------------------------------------------
\echo '=== Q8: feature flag by key (cache miss path) ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT id, key, enabled, targeting_strategy, target_environments, target_scopes
FROM   environment.feature_flags
WHERE  key = 'online-application-enabled';


-- ---------------------------------------------------------------------------
-- Q9: Audit event pagination (newest first)
-- ---------------------------------------------------------------------------
\echo '=== Q9: audit events paginated ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT id, aggregate_type, aggregate_id, event_type, actor_id, occurred_at
FROM   environment.audit_events
ORDER BY occurred_at DESC
LIMIT  50 OFFSET 0;


-- ---------------------------------------------------------------------------
-- Q10: Full table COUNT for pagination totalElements
-- ---------------------------------------------------------------------------
\echo '=== Q10: COUNT(*) for pagination (no filter) ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT COUNT(*)
FROM   admission.applications;

\echo '=== Q10b: COUNT(*) with status filter ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT COUNT(*)
FROM   admission.applications
WHERE  status = 'reviewing';


-- ---------------------------------------------------------------------------
-- Index usage summary
-- ---------------------------------------------------------------------------
\echo '=== INDEX USAGE REPORT ==='
SELECT
    schemaname,
    relname      AS table_name,
    indexrelname AS index_name,
    idx_scan     AS scans,
    idx_tup_read AS tuples_read,
    idx_tup_fetch AS tuples_fetched
FROM   pg_stat_user_indexes
WHERE  schemaname IN ('admission', 'identity', 'environment')
ORDER BY idx_scan DESC
LIMIT  30;
