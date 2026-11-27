-- Add the operational receivable account for genuinely unpaid one-time
-- application fees. Fees already deducted and collected at disbursement remain
-- income (4100) and must not be reclassified as receivables.
-- This is idempotent and safe for organizations created before account 1180 was
-- introduced in the application code.
INSERT INTO chart_of_accounts
    (organization_id, code, name, type, normal_balance, active)
SELECT o.id, '1180', 'Application Fees Receivable', 'ASSET', 'DEBIT', TRUE
FROM organizations o
ON CONFLICT (organization_id, code) DO UPDATE
SET name = EXCLUDED.name,
    type = 'ASSET',
    normal_balance = 'DEBIT',
    active = TRUE;
