-- Diagnose NLS-000054 principal, interest and management GL baseline.
-- Strictly READ ONLY. Can be run against the isolated restored database.
BEGIN TRANSACTION READ ONLY;

-- Compare operational loan receivables with every accounting entry selected
-- by the guarded reconciliation script. Missing accounts are shown explicitly.
WITH loan AS (
  SELECT id, organization_id, reference_number,
         outstanding_balance, interest_outstanding,
         management_fee_outstanding
  FROM loans WHERE id=54
), relevant_codes AS (
  SELECT '1100'::text AS code, 'Principal'::text AS label UNION ALL
  SELECT '1150','Interest' UNION ALL
  SELECT '1160','Management fees'
), ledger AS (
  SELECT a.code, ROUND(SUM(jl.debit-jl.credit),2) AS gl_balance,
         COUNT(*) AS line_count
  FROM journal_lines jl
  JOIN chart_of_accounts a ON a.id=jl.account_id
  JOIN journal_entries je ON je.id=jl.journal_entry_id
  JOIN loan l ON l.organization_id=je.organization_id
  WHERE a.code IN ('1100','1150','1160')
    AND (je.reference=l.reference_number
         OR je.source_id='LOAN:'||l.id)
    AND COALESCE(je.reversed,false)=false
  GROUP BY a.code
)
SELECT c.code,c.label,
       CASE c.code WHEN '1100' THEN l.outstanding_balance
                   WHEN '1150' THEN l.interest_outstanding
                   WHEN '1160' THEN l.management_fee_outstanding END
           AS loan_receivable,
       COALESCE(gl.gl_balance,0) AS gl_receivable,
       ROUND(COALESCE(gl.gl_balance,0) -
          CASE c.code WHEN '1100' THEN l.outstanding_balance
                      WHEN '1150' THEN l.interest_outstanding
                      WHEN '1160' THEN l.management_fee_outstanding END,2)
          AS gl_minus_loan,
       COALESCE(gl.line_count,0) AS included_lines
FROM relevant_codes c
CROSS JOIN loan l
LEFT JOIN ledger gl ON gl.code=c.code
ORDER BY c.code;

-- Line-by-line trace, including every historical opening and cash receipt,
-- to identify incorrect classification, missing opening or duplicate booking.
SELECT je.id AS journal_id, je.entry_date,je.source_type,
       je.source_id, je.reference, COALESCE(je.reversed,false) AS reversed,
       a.code, jl.debit,jl.credit, ROUND(jl.debit-jl.credit,2) AS movement,
       CASE WHEN je.reference=l.reference_number
                 OR je.source_id='LOAN:'||l.id
            THEN 'INCLUDED_BY_CORRECTION'
            ELSE 'EXCLUDED_BY_CORRECTION' END AS identity_match
FROM journal_entries je
JOIN journal_lines jl ON jl.journal_entry_id=je.id
JOIN chart_of_accounts a ON a.id=jl.account_id
JOIN loans l ON l.id=54 AND l.organization_id=je.organization_id
WHERE a.code IN ('1100','1150','1160','1175')
  AND (je.reference=l.reference_number
       OR je.source_id='LOAN:'||l.id
       OR je.source_id LIKE '54-%'
       OR je.source_id LIKE 'LOAN:54:%'
       OR jl.description ILIKE '%'||l.reference_number||'%')
ORDER BY je.entry_date,je.id,a.code,jl.id;

-- Verify chronological receipts and historic imported components.
SELECT id, transaction_reference, created_at, status,reversed,
       amount,principal_component,interest_component,
       management_fee_component,penalty_component,
       extension_fee_component,unapplied_amount
FROM payment_transactions WHERE loan_id=54 ORDER BY created_at,id;

ROLLBACK;
