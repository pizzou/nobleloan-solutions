ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS management_fee_paid NUMERIC(19, 2);