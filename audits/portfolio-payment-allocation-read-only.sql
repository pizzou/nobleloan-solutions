-- Whole-portfolio financial consistency checks for all ordinary and owner-only loans.
-- Read-only; flags discrepancies without overwriting imported opening positions.
BEGIN TRANSACTION READ ONLY;

WITH posted_receipts AS (
 SELECT t.loan_id,
        COUNT(*) AS posted_receipt_count,
        ROUND(SUM(t.amount),2) AS cash_receipts_since_tracking,
        ROUND(SUM(COALESCE(t.principal_component,0)),2) AS principal_collected,
        ROUND(SUM(COALESCE(t.interest_component,0)),2) AS interest_collected,
        ROUND(SUM(COALESCE(t.management_fee_component,0)),2) AS management_collected,
        ROUND(SUM(COALESCE(t.penalty_component,0)),2) AS penalties_collected,
        ROUND(SUM(COALESCE(t.extension_fee_component,0)),2) AS extension_collected,
        ROUND(SUM(COALESCE(t.unapplied_amount,0)),2) AS unapplied_collected,
        ROUND(SUM(t.amount -
                COALESCE(t.principal_component,0) -
                COALESCE(t.interest_component,0) -
                COALESCE(t.management_fee_component,0) -
                COALESCE(t.penalty_component,0) -
                COALESCE(t.extension_fee_component,0) -
                COALESCE(t.unapplied_amount,0)),2) AS receipt_allocation_difference
 FROM payment_transactions t
 WHERE t.status = 'POSTED' AND COALESCE(t.reversed,false) = false
 GROUP BY t.loan_id
)
SELECT l.id, l.reference_number, l.imported, l.status,
       l.penalty_rate, l.days_overdue,
       l.total_paid, COALESCE(r.cash_receipts_since_tracking,0) AS posted_cash,
       ROUND(l.total_paid-COALESCE(r.cash_receipts_since_tracking,0),2) AS historical_import_or_other_paid,
       ROUND(COALESCE(l.amount,0)-COALESCE(l.principal_paid,0)-COALESCE(l.outstanding_balance,0),2) AS principal_difference,
       ROUND(COALESCE(l.total_interest,0)-COALESCE(l.interest_paid,0)-COALESCE(l.interest_outstanding,0),2) AS interest_difference,
       ROUND(COALESCE(l.management_fee,0)-COALESCE(l.management_fee_paid,0)-COALESCE(l.management_fee_outstanding,0),2) AS management_difference,
       ROUND(COALESCE(l.penalties_assessed,0)-COALESCE(l.penalties_paid,0),2) AS outstanding_penalties,
       COALESCE(r.receipt_allocation_difference,0) AS receipt_allocation_difference
FROM loans l
LEFT JOIN posted_receipts r ON r.loan_id=l.id
WHERE ABS(COALESCE(l.amount,0)-COALESCE(l.principal_paid,0)-COALESCE(l.outstanding_balance,0)) > 0.01
   OR ABS(COALESCE(l.total_interest,0)-COALESCE(l.interest_paid,0)-COALESCE(l.interest_outstanding,0)) > 0.01
   OR ABS(COALESCE(l.management_fee,0)-COALESCE(l.management_fee_paid,0)-COALESCE(l.management_fee_outstanding,0)) > 0.01
   OR COALESCE(l.penalties_paid,0)>COALESCE(l.penalties_assessed,0)
   OR ABS(COALESCE(r.receipt_allocation_difference,0))>0.01
ORDER BY l.id;

-- Totals for all posted repayment events, separated by actual allocation.
SELECT COALESCE(SUM(t.amount),0) AS posted_cash,
       COALESCE(SUM(t.penalty_component),0) AS penalties_collected,
       COALESCE(SUM(t.interest_component),0) AS interest_collected,
       COALESCE(SUM(t.management_fee_component),0) AS management_collected,
       COALESCE(SUM(t.extension_fee_component),0) AS extension_collected,
       COALESCE(SUM(t.principal_component),0) AS principal_collected,
       COALESCE(SUM(t.unapplied_amount),0) AS unapplied
FROM payment_transactions t
WHERE t.status='POSTED' AND COALESCE(t.reversed,false)=false;

ROLLBACK;
