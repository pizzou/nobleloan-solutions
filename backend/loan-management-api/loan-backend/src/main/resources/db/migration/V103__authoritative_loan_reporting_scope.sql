

CREATE OR REPLACE FUNCTION public.loan_journal_is_business_owner_only(
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
    v_loan_id BIGINT;
    v_payment_id BIGINT;
    v_source_id TEXT;
    v_original_id BIGINT;
BEGIN
    IF p_organization_id IS NULL OR p_source_type IS NULL THEN
        RETURN FALSE;
    END IF;

    -- A reversal inherits the confidentiality of the original journal.
    IF UPPER(TRIM(p_source_type)) = 'REVERSAL'
       AND COALESCE(p_source_id, '') ~ '^[0-9]+$' THEN
        v_original_id := p_source_id::BIGINT;

        RETURN EXISTS (
            SELECT 1
            FROM journal_entries original
            WHERE original.id = v_original_id
              AND original.organization_id = p_organization_id
              AND (
                    COALESCE(original.business_owner_only, FALSE) = TRUE
                    OR public.loan_journal_is_business_owner_only(
                        original.organization_id,
                        original.source_type,
                        original.source_id,
                        original.reference
                    ) = TRUE
              )
        );
    END IF;

    -- Loan-originated journal families. Their source IDs begin with the
    -- immutable loan ID, optionally prefixed with LOAN:.
    IF UPPER(TRIM(p_source_type)) IN (
        'LOAN_DISBURSEMENT',
        'LOAN_EXTENSION_FEE',
        'PENALTY_ACCRUAL',
        'CONTRACTUAL_MONTHLY_INTEREST_ACCRUAL',
        'CONTRACTUAL_MONTHLY_MANAGEMENT_FEE_ACCRUAL',
        'LEGACY_LOAN_OPENING',
        'LEGACY_LOAN_RECONCILIATION',
        'WRITE_OFF'
    ) THEN
        v_source_id := REGEXP_REPLACE(TRIM(COALESCE(p_source_id, '')), '^LOAN:', '');

        IF v_source_id ~ '^[0-9]+' THEN
            v_loan_id := (REGEXP_MATCH(v_source_id, '^([0-9]+)'))[1]::BIGINT;

            IF EXISTS (
                SELECT 1
                FROM loans l
                WHERE l.id = v_loan_id
                  AND l.organization_id = p_organization_id
                  AND COALESCE(l.business_owner_only, FALSE) = TRUE
            ) THEN
                RETURN TRUE;
            END IF;
        END IF;

        -- Reference is a compatibility fallback for historical entries whose
        -- source ID was not preserved in the newer LOAN:<id> format.
        IF p_reference IS NOT NULL AND BTRIM(p_reference) <> '' THEN
            IF EXISTS (
                SELECT 1
                FROM loans l
                WHERE l.organization_id = p_organization_id
                  AND COALESCE(l.business_owner_only, FALSE) = TRUE
                  AND l.reference_number = BTRIM(p_reference)
            ) THEN
                RETURN TRUE;
            END IF;
        END IF;

        RETURN FALSE;
    END IF;

    -- Payment-originated journal families. Resolve the payment first, then
    -- derive confidentiality from payment.loan, never from a copied payment
    -- flag.
    IF UPPER(TRIM(p_source_type)) IN (
        'PAYMENT_RECEIVED',
        'REFUND_PAYMENT',
        'OVERPAYMENT_REFUND_PAYABLE',
        'LOAN_EXTENSION_FEE_COLLECTION'
    ) THEN
        v_source_id := TRIM(COALESCE(p_source_id, ''));

        IF v_source_id ~ '^[0-9]+' THEN
            v_payment_id := (REGEXP_MATCH(v_source_id, '^([0-9]+)'))[1]::BIGINT;

            IF EXISTS (
                SELECT 1
                FROM payments p
                JOIN loans l ON l.id = p.loan_id
                WHERE p.id = v_payment_id
                  AND l.organization_id = p_organization_id
                  AND COALESCE(l.business_owner_only, FALSE) = TRUE
            ) THEN
                RETURN TRUE;
            END IF;
        END IF;

        RETURN FALSE;
    END IF;

    IF UPPER(TRIM(p_source_type)) IN (
        'SCHEDULED_INTEREST_ACCRUAL',
        'SCHEDULED_MANAGEMENT_FEE_ACCRUAL'
    ) THEN
        v_source_id := TRIM(COALESCE(p_source_id, ''));

        IF v_source_id ~ '^PAYMENT-[0-9]+' THEN
            v_payment_id := (REGEXP_MATCH(v_source_id, '^PAYMENT-([0-9]+)'))[1]::BIGINT;

            IF EXISTS (
                SELECT 1
                FROM payments p
                JOIN loans l ON l.id = p.loan_id
                WHERE p.id = v_payment_id
                  AND l.organization_id = p_organization_id
                  AND COALESCE(l.business_owner_only, FALSE) = TRUE
            ) THEN
                RETURN TRUE;
            END IF;
        END IF;

        RETURN FALSE;
    END IF;

    RETURN FALSE;
END;
$$;

COMMENT ON FUNCTION public.loan_journal_is_business_owner_only(BIGINT, TEXT, TEXT, TEXT)
IS 'Authoritative reporting boundary: resolves journal confidentiality from the originating loan, including payment-originated journals and reversals.';

-- Supporting indexes for the resolver and the reporting predicates.
CREATE INDEX IF NOT EXISTS idx_loans_org_reference_business_owner
    ON loans (organization_id, reference_number, business_owner_only);

CREATE INDEX IF NOT EXISTS idx_payments_loan_id
    ON payments (loan_id);
