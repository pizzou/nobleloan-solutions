-- V117: reversible 30-day user recycle bin with safe post-retention PII purge.
-- User rows are referenced by audit, loan approval, payment and notification history.
-- Therefore the retention job anonymizes personal data but retains the FK identity row.
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS purge_after TIMESTAMP NULL;
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS purged_at TIMESTAMP NULL;
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS deleted_by BIGINT NULL;
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS deletion_reason TEXT NULL;

CREATE INDEX IF NOT EXISTS idx_app_users_recycle_bin_org_deadline
    ON public.app_users (organization_id, purge_after)
    WHERE deleted_at IS NOT NULL AND purged_at IS NULL;

-- Existing suspended/inactive users are not automatically treated as deleted.
-- Only new explicit Business Owner deletion actions enter the recycle bin.
