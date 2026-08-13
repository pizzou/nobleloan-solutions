-- V58__add_disbursed_at_timestamp_to_loans.sql

ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS disbursed_at_timestamp TIMESTAMP;

-- Keep the legacy timestamp synchronized for existing loans.
UPDATE loans
SET disbursed_at_timestamp = disbursed_at
WHERE disbursed_at_timestamp IS NULL
  AND disbursed_at IS NOT NULL;