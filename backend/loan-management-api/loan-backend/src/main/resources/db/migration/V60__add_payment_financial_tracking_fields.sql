-- V60__align_payment_financial_tracking_fields.sql

-- ============================================================
-- 1. ADD COLUMNS
-- ============================================================

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS principal_component NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS interest_component NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS management_fee_component NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS amount_paid NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS scheduled_interest NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS scheduled_management_fee NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS cycle_interest_due NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS cycle_interest_remaining NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS cycle_management_fee_due NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS cycle_management_fee_remaining NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS interest_calculation_date TIMESTAMP;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS penalty NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS penalty_paid NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS waived_amount NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS outstanding_after NUMERIC(19, 2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS paid BOOLEAN;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS is_late BOOLEAN;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS days_late INTEGER;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS status VARCHAR(30);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS verified_at TIMESTAMP;


-- ============================================================
-- 2. BACKFILL EXISTING ROWS
-- ============================================================

UPDATE payments
SET principal_component = 0.00
WHERE principal_component IS NULL;

UPDATE payments
SET interest_component = 0.00
WHERE interest_component IS NULL;

UPDATE payments
SET management_fee_component = 0.00
WHERE management_fee_component IS NULL;

UPDATE payments
SET amount_paid = 0.00
WHERE amount_paid IS NULL;

UPDATE payments
SET scheduled_interest = 0.00
WHERE scheduled_interest IS NULL;

UPDATE payments
SET scheduled_management_fee = 0.00
WHERE scheduled_management_fee IS NULL;

UPDATE payments
SET cycle_interest_due = 0.00
WHERE cycle_interest_due IS NULL;

UPDATE payments
SET cycle_interest_remaining = 0.00
WHERE cycle_interest_remaining IS NULL;

UPDATE payments
SET cycle_management_fee_due = 0.00
WHERE cycle_management_fee_due IS NULL;

UPDATE payments
SET cycle_management_fee_remaining = 0.00
WHERE cycle_management_fee_remaining IS NULL;

UPDATE payments
SET penalty = 0.00
WHERE penalty IS NULL;

UPDATE payments
SET penalty_paid = 0.00
WHERE penalty_paid IS NULL;

UPDATE payments
SET waived_amount = 0.00
WHERE waived_amount IS NULL;

UPDATE payments
SET outstanding_after = 0.00
WHERE outstanding_after IS NULL;

UPDATE payments
SET paid = FALSE
WHERE paid IS NULL;

UPDATE payments
SET is_late = FALSE
WHERE is_late IS NULL;

UPDATE payments
SET days_late = 0
WHERE days_late IS NULL;

UPDATE payments
SET status = 'PENDING'
WHERE status IS NULL;

UPDATE payments
SET updated_at = created_at
WHERE updated_at IS NULL;


-- ============================================================
-- 3. DEFAULTS
-- ============================================================

ALTER TABLE payments
    ALTER COLUMN principal_component SET DEFAULT 0.00;

ALTER TABLE payments
    ALTER COLUMN interest_component SET DEFAULT 0.00;

ALTER TABLE payments
    ALTER COLUMN management_fee_component SET DEFAULT 0.00;

ALTER TABLE payments
    ALTER COLUMN amount_paid SET DEFAULT 0.00;

ALTER TABLE payments
    ALTER COLUMN scheduled_interest SET DEFAULT 0.00;

ALTER TABLE payments
    ALTER COLUMN scheduled_management_fee SET DEFAULT 0.00;

ALTER TABLE payments
    ALTER COLUMN cycle_interest_due SET DEFAULT 0.00;

ALTER TABLE payments
    ALTER COLUMN cycle_interest_remaining SET DEFAULT 0.00;

ALTER TABLE payments
    ALTER COLUMN cycle_management_fee_due SET DEFAULT 0.00;

ALTER TABLE payments
    ALTER COLUMN cycle_management_fee_remaining SET DEFAULT 0.00;

ALTER TABLE payments
    ALTER COLUMN penalty SET DEFAULT 0.00;

ALTER TABLE payments
    ALTER COLUMN penalty_paid SET DEFAULT 0.00;

ALTER TABLE payments
    ALTER COLUMN waived_amount SET DEFAULT 0.00;

ALTER TABLE payments
    ALTER COLUMN outstanding_after SET DEFAULT 0.00;

ALTER TABLE payments
    ALTER COLUMN paid SET DEFAULT FALSE;

ALTER TABLE payments
    ALTER COLUMN is_late SET DEFAULT FALSE;

ALTER TABLE payments
    ALTER COLUMN days_late SET DEFAULT 0;

ALTER TABLE payments
    ALTER COLUMN status SET DEFAULT 'PENDING';


-- ============================================================
-- 4. NOT NULL
-- ============================================================

ALTER TABLE payments
    ALTER COLUMN principal_component SET NOT NULL;

ALTER TABLE payments
    ALTER COLUMN interest_component SET NOT NULL;

ALTER TABLE payments
    ALTER COLUMN management_fee_component SET NOT NULL;

ALTER TABLE payments
    ALTER COLUMN amount_paid SET NOT NULL;

ALTER TABLE payments
    ALTER COLUMN scheduled_interest SET NOT NULL;

ALTER TABLE payments
    ALTER COLUMN scheduled_management_fee SET NOT NULL;

ALTER TABLE payments
    ALTER COLUMN cycle_interest_due SET NOT NULL;

ALTER TABLE payments
    ALTER COLUMN cycle_interest_remaining SET NOT NULL;

ALTER TABLE payments
    ALTER COLUMN cycle_management_fee_due SET NOT NULL;

ALTER TABLE payments
    ALTER COLUMN cycle_management_fee_remaining SET NOT NULL;

ALTER TABLE payments
    ALTER COLUMN penalty SET NOT NULL;

ALTER TABLE payments
    ALTER COLUMN penalty_paid SET NOT NULL;

ALTER TABLE payments
    ALTER COLUMN waived_amount SET NOT NULL;

ALTER TABLE payments
    ALTER COLUMN outstanding_after SET NOT NULL;

ALTER TABLE payments
    ALTER COLUMN paid SET NOT NULL;

ALTER TABLE payments
    ALTER COLUMN is_late SET NOT NULL;

ALTER TABLE payments
    ALTER COLUMN days_late SET NOT NULL;

ALTER TABLE payments
    ALTER COLUMN status SET NOT NULL;