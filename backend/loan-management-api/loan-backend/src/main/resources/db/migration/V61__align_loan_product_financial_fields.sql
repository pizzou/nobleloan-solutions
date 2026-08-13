-- V61__align_loan_product_financial_fields.sql

ALTER TABLE loan_products
    ADD COLUMN IF NOT EXISTS interest_rate_type VARCHAR(20);

ALTER TABLE loan_products
    ADD COLUMN IF NOT EXISTS min_amount NUMERIC(19, 6);

ALTER TABLE loan_products
    ADD COLUMN IF NOT EXISTS max_amount NUMERIC(19, 6);

ALTER TABLE loan_products
    ADD COLUMN IF NOT EXISTS processing_fee_percent NUMERIC(19, 9);

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