-- V52__add_loan_product_fee_penalty_columns.sql

-- Add management fee percentage to loan products.
-- IF NOT EXISTS makes this migration safe if the column was
-- already created manually or by an earlier migration.

ALTER TABLE loan_products
    ADD COLUMN IF NOT EXISTS management_fee_percent NUMERIC(10, 4);

-- Add penalty percentage to loan products.

ALTER TABLE loan_products
    ADD COLUMN IF NOT EXISTS penalty_percent NUMERIC(10, 4);

-- Existing loan products should have explicit zero values rather
-- than NULL so calculations remain predictable.

UPDATE loan_products
SET management_fee_percent = 0
WHERE management_fee_percent IS NULL;

UPDATE loan_products
SET penalty_percent = 0
WHERE penalty_percent IS NULL;