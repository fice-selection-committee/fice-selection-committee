-- =============================================================================
-- Performance Test Seed Scripts
-- =============================================================================
-- Run in order before executing the k6 scenarios:
--   1. seed-test-users.sql       (creates perf-test operators + admin)
--   2. seed-submitted-applications.sql  (creates 500 submitted applications)
--   3. seed-orders-for-signing.sql      (creates 100 draft orders x 50 apps)
-- =============================================================================

-- seed-test-users.sql ----------------------------------------------------------
-- Creates the operator and admin accounts used by k6 scenarios B, C, E.
-- Run against identity-service database schema.

SET search_path = identity, public;

-- Insert operator user (password: OperatorPerf1! bcrypt hash)
INSERT INTO identity.users
    (email, password_hash, first_name, last_name, middle_name, email_verified, created_at)
VALUES
    ('operator@fice-perf.test',
     '$2a$10$dummyhashreplacewithrealbcrypt',
     'Perf', 'Operator', 'Test',
     true, now())
ON CONFLICT (email) DO NOTHING;

-- Insert admin user
INSERT INTO identity.users
    (email, password_hash, first_name, last_name, middle_name, email_verified, created_at)
VALUES
    ('admin@fice-perf.test',
     '$2a$10$dummyhashreplacewithrealbcrypt',
     'Perf', 'Admin', 'Test',
     true, now())
ON CONFLICT (email) DO NOTHING;

-- Assign OPERATOR role to operator user
-- (Adjust role_id to match your actual roles table)
DO $$ 
DECLARE v_user_id BIGINT; v_role_id BIGINT;
BEGIN
    SELECT id INTO v_user_id FROM identity.users WHERE email = 'operator@fice-perf.test';
    SELECT id INTO v_role_id  FROM identity.roles  WHERE name = 'OPERATOR';
    IF v_user_id IS NOT NULL AND v_role_id IS NOT NULL THEN
        UPDATE identity.users SET role_id = v_role_id WHERE id = v_user_id;
    END IF;
END$$;

-- Assign ADMIN role to admin user
DO $$ 
DECLARE v_user_id BIGINT; v_role_id BIGINT;
BEGIN
    SELECT id INTO v_user_id FROM identity.users WHERE email = 'admin@fice-perf.test';
    SELECT id INTO v_role_id  FROM identity.roles  WHERE name = 'ADMIN';
    IF v_user_id IS NOT NULL AND v_role_id IS NOT NULL THEN
        UPDATE identity.users SET role_id = v_role_id WHERE id = v_user_id;
    END IF;
END$$;


-- seed-submitted-applications.sql ---------------------------------------------
-- Seeds 500 submitted applications pre-assigned to the perf operator.
-- Requires educational_programs, faculty, cathedras rows to exist.

SET search_path = admission, identity, public;

DO $$
DECLARE
    v_operator_id BIGINT;
    v_prog_id     INT;
    v_i           INT;
    v_app_id      BIGINT;
    v_base_user   BIGINT := 90000;  -- synthetic applicant user IDs start here
BEGIN
    -- Get operator user id
    SELECT id INTO v_operator_id FROM identity.users WHERE email = 'operator@fice-perf.test';
    -- Use first educational program
    SELECT id INTO v_prog_id FROM admission.educational_programs LIMIT 1;

    FOR v_i IN 1..500 LOOP
        -- Ensure synthetic applicant users exist in identity.users
        INSERT INTO identity.users (id, email, password_hash, first_name, last_name, email_verified, created_at)
        VALUES (v_base_user + v_i,
                'seed.applicant.' || v_i || '@fice-perf.test',
                '$2a$10$dummy', 'Seed', 'Applicant' || v_i, true, now())
        ON CONFLICT (id) DO NOTHING;

        -- Create submitted application
        INSERT INTO admission.applications
            (applicant_user_id, operator_user_id, sex, status, grade, educational_program_id, created_at)
        VALUES
            (v_base_user + v_i, v_operator_id, true, 'reviewing', 'bachelor', v_prog_id,
             now() - (v_i || ' minutes')::interval)
        RETURNING id INTO v_app_id;
    END LOOP;

    RAISE NOTICE 'Seeded 500 reviewing applications.';
END$$;


-- seed-orders-for-signing.sql -------------------------------------------------
-- Seeds 100 draft enrollment orders, each containing 50 accepted applications
-- with assigned groups and registered contracts (required by validateEnrollmentApplications).

SET search_path = admission, identity, public;

DO $$
DECLARE
    v_admin_id   BIGINT;
    v_prog_id    INT;
    v_group_id   INT;
    v_order_id   BIGINT;
    v_app_id     BIGINT;
    v_contract_id BIGINT;
    v_base_user  BIGINT := 80000;
    v_order_num  INT;
    v_app_num    INT;
BEGIN
    SELECT id INTO v_admin_id FROM identity.users WHERE email = 'admin@fice-perf.test';
    SELECT id INTO v_prog_id  FROM admission.educational_programs LIMIT 1;

    -- Create a group for seeded applications
    INSERT INTO admission.groups (enrollment_year, code, educational_program_id)
    VALUES (2026, 'PERF-01', v_prog_id)
    ON CONFLICT (code, enrollment_year) DO NOTHING
    RETURNING id INTO v_group_id;

    IF v_group_id IS NULL THEN
        SELECT id INTO v_group_id FROM admission.groups WHERE code = 'PERF-01' AND enrollment_year = 2026;
    END IF;

    FOR v_order_num IN 1..100 LOOP
        -- Create the draft order
        INSERT INTO admission.orders (order_type, order_date, created_by, status, order_number)
        VALUES ('enrollment', CURRENT_DATE, v_admin_id, 'draft', 'PERF-ORD-' || LPAD(v_order_num::text, 4, '0'))
        RETURNING id INTO v_order_id;

        FOR v_app_num IN 1..50 LOOP
            -- Ensure applicant user exists
            INSERT INTO identity.users (id, email, password_hash, first_name, last_name, email_verified, created_at)
            VALUES (v_base_user + (v_order_num * 100) + v_app_num,
                    'order.seed.' || v_order_num || '.' || v_app_num || '@fice-perf.test',
                    '$2a$10$dummy', 'Order', 'Seed', true, now())
            ON CONFLICT (id) DO NOTHING;

            -- Create accepted application with group
            INSERT INTO admission.applications
                (applicant_user_id, sex, status, grade, group_id, educational_program_id, created_at)
            VALUES
                (v_base_user + (v_order_num * 100) + v_app_num,
                 true, 'accepted', 'bachelor', v_group_id, v_prog_id, now())
            RETURNING id INTO v_app_id;

            -- Create registered contract (required by validateEnrollmentApplications)
            INSERT INTO admission.contracts
                (application_id, contract_number, contract_type, status, registration_date, registered_by)
            VALUES
                (v_app_id,
                 'PERF-' || v_order_num || '-' || v_app_num,
                 'budget', 'registered', CURRENT_DATE, v_admin_id);

            -- Link application to order
            INSERT INTO admission.order_items (order_id, application_id)
            VALUES (v_order_id, v_app_id);
        END LOOP;
    END LOOP;

    RAISE NOTICE 'Seeded 100 draft orders x 50 applications each.';
END$$;
