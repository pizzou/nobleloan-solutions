CREATE TABLE IF NOT EXISTS webhook_receipts (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(40) NOT NULL,
    event_key VARCHAR(128) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP,
    CONSTRAINT uk_webhook_receipt_provider_key UNIQUE(provider,event_key),
    CONSTRAINT chk_webhook_receipt_status CHECK(status IN ('PROCESSING','PROCESSED','FAILED'))
);
CREATE INDEX IF NOT EXISTS idx_webhook_receipt_created ON webhook_receipts(created_at);

CREATE TABLE IF NOT EXISTS rate_limit_buckets (
    bucket_key VARCHAR(255) PRIMARY KEY,
    window_start BIGINT NOT NULL,
    request_count INTEGER NOT NULL
);
