-- One-time, non-reusable email challenge for existing borrower KYC reuse.
CREATE TABLE IF NOT EXISTS borrower_document_reuse_challenges (
    id UUID PRIMARY KEY,
    borrower_id BIGINT NOT NULL REFERENCES borrowers(id),
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    otp_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    verified_at TIMESTAMP,
    proof_hash VARCHAR(64),
    proof_expires_at TIMESTAMP,
    consumed_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_borrower_reuse_borrower_created
    ON borrower_document_reuse_challenges(borrower_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_borrower_reuse_expiration
    ON borrower_document_reuse_challenges(expires_at);
