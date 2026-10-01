-- V108: idempotent safety migration for recycle-bin visibility.
-- This migration is intentionally independent of V107 so an environment
-- that has already recorded V106 but missed V107 is repaired automatically.

CREATE OR REPLACE FUNCTION public.loan_is_visible(
    p_loan_id BIGINT
)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM loans l
        WHERE l.id = p_loan_id
          AND l.deleted_at IS NULL
    );
$$;

COMMENT ON FUNCTION public.loan_is_visible(BIGINT) IS
'Returns true when a referenced loan remains operationally visible. Recycled loans remain stored during the 30-day recovery window but are excluded from normal loan-child queries.';

-- Keep the accounting visibility function available as well. V106 normally
-- creates it; CREATE OR REPLACE makes this migration safe on environments
-- where the function already exists.
CREATE OR REPLACE FUNCTION public.loan_journal_is_recycled(
    p_organization_id BIGINT,
    p_source_type TEXT,
    p_source_id TEXT,
    p_reference TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_source_id TEXT;
    v_loan_id BIGINT;
BEGIN
    IF p_organization_id IS NULL THEN
        RETURN FALSE;
    END IF;

    v_source_id := regexp_replace(
        trim(coalesce(p_source_id, '')),
        '^LOAN:',
        ''
    );

    IF v_source_id ~ '^[0-9]+$'
       AND upper(trim(coalesce(p_source_type, ''))) IN (
            'LOAN_DISBURSEMENT',
            'LOAN_PAYMENT',
            'LOAN_EXTENSION_FEE',
            'LOAN_EXTENSION_FEE_COLLECTION',
            'PENALTY_ACCRUAL',
            'INTEREST_ACCRUAL',
            'MANAGEMENT_FEE_ACCRUAL',
            'SCHEDULED_INTEREST_ACCRUAL',
            'SCHEDULED_MANAGEMENT_FEE_ACCRUAL',
            'CONTRACTUAL_MONTHLY_INTEREST_ACCRUAL',
            'CONTRACTUAL_MONTHLY_MANAGEMENT_FEE_ACCRUAL',
            'HISTORICAL_LOAN_OPENING',
            'LEGACY_LOAN_OPENING',
            'LEGACY_LOAN_RECONCILIATION',
            'LEGACY_LOAN_OPENING_DATE_REPAIR',
            'WRITE_OFF'
       ) THEN
        v_loan_id := v_source_id::BIGINT;

        IF EXISTS (
            SELECT 1 FROM loans l
            WHERE l.id = v_loan_id
              AND l.organization_id = p_organization_id
              AND l.deleted_at IS NOT NULL
        ) OR EXISTS (
            SELECT 1 FROM loan_deletion_tombstones t
            WHERE t.loan_id = v_loan_id
              AND t.organization_id = p_organization_id
        ) THEN
            RETURN TRUE;
        END IF;
    END IF;

    IF v_source_id ~ '^[0-9]+$'
       AND upper(trim(coalesce(p_source_type, ''))) IN (
            'PAYMENT_RECEIVED',
            'REFUND_PAYMENT',
            'PAYMENT_REFUND',
            'PAYMENT_REVERSAL'
       ) THEN
        IF EXISTS (
            SELECT 1
            FROM payments p
            JOIN loans l ON l.id = p.loan_id
            WHERE p.id = v_source_id::BIGINT
              AND l.organization_id = p_organization_id
              AND l.deleted_at IS NOT NULL
        ) THEN
            RETURN TRUE;
        END IF;
    END IF;

    IF p_reference IS NOT NULL AND btrim(p_reference) <> '' THEN
        IF EXISTS (
            SELECT 1 FROM loans l
            WHERE l.organization_id = p_organization_id
              AND l.reference_number = btrim(p_reference)
              AND l.deleted_at IS NOT NULL
        ) OR EXISTS (
            SELECT 1 FROM loan_deletion_tombstones t
            WHERE t.organization_id = p_organization_id
              AND t.reference_number = btrim(p_reference)
        ) THEN
            RETURN TRUE;
        END IF;
    END IF;

    RETURN FALSE;
END;
$$;
