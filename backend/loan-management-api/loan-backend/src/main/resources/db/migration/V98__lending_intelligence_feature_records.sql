CREATE TABLE IF NOT EXISTS lending_feature_records (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    loan_id BIGINT NULL,
    borrower_id BIGINT NULL,
    feature_type VARCHAR(60) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(20),
    amount NUMERIC(19,2),
    due_date DATE,
    assigned_user_id BIGINT,
    payload TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lfr_org FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_lfr_loan FOREIGN KEY (loan_id) REFERENCES loans(id),
    CONSTRAINT fk_lfr_borrower FOREIGN KEY (borrower_id) REFERENCES borrowers(id)
);
CREATE INDEX IF NOT EXISTS idx_lfr_org_type ON lending_feature_records(organization_id, feature_type);
CREATE INDEX IF NOT EXISTS idx_lfr_org_status ON lending_feature_records(organization_id, status);
CREATE INDEX IF NOT EXISTS idx_lfr_org_loan ON lending_feature_records(organization_id, loan_id);
CREATE INDEX IF NOT EXISTS idx_lfr_org_due ON lending_feature_records(organization_id, due_date);
