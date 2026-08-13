-- ================================================================
-- V68__add_management_fee_amount_to_payment_schedules.sql
-- ================================================================


ALTER TABLE payment_schedules
    ADD COLUMN IF NOT EXISTS management_fee_amount NUMERIC(19, 2);

UPDATE payment_schedules
SET management_fee_amount = 0.00
WHERE management_fee_amount IS NULL;

ALTER TABLE payment_schedules
    ALTER COLUMN management_fee_amount SET DEFAULT 0.00;

ALTER TABLE payment_schedules
    ALTER COLUMN management_fee_amount SET NOT NULL;