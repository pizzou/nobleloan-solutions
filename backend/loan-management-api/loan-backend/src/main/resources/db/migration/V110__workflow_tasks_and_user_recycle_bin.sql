CREATE TABLE IF NOT EXISTS workflow_tasks (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    assigned_to_id BIGINT NOT NULL REFERENCES app_users(id),
    task_key VARCHAR(180) NOT NULL,
    task_type VARCHAR(80) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    link VARCHAR(255),
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    reference_type VARCHAR(80),
    reference_id BIGINT,
    due_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    completed_by BIGINT,
    email_sent_at TIMESTAMP,
    email_attempts INTEGER NOT NULL DEFAULT 0,
    last_email_attempt_at TIMESTAMP,
    last_email_error TEXT,
    reminder_count INTEGER NOT NULL DEFAULT 0,
    last_reminder_at TIMESTAMP,
    CONSTRAINT uq_workflow_task_assignee_key
        UNIQUE (organization_id, assigned_to_id, task_key)
);

CREATE INDEX IF NOT EXISTS idx_workflow_tasks_assignee_status
    ON workflow_tasks (assigned_to_id, status, due_at);

CREATE INDEX IF NOT EXISTS idx_workflow_tasks_email_pending
    ON workflow_tasks (status, email_sent_at, created_at);

CREATE INDEX IF NOT EXISTS idx_workflow_tasks_reference
    ON workflow_tasks (organization_id, reference_type, reference_id);

ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deletion_reason TEXT,
    ADD COLUMN IF NOT EXISTS deleted_by BIGINT,
    ADD COLUMN IF NOT EXISTS purge_after TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_app_users_recycle_bin
    ON app_users (organization_id, deleted_at, purge_after);

COMMENT ON TABLE workflow_tasks IS
'Durable human-work tasks. Financial actions use this table as an operational reminder, not as the accounting source of truth.';

COMMENT ON COLUMN app_users.deleted_at IS
'Business-owner recycle/archive timestamp. NULL means the account is not in the user recycle bin.';

COMMENT ON COLUMN app_users.purge_after IS
'End of the 30-day user restore window. The identity remains archived after this timestamp for audit integrity.';
