-- Noble Loan Solutions: cross-report accounting / loan / BNR receivables audit
-- READ ONLY. Does not change, backfill, revalue or post any loan or journal.
-- Organization 1; compares NOW/current portfolio against ALL active journals.
-- Run the same owner-only visibility as the reports being compared.
\pset pager off
BEGIN TRANSACTION READ ONLY;

\echo ==== As-of and source-scope context ====
SELECT CURRENT_DATE AS database_today, current_setting('TimeZone') AS database_timezone,
       1 AS organization_id,
       'ALL including owner-only' AS visibility_scope,
       'Current operational balances versus active all-date GL; not a historical snapshot' AS basis;

\echo ==== Portfolio record counts by status and visibility ====
SELECT status, COALESCE(imported,false) AS imported,
       COALESCE(business_owner_only,false) AS owner_only,
       COUNT(*) AS loans
FROM loans
WHERE organization_id=1 AND deleted_at IS NULL
GROUP BY status, imported, business_owner_only
ORDER BY status, imported, business_owner_only;

\echo ==== Six operational current receivable components, matching Dashboard/BNR funded-status scope ====
WITH eligible AS (
 SELECT *
 FROM loans
 WHERE organization_id=1 AND deleted_at IS NULL
   AND status IN ('ACTIVE','DISBURSED','OVERDUE','DEFAULTED','RESTRUCTURED')
   AND (
     imported IS TRUE
     OR import_batch_id IS NOT NULL
     OR LOWER(COALESCE(internal_notes,'')) LIKE '%imported from legacy ledger%'
     OR LOWER(COALESCE(notes,'')) LIKE '%imported from noble loan historical portfolio workbook%'
     OR disbursed_at IS NOT NULL
   )
), operational AS (
 SELECT '1100' AS code, SUM(COALESCE(outstanding_balance,0)) AS amount FROM eligible
 UNION ALL SELECT '1150', SUM(GREATEST(COALESCE(interest_outstanding,0),0)) FROM eligible
 UNION ALL SELECT '1160', SUM(COALESCE(management_fee_outstanding,0)) FROM eligible
 UNION ALL SELECT '1170', SUM(COALESCE(extension_fee_outstanding,0)) FROM eligible
 UNION ALL SELECT '1175', SUM(GREATEST(COALESCE(penalties_assessed,0)-COALESCE(penalties_paid,0),0)) FROM eligible
 UNION ALL SELECT '1180', SUM(GREATEST(COALESCE(application_fee,0)-COALESCE(application_fee_paid,0),0)) FROM eligible
), ledger AS (
 SELECT a.code, SUM(jl.debit-jl.credit) AS amount
 FROM journal_entries je
 JOIN journal_lines jl ON jl.journal_entry_id=je.id
 JOIN chart_of_accounts a ON a.id=jl.account_id
 WHERE je.organization_id=1
   AND COALESCE(je.reversed,false)=false AND je.recycle_loan_id IS NULL
   AND a.code IN ('1100','1150','1160','1170','1175','1180')
 GROUP BY a.code
)
SELECT op.code, ROUND(COALESCE(op.amount,0),2) AS operational,
       ROUND(COALESCE(gl.amount,0),2) AS gl_net_debit,
       ROUND(COALESCE(op.amount,0)-COALESCE(gl.amount,0),2) AS operational_minus_gl
FROM operational op LEFT JOIN ledger gl ON gl.code=op.code
ORDER BY op.code;

\echo ==== Operational receivables by imported versus system-created, visible only versus owner-only ====
WITH eligible AS (
 SELECT * FROM loans WHERE organization_id=1 AND deleted_at IS NULL
   AND status IN ('ACTIVE','DISBURSED','OVERDUE','DEFAULTED','RESTRUCTURED')
   AND (imported IS TRUE OR import_batch_id IS NOT NULL
        OR LOWER(COALESCE(internal_notes,'')) LIKE '%imported from legacy ledger%'
        OR LOWER(COALESCE(notes,'')) LIKE '%imported from noble loan historical portfolio workbook%'
        OR disbursed_at IS NOT NULL)
)
SELECT COALESCE(imported,false) AS imported,
       COALESCE(business_owner_only,false) AS owner_only,
       COUNT(*) AS loans,
       ROUND(SUM(COALESCE(outstanding_balance,0)),2) AS principal,
       ROUND(SUM(GREATEST(COALESCE(interest_outstanding,0),0)),2) AS interest,
       ROUND(SUM(COALESCE(management_fee_outstanding,0)),2) AS management_fee,
       ROUND(SUM(COALESCE(extension_fee_outstanding,0)),2) AS extension_fee,
       ROUND(SUM(GREATEST(COALESCE(penalties_assessed,0)-COALESCE(penalties_paid,0),0)),2) AS penalties,
       ROUND(SUM(GREATEST(COALESCE(application_fee,0)-COALESCE(application_fee_paid,0),0)),2) AS application_fee
FROM eligible
GROUP BY imported, business_owner_only
ORDER BY imported, owner_only;

\echo ==== Paid loans with nonzero current receivable components (manual review only) ====
SELECT id, reference_number, status,
       outstanding_balance, interest_outstanding,
       management_fee_outstanding, extension_fee_outstanding,
       GREATEST(COALESCE(penalties_assessed,0)-COALESCE(penalties_paid,0),0) AS penalties_unpaid,
       GREATEST(COALESCE(application_fee,0)-COALESCE(application_fee_paid,0),0) AS application_fee_unpaid
FROM loans
WHERE organization_id=1 AND deleted_at IS NULL AND status='PAID'
  AND (ABS(COALESCE(outstanding_balance,0)) >= 0.01
    OR ABS(COALESCE(interest_outstanding,0)) >= 0.01
    OR ABS(COALESCE(management_fee_outstanding,0)) >= 0.01
    OR ABS(COALESCE(extension_fee_outstanding,0)) >= 0.01
    OR COALESCE(penalties_assessed,0)-COALESCE(penalties_paid,0) >= 0.01
    OR COALESCE(application_fee,0)-COALESCE(application_fee_paid,0) >= 0.01)
ORDER BY id;

\echo ==== Historical postings by receivable account and source type ====
SELECT a.code, je.source_type, COUNT(DISTINCT je.id) AS journals,
       ROUND(SUM(jl.debit),2) AS debit, ROUND(SUM(jl.credit),2) AS credit,
       ROUND(SUM(jl.debit-jl.credit),2) AS net_debit
FROM journal_entries je
JOIN journal_lines jl ON jl.journal_entry_id=je.id
JOIN chart_of_accounts a ON a.id=jl.account_id
WHERE je.organization_id=1
 AND COALESCE(je.reversed,false)=false AND je.recycle_loan_id IS NULL
 AND a.code IN ('1100','1150','1160','1170','1175','1180')
GROUP BY a.code, je.source_type
ORDER BY a.code, je.source_type;

COMMIT;
