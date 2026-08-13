ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS management_fee_rate NUMERIC(19, 9);