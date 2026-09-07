-- Bank-grade session revocation support.
-- Existing sessions are preserved; password changes/reset and future security
-- events increment token_version and invalidate all previously issued JWTs.
ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS token_version BIGINT NOT NULL DEFAULT 0;

-- Keep the password-reset token column large enough for the SHA-256 representation
-- used by the application. Existing plaintext reset tokens become invalid after
-- the deployment, which is intentional and safer than trying to migrate live tokens.
ALTER TABLE password_reset_tokens
    ALTER COLUMN token TYPE VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_app_users_token_version
    ON app_users (id, token_version);
