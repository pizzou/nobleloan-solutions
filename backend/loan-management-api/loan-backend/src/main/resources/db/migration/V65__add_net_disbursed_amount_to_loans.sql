ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS net_disbursed_amount NUMERIC(19, 2);