-- ============================================================================
-- Noble Loan Solutions — V105 Loan write-off financial invariant correction
-- ============================================================================
--
-- The principal reconciliation invariant is correct for loans whose principal
-- is still an outstanding/paid balance:
--
--     principal_paid + outstanding_balance = amount
--
-- A WRITE_OFF is different: accounting removes the written-off principal from
-- the collectible outstanding balance without pretending that principal was
-- paid by the borrower. Therefore a written-off loan must have:
--
--     outstanding_balance = 0
--     0 <= principal_paid <= amount
--
-- This migration does NOT rewrite historical loan values. The constraint is
-- intentionally NOT VALID so legacy rows are not silently modified, while all
-- future inserts/updates are protected.
--
-- V105 also repairs the V86 database trigger, because a CHECK constraint alone
-- is insufficient: the deferred financial trigger would otherwise reject the
-- same legitimate WRITE_OFF state at transaction commit.
-- ============================================================================

DROP FUNCTION IF EXISTS enforce_loan_financial_invariants() CASCADE;

CREATE FUNCTION enforce_loan_financial_invariants()
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

    IF NEW.status = 'WRITTEN_OFF' THEN
        IF ABS(COALESCE(NEW.outstanding_balance, 0)) >= tolerance THEN
            RAISE EXCEPTION
                'Loan % written-off principal must have zero outstanding balance: outstanding=%',
                NEW.id, COALESCE(NEW.outstanding_balance, 0);
        END IF;

        IF COALESCE(NEW.principal_paid, 0)
           - COALESCE(NEW.amount, 0) > tolerance THEN
            RAISE EXCEPTION
                'Loan % written-off principal paid cannot exceed amount: paid=% amount=%',
                NEW.id, COALESCE(NEW.principal_paid, 0), COALESCE(NEW.amount, 0);
        END IF;
    ELSE
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

ALTER TABLE loans
    DROP CONSTRAINT IF EXISTS ck_loan_principal_reconciliation;

ALTER TABLE loans
    ADD CONSTRAINT ck_loan_principal_reconciliation
    CHECK (
        amount IS NOT NULL
        AND principal_paid IS NOT NULL
        AND outstanding_balance IS NOT NULL
        AND amount >= 0
        AND principal_paid >= 0
        AND outstanding_balance >= 0
        AND (
            (
                status = 'WRITTEN_OFF'
                AND round(outstanding_balance::numeric, 2) = 0
                AND round((principal_paid - amount)::numeric, 2) <= 0
            )
            OR
            (
                status <> 'WRITTEN_OFF'
                AND round((principal_paid + outstanding_balance - amount)::numeric, 2) = 0
            )
        )
    ) NOT VALID;
