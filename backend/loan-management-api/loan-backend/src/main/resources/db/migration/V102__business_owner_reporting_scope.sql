-- ============================================================================
-- V102 - BUSINESS OWNER REPORTING SCOPE
-- ============================================================================
--
-- One accounting system, two reporting scopes:
--
-- NORMAL_SCOPE
--   Ordinary loans and their accounting entries.
--
-- BUSINESS_OWNER_SCOPE
--   Ordinary loans + explicitly classified BUSINESS_OWNER_ONLY loans.
--
-- This migration is intentionally V102 because the source project already
-- contains V100 and V101. Do not rename this migration to an earlier version.
--
-- Existing loans remain NORMAL_SCOPE unless explicitly classified later.
-- Existing journal entries are backfilled from the originating loan where the
-- relationship can be determined safely.
-- ============================================================================

ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS business_owner_only BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE journal_entries
    ADD COLUMN IF NOT EXISTS business_owner_only BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE payment_settlements
    ADD COLUMN IF NOT EXISTS business_owner_only BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_loans_org_business_owner_scope
    ON loans (organization_id, business_owner_only, status);

CREATE INDEX IF NOT EXISTS idx_journal_entries_org_business_owner_scope
    ON journal_entries (organization_id, business_owner_only, entry_date);

CREATE INDEX IF NOT EXISTS idx_payment_settlements_org_business_owner_scope
    ON payment_settlements (organization_id, business_owner_only, settlement_date);

-- ============================================================================
-- PAYMENT SETTLEMENT SCOPE
-- ============================================================================
UPDATE payment_settlements ps
SET business_owner_only = TRUE
FROM payments p
JOIN loans l ON l.id = p.loan_id
WHERE ps.internal_payment_reference = p.payment_reference
  AND ps.organization_id = l.organization_id
  AND l.business_owner_only = TRUE;

-- ============================================================================
-- ROLE

INSERT INTO roles (name, description)
VALUES (
    'BUSINESS_OWNER',
    'Business owner — super permission with complete financial and business-performance visibility'
)
ON CONFLICT (name) DO UPDATE
SET description = EXCLUDED.description;

-- ============================================================================
-- BACKFILL LOAN-ORIGINATED JOURNAL ENTRIES
-- ============================================================================
--
-- Only entries whose source can be tied to a specific loan are classified.
-- General/manual organization accounting remains NORMAL_SCOPE.
--
-- Loan source IDs in the current accounting implementation use either:
--   loanId
--   loanId-period
--   loanId-date
--   PAYMENT-paymentId for installment accruals
--   paymentId-EXTENSION-FEE-COLLECTION
--   paymentId for PAYMENT_RECEIVED
-- ============================================================================

UPDATE journal_entries je
SET business_owner_only = TRUE
FROM loans l
WHERE je.organization_id = l.organization_id
  AND l.business_owner_only = TRUE
  AND (
        (
            je.source_type IN (
                'LOAN_DISBURSEMENT',
                'LOAN_EXTENSION_FEE',
                'PENALTY_ACCRUAL',
                'CONTRACTUAL_MONTHLY_INTEREST_ACCRUAL',
                'CONTRACTUAL_MONTHLY_MANAGEMENT_FEE_ACCRUAL',
                'LEGACY_LOAN_OPENING',
                'LEGACY_LOAN_RECONCILIATION',
                'WRITE_OFF'
            )
            AND (
                je.source_id = l.id::text
                OR je.source_id = ('LOAN:' || l.id::text)
                OR je.source_id LIKE ('LOAN:' || l.id::text || ':%')
                OR je.source_id LIKE (l.id::text || '-%')
            )
        )
        OR
        (
            je.source_type IN (
                'PAYMENT_RECEIVED',
                'REFUND_PAYMENT',
                'OVERPAYMENT_REFUND_PAYABLE',
                'LOAN_EXTENSION_FEE_COLLECTION'
            )
            AND EXISTS (
                SELECT 1
                FROM payments p
                WHERE p.id::text = je.source_id
                  AND p.loan_id = l.id
            )
        )
        OR
        (
            je.source_type IN (
                'SCHEDULED_INTEREST_ACCRUAL',
                'SCHEDULED_MANAGEMENT_FEE_ACCRUAL'
            )
            AND je.source_id LIKE 'PAYMENT-%'
            AND substring(je.source_id FROM 9) ~ '^[0-9]+$'
            AND EXISTS (
                SELECT 1
                FROM payments p
                WHERE p.id = substring(je.source_id FROM 9)::bigint
                  AND p.loan_id = l.id
            )
        )
        OR
        (
            je.source_type = 'LOAN_EXTENSION_FEE_COLLECTION'
            AND je.source_id ~ '^[0-9]+-EXTENSION-FEE-COLLECTION$'
            AND EXISTS (
                SELECT 1
                FROM payments p
                WHERE p.id = split_part(je.source_id, '-', 1)::bigint
                  AND p.loan_id = l.id
            )
        )
    );

-- ============================================================================
-- REVERSALS INHERIT THE ORIGINAL ENTRY'S REPORTING SCOPE
-- ============================================================================

UPDATE journal_entries reversal
SET business_owner_only = original.business_owner_only
FROM journal_entries original
WHERE reversal.source_type = 'REVERSAL'
  AND reversal.source_id = original.id::text
  AND reversal.organization_id = original.organization_id;
