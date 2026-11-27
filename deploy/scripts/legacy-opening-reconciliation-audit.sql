-- Noble Loan Solutions: legacy opening population and GL validation.
-- READ ONLY. No replay of historical cash; no automatic journal creation.
-- Run against the intended production DB only after reviewing the SQL.
\pset pager off
BEGIN TRANSACTION READ ONLY;

\echo ==== Imported loan population by status and opening-journal presence ====
WITH openings AS (
 SELECT organization_id, source_id, COUNT(*) AS journal_count
 FROM journal_entries
 WHERE organization_id = 1 AND source_type = 'LEGACY_LOAN_OPENING'
   AND COALESCE(reversed,false) = false AND recycle_loan_id IS NULL
 GROUP BY organization_id, source_id
)
SELECT l.status,
       COUNT(*) AS imported_loans,
       COUNT(*) FILTER (WHERE o.source_id IS NOT NULL) AS with_opening,
       COUNT(*) FILTER (WHERE o.source_id IS NULL) AS without_opening
FROM loans l
LEFT JOIN openings o ON o.organization_id = l.organization_id
 AND o.source_id = 'LOAN:' || l.id::text
WHERE l.organization_id = 1 AND l.imported = true AND l.deleted_at IS NULL
GROUP BY l.status ORDER BY l.status;

\echo ==== Imported loans missing an active opening journal (diagnostic only) ====
SELECT l.id, l.reference_number, l.status
FROM loans l
WHERE l.organization_id = 1 AND l.imported = true AND l.deleted_at IS NULL
 AND NOT EXISTS (
   SELECT 1 FROM journal_entries je
   WHERE je.organization_id = l.organization_id
     AND je.source_type = 'LEGACY_LOAN_OPENING'
     AND je.source_id = 'LOAN:' || l.id::text
     AND COALESCE(je.reversed,false) = false
     AND je.recycle_loan_id IS NULL
 )
ORDER BY l.status, l.id;

\echo ==== Opening journals with missing/non-imported loan references or duplicates ====
SELECT je.source_id, COUNT(*) AS active_journals,
       MIN(je.id) AS first_journal_id,
       l.id AS loan_id, l.status, l.imported
FROM journal_entries je
LEFT JOIN loans l ON l.organization_id = je.organization_id
 AND je.source_id = 'LOAN:' || l.id::text
WHERE je.organization_id = 1 AND je.source_type = 'LEGACY_LOAN_OPENING'
 AND COALESCE(je.reversed,false) = false AND je.recycle_loan_id IS NULL
GROUP BY je.source_id, l.id, l.status, l.imported
HAVING COUNT(*) <> 1 OR l.id IS NULL OR l.imported IS DISTINCT FROM true
ORDER BY je.source_id;

\echo ==== Legacy opening amounts by account (not current receivables) ====
SELECT coa.code, coa.name,
       COUNT(DISTINCT je.id) AS opening_journals,
       ROUND(SUM(jl.debit),2) AS debit,
       ROUND(SUM(jl.credit),2) AS credit,
       ROUND(SUM(jl.debit-jl.credit),2) AS net_debit
FROM journal_entries je
JOIN journal_lines jl ON jl.journal_entry_id = je.id
JOIN chart_of_accounts coa ON coa.id = jl.account_id
WHERE je.organization_id = 1 AND je.source_type = 'LEGACY_LOAN_OPENING'
 AND COALESCE(je.reversed,false) = false AND je.recycle_loan_id IS NULL
GROUP BY coa.code, coa.name ORDER BY coa.code;

\echo ==== Each legacy opening journal balances ====
SELECT je.id, je.source_id,
       ROUND(SUM(jl.debit),2) AS debit,
       ROUND(SUM(jl.credit),2) AS credit,
       ROUND(SUM(jl.debit-jl.credit),2) AS difference
FROM journal_entries je
JOIN journal_lines jl ON jl.journal_entry_id = je.id
WHERE je.organization_id = 1 AND je.source_type = 'LEGACY_LOAN_OPENING'
 AND COALESCE(je.reversed,false) = false AND je.recycle_loan_id IS NULL
GROUP BY je.id, je.source_id
HAVING ABS(SUM(jl.debit-jl.credit)) >= 0.01
ORDER BY je.id;

COMMIT;
