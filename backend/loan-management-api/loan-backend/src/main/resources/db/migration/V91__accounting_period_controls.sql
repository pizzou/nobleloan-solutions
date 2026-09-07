ALTER TABLE loans ADD COLUMN IF NOT EXISTS written_off_at TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_loans_written_off_at ON loans(written_off_at);

CREATE TABLE IF NOT EXISTS accounting_periods (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    year INTEGER NOT NULL,
    month INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    opened_at TIMESTAMP,
    opened_by VARCHAR(255),
    closed_at TIMESTAMP,
    closed_by VARCHAR(255),
    CONSTRAINT fk_accounting_period_org FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT ck_accounting_period_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT ck_accounting_period_status CHECK (status IN ('OPEN','CLOSED')),
    CONSTRAINT uk_accounting_period_org_month UNIQUE (organization_id, year, month)
);
CREATE INDEX IF NOT EXISTS idx_accounting_period_org_status ON accounting_periods(organization_id, status);
