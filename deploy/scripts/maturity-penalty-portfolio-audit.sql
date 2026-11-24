-- Read only. Flags legacy/system loans whose recorded penalty journal
-- precedes contractual maturity plus three full calendar grace days,
-- and payments credited to principal while penalties/fees remain.
-- REVIEW flags only: journal posting date is not necessarily the
-- earliest day included in a historical catch-up amount.
\pset pager off
BEGIN TRANSACTION READ ONLY;
\echo ==== Contract dates / accrued penalties for all loans ====
SELECT l.id, l.reference_number,
       b.first_name || ' ' || b.last_name AS borrower,
       l.imported, l.status, l.duration_months, l.disbursed_at,
       l.maturity_date, l.next_due_date,
       (l.maturity_date + 4) AS first_penalty_day,
       l.penalty_rate, l.outstanding_balance,
       l.interest_outstanding, l.management_fee_outstanding,
       l.penalties_assessed, l.penalties_paid,
       CASE
           WHEN l.maturity_date IS NULL AND l.penalties_assessed > 0 THEN 'REVIEW: PENALTY WITH NO VERIFIED MATURITY'
           WHEN l.maturity_date IS NOT NULL
                AND l.maturity_date + 4 > DATE '2026-11-24'
                AND l.penalties_assessed > 0 THEN 'REVIEW: PENALTY BEFORE MATURITY'
           WHEN l.penalties_assessed > l.penalties_paid THEN 'REVIEW: PENALTY POSTINGS'
           ELSE 'NO PENALTY FLAG'
       END AS audit_flag
FROM loans l JOIN borrowers b ON b.id=l.borrower_id
WHERE l.organization_id=1
ORDER BY l.id;

\echo ==== Penalty journals posted BEFORE first maturity-based chargeable day ====
SELECT l.id AS loan_id, l.reference_number, l.imported,
       l.maturity_date, (l.maturity_date + 4) AS first_penalty_day,
       je.id AS journal_id, je.entry_date, je.source_type,
       coa.code AS account_code,
       ROUND(SUM(jl.debit-jl.credit),2) AS receivable_effect
FROM loans l JOIN journal_entries je
       ON je.organization_id=l.organization_id
      AND (je.reference=l.reference_number OR je.source_id LIKE ('LOAN:' || l.id || ':%'))
JOIN journal_lines jl ON jl.journal_entry_id=je.id
JOIN chart_of_accounts coa ON coa.id=jl.account_id
WHERE l.organization_id=1 AND coa.code='1175'
  AND je.source_type IN ('PENALTY_ACCRUAL','LEGACY_PENALTY_OPENING')
  AND (l.maturity_date IS NULL OR je.entry_date < l.maturity_date + 4)
GROUP BY l.id,l.reference_number,l.imported,l.maturity_date,je.id,je.entry_date,je.source_type,coa.code
ORDER BY l.reference_number,je.entry_date,je.id;

\echo ==== Portfolio mismatch between loan-level and GL penalty balances ====
SELECT l.id, l.reference_number,
       ROUND(l.penalties_assessed-l.penalties_paid,2) AS operational_penalty_outstanding,
       COALESCE(ROUND(SUM(jl.debit-jl.credit),2),0) AS gl_penalty_outstanding,
       ROUND((l.penalties_assessed-l.penalties_paid)
         - COALESCE(SUM(jl.debit-jl.credit),0),2) AS difference
FROM loans l
LEFT JOIN journal_entries je ON je.organization_id=l.organization_id
                            AND je.reference=l.reference_number
LEFT JOIN journal_lines jl ON jl.journal_entry_id=je.id
LEFT JOIN chart_of_accounts coa ON coa.id=jl.account_id AND coa.code='1175'
WHERE l.organization_id=1
GROUP BY l.id,l.reference_number,l.penalties_assessed,l.penalties_paid
HAVING ROUND((l.penalties_assessed-l.penalties_paid)
         - COALESCE(SUM(CASE WHEN coa.code='1175' THEN jl.debit-jl.credit ELSE 0 END),0),2) <> 0
ORDER BY l.id;
COMMIT;
