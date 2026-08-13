-- V52__add_loan_collections_fields.sql

ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS arrears_status VARCHAR(20);

ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS classified_at TIMESTAMP;

ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS collections_stage VARCHAR(20);

-- Existing loans must receive valid values before the columns
-- are made NOT NULL, because the Java entity requires them.
UPDATE loans
SET arrears_status = 'NOT_DUE'
WHERE arrears_status IS NULL;

UPDATE loans
SET collections_stage = 'NORMAL'
WHERE collections_stage IS NULL;

ALTER TABLE loans
    ALTER COLUMN arrears_status SET DEFAULT 'NOT_DUE';

ALTER TABLE loans
    ALTER COLUMN collections_stage SET DEFAULT 'NORMAL';

ALTER TABLE loans
    ALTER COLUMN arrears_status SET NOT NULL;

ALTER TABLE loans
    ALTER COLUMN collections_stage SET NOT NULL;