-- Bank-grade payment transaction history.
-- payment_transactions already exists in the schema; this migration adds
-- the indexes required for organization-wide chronological audit/reporting.
CREATE INDEX IF NOT EXISTS idx_payment_tx_org_created_at
    ON payment_transactions (organization_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_payment_tx_external_reference
    ON payment_transactions (organization_id, external_reference)
    WHERE external_reference IS NOT NULL;
