

ALTER TABLE loan_products
ADD COLUMN management_fee_percent NUMERIC(10, 4) NOT NULL DEFAULT 0.0000;