-- Noble Loan Solutions: cash GL reconciliation (READ ONLY).
-- Purpose: explain a credit balance in GL 1000 without creating unsupported journals.
-- Execute on the intended database after confirming the connection.
-- Example:
-- docker exec -i loansaas-postgres sh -c 'exec psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d loansaas_nobleloansolutions' < deploy/scripts/cash-gl-reconciliation-audit.sql
-- A balanced trial balance does not establish that opening funding is complete.

\pset pager off
BEGIN TRANSACTION READ ONLY;

\echo ==== GL 1000 balance by organization (net debit positive; net credit negative) ====
SELECT je.organization_id,
       coa.code AS account_code,
       coa.name AS account_name,
       ROUND(COALESCE(SUM(jl.debit),0),2) AS total_debit,
       ROUND(COALESCE(SUM(jl.credit),0),2) AS total_credit,
       ROUND(COALESCE(SUM(jl.debit - jl.credit),0),2) AS net_debit_balance
FROM journal_entries je
JOIN journal_lines jl ON jl.journal_entry_id = je.id
JOIN chart_of_accounts coa ON coa.id = jl.account_id
WHERE coa.code = '1000'
  AND COALESCE(je.reversed,false) = false
  AND je.recycle_loan_id IS NULL
GROUP BY je.organization_id, coa.code, coa.name
ORDER BY je.organization_id;

\echo ==== GL 1000 movements by journal source (includes non-loan funding if recorded) ====
SELECT je.organization_id, je.source_type,
       COUNT(DISTINCT je.id) AS journal_count,
       ROUND(SUM(jl.debit),2) AS cash_debit,
       ROUND(SUM(jl.credit),2) AS cash_credit,
       ROUND(SUM(jl.debit-jl.credit),2) AS cash_net
FROM journal_entries je
JOIN journal_lines jl ON jl.journal_entry_id = je.id
JOIN chart_of_accounts coa ON coa.id = jl.account_id
WHERE coa.code = '1000'
  AND COALESCE(je.reversed,false) = false
  AND je.recycle_loan_id IS NULL
GROUP BY je.organization_id, je.source_type
ORDER BY je.organization_id, je.source_type;

\echo ==== Individual cash journal movements (audit source IDs before proposing entries) ====
SELECT je.organization_id, je.id AS journal_id, je.source_type, je.source_id,
       ROUND(SUM(jl.debit),2) AS cash_debit,
       ROUND(SUM(jl.credit),2) AS cash_credit,
       ROUND(SUM(jl.debit-jl.credit),2) AS cash_net
FROM journal_entries je
JOIN journal_lines jl ON jl.journal_entry_id = je.id
JOIN chart_of_accounts coa ON coa.id = jl.account_id
WHERE coa.code = '1000'
  AND COALESCE(je.reversed,false) = false
  AND je.recycle_loan_id IS NULL
GROUP BY je.organization_id, je.id, je.source_type, je.source_id
ORDER BY je.organization_id, je.id;

\echo ==== Trial balance across all active GL accounts (check debit/credit equality) ====
WITH balances AS (
 SELECT je.organization_id, coa.code,
        SUM(jl.debit-jl.credit) AS net_balance
 FROM journal_entries je
 JOIN journal_lines jl ON jl.journal_entry_id = je.id
 JOIN chart_of_accounts coa ON coa.id = jl.account_id
 WHERE COALESCE(je.reversed,false) = false
   AND je.recycle_loan_id IS NULL
 GROUP BY je.organization_id, coa.code
)
SELECT organization_id,
       ROUND(SUM(CASE WHEN net_balance > 0 THEN net_balance ELSE 0 END),2) AS trial_debits,
       ROUND(SUM(CASE WHEN net_balance < 0 THEN -net_balance ELSE 0 END),2) AS trial_credits,
       ROUND(SUM(net_balance),2) AS difference
FROM balances GROUP BY organization_id ORDER BY organization_id;

COMMIT;
