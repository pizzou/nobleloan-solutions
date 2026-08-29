-- ============================================================================
-- V88: Durable financial reconciliation jobs
-- ============================================================================
-- Long-running accounting reconciliation must never depend on the lifetime of
-- an HTTP request. This table persists the control result, including whether
-- the post-repair financial reconciliation actually balanced.

CREATE TABLE IF NOT EXISTS financial_reconciliation_jobs (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    requested_by BIGINT NULL,
    status VARCHAR(32) NOT NULL,
    phase VARCHAR(64) NOT NULL,
    processed_loans INTEGER NOT NULL DEFAULT 0,
    journal_adjustments_created INTEGER NOT NULL DEFAULT 0,
    before_balanced BOOLEAN,
    after_balanced BOOLEAN,
    before_maximum_difference NUMERIC(19,2),
    after_maximum_difference NUMERIC(19,2),
    result_json TEXT,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    heartbeat_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_fin_recon_job_org
        FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_fin_recon_job_user
        FOREIGN KEY (requested_by) REFERENCES app_users(id)
);

CREATE INDEX IF NOT EXISTS idx_fin_recon_job_org_created
    ON financial_reconciliation_jobs (organization_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_fin_recon_job_status
    ON financial_reconciliation_jobs (status);

CREATE INDEX IF NOT EXISTS idx_fin_recon_job_heartbeat
    ON financial_reconciliation_jobs (heartbeat_at);

-- At most one queued/active reconciliation per organization.
-- Completed/failed history remains unlimited.
CREATE UNIQUE INDEX IF NOT EXISTS uq_fin_recon_job_active_org
    ON financial_reconciliation_jobs (organization_id)
    WHERE status IN ('QUEUED', 'PROCESSING', 'VERIFYING');
