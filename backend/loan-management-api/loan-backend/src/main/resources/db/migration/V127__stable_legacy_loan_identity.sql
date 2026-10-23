-- V127: idempotent, stable identifiers for imported historical loans.
-- Does NOT renumber, delete or modify existing loan/accounting history.
-- Older imports with a NULL identity remain intact; application checks
-- existing records before accepting a new import.
ALTER TABLE public.loans ADD COLUMN IF NOT EXISTS legacy_loan_identity VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uq_loans_legacy_loan_identity_per_org
  ON public.loans (organization_id, legacy_loan_identity)
  WHERE legacy_loan_identity IS NOT NULL;
