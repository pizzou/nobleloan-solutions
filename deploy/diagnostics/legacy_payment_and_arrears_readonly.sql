-- READ ONLY. Do not fix production dates or balances by mass UPDATE.
-- Run against the current Noble Loan database as a read-only diagnostic.
-- Loan outstanding_balance is PRINCIPAL only; total_repayable - total_paid
-- represents the aggregate contractual receivable (subject to reconciliation).
BEGIN TRANSACTION READ ONLY;

SELECT l.id, l.reference_number,
       TRIM(CONCAT(COALESCE(b.first_name,''), ' ', COALESCE(b.last_name,''))) AS borrower,
       l.start_date, l.next_due_date, l.days_overdue,
       l.status, l.imported,
       l.amount AS original_principal,
       l.principal_paid, l.interest_paid, l.management_fee_paid,
       l.total_paid AS historical_and_current_paid,
       l.outstanding_balance AS principal_still_owed,
       l.interest_outstanding, l.management_fee_outstanding,
       l.penalties_assessed, l.penalties_paid,
       (COALESCE(l.total_repayable,0) - COALESCE(l.total_paid,0)) AS aggregate_contractual_outstanding,
       CASE
         WHEN l.next_due_date IS NOT NULL AND l.start_date IS NOT NULL
              AND l.next_due_date <= l.start_date
           THEN 'URGENT: Due date at/before disbursement; verify contract'
         WHEN COALESCE(l.total_paid,0) > 0
              AND COALESCE(l.total_repayable,0) > COALESCE(l.total_paid,0)
              AND l.next_due_date IS NOT NULL
           THEN 'REVIEW: Partial payments; verify oldest unpaid installment due date'
         WHEN l.next_due_date IS NULL AND l.status IN ('OVERDUE','DEFAULTED','RESTRUCTURED')
           THEN 'REVIEW: Outstanding loan has no due date'
         ELSE 'No date anomaly detected by this report'
       END AS due_date_review
FROM public.loans l
JOIN public.borrowers b ON b.id = l.borrower_id
WHERE l.organization_id = 1 AND l.imported = true
ORDER BY CASE WHEN l.next_due_date IS NOT NULL AND l.start_date IS NOT NULL
              AND l.next_due_date <= l.start_date THEN 0 ELSE 1 END,
         l.days_overdue DESC NULLS LAST, l.id
LIMIT 500;

-- Summary only, no personal data.
SELECT COUNT(*) AS imported_loans,
       COUNT(*) FILTER (WHERE COALESCE(total_paid,0) > 0) AS with_payments,
       COUNT(*) FILTER (WHERE COALESCE(total_paid,0)>0
                             AND COALESCE(total_repayable,0)>COALESCE(total_paid,0)) AS partially_paid_and_still_owing,
       COUNT(*) FILTER (WHERE next_due_date <= start_date) AS impossible_next_due_dates,
       COUNT(*) FILTER (WHERE COALESCE(days_overdue,0)>0) AS shown_overdue
FROM public.loans WHERE organization_id = 1 AND imported = true;
ROLLBACK;
