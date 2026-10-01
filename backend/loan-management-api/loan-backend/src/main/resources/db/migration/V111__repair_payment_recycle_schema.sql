-- ============================================================
-- V111 - Repair payment recycle schema
-- ============================================================
-- Purpose:
--   The running Hibernate application is requesting
--   payments.deleted_at, but the database currently does not
--   contain that column.
--
-- This migration is deliberately idempotent so it is safe on
-- environments where the column was partially created.
--
-- Existing payment amounts, balances, schedules and accounting
-- records are NOT modified.
-- ============================================================

BEGIN;

-- ------------------------------------------------------------
-- 1. Ensure the payments table exists.
-- ------------------------------------------------------------
DO $$
BEGIN
    IF to_regclass('public.payments') IS NULL THEN
        RAISE EXCEPTION
            'Required table public.payments does not exist';
    END IF;
END
$$;

-- ------------------------------------------------------------
-- 2. Add the column required by the currently deployed
--    Hibernate Payment mapping.
--
-- NULL = active/not deleted.
-- ------------------------------------------------------------
ALTER TABLE public.payments
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ NULL;

-- ------------------------------------------------------------
-- 3. Make sure existing payments remain active.
--
-- This intentionally does NOT populate deleted_at with NOW().
-- ------------------------------------------------------------
UPDATE public.payments
SET deleted_at = NULL
WHERE deleted_at IS NOT NULL
  AND deleted_at < TIMESTAMPTZ '1900-01-01';

-- ------------------------------------------------------------
-- 4. Index payment recycle/visibility lookups.
-- ------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_payments_deleted_at
    ON public.payments (deleted_at);

CREATE INDEX IF NOT EXISTS idx_payments_loan_deleted_at
    ON public.payments (loan_id, deleted_at);

COMMIT;