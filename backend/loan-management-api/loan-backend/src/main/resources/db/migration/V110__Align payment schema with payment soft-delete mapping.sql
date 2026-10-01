-- ============================================================
-- V102 - Align payment schema with payment soft-delete mapping
-- ============================================================
-- Existing migrations are intentionally not modified.
-- This migration is safe for databases that already contain
-- production/test payment records.
-- ============================================================

BEGIN;

-- ------------------------------------------------------------
-- 1. Add the column expected by Hibernate Payment.deletedAt
-- ------------------------------------------------------------
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- ------------------------------------------------------------
-- 2. Existing payments remain active.
--    NULL means the payment has not been deleted.
-- ------------------------------------------------------------

-- ------------------------------------------------------------
-- 3. Index for payment visibility/recycle-bin queries.
-- ------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_payments_deleted_at
    ON payments (deleted_at);

-- ------------------------------------------------------------
-- 4. Index for the common:
--       loan_id + deleted_at
--    visibility lookup.
-- ------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_payments_loan_deleted_at
    ON payments (loan_id, deleted_at);

COMMIT;