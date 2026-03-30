-- =============================================================================
-- Concurrent Write Contention Tests
-- =============================================================================
-- These tests use pgbench to simulate concurrent writes to the applications
-- and contracts tables and detect:
--   1. Deadlocks during concurrent status transitions
--   2. Sequence contention on contract_budget_seq / contract_paid_seq
--   3. Connection pool exhaustion behaviour under load
--
-- Usage:
--   # Test 1: concurrent application status updates
--   pgbench -U scadmin -d selection-committee \
--           -f 03_concurrent_status_updates.sql \
--           -c 20 -j 4 -t 500 -n
--
--   # Test 2: concurrent sequence calls
--   pgbench -U scadmin -d selection-committee \
--           -f 03_concurrent_sequence_calls.sql \
--           -c 50 -j 8 -t 1000 -n
-- =============================================================================

-- ---------------------------------------------------------------------------
-- pgbench script 1: concurrent application status updates
-- File: 03_concurrent_status_updates.sql
-- Simulates 20 concurrent operators each accepting a different application.
-- Expected: zero deadlocks (each row is updated independently).
-- ---------------------------------------------------------------------------
\set app_id random(1, 10000)

BEGIN;
SELECT id, status
FROM   admission.applications
WHERE  id = :app_id
  AND  status = 'reviewing'
FOR UPDATE SKIP LOCKED;

-- Only update if we successfully locked the row (SKIP LOCKED returns nothing otherwise)
UPDATE admission.applications
SET    status     = 'accepted',
       updated_at = now()
WHERE  id         = :app_id
  AND  status     = 'reviewing';

-- Publish a synthetic audit event (mirrors ApplicationEventPublisher)
-- This INSERT simulates the RabbitMQ outbox pattern
INSERT INTO environment.audit_events
    (aggregate_type, aggregate_id, event_type, actor_id, payload, occurred_at)
VALUES
    ('Application', :app_id, 'STATUS_CHANGED', 1, '{"from":"reviewing","to":"accepted"}', now())
ON CONFLICT DO NOTHING;

COMMIT;


-- ---------------------------------------------------------------------------
-- pgbench script 2: concurrent sequence calls
-- File: 03_concurrent_sequence_calls.sql
-- 50 concurrent clients all calling nextval on the contract sequences.
-- Expected: no blocking (PostgreSQL sequences are lock-free per-session).
-- ---------------------------------------------------------------------------
SELECT nextval('admission.contract_budget_seq');
SELECT nextval('admission.contract_paid_seq');


-- ---------------------------------------------------------------------------
-- pgbench script 3: order signing contention
-- File: 03_concurrent_order_signing.sql
-- Simulates 5 admins concurrently signing different orders.
-- Each signing updates ~50 application rows in a single TX.
-- Expected: no deadlocks; p99 TX time < 5 000 ms.
-- ---------------------------------------------------------------------------
\set order_id random(1, 100)

BEGIN;
-- Lock the order row first (prevents double-sign)
SELECT id, status
FROM   admission.orders
WHERE  id = :order_id AND status = 'draft'
FOR UPDATE SKIP LOCKED;

-- Update all applications in the order
UPDATE admission.applications a
SET    status = 'enrolled'
FROM   admission.order_items oi
WHERE  oi.order_id    = :order_id
  AND  oi.application_id = a.id
  AND  a.status        = 'accepted';

-- Mark order as signed
UPDATE admission.orders
SET    status = 'signed'
WHERE  id     = :order_id
  AND  status = 'draft';

COMMIT;
