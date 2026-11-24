-- System-wide maturity-based penalty recomputation; READ ONLY.
-- This identifies verifiable no-principal-movement candidates separately
-- from loans requiring original repayment history reconstruction.
-- Do NOT use the differences below as unreviewed migration amounts.
BEGIN TRANSACTION READ ONLY;

WITH portfolio AS (
 SELECT l.id, l.reference_number, l.imported, l.status,
        l.organization_id, l.disbursed_at::date AS disbursed_on,
        l.duration_months, l.maturity_date, l.created_at::date AS imported_on,
        l.amount::numeric AS original_principal,
        COALESCE(l.principal_paid,0)::numeric AS principal_paid,
        COALESCE(l.outstanding_balance,0)::numeric AS principal_outstanding,
        COALESCE(l.penalties_assessed,0)::numeric AS current_assessed,
        COALESCE(l.penalties_paid,0)::numeric AS current_paid,
        l.penalty_rate::numeric AS monthly_penalty_percent,
        CASE WHEN l.disbursed_at IS NOT NULL AND l.duration_months>0
          THEN (l.disbursed_at::date + make_interval(months=>l.duration_months))::date
          ELSE l.maturity_date END AS maturity
 FROM loans l
 WHERE l.status IN ('ACTIVE','OVERDUE','DEFAULTED','RESTRUCTURED')
), transactions AS (
 SELECT loan_id,
        COUNT(*) AS posted_receipts,
        ROUND(SUM(COALESCE(principal_component,0)),2) AS posted_principal,
        ROUND(SUM(COALESCE(penalty_component,0)),2) AS posted_penalty
 FROM payment_transactions
 WHERE status='POSTED' AND COALESCE(reversed,false)=false
 GROUP BY loan_id
), assessment AS (
 SELECT p.id,
        ROUND(COALESCE(SUM(
           p.principal_outstanding
           * p.monthly_penalty_percent / 100
           / EXTRACT(DAY FROM (
              date_trunc('month', d.charge_date)::date
              + interval '1 month' - interval '1 day'
             ))
         ),0),2) AS penalty_if_principal_constant
 FROM portfolio p
 LEFT JOIN LATERAL generate_series(
        p.maturity + INTERVAL '4 days',
        CURRENT_DATE::timestamp,
        INTERVAL '1 day') d(charge_date)
   ON p.maturity IS NOT NULL AND p.principal_outstanding > 0
 GROUP BY p.id
)
SELECT p.id,p.reference_number,p.status,p.imported,p.maturity,
       GREATEST(0,CURRENT_DATE-p.maturity) AS days_past_maturity,
       GREATEST(0,CURRENT_DATE-p.maturity-3) AS penalty_days,
       p.original_principal,p.principal_paid,p.principal_outstanding,
       p.current_assessed,p.current_paid,
       a.penalty_if_principal_constant,
       ROUND(a.penalty_if_principal_constant-p.current_assessed,2) AS estimated_assessed_change,
       COALESCE(t.posted_receipts,0) AS posted_receipts,
       COALESCE(t.posted_principal,0) AS posted_principal,
       CASE WHEN p.maturity IS NULL OR p.monthly_penalty_percent IS NULL
                 THEN 'MISSING_CONTRACT_EVIDENCE'
            WHEN COALESCE(t.posted_receipts,0)=0 AND p.principal_paid=0
                 AND p.current_paid=0 AND p.original_principal=p.principal_outstanding
                 THEN 'OPENING_PRINCIPAL_UNCHANGED_REVIEW'
            ELSE 'REQUIRES_HISTORICAL_RECEIPT_REPLAY'
       END AS correction_classification
FROM portfolio p
JOIN assessment a ON a.id=p.id
LEFT JOIN transactions t ON t.loan_id=p.id
ORDER BY p.id;

ROLLBACK;
