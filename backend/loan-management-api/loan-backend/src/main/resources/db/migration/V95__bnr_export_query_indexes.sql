-- Additional indexes for the BNR portfolio predicates used by the XLSX exporter.
-- These indexes are additive and do not change accounting/reporting semantics.

CREATE INDEX IF NOT EXISTS idx_loans_bnr_org_imported_disbursed
    ON loans (organization_id, imported, disbursed_at);

CREATE INDEX IF NOT EXISTS idx_loans_bnr_org_import_batch
    ON loans (organization_id, import_batch_id);
