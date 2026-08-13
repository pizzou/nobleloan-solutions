-- V54__add_classified_at_to_loans.sql

ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS classified_at TIMESTAMP;