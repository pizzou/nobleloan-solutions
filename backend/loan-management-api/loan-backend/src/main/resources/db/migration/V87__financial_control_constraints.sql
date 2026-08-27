-- V86: enforce core loan financial invariants for all new/updated rows.
-- Existing historical data is not rewritten by this migration. The
-- reconciliation service remains the authoritative detector for legacy data.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_loan_principal_reconciliation'
    ) THEN
        ALTER TABLE loans
            ADD CONSTRAINT ck_loan_principal_reconciliation
            CHECK (
                amount IS NOT NULL
                AND principal_paid IS NOT NULL
                AND outstanding_balance IS NOT NULL
                AND round((principal_paid + outstanding_balance - amount)::numeric, 2) = 0
                AND principal_paid >= 0
                AND outstanding_balance >= 0
            ) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_loan_interest_reconciliation'
    ) THEN
        ALTER TABLE loans
            ADD CONSTRAINT ck_loan_interest_reconciliation
            CHECK (
                total_interest IS NOT NULL
                AND interest_paid IS NOT NULL
                AND interest_outstanding IS NOT NULL
                AND round((interest_paid + interest_outstanding - total_interest)::numeric, 2) = 0
                AND interest_paid >= 0
                AND interest_outstanding >= 0
                AND total_interest >= 0
            ) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_loan_management_fee_reconciliation'
    ) THEN
        ALTER TABLE loans
            ADD CONSTRAINT ck_loan_management_fee_reconciliation
            CHECK (
                management_fee IS NOT NULL
                AND management_fee_paid IS NOT NULL
                AND management_fee_outstanding IS NOT NULL
                AND round((management_fee_paid + management_fee_outstanding - management_fee)::numeric, 2) = 0
                AND management_fee_paid >= 0
                AND management_fee_outstanding >= 0
                AND management_fee >= 0
            ) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_loan_application_fee_reconciliation'
    ) THEN
        ALTER TABLE loans
            ADD CONSTRAINT ck_loan_application_fee_reconciliation
            CHECK (
                application_fee IS NOT NULL
                AND application_fee_paid IS NOT NULL
                AND application_fee >= 0
                AND application_fee_paid >= 0
                AND application_fee_paid <= application_fee
            ) NOT VALID;
    END IF;
END $$;

-- Deterministic duplicate protection for encrypted borrower identifiers.
-- Partial indexes keep legacy rows with missing hashes importable while making
-- every newly indexed identifier unique inside the organization.
CREATE UNIQUE INDEX IF NOT EXISTS uq_borrower_org_national_id_hash
    ON borrowers (organization_id, national_id_hash)
    WHERE national_id_hash IS NOT NULL AND btrim(national_id_hash) <> '';

