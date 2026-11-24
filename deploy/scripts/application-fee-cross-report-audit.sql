-- Application fee cross-report audit: period GL source versus lifetime loan totals.
-- READ ONLY: no postings, no backfills, no loan changes.
-- Organization 1; UTC journal dates. Set report period at the top as needed.
\pset pager off
BEGIN TRANSACTION READ ONLY;

\echo ==== Report period ====
SELECT DATE '2026-11-01' AS report_from, DATE '2026-11-24' AS report_to,
       1 AS organization_id, 'ALL INCLUDING OWNER-ONLY' AS source_scope;

\echo ==== Loan application fee lifetime totals by imported flag and loan status ====
SELECT COALESCE(imported,false) AS imported, status, COALESCE(business_owner_only,false) AS owner_only,
       COUNT(*) AS loan_count,
       ROUND(SUM(COALESCE(application_fee,0)),2) AS fee_charged,
       ROUND(SUM(COALESCE(application_fee_paid,0)),2) AS fee_paid,
       ROUND(SUM(GREATEST(COALESCE(application_fee,0)-COALESCE(application_fee_paid,0),0)),2) AS fee_unpaid
FROM loans
WHERE organization_id=1 AND deleted_at IS NULL AND status <> 'CANCELLED'
GROUP BY imported,status,business_owner_only
ORDER BY imported,status,business_owner_only;

\echo ==== All account 4100 GL entries classified by source and explanatory text ====
SELECT je.source_type,
       CASE
         WHEN je.source_type='APPLICATION_FEE_INCOME_RECLASSIFICATION'
           OR (LOWER(COALESCE(jl.description,'')) LIKE '%application fee%'
               AND LOWER(COALESCE(jl.description,'')) LIKE '%income%')
             THEN 'APPLICATION_FEE_INCOME'
         WHEN je.source_type='PENALTY_ACCRUAL'
           OR (LOWER(COALESCE(jl.description,'')) LIKE '%penalty%'
               AND LOWER(COALESCE(jl.description,'')) LIKE '%income%')
             THEN 'PENALTY_INCOME'
         WHEN je.source_type='CONTRACTUAL_MONTHLY_MANAGEMENT_FEE_ACCRUAL'
           OR (LOWER(COALESCE(jl.description,'')) LIKE '%management fee%'
               AND LOWER(COALESCE(jl.description,'')) LIKE '%income%')
             THEN 'MANAGEMENT_FEE_INCOME'
         ELSE 'OTHER_FEE_INCOME'
       END AS income_class,
       COUNT(DISTINCT je.id) AS journal_count,
       ROUND(SUM(jl.credit-jl.debit),2) AS net_income_lifetime,
       ROUND(SUM(CASE WHEN je.entry_date >= DATE '2026-11-01'
                           AND je.entry_date <= DATE '2026-11-24'
                      THEN jl.credit-jl.debit ELSE 0 END),2) AS net_income_period
FROM journal_entries je
JOIN journal_lines jl ON jl.journal_entry_id=je.id
JOIN chart_of_accounts coa ON coa.id=jl.account_id
WHERE je.organization_id=1 AND coa.code='4100'
  AND COALESCE(je.reversed,false)=false AND je.recycle_loan_id IS NULL
GROUP BY je.source_type,income_class
ORDER BY income_class,je.source_type;

\echo ==== Application fee collected on system-created loans and imported loans, NOT period-attributed ====
SELECT COUNT(*) AS loan_count,
       ROUND(SUM(COALESCE(application_fee,0)),2) AS all_assessed,
       ROUND(SUM(COALESCE(application_fee_paid,0)),2) AS all_paid,
       ROUND(SUM(CASE WHEN imported IS TRUE THEN COALESCE(application_fee_paid,0) ELSE 0 END),2) AS imported_paid,
       ROUND(SUM(CASE WHEN imported IS FALSE THEN COALESCE(application_fee_paid,0) ELSE 0 END),2) AS created_paid
FROM loans
WHERE organization_id=1 AND deleted_at IS NULL AND status <> 'CANCELLED';

\echo ==== GL 4100 journal detail for November 2026 ====
SELECT je.id,je.entry_date,je.source_type,je.source_id,je.reference,
       ROUND(SUM(jl.credit-jl.debit),2) AS income
FROM journal_entries je
JOIN journal_lines jl ON jl.journal_entry_id=je.id
JOIN chart_of_accounts coa ON coa.id=jl.account_id
WHERE je.organization_id=1
  AND coa.code='4100'
  AND je.entry_date BETWEEN DATE '2026-11-01' AND DATE '2026-11-24'
  AND COALESCE(je.reversed,false)=false AND je.recycle_loan_id IS NULL
GROUP BY je.id,je.entry_date,je.source_type,je.source_id,je.reference
ORDER BY je.entry_date,je.id;

COMMIT;
