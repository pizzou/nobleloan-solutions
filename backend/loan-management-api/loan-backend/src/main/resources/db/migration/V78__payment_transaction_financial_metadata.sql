-- V77: complete the immutable payment transaction ledger with provider and
-- component-level financial metadata.

ALTER TABLE payment_transactions
    ADD COLUMN IF NOT EXISTS management_fee_component NUMERIC(19,2),
    ADD COLUMN IF NOT EXISTS extension_fee_component NUMERIC(19,2),
    ADD COLUMN IF NOT EXISTS provider VARCHAR(40),
    ADD COLUMN IF NOT EXISTS currency VARCHAR(12),
    ADD COLUMN IF NOT EXISTS external_reference VARCHAR(120),
    ADD COLUMN IF NOT EXISTS gateway_status VARCHAR(40);

UPDATE payment_transactions
SET management_fee_component = 0.00
WHERE management_fee_component IS NULL;

UPDATE payment_transactions
SET extension_fee_component = 0.00
WHERE extension_fee_component IS NULL;

ALTER TABLE payment_transactions
    ALTER COLUMN management_fee_component SET DEFAULT 0.00,
    ALTER COLUMN extension_fee_component SET DEFAULT 0.00,
    ALTER COLUMN management_fee_component SET NOT NULL,
    ALTER COLUMN extension_fee_component SET NOT NULL;

ALTER TABLE payment_transactions
    DROP CONSTRAINT IF EXISTS chk_payment_tx_status;

ALTER TABLE payment_transactions
    ADD CONSTRAINT chk_payment_tx_status
    CHECK (status IN ('INITIATED', 'PENDING', 'POSTED', 'FAILED', 'REVERSED'));

CREATE INDEX IF NOT EXISTS idx_payment_tx_provider_external
    ON payment_transactions (organization_id, provider, external_reference);

CREATE INDEX IF NOT EXISTS idx_payment_tx_loan_created
    ON payment_transactions (loan_id, created_at DESC);