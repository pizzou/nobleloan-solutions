-- V84: Harden distributed scheduler locks for production monitoring.
CREATE TABLE IF NOT EXISTS scheduler_locks (
    job_name     VARCHAR(100) PRIMARY KEY,
    locked_until TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_scheduler_locks_locked_until
    ON scheduler_locks (locked_until);
