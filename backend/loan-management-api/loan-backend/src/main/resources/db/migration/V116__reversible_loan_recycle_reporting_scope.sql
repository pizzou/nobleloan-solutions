-- V116: preserve per-row reporting scope during loan recycle and prepare user retention metadata.
-- Recycle is reversible: original scope is captured before entries are hidden from normal-scope reports.
ALTER TABLE public.loan_deletion_tombstones
    ADD COLUMN IF NOT EXISTS original_business_owner_only BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE public.journal_entries
    ADD COLUMN IF NOT EXISTS recycle_original_business_owner_only BOOLEAN NULL,
    ADD COLUMN IF NOT EXISTS recycle_loan_id BIGINT NULL;

ALTER TABLE public.payment_settlements
    ADD COLUMN IF NOT EXISTS recycle_original_business_owner_only BOOLEAN NULL,
    ADD COLUMN IF NOT EXISTS recycle_loan_id BIGINT NULL;

CREATE INDEX IF NOT EXISTS idx_journal_entries_recycle_scope
    ON public.journal_entries (organization_id, recycle_loan_id)
    WHERE recycle_loan_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_payment_settlements_recycle_scope
    ON public.payment_settlements (organization_id, recycle_loan_id)
    WHERE recycle_loan_id IS NOT NULL;

-- Existing recycled loans predate this migration and did not yet change their
-- reporting scope. Capture their current scope so a restore remains lossless.
UPDATE public.loan_deletion_tombstones t
SET original_business_owner_only = COALESCE(l.business_owner_only, FALSE)
FROM public.loans l
WHERE l.id = t.loan_id
  AND t.purged_at IS NULL;
