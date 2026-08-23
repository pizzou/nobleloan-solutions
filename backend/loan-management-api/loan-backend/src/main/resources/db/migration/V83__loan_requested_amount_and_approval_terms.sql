
ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS requested_amount NUMERIC(19,2);

UPDATE loans
SET requested_amount = amount
WHERE requested_amount IS NULL;

ALTER TABLE loans
    ALTER COLUMN requested_amount SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_loans_requested_amount
    ON loans (organization_id, requested_amount);
