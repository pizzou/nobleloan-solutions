-- Institution-wide maturity/overdue evidence: READ ONLY.
-- Actual term controls overdue; monthly installment dates remain independent.
BEGIN TRANSACTION READ ONLY;

WITH loan_contracts AS (
 SELECT l.id, l.reference_number, l.imported, l.status,
        l.disbursed_at::date AS disbursement_date,
        l.duration_months, l.maturity_date AS saved_maturity,
        l.next_due_date AS scheduled_due_date,
        l.days_overdue AS stored_overdue_days,
        l.penalty_rate,
        l.outstanding_balance, l.penalties_assessed, l.penalties_paid,
        CASE WHEN l.disbursed_at IS NOT NULL
                       AND l.duration_months BETWEEN 1 AND 120
             THEN (l.disbursed_at::date + make_interval(months => l.duration_months))::date
             ELSE l.maturity_date END AS contractual_maturity
 FROM loans l
 WHERE l.status IN ('ACTIVE','OVERDUE','DEFAULTED','RESTRUCTURED')
)
SELECT id, reference_number, imported, status, disbursement_date,
       duration_months, saved_maturity, contractual_maturity,
       scheduled_due_date, stored_overdue_days,
       CASE WHEN contractual_maturity IS NULL THEN NULL
            ELSE GREATEST(0, CURRENT_DATE - contractual_maturity - 3) END AS expected_overdue_days,
       CASE WHEN contractual_maturity IS NULL THEN NULL
            ELSE GREATEST(0, CURRENT_DATE - (contractual_maturity + INTERVAL '4 days')::date + 1) END
            AS expected_penalty_chargeable_days,
       penalty_rate, outstanding_balance,
       (COALESCE(penalties_assessed,0)-COALESCE(penalties_paid,0)) AS penalties_outstanding
FROM loan_contracts
ORDER BY id;

ROLLBACK;
