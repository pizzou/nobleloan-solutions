ALTER TABLE import_batches ADD COLUMN IF NOT EXISTS processed_rows INTEGER NOT NULL DEFAULT 0;
ALTER TABLE import_batches ADD COLUMN IF NOT EXISTS progress_percent INTEGER NOT NULL DEFAULT 0;
ALTER TABLE import_batches ADD COLUMN IF NOT EXISTS file_size BIGINT;
ALTER TABLE import_batches ADD COLUMN IF NOT EXISTS staged_file_path TEXT;
ALTER TABLE import_batches ADD COLUMN IF NOT EXISTS error_message TEXT;
CREATE INDEX IF NOT EXISTS idx_import_batches_org_created ON import_batches(organization_id,created_at DESC);
ALTER TABLE import_batches ADD COLUMN IF NOT EXISTS error_report_path TEXT;
