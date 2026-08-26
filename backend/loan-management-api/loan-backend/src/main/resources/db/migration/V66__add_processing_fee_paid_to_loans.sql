ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS application_fee_paid NUMERIC(19, 2);