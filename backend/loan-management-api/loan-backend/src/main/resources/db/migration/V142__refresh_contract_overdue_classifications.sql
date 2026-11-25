-- Recalculate stored delinquency when this release is deployed.
-- Use the same maturity, grace, and outstanding-receivable rules as the daily job.
WITH open_loans AS (
    SELECT id, status,
           COALESCE(
               maturity_date,
               CASE WHEN duration_months > 0 THEN
                   (COALESCE(disbursed_at::date, start_date)
                    + make_interval(months => duration_months))::date
               END
           ) AS contract_maturity,
           COALESCE(outstanding_balance, 0)
               + COALESCE(interest_outstanding, 0)
               + COALESCE(management_fee_outstanding, 0)
               + COALESCE(extension_fee_outstanding, 0)
               + GREATEST(0, COALESCE(application_fee, 0) - COALESCE(application_fee_paid, 0))
               + GREATEST(0, COALESCE(penalties_assessed, 0) - COALESCE(penalties_paid, 0))
               AS remaining_receivable
    FROM loans
    WHERE deleted_at IS NULL
      AND status IN ('ACTIVE', 'OVERDUE', 'DEFAULTED', 'RESTRUCTURED')
), aging AS (
    SELECT id, status,
           CASE WHEN remaining_receivable > 0 THEN
               GREATEST(0, (CURRENT_TIMESTAMP AT TIME ZONE 'Africa/Kigali')::date
                           - (contract_maturity + 3))
           ELSE 0 END AS overdue_days
    FROM open_loans
    WHERE contract_maturity IS NOT NULL
), classified AS (
    SELECT id, overdue_days,
           CASE WHEN status IN ('ACTIVE', 'OVERDUE') THEN
               CASE WHEN overdue_days > 0 THEN 'OVERDUE' ELSE 'ACTIVE' END
           ELSE status END AS loan_status,
           CASE WHEN overdue_days = 0 THEN 'CURRENT'
                WHEN overdue_days <= 89 THEN 'WATCH'
                WHEN overdue_days <= 179 THEN 'SUBSTANDARD'
                WHEN overdue_days <= 359 THEN 'DOUBTFUL'
                ELSE 'WRITTEN_OFF' END AS quality,
           CASE WHEN overdue_days > 0 THEN 'PAST_DUE' ELSE 'NOT_DUE' END AS arrears,
           CASE WHEN overdue_days = 0 THEN 'NORMAL'
                WHEN overdue_days <= 89 THEN 'REMINDER'
                WHEN overdue_days <= 179 THEN 'COLLECTION'
                WHEN overdue_days <= 359 THEN 'LEGAL'
                ELSE 'RECOVERY' END AS collections
    FROM aging
)
UPDATE loans AS loan
SET days_overdue = c.overdue_days,
    status = c.loan_status,
    credit_quality = c.quality,
    arrears_status = c.arrears,
    collections_stage = c.collections,
    classified_at = CURRENT_TIMESTAMP
FROM classified AS c
WHERE loan.id = c.id
  AND (loan.days_overdue, loan.status, loan.credit_quality, loan.arrears_status, loan.collections_stage)
      IS DISTINCT FROM (c.overdue_days, c.loan_status, c.quality, c.arrears, c.collections);
