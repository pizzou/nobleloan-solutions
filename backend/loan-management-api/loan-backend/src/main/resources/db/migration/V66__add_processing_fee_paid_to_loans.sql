ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS processing_fee_paid NUMERIC(19, 2);