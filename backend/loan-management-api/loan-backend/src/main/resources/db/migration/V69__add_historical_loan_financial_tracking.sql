-- Preserve legacy portfolio financial state without creating duplicate current-period journals.
ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS principal_paid NUMERIC(19,2) NOT NULL DEFAULT 0.00;

ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS interest_outstanding NUMERIC(19,2) NOT NULL DEFAULT 0.00;

ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS management_fee_outstanding NUMERIC(19,2) NOT NULL DEFAULT 0.00;

ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS penalties_assessed NUMERIC(19,2) NOT NULL DEFAULT 0.00;

ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS penalties_paid NUMERIC(19,2) NOT NULL DEFAULT 0.00;

-- Keep existing rows coherent with the current principal balance model.
UPDATE loans
SET principal_paid = GREATEST(COALESCE(amount, 0.00) - COALESCE(outstanding_balance, 0.00), 0.00)
WHERE principal_paid = 0.00
  AND COALESCE(amount, 0.00) >= COALESCE(outstanding_balance, 0.00);

UPDATE loans
SET interest_outstanding = GREATEST(COALESCE(total_interest, 0.00) - COALESCE(interest_paid, 0.00), 0.00)
WHERE interest_outstanding = 0.00;

UPDATE loans
SET management_fee_outstanding = GREATEST(COALESCE(management_fee, 0.00) - COALESCE(management_fee_paid, 0.00), 0.00)
WHERE management_fee_outstanding = 0.00;