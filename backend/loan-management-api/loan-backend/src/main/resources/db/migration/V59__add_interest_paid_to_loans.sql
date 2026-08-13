-- V59__add_interest_paid_to_loans.sql

ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS interest_paid NUMERIC(19, 2);

UPDATE loans
SET interest_paid = 0.00
WHERE interest_paid IS NULL;

ALTER TABLE loans
    ALTER COLUMN interest_paid SET DEFAULT 0.00;

ALTER TABLE loans
    ALTER COLUMN interest_paid SET NOT NULL;