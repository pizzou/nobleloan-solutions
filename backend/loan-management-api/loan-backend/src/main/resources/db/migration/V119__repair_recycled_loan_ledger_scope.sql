-- V119: repair reporting scope for legacy recycled loans without hiding or deleting ledger evidence.
-- Forward-only: V116/V118 may already have been applied in production.
-- Posted journal rows and settlements remain retained; only their user-facing visibility metadata changes.

-- Reconstruct original scope conservatively. Existing row markers take precedence; if legacy
-- metadata is missing, retain owner-only visibility rather than risk exposing private loan data.
UPDATE public.loan_deletion_tombstones t
SET original_business_owner_only = CASE
    WHEN EXISTS (
        SELECT 1
        FROM public.journal_entries je
        WHERE je.organization_id = t.organization_id
          AND je.recycle_loan_id = t.loan_id
          AND je.recycle_original_business_owner_only IS NOT NULL
    ) THEN COALESCE((
        SELECT BOOL_OR(je.recycle_original_business_owner_only)
        FROM public.journal_entries je
        WHERE je.organization_id = t.organization_id
          AND je.recycle_loan_id = t.loan_id
          AND je.recycle_original_business_owner_only IS NOT NULL
    ), FALSE)
    ELSE (
        COALESCE((
            SELECT l.business_owner_only
            FROM public.loans l
            WHERE l.id = t.loan_id
              AND l.organization_id = t.organization_id
        ), FALSE)
        OR COALESCE(t.original_business_owner_only, FALSE)
    )
END;

WITH recycle_targets AS (
    SELECT t.organization_id,
           t.loan_id,
           t.reference_number,
           COALESCE(t.original_business_owner_only, FALSE) AS original_business_owner_only
    FROM public.loan_deletion_tombstones t
), related_entries AS (
    SELECT DISTINCT ON (je.id)
           je.id AS journal_entry_id,
           t.organization_id,
           t.loan_id,
           t.original_business_owner_only
    FROM public.journal_entries je
    JOIN recycle_targets t
      ON t.organization_id = je.organization_id
    WHERE
        je.reference = t.reference_number
        OR (
            je.source_type IN (
                'LOAN_DISBURSEMENT', 'LOAN_PAYMENT', 'LOAN_EXTENSION_FEE',
                'LOAN_EXTENSION_FEE_COLLECTION', 'PENALTY_ACCRUAL',
                'INTEREST_ACCRUAL', 'MANAGEMENT_FEE_ACCRUAL',
                'SCHEDULED_INTEREST_ACCRUAL', 'SCHEDULED_MANAGEMENT_FEE_ACCRUAL',
                'CONTRACTUAL_MONTHLY_INTEREST_ACCRUAL',
                'CONTRACTUAL_MONTHLY_MANAGEMENT_FEE_ACCRUAL',
                'HISTORICAL_LOAN_OPENING', 'LEGACY_LOAN_OPENING',
                'LEGACY_LOAN_RECONCILIATION', 'LEGACY_LOAN_OPENING_DATE_REPAIR',
                'WRITE_OFF', 'APPLICATION_FEE_INCOME_RECLASSIFICATION'
            )
            AND regexp_replace(trim(COALESCE(je.source_id, '')), '^LOAN:', '')
                ~ ('^' || t.loan_id::text || '($|[-:])')
        )
        OR (
            je.source_type IN (
                'PAYMENT_RECEIVED', 'REFUND_PAYMENT', 'PAYMENT_REFUND',
                'PAYMENT_REVERSAL', 'OVERPAYMENT_REFUND_PAYABLE'
            )
            AND je.source_id IN (
                SELECT p.id::text
                FROM public.payments p
                WHERE p.loan_id = t.loan_id
            )
        )
        OR (
            je.source_type IN ('SCHEDULED_INTEREST_ACCRUAL', 'SCHEDULED_MANAGEMENT_FEE_ACCRUAL')
            AND je.source_id IN (
                SELECT 'PAYMENT-' || p.id::text
                FROM public.payments p
                WHERE p.loan_id = t.loan_id
            )
        )
        OR (
            je.source_type = 'REVERSAL'
            AND je.source_id IN (
                SELECT original.id::text
                FROM public.journal_entries original
                WHERE original.organization_id = t.organization_id
                  AND (
                      original.reference = t.reference_number
                      OR (
                          original.source_type IN (
                              'LOAN_DISBURSEMENT', 'LOAN_PAYMENT', 'LOAN_EXTENSION_FEE',
                              'LOAN_EXTENSION_FEE_COLLECTION', 'PENALTY_ACCRUAL',
                              'INTEREST_ACCRUAL', 'MANAGEMENT_FEE_ACCRUAL',
                              'SCHEDULED_INTEREST_ACCRUAL', 'SCHEDULED_MANAGEMENT_FEE_ACCRUAL',
                              'CONTRACTUAL_MONTHLY_INTEREST_ACCRUAL',
                              'CONTRACTUAL_MONTHLY_MANAGEMENT_FEE_ACCRUAL',
                              'HISTORICAL_LOAN_OPENING', 'LEGACY_LOAN_OPENING',
                              'LEGACY_LOAN_RECONCILIATION', 'LEGACY_LOAN_OPENING_DATE_REPAIR',
                              'WRITE_OFF', 'APPLICATION_FEE_INCOME_RECLASSIFICATION'
                          )
                          AND regexp_replace(trim(COALESCE(original.source_id, '')), '^LOAN:', '')
                              ~ ('^' || t.loan_id::text || '($|[-:])')
                      )
                      OR original.source_id IN (
                          SELECT p.id::text
                          FROM public.payments p
                          WHERE p.loan_id = t.loan_id
                      )
                  )
            )
        )
    ORDER BY je.id, t.loan_id
)
UPDATE public.journal_entries je
SET recycle_original_business_owner_only = COALESCE(
        je.recycle_original_business_owner_only,
        (COALESCE(je.business_owner_only, FALSE) OR COALESCE(r.original_business_owner_only, FALSE)),
        FALSE
    ),
    recycle_loan_id = r.loan_id,
    business_owner_only = TRUE
FROM related_entries r
WHERE je.id = r.journal_entry_id
  AND je.organization_id = r.organization_id;

-- Apply the same owner-only visibility to settlement evidence linked to payments.
-- Settlement rows are retained even when operational payment/loan rows are purged.
WITH recycle_targets AS (
    SELECT t.organization_id,
           t.loan_id,
           COALESCE(t.original_business_owner_only, FALSE) AS original_business_owner_only
    FROM public.loan_deletion_tombstones t
), related_settlements AS (
    SELECT DISTINCT ON (ps.id)
           ps.id AS settlement_id,
           t.organization_id,
           t.loan_id,
           t.original_business_owner_only
    FROM public.payment_settlements ps
    JOIN recycle_targets t
      ON t.organization_id = ps.organization_id
    JOIN public.payments p
      ON p.loan_id = t.loan_id
     AND p.payment_reference = ps.internal_payment_reference
    ORDER BY ps.id, t.loan_id
)
UPDATE public.payment_settlements ps
SET recycle_original_business_owner_only = COALESCE(
        ps.recycle_original_business_owner_only,
        (COALESCE(ps.business_owner_only, FALSE) OR COALESCE(r.original_business_owner_only, FALSE)),
        FALSE
    ),
    recycle_loan_id = r.loan_id,
    business_owner_only = TRUE
FROM related_settlements r
WHERE ps.id = r.settlement_id
  AND ps.organization_id = r.organization_id;

CREATE INDEX IF NOT EXISTS idx_journal_entries_recycle_scope
    ON public.journal_entries (organization_id, recycle_loan_id)
    WHERE recycle_loan_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_payment_settlements_recycle_scope
    ON public.payment_settlements (organization_id, recycle_loan_id)
    WHERE recycle_loan_id IS NOT NULL;
