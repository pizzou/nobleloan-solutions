-- Reporting/dashboard indexes.
-- These are additive and preserve all existing financial definitions.
CREATE INDEX IF NOT EXISTS idx_loans_org_credit_quality
    ON loans (organization_id, credit_quality);

CREATE INDEX IF NOT EXISTS idx_loans_org_borrower
    ON loans (organization_id, borrower_id);

CREATE INDEX IF NOT EXISTS idx_collection_cases_org_filter
    ON collection_cases (organization_id, bucket, status, assigned_agent_id);

CREATE INDEX IF NOT EXISTS idx_payments_org_paid_due
    ON payments (organization_id, paid, due_date);
