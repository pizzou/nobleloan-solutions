-- V110: bank-grade staff workflow tasks and reversible user recycle-bin metadata.

ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deletion_reason TEXT,
    ADD COLUMN IF NOT EXISTS deleted_by BIGINT,
    ADD COLUMN IF NOT EXISTS purge_after TIMESTAMP,
    ADD COLUMN IF NOT EXISTS permanently_retired_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_app_users_recycle_bin
    ON app_users (organization_id, deleted_at, purge_after);

CREATE INDEX IF NOT EXISTS idx_app_users_active_role
    ON app_users (organization_id, role_id, status, deleted_at);

CREATE TABLE IF NOT EXISTS staff_tasks (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    assigned_user_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL,
    assigned_role VARCHAR(64),
    task_type VARCHAR(100) NOT NULL,
    entity_type VARCHAR(64),
    entity_id BIGINT,
    reference VARCHAR(255),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    priority VARCHAR(32) NOT NULL DEFAULT 'HIGH',
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    due_at TIMESTAMP,
    claimed_at TIMESTAMP,
    completed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    last_reminder_at TIMESTAMP,
    reminder_count INTEGER NOT NULL DEFAULT 0,
    created_by BIGINT REFERENCES app_users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT ck_staff_tasks_priority
        CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),

    CONSTRAINT ck_staff_tasks_status
        CHECK (status IN ('OPEN','CLAIMED','COMPLETED','CANCELLED','EXPIRED'))
);

CREATE INDEX IF NOT EXISTS idx_staff_tasks_user_status
    ON staff_tasks (organization_id, assigned_user_id, status, due_at);

CREATE INDEX IF NOT EXISTS idx_staff_tasks_role_status
    ON staff_tasks (organization_id, assigned_role, status, due_at);

CREATE INDEX IF NOT EXISTS idx_staff_tasks_entity
    ON staff_tasks (organization_id, entity_type, entity_id, status);

CREATE INDEX IF NOT EXISTS idx_staff_tasks_due
    ON staff_tasks (status, due_at, last_reminder_at);

CREATE UNIQUE INDEX IF NOT EXISTS uq_staff_tasks_open_entity
    ON staff_tasks (organization_id, task_type, entity_type, entity_id)
    WHERE entity_id IS NOT NULL
      AND status IN ('OPEN','CLAIMED');

COMMENT ON TABLE staff_tasks IS
'Persistent operational work queue for staff actions. Financial transactions remain in the accounting ledger and are never represented by task rows.';

COMMENT ON COLUMN app_users.deleted_at IS
'Business-owner recycle-bin timestamp. NULL means the staff account is not in the deletion workflow.';

COMMENT ON COLUMN app_users.purge_after IS
'End of the 30-day user restore window. Expiry retires the account permanently from authentication while retaining audit identity.';
