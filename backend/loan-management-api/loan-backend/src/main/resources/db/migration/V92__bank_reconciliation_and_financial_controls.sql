CREATE TABLE IF NOT EXISTS financial_approvals (
 id BIGSERIAL PRIMARY KEY,
 organization_id BIGINT NOT NULL REFERENCES organizations(id),
 operation_type VARCHAR(50) NOT NULL,
 operation_id VARCHAR(100),
 amount NUMERIC(19,2),
 currency VARCHAR(3) NOT NULL DEFAULT 'RWF',
 maker_user_id BIGINT NOT NULL,
 maker_name VARCHAR(255),
 checker_user_id BIGINT,
 checker_name VARCHAR(255),
 required_level INTEGER NOT NULL DEFAULT 1,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 reason TEXT,
 payload_hash VARCHAR(64) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 decided_at TIMESTAMP,
 CONSTRAINT ck_fin_approval_status CHECK (status IN ('PENDING','APPROVED','REJECTED')),
 CONSTRAINT ck_fin_approval_level CHECK (required_level BETWEEN 1 AND 3),
 CONSTRAINT ck_fin_approval_amount CHECK (amount IS NULL OR amount >= 0)
);
CREATE INDEX IF NOT EXISTS idx_fin_approval_org_status ON financial_approvals(organization_id,status);
CREATE INDEX IF NOT EXISTS idx_fin_approval_operation ON financial_approvals(organization_id,operation_type,operation_id);

CREATE TABLE IF NOT EXISTS bank_statement_lines (
 id BIGSERIAL PRIMARY KEY,
 organization_id BIGINT NOT NULL REFERENCES organizations(id),
 bank_account_id BIGINT NOT NULL REFERENCES bank_accounts(id),
 transaction_date DATE NOT NULL,
 value_date DATE,
 reference VARCHAR(255),
 description TEXT,
 amount NUMERIC(19,2) NOT NULL,
 currency VARCHAR(3) NOT NULL DEFAULT 'RWF',
 external_id VARCHAR(150),
 reconciliation_status VARCHAR(20) NOT NULL DEFAULT 'UNMATCHED',
 matched_journal_entry_id BIGINT REFERENCES journal_entries(id),
 matched_at TIMESTAMP,
 matched_by VARCHAR(255),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT ck_bsl_status CHECK (reconciliation_status IN ('UNMATCHED','MATCHED','EXCEPTION'))
);
CREATE INDEX IF NOT EXISTS idx_bsl_org_account_date ON bank_statement_lines(organization_id,bank_account_id,transaction_date);
CREATE INDEX IF NOT EXISTS idx_bsl_match ON bank_statement_lines(bank_account_id,reconciliation_status);
CREATE UNIQUE INDEX IF NOT EXISTS uk_bsl_external_id ON bank_statement_lines(organization_id,bank_account_id,external_id) WHERE external_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS payment_settlements (
 id BIGSERIAL PRIMARY KEY,
 organization_id BIGINT NOT NULL REFERENCES organizations(id),
 provider VARCHAR(50) NOT NULL,
 provider_reference VARCHAR(150) NOT NULL,
 internal_payment_reference VARCHAR(150),
 amount NUMERIC(19,2) NOT NULL,
 currency VARCHAR(3) NOT NULL,
 settlement_date DATE NOT NULL,
 bank_account_id BIGINT REFERENCES bank_accounts(id),
 status VARCHAR(20) NOT NULL DEFAULT 'UNRECONCILED',
 bank_statement_line_id BIGINT REFERENCES bank_statement_lines(id),
 difference NUMERIC(19,2),
 matched_at TIMESTAMP,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_settlement_provider_ref UNIQUE(organization_id,provider,provider_reference),
 CONSTRAINT ck_settlement_status CHECK (status IN ('UNRECONCILED','RECONCILED','EXCEPTION')),
 CONSTRAINT ck_settlement_amount CHECK (amount > 0)
);
CREATE INDEX IF NOT EXISTS idx_settlement_status ON payment_settlements(organization_id,status);
