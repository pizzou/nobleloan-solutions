-- V118: repair missing recycle tombstones using a forward-only migration.
-- Do not edit V116: it may already be applied in production and changing it would
-- cause a Flyway checksum mismatch. This migration changes metadata only.

ALTER TABLE public.loan_deletion_tombstones
    ADD COLUMN IF NOT EXISTS original_business_owner_only BOOLEAN NOT NULL DEFAULT FALSE;

-- V116 captures each related journal/settlement row's prior visibility in recycle_*
-- columns. Reconstruct the tombstone from that marker when available. If no marker
-- exists, keep the current loan scope (rather than broadening access on restore).
INSERT INTO public.loan_deletion_tombstones
    (organization_id, loan_id, reference_number, deleted_at, original_business_owner_only)
SELECT l.organization_id,
       l.id,
       COALESCE(NULLIF(BTRIM(l.reference_number), ''), 'LOAN-' || l.id::text),
       l.deleted_at,
       COALESCE((
           SELECT BOOL_OR(je.recycle_original_business_owner_only)
           FROM public.journal_entries je
           WHERE je.organization_id = l.organization_id
             AND je.recycle_loan_id = l.id
       ), l.business_owner_only, FALSE)
FROM public.loans l
WHERE l.deleted_at IS NOT NULL
ON CONFLICT (organization_id, loan_id) DO NOTHING;
