-- Noble Loan Solutions: READ-ONLY payment-to-GL audit.
-- Run with: docker exec -i loansaas-postgres psql -X -v ON_ERROR_STOP=1
--   -U loansaas -d loansaas_nobleloansolutions -f - < deploy/scripts/payment-journal-audit.sql
-- This script deliberately never inserts, updates, or deletes financial records.
BEGIN TRANSACTION READ ONLY;

\pset pager off
\echo ==== Overview: posted payment events and receipt journals by organization ====
SELECT pt.organization_id,
       COUNT(*) FILTER (WHERE pt.status = 'POSTED' AND COALESCE(pt.reversed, false) = false) AS posted_transactions,
       COALESCE(SUM(pt.amount) FILTER (WHERE pt.status = 'POSTED' AND COALESCE(pt.reversed, false) = false),0) AS posted_amount,
       (SELECT COUNT(*) FROM journal_entries je
          WHERE je.organization_id = pt.organization_id
            AND je.source_type = 'PAYMENT_RECEIVED'
            AND COALESCE(je.reversed,false) = false
            AND je.recycle_loan_id IS NULL) AS active_payment_journals
FROM payment_transactions pt
GROUP BY pt.organization_id ORDER BY pt.organization_id;

\echo ==== Transactions missing a matching NEW transaction-based receipt journal ====
-- Legacy transactions intentionally do not have PAYTX: source IDs. The list below
-- includes all historical posted transactions and is DIAGNOSTIC, not a repair queue.
-- Check the legacy journal by installment ID before declaring a transaction missing.
SELECT pt.id AS payment_transaction_id, pt.organization_id, pt.loan_id,
       pt.installment_id, pt.transaction_reference, pt.amount, pt.created_at
FROM payment_transactions pt
LEFT JOIN journal_entries je
  ON je.organization_id = pt.organization_id
 AND je.source_type = 'PAYMENT_RECEIVED'
 AND je.source_id = 'PAYTX:' || pt.id::text
 AND COALESCE(je.reversed,false) = false
WHERE pt.status = 'POSTED' AND COALESCE(pt.reversed,false) = false
  AND je.id IS NULL
ORDER BY pt.organization_id, pt.id;

\echo ==== New transaction-based payment journals with incorrect cash debits ====
WITH new_journals AS (
 SELECT je.id AS journal_id, je.organization_id, je.source_id,
        SUM(CASE WHEN coa.code = '1000' THEN jl.debit - jl.credit ELSE 0 END) AS cash_delta,
        SUM(jl.debit - jl.credit) AS journal_imbalance
 FROM journal_entries je
 JOIN journal_lines jl ON jl.journal_entry_id = je.id
 JOIN chart_of_accounts coa ON coa.id = jl.account_id
 WHERE je.source_type = 'PAYMENT_RECEIVED'
   AND je.source_id LIKE 'PAYTX:%'
   AND COALESCE(je.reversed,false) = false
 GROUP BY je.id, je.organization_id, je.source_id
)
SELECT j.organization_id, j.journal_id, j.source_id,
       pt.id AS transaction_id, pt.amount AS transaction_amount,
       j.cash_delta, j.journal_imbalance
FROM new_journals j
LEFT JOIN payment_transactions pt ON pt.organization_id = j.organization_id
 AND j.source_id = 'PAYTX:' || pt.id::text
WHERE pt.id IS NULL
   OR ABS(j.cash_delta - pt.amount) >= 0.01
   OR ABS(j.journal_imbalance) >= 0.01
ORDER BY j.organization_id,j.journal_id;

\echo ==== Duplicate active PAYMENT_RECEIVED source events ====
SELECT organization_id, source_id, COUNT(*) AS journal_count
FROM journal_entries
WHERE source_type = 'PAYMENT_RECEIVED'
  AND COALESCE(reversed,false) = false
GROUP BY organization_id, source_id
HAVING COUNT(*) > 1
ORDER BY organization_id, source_id;

COMMIT;
