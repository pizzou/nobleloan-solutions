-- READ ONLY: verify GAKUBA CLAVER's recorded 12,100,000 RWF payment.
-- Assumed loan reference NLS-000054 from PAY-NLS-000054 bank GL cash receipt.
-- Confirm borrower identity in the first result before interpreting any amounts.
-- Does not edit payments, penalties, interest, maturity, or journals.
\pset pager off
BEGIN TRANSACTION READ ONLY;
\echo ==== Borrower/loan contractual facts ====
SELECT l.id AS loan_id, l.reference_number,
       b.first_name, b.last_name,
       l.imported, l.status, l.duration_months,
       l.disbursed_at, l.start_date, l.maturity_date, l.next_due_date,
       l.interest_rate, l.management_fee_rate, l.penalty_rate,
       l.amount AS original_principal,
       l.outstanding_balance AS principal_outstanding,
       l.total_interest, l.interest_paid, l.interest_outstanding,
       l.management_fee, l.management_fee_paid, l.management_fee_outstanding,
       l.penalties_assessed, l.penalties_paid,
       l.principal_paid, l.total_paid, l.total_repayable
FROM loans l JOIN borrowers b ON b.id=l.borrower_id
WHERE l.organization_id=1 AND l.reference_number='NLS-000054';
\echo ==== Instalments, including all unpaid monthly interest/management fees ====
SELECT p.id, p.installment_number, p.due_date,
       p.paid_date, p.paid, p.status,
       p.amount, p.amount_paid,
       p.scheduled_interest,p.scheduled_management_fee,
       p.cycle_interest_due,p.cycle_interest_remaining,
       p.cycle_management_fee_due,p.cycle_management_fee_remaining,
       p.principal_component,p.interest_component,
       p.management_fee_component,p.penalty,p.penalty_paid
FROM payments p JOIN loans l ON l.id=p.loan_id
WHERE l.organization_id=1 AND l.reference_number='NLS-000054'
ORDER BY p.due_date NULLS LAST,p.id;
\echo ==== Immutable payment transactions: original cash and allocation ====
SELECT pt.id,pt.created_at,pt.amount,
       pt.principal_component,pt.interest_component,
       pt.management_fee_component,pt.penalty_component,
       pt.extension_fee_component,pt.unapplied_amount
FROM payment_transactions pt JOIN loans l ON l.id=pt.loan_id
WHERE l.organization_id=1 AND l.reference_number='NLS-000054'
ORDER BY pt.created_at,pt.id;
\echo ==== Journal debits and credits for GAKUBA cash receipt and loan ====
SELECT je.id,je.entry_date,je.source_type,je.source_id,je.reference,
       coa.code,coa.name,
       ROUND(jl.debit,2) debit, ROUND(jl.credit,2) credit
FROM journal_entries je JOIN journal_lines jl ON jl.journal_entry_id=je.id
JOIN chart_of_accounts coa ON coa.id=jl.account_id
WHERE je.organization_id=1
  AND (je.reference LIKE '%NLS-000054%'
       OR je.source_id IN ('29','LOAN:54'))
ORDER BY je.entry_date,je.id,coa.code;
COMMIT;
