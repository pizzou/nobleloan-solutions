-- Bank-grade approval replay protection.
ALTER TABLE financial_approvals
    ADD COLUMN IF NOT EXISTS consumed_at TIMESTAMP;

ALTER TABLE financial_approvals
    DROP CONSTRAINT IF EXISTS ck_fin_approval_status;

ALTER TABLE financial_approvals
    ADD CONSTRAINT ck_fin_approval_status
    CHECK (status IN ('PENDING','APPROVED','REJECTED','CONSUMED'));

CREATE UNIQUE INDEX IF NOT EXISTS uk_fin_approval_pending_operation
    ON financial_approvals(organization_id, operation_type, operation_id)
    WHERE status = 'PENDING' AND operation_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_fin_approval_consumed
    ON financial_approvals(organization_id, consumed_at);
