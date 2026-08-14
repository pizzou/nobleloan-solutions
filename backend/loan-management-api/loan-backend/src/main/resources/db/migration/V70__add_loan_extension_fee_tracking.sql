ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS extension_fee_assessed NUMERIC(19,2) NOT NULL DEFAULT 0.00;

ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS extension_fee_paid NUMERIC(19,2) NOT NULL DEFAULT 0.00;

ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS extension_fee_outstanding NUMERIC(19,2) NOT NULL DEFAULT 0.00;

ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS extension_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS last_extension_date DATE;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS extension_fee_component NUMERIC(19,2) NOT NULL DEFAULT 0.00;

UPDATE loans
SET extension_fee_outstanding = GREATEST(
        COALESCE(extension_fee_assessed, 0.00) -
        COALESCE(extension_fee_paid, 0.00),
        0.00
    );