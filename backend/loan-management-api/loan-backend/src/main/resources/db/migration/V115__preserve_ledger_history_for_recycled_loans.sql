-- V113: preserve statutory/audit ledger history for recycled and purged loans.
-- A recycle-bin decision changes operational visibility only. It must never
-- remove a posted journal from the general ledger, P&L, balance sheet or BNR reports.
-- This migration is forward-only and deliberately restores previously hidden entries.


-- Restore visibility for entries hidden by the old recycle-bin implementation.
-- Journal entries remain immutable: only the legacy visibility marker is cleared.
UPDATE public.journal_entries
SET hidden_loan_id = NULL
WHERE hidden_loan_id IS NOT NULL;

-- Legacy report queries call this function. Keep it as a compatibility shim
-- returning FALSE so deployed query code cannot suppress financial history.
CREATE OR REPLACE FUNCTION public.loan_journal_is_recycled(
    p_organization_id BIGINT,
    p_source_type TEXT,
    p_source_id TEXT,
    p_reference TEXT
)
RETURNS BOOLEAN
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
AS $$
    SELECT FALSE;
$$;

COMMENT ON FUNCTION public.loan_journal_is_recycled(BIGINT, TEXT, TEXT, TEXT) IS
'Compatibility function. Always FALSE: operational loan recycle-bin state must not hide posted accounting journals or regulatory reporting history.';

COMMENT ON COLUMN public.journal_entries.hidden_loan_id IS
'Legacy field retained for schema compatibility. V113 clears existing values; new code must not use this field to exclude accounting records.';

