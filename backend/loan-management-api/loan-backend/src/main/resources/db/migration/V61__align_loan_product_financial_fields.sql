-- V61__align_loan_product_financial_fields.sql

-- ============================================================
-- 1. ADD COLUMNS
-- ============================================================

ALTER TABLE loan_products
    ADD COLUMN IF NOT EXISTS interest_rate_type VARCHAR(20);

ALTER TABLE loan_products
    ADD COLUMN IF NOT EXISTS min_amount NUMERIC(19, 6);

ALTER TABLE loan_products
    ADD COLUMN IF NOT EXISTS max_amount NUMERIC(19, 6);

ALTER TABLE loan_products
    ADD COLUMN IF NOT EXISTS application_fee_percent NUMERIC(19, 9);

ALTER TABLE loan_products
    ADD COLUMN IF NOT EXISTS management_fee_percent NUMERIC(19, 9);

ALTER TABLE loan_products
    ADD COLUMN IF NOT EXISTS penalty_percent NUMERIC(19, 9);

ALTER TABLE loan_products
    ADD COLUMN IF NOT EXISTS active BOOLEAN;

ALTER TABLE loan_products
    ADD COLUMN IF NOT EXISTS display_order INTEGER;

ALTER TABLE loan_products
    ADD COLUMN IF NOT EXISTS required_document_types TEXT;


-- ============================================================
-- 2. BACKFILL EXISTING ROWS
-- ============================================================

UPDATE loan_products
SET interest_rate_type = 'MONTHLY'
WHERE interest_rate_type IS NULL;

UPDATE loan_products
SET min_amount = 500000.00
WHERE min_amount IS NULL;

UPDATE loan_products
SET application_fee_percent = 2.00
WHERE application_fee_percent IS NULL;

UPDATE loan_products
SET management_fee_percent = 5.00
WHERE management_fee_percent IS NULL;

UPDATE loan_products
SET penalty_percent = 15.00
WHERE penalty_percent IS NULL;

UPDATE loan_products
SET active = TRUE
WHERE active IS NULL;


-- ============================================================
-- 3. DEFAULTS
-- ============================================================

ALTER TABLE loan_products
    ALTER COLUMN interest_rate_type SET DEFAULT 'MONTHLY';

ALTER TABLE loan_products
    ALTER COLUMN min_amount SET DEFAULT 500000.00;

ALTER TABLE loan_products
    ALTER COLUMN application_fee_percent SET DEFAULT 2.00;

ALTER TABLE loan_products
    ALTER COLUMN management_fee_percent SET DEFAULT 5.00;

ALTER TABLE loan_products
    ALTER COLUMN penalty_percent SET DEFAULT 15.00;

ALTER TABLE loan_products
    ALTER COLUMN active SET DEFAULT TRUE;


-- ============================================================
-- 4. NOT NULL
-- ============================================================

ALTER TABLE loan_products
    ALTER COLUMN interest_rate_type SET NOT NULL;

ALTER TABLE loan_products
    ALTER COLUMN min_amount SET NOT NULL;

ALTER TABLE loan_products
    ALTER COLUMN application_fee_percent SET NOT NULL;

ALTER TABLE loan_products
    ALTER COLUMN management_fee_percent SET NOT NULL;

ALTER TABLE loan_products
    ALTER COLUMN penalty_percent SET NOT NULL;

ALTER TABLE loan_products
    ALTER COLUMN active SET NOT NULL;