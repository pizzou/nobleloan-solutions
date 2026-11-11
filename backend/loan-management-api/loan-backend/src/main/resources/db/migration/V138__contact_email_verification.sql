-- Public contact email verification, no SMS dependency.
CREATE TABLE IF NOT EXISTS public.contact_email_challenges (
    id UUID PRIMARY KEY,
    email VARCHAR(180) NOT NULL,
    ip_address VARCHAR(45),
    code_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_contact_email_challenges_email_created
    ON public.contact_email_challenges (email, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_contact_email_challenges_ip_created
    ON public.contact_email_challenges (ip_address, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_contact_email_challenges_expiry
    ON public.contact_email_challenges (expires_at);
