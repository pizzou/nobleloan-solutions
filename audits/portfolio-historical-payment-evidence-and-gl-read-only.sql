-- Portfolio-wide historical-import evidence for safe financial correction.
-- READ ONLY. No automatic arrears, penalty or principal writeback.
-- Imported paid amounts may predate system use and have no payment_transactions.
BEGIN TRANSACTION READ ONLY;

WITH open_loans AS (
 SELECT l.id, l.reference_number, l.organization_id, l.status, l.imported,
        l.amount, l.principal_paid, l.outstanding_balance,
        l.interest_paid, l.interest_outstanding,
        l.management_fee_paid, l.management_fee_outstanding,
        l.penalties_assessed, l.penalties_paid,
        l.total_paid, l.total_repayable,
        l.disbursed_at::date AS disbursement_date, l.duration_months,
        CASE WHEN l.disbursed_at IS NOT NULL AND l.duration_months>0
             THEN (l.disbursed_at::date + make_interval(months=>l.duration_months))::date
             ELSE l.maturity_date END AS contractual_maturity
 FROM loans l
 WHERE l.status IN ('ACTIVE','OVERDUE','DEFAULTED','RESTRUCTURED')
), posted AS (
 SELECT t.loan_id, COUNT(*) AS posted_count,
        ROUND(SUM(t.amount),2) AS receipt_total,
        ROUND(SUM(COALESCE(t.penalty_component,0)),2) AS receipt_penalty,
        ROUND(SUM(COALESCE(t.principal_component,0)),2) AS receipt_principal,
        MIN(t.created_at::date) AS earliest_posted,
        MAX(t.created_at::date) AS latest_posted
 FROM payment_transactions t
 WHERE t.status='POSTED' AND COALESCE(t.reversed,false)=false
 GROUP BY t.loan_id
), journal_receivables AS (
 SELECT l.id AS loan_id, a.code,
        ROUND(SUM(jl.debit-jl.credit),2) AS balance,
        COUNT(*) AS posted_lines
 FROM open_loans l
 JOIN journal_entries je ON je.organization_id=l.organization_id
      AND COALESCE(je.reversed,false)=false
      AND (
           je.reference=l.reference_number
           OR je.source_id='LOAN:'||l.id
           OR (je.source_type IN ('PAYMENT_RECEIVED','LOAN_PAYMENT') AND EXISTS (
               SELECT 1 FROM payments inst
               WHERE inst.id::text=je.source_id AND inst.loan_id=l.id
           ))
      )
 JOIN journal_lines jl ON jl.journal_entry_id=je.id
 JOIN chart_of_accounts a ON a.id=jl.account_id AND a.code IN ('1100','1150','1160','1175')
 GROUP BY l.id,a.code
), gl AS (
 SELECT loan_id,
       SUM(balance) FILTER (WHERE code='1100') AS principal_gl,
       SUM(balance) FILTER (WHERE code='1150') AS interest_gl,
       SUM(balance) FILTER (WHERE code='1160') AS management_gl,
       SUM(balance) FILTER (WHERE code='1175') AS penalty_gl
 FROM journal_receivables
 GROUP BY loan_id
)
SELECT l.id,l.reference_number,l.status,l.imported,l.contractual_maturity,
       GREATEST(0,CURRENT_DATE-l.contractual_maturity-3) AS expected_days_overdue,
       l.principal_paid AS lifetime_principal_paid,
       COALESCE(p.posted_count,0) AS posted_transaction_count,
       COALESCE(p.receipt_total,0) AS posted_cash,
       COALESCE(p.receipt_principal,0) AS posted_principal,
       ROUND(l.principal_paid-COALESCE(p.receipt_principal,0),2) AS imported_or_untraced_principal_paid,
       l.penalties_assessed,l.penalties_paid,
       ROUND(l.penalties_assessed-l.penalties_paid,2) AS loan_penalty_due,
       COALESCE(gl.penalty_gl,0) AS gl_penalty_due,
       ROUND(l.penalties_assessed-l.penalties_paid-COALESCE(gl.penalty_gl,0),2) AS penalty_gl_difference,
       ROUND(l.outstanding_balance-COALESCE(gl.principal_gl,0),2) AS principal_gl_difference,
       ROUND(l.interest_outstanding-COALESCE(gl.interest_gl,0),2) AS interest_gl_difference,
       ROUND(l.management_fee_outstanding-COALESCE(gl.management_gl,0),2) AS management_gl_difference,
       CASE WHEN l.contractual_maturity > CURRENT_DATE
                  AND l.penalties_assessed>l.penalties_paid THEN 'PRE_MATURITY_PENALTY_REVIEW'
            WHEN l.principal_paid<>COALESCE(p.receipt_principal,0) THEN 'HISTORICAL_PRINCIPAL_EVIDENCE_REQUIRED'
            WHEN ROUND(l.penalties_assessed-l.penalties_paid-COALESCE(gl.penalty_gl,0),2)<>0 THEN 'PENALTY_GL_RECONCILIATION_REQUIRED'
            WHEN l.penalties_paid<>COALESCE(p.receipt_penalty,0) THEN 'HISTORICAL_PAID_PENALTY_EVIDENCE_REQUIRED'
            ELSE 'CONTRACTUAL_PENALTY_ASSESSMENT_REVIEW'
       END AS next_review
FROM open_loans l
LEFT JOIN posted p ON p.loan_id=l.id
LEFT JOIN gl ON gl.loan_id=l.id
ORDER BY l.id;

ROLLBACK;
