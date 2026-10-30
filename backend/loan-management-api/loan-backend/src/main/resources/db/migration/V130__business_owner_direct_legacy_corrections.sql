-- Allows explicitly audited Business Owner direct corrections of untouched
-- imported opening positions. Never edit previously applied V128/V129.
ALTER TABLE public.legacy_loan_corrections
    ADD COLUMN IF NOT EXISTS owner_direct BOOLEAN NOT NULL DEFAULT FALSE;

-- Keep original maker/checker restrictions for normal requests. A direct
-- Business Owner record is born PENDING in the same DB transaction and is
-- reviewed by its creator only when owner_direct was set on INSERT.
CREATE OR REPLACE FUNCTION public.guard_legacy_correction_immutability()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'Legacy correction audit records cannot be deleted';
  END IF;
  IF OLD.status <> 'PENDING'
     OR NEW.status NOT IN ('APPROVED','REJECTED')
     OR NEW.organization_id IS DISTINCT FROM OLD.organization_id
     OR NEW.loan_id IS DISTINCT FROM OLD.loan_id
     OR NEW.maker_id IS DISTINCT FROM OLD.maker_id
     OR NEW.reason IS DISTINCT FROM OLD.reason
     OR NEW.evidence_reference IS DISTINCT FROM OLD.evidence_reference
     OR NEW.before_snapshot IS DISTINCT FROM OLD.before_snapshot
     OR NEW.after_snapshot IS DISTINCT FROM OLD.after_snapshot
     OR NEW.snapshot_hash IS DISTINCT FROM OLD.snapshot_hash
     OR NEW.created_at IS DISTINCT FROM OLD.created_at
     OR NEW.owner_direct IS DISTINCT FROM OLD.owner_direct
     OR NEW.checker_id IS NULL
     OR NEW.reviewed_at IS NULL
     OR length(trim(coalesce(NEW.review_reason,''))) < 20
     OR (NEW.status = 'REJECTED' AND NEW.journal_entry_id IS NOT NULL)
     OR (OLD.owner_direct = FALSE AND NEW.checker_id = OLD.maker_id)
     OR (OLD.owner_direct = TRUE
           AND (NEW.checker_id IS DISTINCT FROM OLD.maker_id OR NEW.status <> 'APPROVED'))
  THEN
    RAISE EXCEPTION 'Illegal legacy correction audit amendment';
  END IF;
  RETURN NEW;
END $$;

COMMENT ON COLUMN public.legacy_loan_corrections.owner_direct IS
'Explicit owner-performed correction of a verified untouched import; original maker/checker still applies when false.';
