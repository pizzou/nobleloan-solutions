-- V121__raise_minimum_loan_amount_to_one_million.sql
-- New loan requests and configured products must start at RWF 1,000,000.
-- This updates product policy only; existing loan balances and ledger history are untouched.

UPDATE loan_products
SET min_amount = 1000000.00,
    updated_at = CURRENT_TIMESTAMP
WHERE min_amount IS NULL OR min_amount < 1000000.00;

ALTER TABLE loan_products
    ALTER COLUMN min_amount SET DEFAULT 1000000.00;
