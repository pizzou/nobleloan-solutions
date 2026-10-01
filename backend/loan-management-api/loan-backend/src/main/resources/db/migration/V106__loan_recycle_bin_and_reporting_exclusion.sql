-- V106: controlled loan recycle bin, 30-day restore window, permanent
-- operational purge, and immutable accounting/reporting visibility markers.

ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deletion_reason TEXT,
    ADD COLUMN IF NOT EXISTS deleted_by BIGINT,
    ADD COLUMN IF NOT EXISTS purge_after TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_loans_recycle_bin
    ON loans (organization_id, deleted_at, purge_after);

ALTER TABLE journal_entries
    ADD COLUMN IF NOT EXISTS hidden_loan_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_journal_entries_hidden_loan
    ON journal_entries (organization_id, hidden_loan_id);

CREATE TABLE IF NOT EXISTS loan_deletion_tombstones (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    loan_id BIGINT NOT NULL,
    reference_number VARCHAR(255) NOT NULL,
    deleted_at TIMESTAMP NOT NULL,
    purged_at TIMESTAMP,
    CONSTRAINT uq_loan_deletion_tombstone_org_loan
        UNIQUE (organization_id, loan_id)
);

CREATE INDEX IF NOT EXISTS idx_loan_deletion_tombstones_reference
    ON loan_deletion_tombstones (organization_id, reference_number);

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
            SELECT 1
            FROM loans l
            WHERE l.id = v_loan_id
              AND l.organization_id = p_organization_id
              AND l.deleted_at IS NOT NULL
        ) OR EXISTS (
            SELECT 1
            FROM loan_deletion_tombstones t
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
            SELECT 1
            FROM loans l
            WHERE l.organization_id = p_organization_id
              AND l.reference_number = btrim(p_reference)
              AND l.deleted_at IS NOT NULL
        ) OR EXISTS (
            SELECT 1
            FROM loan_deletion_tombstones t
            WHERE t.organization_id = p_organization_id
              AND t.reference_number = btrim(p_reference)
        ) THEN
            RETURN TRUE;
        END IF;
    END IF;

    RETURN FALSE;
END;
$$;

COMMENT ON TABLE loan_deletion_tombstones IS
'Permanent reporting/accounting tombstones for recycled loans. Immutable journals remain retained but hidden from current reporting.';

COMMENT ON COLUMN loans.deleted_at IS
'Recycle-bin timestamp. NULL means normal operational visibility.';

COMMENT ON COLUMN loans.purge_after IS
'Automatic permanent operational purge time. The controlled workflow uses 30 days.';

COMMENT ON COLUMN journal_entries.hidden_loan_id IS
'Loan recycle-bin marker. Non-null entries are excluded from current accounting/reporting but retained as immutable history.';
