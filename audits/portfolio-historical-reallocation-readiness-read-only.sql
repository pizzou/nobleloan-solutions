-- Institution-wide historical repayment reallocation readiness.
-- READ ONLY. Never changes loans, receipts, payments, journals or cash.
-- This is evidence discovery, not authorization to post adjustments.
BEGIN TRANSACTION READ ONLY;

WITH contracts AS (
    SELECT l.id, l.reference_number, l.status, l.imported,
           COALESCE(l.disbursed_at, l.disbursed_at_timestamp)::date AS actual_disbursed_on,
           l.duration_months,
           l.amount::numeric AS original_principal,
           COALESCE(l.principal_paid,0)::numeric AS lifetime_principal_paid,
           COALESCE(l.outstanding_balance,0)::numeric AS principal_outstanding,
           COALESCE(l.total_paid,0)::numeric AS reported_total_paid,
           COALESCE(l.penalties_assessed,0)::numeric AS penalties_assessed,
           COALESCE(l.penalties_paid,0)::numeric AS penalties_paid,
           CASE WHEN COALESCE(l.disbursed_at, l.disbursed_at_timestamp) IS NOT NULL
                         AND l.duration_months > 0
                THEN (COALESCE(l.disbursed_at, l.disbursed_at_timestamp)::date
                        + make_interval(months=>l.duration_months))::date
                ELSE l.maturity_date END AS contractual_maturity
    FROM loans l
    WHERE l.status IN ('ACTIVE','OVERDUE','DEFAULTED','RESTRUCTURED','DISBURSED','PAID','CLOSED')
), receipts AS (
    SELECT t.loan_id,
           COUNT(*) FILTER (WHERE t.status='POSTED' AND COALESCE(t.reversed,false)=false)
               AS active_receipt_count,
           ROUND(COALESCE(SUM(t.amount) FILTER
               (WHERE t.status='POSTED' AND COALESCE(t.reversed,false)=false),0),2)
               AS posted_cash,
           ROUND(COALESCE(SUM(COALESCE(t.principal_component,0)) FILTER
               (WHERE t.status='POSTED' AND COALESCE(t.reversed,false)=false),0),2)
               AS posted_principal,
           COUNT(*) FILTER (WHERE t.status='POSTED' AND COALESCE(t.reversed,false)=false
               AND t.created_at IS NULL) AS missing_receipt_dates,
           COUNT(*) FILTER (WHERE t.status='POSTED' AND COALESCE(t.reversed,false)=false
               AND ROUND(COALESCE(t.penalty_component,0)
                         + COALESCE(t.interest_component,0)
                         + COALESCE(t.management_fee_component,0)
                         + COALESCE(t.extension_fee_component,0)
                         + COALESCE(t.principal_component,0)
                         + COALESCE(t.unapplied_amount,0),2) <> ROUND(t.amount,2))
               AS receipt_component_mismatches,
           MIN(t.created_at::date) FILTER
               (WHERE t.status='POSTED' AND COALESCE(t.reversed,false)=false)
               AS first_receipt_date,
           MAX(t.created_at::date) FILTER
               (WHERE t.status='POSTED' AND COALESCE(t.reversed,false)=false)
               AS last_receipt_date
    FROM payment_transactions t
    GROUP BY t.loan_id
), schedule AS (
    SELECT p.loan_id, COUNT(*) AS installment_rows,
           COUNT(*) FILTER (WHERE p.due_date IS NULL) AS installments_without_due_date,
           COUNT(*) FILTER (WHERE p.due_date IS NOT NULL) AS dated_installment_rows
    FROM payments p
    GROUP BY p.loan_id
)
SELECT c.id, c.reference_number, c.status, c.imported,
       c.actual_disbursed_on, c.duration_months, c.contractual_maturity,
       CASE WHEN c.contractual_maturity IS NULL THEN NULL
            ELSE GREATEST(0, CURRENT_DATE - c.contractual_maturity - 3)
       END AS canonical_overdue_days,
       c.original_principal, c.lifetime_principal_paid,
       c.principal_outstanding, c.reported_total_paid,
       COALESCE(r.active_receipt_count,0) AS posted_receipts,
       COALESCE(r.posted_cash,0) AS traceable_posted_cash,
       COALESCE(r.posted_principal,0) AS traceable_posted_principal,
       ROUND(c.lifetime_principal_paid-COALESCE(r.posted_principal,0),2)
           AS historical_or_untraced_principal,
       COALESCE(s.installment_rows,0) AS installment_rows,
       COALESCE(s.installments_without_due_date,0) AS undated_installment_rows,
       COALESCE(r.missing_receipt_dates,0) AS missing_receipt_dates,
       COALESCE(r.receipt_component_mismatches,0) AS receipt_component_mismatches,
       r.first_receipt_date, r.last_receipt_date,
       c.penalties_assessed, c.penalties_paid,
       CASE
         WHEN c.actual_disbursed_on IS NULL
           OR c.duration_months IS NULL OR c.duration_months <= 0
             THEN 'MISSING_CONTRACT_DATE_OR_DURATION'
         WHEN c.original_principal < 0 OR c.principal_outstanding < 0
             THEN 'INVALID_PRINCIPAL'
         WHEN COALESCE(r.missing_receipt_dates,0)>0
           OR COALESCE(r.receipt_component_mismatches,0)>0
             THEN 'INVALID_RECEIPT_EVIDENCE'
         WHEN ABS(c.lifetime_principal_paid - COALESCE(r.posted_principal,0)) > 0.01
             THEN 'HISTORICAL_OPENING_PAYMENTS_NEED_DATES'
         WHEN COALESCE(r.active_receipt_count,0)=0
             THEN 'NO_POSTED_RECEIPTS_TO_REALLOCATE'
         WHEN COALESCE(s.installment_rows,0)=0
             THEN 'DATED_CHARGE_SCHEDULE_NOT_AVAILABLE'
         WHEN COALESCE(s.installments_without_due_date,0)>0
             THEN 'UNDATED_INSTALLMENT_EVIDENCE'
         ELSE 'RECEIPTS_TRACEABLE_VERIFY_DATED_FEES_AND_GL'
       END AS readiness
FROM contracts c
LEFT JOIN receipts r ON r.loan_id=c.id
LEFT JOIN schedule s ON s.loan_id=c.id
ORDER BY c.id;

ROLLBACK;
