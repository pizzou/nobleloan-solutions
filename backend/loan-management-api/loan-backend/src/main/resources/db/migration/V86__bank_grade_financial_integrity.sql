-- ============================================================================
-- Noble Loan Solutions — V86 Bank-Grade Financial Integrity
-- ============================================================================
-- This migration does NOT alter historical values. It creates database-level
-- uniqueness and financial invariant guards so future writes cannot introduce
-- duplicate borrowers/loans or financially incoherent disbursed balances.
-- Existing duplicates are intentionally rejected instead of being silently
-- merged or deleted.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM loans
        WHERE reference_number IS NOT NULL
          AND btrim(reference_number) <> ''
        GROUP BY organization_id, lower(btrim(reference_number))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot create bank-grade loan reference uniqueness boundary: duplicate organization/reference rows exist';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM borrowers
        WHERE national_id_hash IS NOT NULL
          AND btrim(national_id_hash) <> ''
        GROUP BY organization_id, lower(btrim(national_id_hash))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot create bank-grade borrower uniqueness boundary: duplicate organization/national_id_hash rows exist';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_loans_org_reference_bank_grade
    ON loans (organization_id, lower(btrim(reference_number)))
    WHERE reference_number IS NOT NULL AND btrim(reference_number) <> '';

CREATE UNIQUE INDEX IF NOT EXISTS uq_borrowers_org_national_id_hash_bank_grade
    ON borrowers (organization_id, lower(btrim(national_id_hash)))
    WHERE national_id_hash IS NOT NULL AND btrim(national_id_hash) <> '';

CREATE INDEX IF NOT EXISTS idx_loans_org_import_batch
    ON loans (organization_id, import_batch_id);

-- --------------------------------------------------------------------------
-- Financial invariant guard
-- --------------------------------------------------------------------------
-- Approval/pipeline rows are intentionally excluded because the existing
-- application workflow can carry provisional schedule balances before actual
-- disbursement. Imported historical loans and financially originated loans are
-- protected.

CREATE OR REPLACE FUNCTION enforce_loan_financial_invariants()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    financially_originated BOOLEAN;
    tolerance NUMERIC := 0.01;
BEGIN
    financially_originated :=
        COALESCE(NEW.imported, FALSE)
        OR NEW.import_batch_id IS NOT NULL
        OR NEW.status IN (
            'DISBURSED', 'ACTIVE', 'OVERDUE', 'DEFAULTED',
            'RESTRUCTURED', 'WRITTEN_OFF', 'PAID', 'CLOSED'
        );

    IF NOT financially_originated THEN
        RETURN NEW;
    END IF;

    IF COALESCE(NEW.amount, 0) < 0
       OR COALESCE(NEW.principal_paid, 0) < 0
       OR COALESCE(NEW.outstanding_balance, 0) < 0 THEN
        RAISE EXCEPTION
            'Loan % has negative principal financial values', NEW.id;
    END IF;

    IF ABS(
        ROUND(
            COALESCE(NEW.principal_paid, 0)
            + COALESCE(NEW.outstanding_balance, 0)
            - COALESCE(NEW.amount, 0),
            2
        )
    ) >= tolerance THEN
        RAISE EXCEPTION
            'Loan % principal reconciliation failed: paid=% outstanding=% amount=%',
            NEW.id,
            COALESCE(NEW.principal_paid, 0),
            COALESCE(NEW.outstanding_balance, 0),
            COALESCE(NEW.amount, 0);
    END IF;

    IF COALESCE(NEW.total_interest, 0) < 0
       OR COALESCE(NEW.interest_paid, 0) < 0
       OR COALESCE(NEW.interest_outstanding, 0) < 0 THEN
        RAISE EXCEPTION
            'Loan % has negative interest financial values', NEW.id;
    END IF;

    IF ABS(
        ROUND(
            COALESCE(NEW.interest_paid, 0)
            + COALESCE(NEW.interest_outstanding, 0)
            - COALESCE(NEW.total_interest, 0),
            2
        )
    ) >= tolerance THEN
        RAISE EXCEPTION
            'Loan % interest reconciliation failed: paid=% outstanding=% total=%',
            NEW.id,
            COALESCE(NEW.interest_paid, 0),
            COALESCE(NEW.interest_outstanding, 0),
            COALESCE(NEW.total_interest, 0);
    END IF;

    IF COALESCE(NEW.management_fee, 0) < 0
       OR COALESCE(NEW.management_fee_paid, 0) < 0
       OR COALESCE(NEW.management_fee_outstanding, 0) < 0 THEN
        RAISE EXCEPTION
            'Loan % has negative management-fee financial values', NEW.id;
    END IF;

    IF ABS(
        ROUND(
            COALESCE(NEW.management_fee_paid, 0)
            + COALESCE(NEW.management_fee_outstanding, 0)
            - COALESCE(NEW.management_fee, 0),
            2
        )
    ) >= tolerance THEN
        RAISE EXCEPTION
            'Loan % management-fee reconciliation failed: paid=% outstanding=% total=%',
            NEW.id,
            COALESCE(NEW.management_fee_paid, 0),
            COALESCE(NEW.management_fee_outstanding, 0),
            COALESCE(NEW.management_fee, 0);
    END IF;

    IF COALESCE(NEW.application_fee, 0) < 0
       OR COALESCE(NEW.application_fee_paid, 0) < 0 THEN
        RAISE EXCEPTION
            'Loan % has negative application-fee financial values', NEW.id;
    END IF;

    -- application_fee_outstanding is intentionally derived as
    -- application_fee - application_fee_paid in the current domain model.
    IF COALESCE(NEW.application_fee_paid, 0)
       - COALESCE(NEW.application_fee, 0) > tolerance THEN
        RAISE EXCEPTION
            'Loan % application/processing fee reconciliation failed: paid=% fee=%',
            NEW.id,
            COALESCE(NEW.application_fee_paid, 0),
            COALESCE(NEW.application_fee, 0);
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_loan_financial_invariants ON loans;

CREATE CONSTRAINT TRIGGER trg_loan_financial_invariants
AFTER INSERT OR UPDATE OF
    amount,
    principal_paid,
    outstanding_balance,
    total_interest,
    interest_paid,
    interest_outstanding,
    management_fee,
    management_fee_paid,
    management_fee_outstanding,
    application_fee,
    application_fee_paid,
    imported,
    import_batch_id,
    status
ON loans
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION enforce_loan_financial_invariants();
