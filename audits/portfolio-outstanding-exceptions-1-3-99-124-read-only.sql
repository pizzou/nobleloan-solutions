-- Review the four material exceptions from the Nov 25 portfolio audit.
-- READ ONLY. Show source journal lines rather than assuming adjustment amounts.
BEGIN TRANSACTION READ ONLY;

WITH scope AS (
  SELECT l.id,l.reference_number,l.organization_id,l.status,
         l.disbursed_at::date AS disbursed_on,l.duration_months,l.maturity_date,
         l.outstanding_balance,l.principal_paid,l.interest_paid,
         l.interest_outstanding,l.management_fee_paid,
         l.management_fee_outstanding,l.penalties_assessed,l.penalties_paid
  FROM loans l WHERE l.id IN (1,3,99,124)
), loan_account_lines AS (
 SELECT s.id AS loan_id, s.reference_number,je.id AS journal_id,
        je.entry_date,je.source_type,je.source_id,
        je.reference AS journal_reference,je.description AS journal_description,
        a.code, jl.debit,jl.credit,
        jl.description AS line_description,
        CASE WHEN je.reference=s.reference_number
               OR je.source_id='LOAN:'||s.id
               OR (je.source_type IN ('PAYMENT_RECEIVED','LOAN_PAYMENT')
                   AND EXISTS (SELECT 1 FROM payments inst
                               WHERE inst.id::text=je.source_id
                                 AND inst.loan_id=s.id))
             THEN 'MATCHED' ELSE 'RELATED_DESCRIPTION_ONLY' END AS identity_match
 FROM scope s
 JOIN journal_entries je ON je.organization_id=s.organization_id
 JOIN journal_lines jl ON jl.journal_entry_id=je.id
 JOIN chart_of_accounts a ON a.id=jl.account_id
 WHERE a.code IN ('1000','1100','1150','1160','1175','3000','3010','4100')
   AND COALESCE(je.reversed,false)=false
   AND (je.reference=s.reference_number
        OR je.source_id='LOAN:'||s.id
        OR (je.source_type IN ('PAYMENT_RECEIVED','LOAN_PAYMENT')
            AND EXISTS (SELECT 1 FROM payments inst
                        WHERE inst.id::text=je.source_id
                          AND inst.loan_id=s.id))
        OR jl.description ILIKE '%'||s.reference_number||'%')
)
SELECT id,reference_number,status,disbursed_on,duration_months,maturity_date,
       outstanding_balance,principal_paid,interest_paid,interest_outstanding,
       management_fee_paid,management_fee_outstanding,
       penalties_assessed,penalties_paid
FROM scope ORDER BY id;

WITH scope AS (
  SELECT l.id,l.reference_number,l.organization_id,l.status,
         l.disbursed_at::date AS disbursed_on,l.duration_months,l.maturity_date,
         l.outstanding_balance,l.principal_paid,l.interest_paid,
         l.interest_outstanding,l.management_fee_paid,
         l.management_fee_outstanding,l.penalties_assessed,l.penalties_paid
  FROM loans l WHERE l.id IN (1,3,99,124)
), loan_account_lines AS (
 SELECT s.id AS loan_id, s.reference_number,je.id AS journal_id,
        je.entry_date,je.source_type,je.source_id,
        je.reference AS journal_reference,je.description AS journal_description,
        a.code, jl.debit,jl.credit,
        jl.description AS line_description,
        CASE WHEN je.reference=s.reference_number
               OR je.source_id='LOAN:'||s.id
               OR (je.source_type IN ('PAYMENT_RECEIVED','LOAN_PAYMENT')
                   AND EXISTS (SELECT 1 FROM payments inst
                               WHERE inst.id::text=je.source_id
                                 AND inst.loan_id=s.id))
             THEN 'MATCHED' ELSE 'RELATED_DESCRIPTION_ONLY' END AS identity_match
 FROM scope s
 JOIN journal_entries je ON je.organization_id=s.organization_id
 JOIN journal_lines jl ON jl.journal_entry_id=je.id
 JOIN chart_of_accounts a ON a.id=jl.account_id
 WHERE a.code IN ('1000','1100','1150','1160','1175','3000','3010','4100')
   AND COALESCE(je.reversed,false)=false
   AND (je.reference=s.reference_number
        OR je.source_id='LOAN:'||s.id
        OR (je.source_type IN ('PAYMENT_RECEIVED','LOAN_PAYMENT')
            AND EXISTS (SELECT 1 FROM payments inst
                        WHERE inst.id::text=je.source_id
                          AND inst.loan_id=s.id))
        OR jl.description ILIKE '%'||s.reference_number||'%')
)
SELECT loan_id,reference_number,code,
       ROUND(SUM(debit),2) AS total_debits,
       ROUND(SUM(credit),2) AS total_credits,
       ROUND(SUM(debit-credit),2) AS net_balance
FROM loan_account_lines
WHERE identity_match='MATCHED'
GROUP BY loan_id,reference_number,code
ORDER BY loan_id,code;

WITH scope AS (
  SELECT l.id,l.reference_number,l.organization_id,l.status,
         l.disbursed_at::date AS disbursed_on,l.duration_months,l.maturity_date,
         l.outstanding_balance,l.principal_paid,l.interest_paid,
         l.interest_outstanding,l.management_fee_paid,
         l.management_fee_outstanding,l.penalties_assessed,l.penalties_paid
  FROM loans l WHERE l.id IN (1,3,99,124)
), loan_account_lines AS (
 SELECT s.id AS loan_id, s.reference_number,je.id AS journal_id,
        je.entry_date,je.source_type,je.source_id,
        je.reference AS journal_reference,je.description AS journal_description,
        a.code, jl.debit,jl.credit,
        jl.description AS line_description,
        CASE WHEN je.reference=s.reference_number
               OR je.source_id='LOAN:'||s.id
               OR (je.source_type IN ('PAYMENT_RECEIVED','LOAN_PAYMENT')
                   AND EXISTS (SELECT 1 FROM payments inst
                               WHERE inst.id::text=je.source_id
                                 AND inst.loan_id=s.id))
             THEN 'MATCHED' ELSE 'RELATED_DESCRIPTION_ONLY' END AS identity_match
 FROM scope s
 JOIN journal_entries je ON je.organization_id=s.organization_id
 JOIN journal_lines jl ON jl.journal_entry_id=je.id
 JOIN chart_of_accounts a ON a.id=jl.account_id
 WHERE a.code IN ('1000','1100','1150','1160','1175','3000','3010','4100')
   AND COALESCE(je.reversed,false)=false
   AND (je.reference=s.reference_number
        OR je.source_id='LOAN:'||s.id
        OR (je.source_type IN ('PAYMENT_RECEIVED','LOAN_PAYMENT')
            AND EXISTS (SELECT 1 FROM payments inst
                        WHERE inst.id::text=je.source_id
                          AND inst.loan_id=s.id))
        OR jl.description ILIKE '%'||s.reference_number||'%')
)
SELECT loan_id,journal_id,entry_date,source_type,source_id,
       journal_reference,code,debit,credit,identity_match,
       line_description
FROM loan_account_lines
ORDER BY loan_id,entry_date,journal_id,code;

SELECT t.loan_id,t.id,t.transaction_reference,t.created_at,
       t.amount,t.status,t.reversed,t.penalty_component,t.interest_component,
       t.management_fee_component,t.principal_component,t.unapplied_amount
FROM payment_transactions t WHERE t.loan_id IN (1,3,99,124)
ORDER BY t.loan_id,t.created_at,t.id;

ROLLBACK;
