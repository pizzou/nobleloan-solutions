-- Noble Loan Solutions: read-only production evidence for NLS-000054.
-- Run with psql -X -v ON_ERROR_STOP=1; DO NOT UPDATE source records.
BEGIN TRANSACTION READ ONLY;

SELECT id, reference_number, status, imported, amount, disbursed_at,
       next_due_date, days_overdue, penalty_rate,
       principal_paid, outstanding_balance, interest_paid,
       interest_outstanding, management_fee_paid,
       management_fee_outstanding, penalties_assessed, penalties_paid,
       (COALESCE(penalties_assessed,0)-COALESCE(penalties_paid,0)) AS penalty_outstanding,
       extension_fee_outstanding, total_paid, total_repayable
FROM loans
WHERE id = 54 OR reference_number = 'NLS-000054';

SELECT p.id, p.loan_id, p.installment_number, p.due_date, p.paid_date,
       p.paid, p.status, p.amount, p.amount_paid,
       p.scheduled_interest, p.scheduled_management_fee,
       p.cycle_interest_due, p.cycle_interest_remaining,
       p.cycle_management_fee_due, p.cycle_management_fee_remaining,
       p.interest_component, p.management_fee_component,
       p.principal_component, p.penalty, p.penalty_paid
FROM payments p
WHERE p.loan_id IN (SELECT id FROM loans
                    WHERE id = 54 OR reference_number = 'NLS-000054')
ORDER BY p.due_date NULLS LAST, p.id;

SELECT t.id, t.created_at, t.transaction_reference, t.amount,
       t.penalty_component, t.interest_component,
       t.management_fee_component, t.extension_fee_component,
       t.principal_component, t.unapplied_amount, t.status, t.reversed
FROM payment_transactions t
WHERE t.loan_id IN (SELECT id FROM loans
                    WHERE id = 54 OR reference_number = 'NLS-000054')
ORDER BY t.created_at, t.id;

SELECT t.loan_id,
       COALESCE(SUM(t.amount),0) AS posted_receipts,
       COALESCE(SUM(t.penalty_component),0) AS allocated_penalties,
       COALESCE(SUM(t.interest_component),0) AS allocated_interest,
       COALESCE(SUM(t.management_fee_component),0) AS allocated_management,
       COALESCE(SUM(t.extension_fee_component),0) AS allocated_extension,
       COALESCE(SUM(t.principal_component),0) AS allocated_principal,
       COALESCE(SUM(t.unapplied_amount),0) AS allocated_unapplied,
       COALESCE(SUM(t.amount - COALESCE(t.penalty_component,0)
           - COALESCE(t.interest_component,0)
           - COALESCE(t.management_fee_component,0)
           - COALESCE(t.extension_fee_component,0)
           - COALESCE(t.principal_component,0)
           - COALESCE(t.unapplied_amount,0)),0) AS allocation_difference
FROM payment_transactions t
WHERE t.loan_id IN (SELECT id FROM loans
                    WHERE id = 54 OR reference_number = 'NLS-000054')
  AND t.status = 'POSTED' AND t.reversed = false
GROUP BY t.loan_id;

-- Journal source references and penalty accruals must be reviewed before
-- making any adjustments. No existing journal is deleted or rewritten.
SELECT id, entry_date, source_type, source_id, reference, description
FROM journal_entries
WHERE source_id LIKE '%54%'
   OR reference LIKE '%NLS-000054%'
ORDER BY entry_date, id;

ROLLBACK;
