CREATE TABLE loan_principal_amendment_requests (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    loan_id BIGINT NOT NULL REFERENCES loans(id),
    requester_id BIGINT NOT NULL REFERENCES app_users(id),
    reviewer_id BIGINT REFERENCES app_users(id),
    correction_type VARCHAR(40) NOT NULL CHECK (correction_type IN ('RECORDING_ERROR', 'ADDITIONAL_DISBURSEMENT', 'CONTRACT_TERMS')),
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING_REVIEW' CHECK (status IN ('PENDING_REVIEW', 'REJECTED', 'APPROVED_FOR_RECONCILIATION', 'APPLIED')),
    old_principal NUMERIC(19,2) NOT NULL,
    corrected_principal NUMERIC(19,2) NOT NULL,
    original_disbursed_amount NUMERIC(19,2) NOT NULL,
    original_total_paid NUMERIC(19,2) NOT NULL,
    old_disbursement_date DATE,
    corrected_disbursement_date DATE,
    old_interest_rate NUMERIC(12,6),
    corrected_interest_rate NUMERIC(12,6),
    old_management_fee_rate NUMERIC(12,6),
    corrected_management_fee_rate NUMERIC(12,6),
    loan_reference VARCHAR(100) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    evidence_reference VARCHAR(250) NOT NULL,
    reviewer_reason VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_principal_amendment_positive CHECK (old_principal > 0 AND corrected_principal > 0),
    CONSTRAINT ck_loan_amendment_has_change CHECK (
      corrected_principal <> old_principal
      OR corrected_disbursement_date IS DISTINCT FROM old_disbursement_date
      OR corrected_interest_rate IS DISTINCT FROM old_interest_rate
      OR corrected_management_fee_rate IS DISTINCT FROM old_management_fee_rate)
);

CREATE UNIQUE INDEX uq_loan_principal_amendment_open ON loan_principal_amendment_requests (organization_id, loan_id)
    WHERE status IN ('PENDING_REVIEW', 'APPROVED_FOR_RECONCILIATION');
CREATE INDEX ix_loan_principal_amendment_inbox ON loan_principal_amendment_requests (organization_id, status, created_at);

CREATE OR REPLACE FUNCTION guard_principal_amendment_audit()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Principal amendment requests cannot be deleted';
    END IF;
    IF OLD.status NOT IN ('PENDING_REVIEW', 'APPROVED_FOR_RECONCILIATION')
       OR (OLD.status = 'PENDING_REVIEW' AND NEW.status NOT IN ('REJECTED','APPROVED_FOR_RECONCILIATION'))
       OR (OLD.status = 'APPROVED_FOR_RECONCILIATION' AND NEW.status <> 'APPLIED')
       OR NEW.organization_id IS DISTINCT FROM OLD.organization_id
       OR NEW.loan_id IS DISTINCT FROM OLD.loan_id
       OR NEW.requester_id IS DISTINCT FROM OLD.requester_id
       OR NEW.correction_type IS DISTINCT FROM OLD.correction_type
       OR NEW.old_principal IS DISTINCT FROM OLD.old_principal
       OR NEW.corrected_principal IS DISTINCT FROM OLD.corrected_principal
       OR NEW.original_disbursed_amount IS DISTINCT FROM OLD.original_disbursed_amount
       OR NEW.original_total_paid IS DISTINCT FROM OLD.original_total_paid
       OR NEW.old_disbursement_date IS DISTINCT FROM OLD.old_disbursement_date
       OR NEW.corrected_disbursement_date IS DISTINCT FROM OLD.corrected_disbursement_date
       OR NEW.old_interest_rate IS DISTINCT FROM OLD.old_interest_rate
       OR NEW.corrected_interest_rate IS DISTINCT FROM OLD.corrected_interest_rate
       OR NEW.old_management_fee_rate IS DISTINCT FROM OLD.old_management_fee_rate
       OR NEW.corrected_management_fee_rate IS DISTINCT FROM OLD.corrected_management_fee_rate
       OR NEW.loan_reference IS DISTINCT FROM OLD.loan_reference
       OR NEW.reason IS DISTINCT FROM OLD.reason
       OR NEW.evidence_reference IS DISTINCT FROM OLD.evidence_reference
       OR NEW.created_at IS DISTINCT FROM OLD.created_at
       OR (OLD.status = 'PENDING_REVIEW' AND (NEW.reviewer_id = OLD.requester_id
           OR NEW.reviewer_id IS NULL OR NEW.reviewed_at IS NULL
           OR LENGTH(TRIM(COALESCE(NEW.reviewer_reason, ''))) < 20))
    THEN
        RAISE EXCEPTION 'Invalid principal amendment audit transition';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_guard_principal_amendment_audit
    BEFORE UPDATE OR DELETE ON loan_principal_amendment_requests
    FOR EACH ROW EXECUTE FUNCTION guard_principal_amendment_audit();
