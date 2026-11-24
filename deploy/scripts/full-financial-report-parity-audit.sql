-- Noble Loan Solutions: source-ledger reconciliation for ALL accounting report metrics
-- READ ONLY; company org 1; report period 2026-11-01 through 2026-11-24.
-- Accounting and General Report use AccountingService.getFinancialReport.
-- BNR FINANCIAL statement now uses exactly that same service.
\pset pager off
BEGIN TRANSACTION READ ONLY;
\echo ==== Period and organization ====
SELECT 1 AS organization_id, DATE '2026-11-01' AS period_from,
       DATE '2026-11-24' AS period_to,
       'All current visible journals; no GL corrections in this audit' AS scope;
\echo ==== All account balances as of Nov 24 and current-period movement ====
WITH movements AS (
 SELECT coa.code,coa.name,coa.type,coa.normal_balance,
        SUM(CASE WHEN je.entry_date<= DATE '2026-11-24' THEN COALESCE(jl.debit,0) ELSE 0 END) debit_to_date,
        SUM(CASE WHEN je.entry_date<= DATE '2026-11-24' THEN COALESCE(jl.credit,0) ELSE 0 END) credit_to_date,
        SUM(CASE WHEN je.entry_date BETWEEN DATE '2026-11-01' AND DATE '2026-11-24' THEN COALESCE(jl.debit,0) ELSE 0 END) debit_period,
        SUM(CASE WHEN je.entry_date BETWEEN DATE '2026-11-01' AND DATE '2026-11-24' THEN COALESCE(jl.credit,0) ELSE 0 END) credit_period
 FROM journal_entries je JOIN journal_lines jl ON jl.journal_entry_id=je.id
 JOIN chart_of_accounts coa ON coa.id=jl.account_id
 WHERE je.organization_id=1 AND COALESCE(je.reversed,false)=false AND je.recycle_loan_id IS NULL
 GROUP BY coa.code,coa.name,coa.type,coa.normal_balance
)
SELECT code,name,type,
 ROUND(debit_to_date-credit_to_date,2) AS signed_debit_gl_balance,
 ROUND(debit_period,2) AS period_debit,
 ROUND(credit_period,2) AS period_credit,
 ROUND(CASE WHEN type='INCOME' THEN credit_period-debit_period
            WHEN type='EXPENSE' THEN debit_period-credit_period
            ELSE 0 END,2) AS period_profit_or_loss
FROM movements ORDER BY code;
\echo ==== Trial balance as-of 2026-11-24 ====
WITH accounts AS (
 SELECT coa.id,
   SUM(COALESCE(jl.debit,0)-COALESCE(jl.credit,0)) AS net
 FROM journal_entries je JOIN journal_lines jl ON jl.journal_entry_id=je.id
 JOIN chart_of_accounts coa ON coa.id=jl.account_id
 WHERE je.organization_id=1 AND je.entry_date <= DATE '2026-11-24'
  AND COALESCE(je.reversed,false)=false AND je.recycle_loan_id IS NULL
 GROUP BY coa.id
)
SELECT ROUND(SUM(GREATEST(net,0)),2) trial_balance_debit,
 ROUND(SUM(GREATEST(-net,0)),2) trial_balance_credit,
 ROUND(SUM(net),2) difference FROM accounts;
\echo ==== Profit and loss by income and expense GL account in period ====
SELECT coa.code,coa.name,coa.type,
 ROUND(SUM(CASE WHEN coa.type='INCOME' THEN jl.credit-jl.debit
                WHEN coa.type='EXPENSE' THEN jl.debit-jl.credit ELSE 0 END),2) AS amount
FROM journal_entries je JOIN journal_lines jl ON jl.journal_entry_id=je.id
 JOIN chart_of_accounts coa ON coa.id=jl.account_id
WHERE je.organization_id=1
 AND je.entry_date BETWEEN DATE '2026-11-01' AND DATE '2026-11-24'
 AND coa.type IN ('INCOME','EXPENSE')
 AND COALESCE(je.reversed,false)=false AND je.recycle_loan_id IS NULL
GROUP BY coa.code,coa.name,coa.type ORDER BY coa.code;
\echo ==== Cash GL 1000 period ledger grouped by transaction type ====
SELECT je.source_type,
 COUNT(DISTINCT je.id) journals,
 ROUND(SUM(jl.debit),2) cash_in,
 ROUND(SUM(jl.credit),2) cash_out,
 ROUND(SUM(jl.debit-jl.credit),2) net_cash_movement
FROM journal_entries je JOIN journal_lines jl ON jl.journal_entry_id=je.id
 JOIN chart_of_accounts coa ON coa.id=jl.account_id
WHERE je.organization_id=1
 AND je.entry_date BETWEEN DATE '2026-11-01' AND DATE '2026-11-24'
 AND coa.code='1000'
 AND COALESCE(je.reversed,false)=false AND je.recycle_loan_id IS NULL
GROUP BY je.source_type ORDER BY je.source_type;
\echo ==== Principal, interest, management, penalty, extension, application fee receivables as of date on GL ====
SELECT coa.code,coa.name,
 ROUND(SUM(jl.debit-jl.credit),2) net_receivable
FROM journal_entries je JOIN journal_lines jl ON jl.journal_entry_id=je.id
 JOIN chart_of_accounts coa ON coa.id=jl.account_id
WHERE je.organization_id=1
 AND je.entry_date<=DATE '2026-11-24'
 AND coa.code IN ('1100','1150','1160','1170','1175','1180')
 AND COALESCE(je.reversed,false)=false AND je.recycle_loan_id IS NULL
GROUP BY coa.code,coa.name ORDER BY coa.code;
COMMIT;
