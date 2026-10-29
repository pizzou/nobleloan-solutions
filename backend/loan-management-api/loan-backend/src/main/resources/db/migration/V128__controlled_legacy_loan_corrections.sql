-- Controlled historical loan corrections. No loan/accounting history is overwritten.
CREATE TABLE IF NOT EXISTS public.legacy_loan_corrections (
 id BIGSERIAL PRIMARY KEY,
 organization_id BIGINT NOT NULL REFERENCES public.organizations(id),
 loan_id BIGINT NOT NULL REFERENCES public.loans(id),
 maker_id BIGINT NOT NULL REFERENCES public.app_users(id),
 checker_id BIGINT REFERENCES public.app_users(id),
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
   CHECK (status IN ('PENDING','APPROVED','REJECTED')),
 reason VARCHAR(2000) NOT NULL,
 evidence_reference VARCHAR(250) NOT NULL,
 before_snapshot TEXT NOT NULL,
 after_snapshot TEXT NOT NULL,
 snapshot_hash VARCHAR(64) NOT NULL,
 review_reason VARCHAR(2000),
 journal_entry_id BIGINT REFERENCES public.journal_entries(id),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 reviewed_at TIMESTAMP,
 version BIGINT
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_pending_legacy_correction_per_loan
 ON public.legacy_loan_corrections(organization_id,loan_id) WHERE status='PENDING';
CREATE INDEX IF NOT EXISTS idx_legacy_correction_review
 ON public.legacy_loan_corrections(organization_id,status,created_at DESC);

-- A posted or rejected request is immutable; the first review is the only permitted
-- change. Audit snapshots, maker and evidence cannot be rewritten by application SQL.
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
     OR NEW.checker_id IS NULL
     OR NEW.checker_id = OLD.maker_id
     OR NEW.reviewed_at IS NULL
     OR length(trim(coalesce(NEW.review_reason,''))) < 20
     OR (NEW.status = 'REJECTED' AND NEW.journal_entry_id IS NOT NULL) THEN
    RAISE EXCEPTION 'Illegal legacy correction audit amendment';
  END IF;
  RETURN NEW;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_guard_legacy_correction_immutability') THEN
    CREATE TRIGGER trg_guard_legacy_correction_immutability
    BEFORE UPDATE OR DELETE ON public.legacy_loan_corrections
    FOR EACH ROW EXECUTE FUNCTION public.guard_legacy_correction_immutability();
  END IF;
END $$;
