-- Noble Loan Solutions: interest / management fee recognition diagnostic
-- READ ONLY. Operates on current balances, not a historical as-of snapshot.
\pset pager off
BEGIN TRANSACTION READ ONLY;

\echo ==== System-created originated loans: operational contractual balances ====
SELECT l.id, l.reference_number, l.status, l.imported, l.amount,
       l.disbursed_at, l.next_due_date,
       l.total_interest, l.interest_paid, l.interest_outstanding,
       l.management_fee, l.management_fee_paid, l.management_fee_outstanding,
       l.total_paid, l.outstanding_balance
FROM loans l
WHERE l.organization_id = 1 AND l.deleted_at IS NULL
  AND l.imported = false
  AND l.status IN ('ACTIVE','DISBURSED','OVERDUE','DEFAULTED','RESTRUCTURED')
ORDER BY l.id;

\echo ==== Paid loan with nonzero interest/management balances (known loan NLS-000130) ====
SELECT id, reference_number, status, imported, amount, disbursed_at,
       total_interest, interest_paid, interest_outstanding,
       management_fee, management_fee_paid, management_fee_outstanding,
       total_paid, outstanding_balance
FROM loans
WHERE organization_id=1 AND deleted_at IS NULL
  AND status='PAID'
  AND (ABS(COALESCE(interest_outstanding,0)) >= 0.01
       OR ABS(COALESCE(management_fee_outstanding,0)) >= 0.01)
ORDER BY id;

\echo ==== Journal receivable movements tied to system-created loan IDs 1,3 and paid loan 130 ====
SELECT je.id AS journal_id, je.entry_date, je.source_type, je.source_id,
       a.code, a.name,
       ROUND(SUM(jl.debit),2) AS debit, ROUND(SUM(jl.credit),2) AS credit,
       ROUND(SUM(jl.debit-jl.credit),2) AS signed_net
FROM journal_entries je
JOIN journal_lines jl ON jl.journal_entry_id=je.id
JOIN chart_of_accounts a ON a.id=jl.account_id
WHERE je.organization_id=1
  AND COALESCE(je.reversed,false)=false AND je.recycle_loan_id IS NULL
  AND a.code IN ('1100','1150','1160','4100','4000','4010','4020')
  AND (
       je.source_id IN ('LOAN:1','LOAN:3','LOAN:130')
       OR je.source_id LIKE 'LOAN:1:%'
       OR je.source_id LIKE 'LOAN:3:%'
       OR je.source_id LIKE 'LOAN:130:%'
       OR je.source_id LIKE '1-%'
       OR je.source_id LIKE '3-%'
       OR je.source_id LIKE '130-%'
  )
GROUP BY je.id, je.entry_date, je.source_type, je.source_id, a.code, a.name
ORDER BY je.entry_date, je.id, a.code;

\echo ==== All interest & management GL movement by journal source, regardless of loan ====
SELECT a.code, je.source_type, COUNT(DISTINCT je.id) AS journals,
       ROUND(SUM(jl.debit),2) AS debit,
       ROUND(SUM(jl.credit),2) AS credit
FROM journal_entries je
JOIN journal_lines jl ON jl.journal_entry_id=je.id
JOIN chart_of_accounts a ON a.id=jl.account_id
WHERE je.organization_id=1
  AND COALESCE(je.reversed,false)=false AND je.recycle_loan_id IS NULL
  AND a.code IN ('1150','1160')
GROUP BY a.code,je.source_type ORDER BY a.code,je.source_type;

COMMIT;
