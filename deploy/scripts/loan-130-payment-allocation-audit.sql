-- Noble Loan Solutions: loan NLS-000130 payment/allocation diagnostics.
-- READ ONLY. This script cannot change loan status, installment history or GL.
\pset pager off
BEGIN TRANSACTION READ ONLY;

\echo ==== Loan 130 current contractual versus paid components ====
SELECT id,reference_number,status,imported,amount,
       principal_paid,outstanding_balance,total_repayable,total_paid,
       total_interest,interest_paid,interest_outstanding,
       management_fee,management_fee_paid,management_fee_outstanding,
       extension_fee_outstanding,penalties_assessed,penalties_paid,
       application_fee,application_fee_paid
FROM loans WHERE organization_id=1 AND id=130;

\echo ==== All installment records for loan 130 ====
SELECT id,installment_number,due_date,paid_date,amount,amount_paid,paid,status,
       principal_component,interest_component,management_fee_component,
       extension_fee_component,penalty,penalty_paid,
       scheduled_interest,scheduled_management_fee
FROM payments
WHERE organization_id=1 AND loan_id=130
ORDER BY installment_number,id;

\echo ==== Immutable transaction events for loan 130 ====
SELECT id,created_at,status,reversed,transaction_reference,amount,
       principal_component,interest_component,management_fee_component,
       penalty_component,extension_fee_component,unapplied_amount
FROM payment_transactions
WHERE organization_id=1 AND loan_id=130
ORDER BY id;

\echo ==== Loan 130 opening and repayment GL sources (possibly with different reference scheme) ====
SELECT je.id,je.entry_date,je.source_type,je.source_id,
       a.code,ROUND(SUM(jl.debit),2) AS debit,
       ROUND(SUM(jl.credit),2) AS credit
FROM journal_entries je
JOIN journal_lines jl ON jl.journal_entry_id=je.id
JOIN chart_of_accounts a ON a.id=jl.account_id
WHERE je.organization_id=1
 AND COALESCE(je.reversed,false)=false AND je.recycle_loan_id IS NULL
 AND (je.source_id='LOAN:130'
    OR je.source_id='130'
    OR je.source_id LIKE 'LOAN:130:%'
    OR je.source_id LIKE '130-%')
GROUP BY je.id,je.entry_date,je.source_type,je.source_id,a.code
ORDER BY je.id,a.code;

COMMIT;
