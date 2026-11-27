CREATE TABLE IF NOT EXISTS loan_contracts (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    loan_id BIGINT NOT NULL UNIQUE REFERENCES loans(id),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    file_name VARCHAR(180) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    document_data BYTEA NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    generated_by_id BIGINT NULL REFERENCES app_users(id),
    sent_by_id BIGINT NULL REFERENCES app_users(id),
    generated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP WITHOUT TIME ZONE NULL,
    borrower_notified_at TIMESTAMP WITHOUT TIME ZONE NULL,
    CONSTRAINT ck_loan_contract_status CHECK (status IN ('DRAFT', 'SENT_TO_BORROWER'))
);

CREATE INDEX IF NOT EXISTS idx_loan_contract_org_loan
    ON loan_contracts (organization_id, loan_id);

COMMENT ON TABLE loan_contracts IS
    'Generated draft loan contract snapshot. The borrower must review and sign a printed copy; generation does not mean acceptance or disbursement.';

-- Signed paper contracts are retained as borrower documents and linked to the exact loan.
-- Existing borrower documents remain unlinked (NULL loan_id) and are not changed.
ALTER TABLE borrower_files
    ADD COLUMN IF NOT EXISTS loan_id BIGINT NULL REFERENCES loans(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_borrower_files_loan_document_type
    ON borrower_files (loan_id, document_type, uploaded_at DESC);

COMMENT ON COLUMN borrower_files.loan_id IS
    'Optional exact loan association; signed loan contracts are listed in that loan''s Borrower Documents section.';
