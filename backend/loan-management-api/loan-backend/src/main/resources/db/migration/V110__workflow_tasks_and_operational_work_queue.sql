-- Durable operator work queue. Tasks are not financial transactions; they track
-- accountable human work while financial state remains controlled by the
-- existing approval/disbursement services.
CREATE TABLE IF NOT EXISTS workflow_tasks (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    assigned_to BIGINT NOT NULL REFERENCES app_users(id),
    assigned_by BIGINT REFERENCES app_users(id),
    title VARCHAR(180) NOT NULL,
    description TEXT,
    task_type VARCHAR(60) NOT NULL,
    entity_type VARCHAR(40),
    entity_id BIGINT,
    reference_number VARCHAR(255),
    link VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    due_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    completed_by BIGINT REFERENCES app_users(id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_workflow_task_status CHECK (status IN ('OPEN','IN_PROGRESS','COMPLETED','CANCELLED')),
    CONSTRAINT chk_workflow_task_priority CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
    CONSTRAINT chk_workflow_task_entity_pair CHECK (
        (entity_type IS NULL AND entity_id IS NULL)
        OR (entity_type IS NOT NULL AND entity_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_workflow_tasks_assignee_status
    ON workflow_tasks (organization_id, assigned_to, status, due_at);

CREATE INDEX IF NOT EXISTS idx_workflow_tasks_entity
    ON workflow_tasks (organization_id, entity_type, entity_id);

CREATE INDEX IF NOT EXISTS idx_workflow_tasks_due
    ON workflow_tasks (organization_id, status, due_at);

CREATE INDEX IF NOT EXISTS idx_workflow_tasks_created
    ON workflow_tasks (organization_id, created_at DESC);

-- Idempotent operational assignment: at most one active task of the same type
-- for the same entity and assignee. Completed work may be created again later.
CREATE UNIQUE INDEX IF NOT EXISTS uq_workflow_tasks_active_entity_assignee
    ON workflow_tasks (organization_id, task_type, entity_type, entity_id, assigned_to)
    WHERE status IN ('OPEN','IN_PROGRESS')
      AND entity_type IS NOT NULL
      AND entity_id IS NOT NULL;
