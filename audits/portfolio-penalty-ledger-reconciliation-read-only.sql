-- Portfolio-wide evidence for controlled maturity-policy financial reclassification.
-- NO WRITE OPERATIONS. Do not use calculated differences for automated edits.
BEGIN TRANSACTION READ ONLY;

WITH contracts AS (
  SELECT l.id, l.reference_number, l.imported, l.status,
         l.disbursed_at::date AS disbursed_on, l.duration_months,
         l.maturity_date AS saved_maturity,
         CASE WHEN l.disbursed_at IS NOT NULL AND l.duration_months > 0
              THEN (l.disbursed_at::date + make_interval(months => l.duration_months))::date
              ELSE l.maturity_date END AS contractual_maturity,
         l.days_overdue AS current_days_overdue, l.penalty_rate,
         l.amount, l.outstanding_balance, l.principal_paid,
         l.penalties_assessed, l.penalties_paid,
         l.interest_outstanding, l.management_fee_outstanding
  FROM loans l
  WHERE l.status IN ('ACTIVE','OVERDUE','DEFAULTED','RESTRUCTURED')
),
posted_penalty_gl AS (
  SELECT c.id AS loan_id,
         ROUND(COALESCE(SUM(jl.debit),0),2) AS gl_penalty_debits,
         ROUND(COALESCE(SUM(jl.credit),0),2) AS gl_penalty_credits,
         ROUND(COALESCE(SUM(jl.debit-jl.credit),0),2) AS gl_penalty_balance,
         ROUND(COALESCE(SUM(CASE
             WHEN je.source_type IN ('PENALTY_ACCRUAL','LEGACY_PENALTY_OPENING')
                  AND c.contractual_maturity IS NOT NULL
                  AND je.entry_date <= c.contractual_maturity + 3
             THEN jl.debit - jl.credit ELSE 0 END),0),2) AS penalty_booked_before_grace_end
  FROM contracts c
  LEFT JOIN journal_entries je ON je.reference = c.reference_number
  LEFT JOIN journal_lines jl ON jl.journal_entry_id=je.id
  LEFT JOIN chart_of_accounts a ON a.id=jl.account_id AND a.code='1175'
  WHERE a.code='1175'
  GROUP BY c.id
),
posted_receipts AS (
  SELECT t.loan_id,
         COUNT(*) AS count_receipts,
         ROUND(SUM(t.amount),2) AS received_cash,
         ROUND(SUM(COALESCE(t.penalty_component,0)),2) AS penalty_paid_receipts,
         ROUND(SUM(COALESCE(t.principal_component,0)),2) AS principal_paid_receipts,
         MIN(t.created_at::date) AS first_receipt_date
  FROM payment_transactions t
  WHERE t.status='POSTED' AND COALESCE(t.reversed,false)=false
  GROUP BY t.loan_id
)
SELECT c.id, c.reference_number, c.imported, c.status,
       c.disbursed_on, c.duration_months, c.saved_maturity,
       c.contractual_maturity, c.current_days_overdue,
       CASE WHEN c.contractual_maturity IS NULL THEN NULL
            ELSE GREATEST(0,CURRENT_DATE-c.contractual_maturity) END AS expected_days_overdue,
       CASE WHEN c.contractual_maturity IS NULL THEN NULL
            ELSE GREATEST(0,CURRENT_DATE-(c.contractual_maturity+4)+1) END AS chargeable_days,
       c.penalty_rate, c.amount, c.outstanding_balance,
       c.principal_paid, c.penalties_assessed,c.penalties_paid,
       ROUND(COALESCE(c.penalties_assessed,0)-COALESCE(c.penalties_paid,0),2) AS loan_penalty_outstanding,
       COALESCE(g.gl_penalty_balance,0) AS gl_penalty_outstanding,
       ROUND(COALESCE(c.penalties_assessed,0)-COALESCE(c.penalties_paid,0)
             -COALESCE(g.gl_penalty_balance,0),2) AS loan_vs_gl_difference,
       COALESCE(g.penalty_booked_before_grace_end,0) AS penalty_gl_booked_before_grace_end,
       COALESCE(r.count_receipts,0) AS posted_receipts,
       COALESCE(r.received_cash,0) AS posted_cash,
       COALESCE(r.penalty_paid_receipts,0) AS posted_penalty_receipts,
       COALESCE(r.principal_paid_receipts,0) AS posted_principal_receipts,
       r.first_receipt_date
FROM contracts c
LEFT JOIN posted_penalty_gl g ON g.loan_id=c.id
LEFT JOIN posted_receipts r ON r.loan_id=c.id
ORDER BY c.id;

ROLLBACK;
