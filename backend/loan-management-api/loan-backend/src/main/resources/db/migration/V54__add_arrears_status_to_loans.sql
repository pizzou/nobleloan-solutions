-- V53__add_arrears_status_to_loans.sql

ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS arrears_status VARCHAR(50);

-- Existing loans need a valid default status.
UPDATE loans
SET arrears_status = 'CURRENT'
WHERE arrears_status IS NULL;

-- Make the column non-null after existing records have been populated.
ALTER TABLE loans
    ALTER COLUMN arrears_status SET DEFAULT 'CURRENT';

ALTER TABLE loans
    ALTER COLUMN arrears_status SET NOT NULL;