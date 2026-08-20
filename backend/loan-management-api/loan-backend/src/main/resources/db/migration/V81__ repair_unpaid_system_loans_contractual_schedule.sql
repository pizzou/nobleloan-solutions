-- CONTROLLED REPAIR — RUN ONLY AFTER A DATABASE BACKUP
--
-- Scope:
--   * system-originated loans only (imported = false/null)
--   * no payment activity (amount_paid = 0 and paid = false)
--   * non-terminal loan statuses
--
-- This intentionally does NOT modify imported/legacy loans or loans with
-- real payment activity.
--
-- The repair changes only the persisted contractual schedule/totals so they
-- agree with the production FinancialPolicy implementation:
--   equal principal + monthly interest on opening principal
--   + monthly management fee on opening principal.

BEGIN;

CREATE TEMP TABLE tmp_contractual_schedule ON COMMIT DROP AS
WITH RECURSIVE candidates AS (
    SELECT
        l.id AS loan_id,
        l.amount::numeric AS principal,
        GREATEST(COALESCE(l.duration_months, 0), 1) AS months,
        COALESCE(l.interest_rate, 5.00)::numeric AS interest_rate,
        COALESCE(l.management_fee_rate, 5.00)::numeric AS management_rate
    FROM loans l
    WHERE COALESCE(l.imported, false) = false
      AND l.status IN ('PENDING','UNDER_REVIEW','APPROVED','DISBURSED','ACTIVE','OVERDUE','RESTRUCTURED')
      AND NOT EXISTS (
          SELECT 1
          FROM payments p
          WHERE p.loan_id = l.id
            AND (COALESCE(p.amount_paid, 0) > 0 OR p.paid = true)
      )
), schedule AS (
    SELECT
        c.loan_id,
        1 AS installment_number,
        c.months,
        c.interest_rate,
        c.management_rate,
        ROUND(c.principal, 2) AS opening_balance,
        ROUND(
            CASE WHEN c.months = 1 THEN c.principal
                 ELSE c.principal / c.months
            END, 2
        ) AS principal_component
    FROM candidates c

    UNION ALL

    SELECT
        s.loan_id,
        s.installment_number + 1,
        s.months,
        s.interest_rate,
        s.management_rate,
        ROUND(s.opening_balance - s.principal_component, 2),
        ROUND(
            CASE
                WHEN s.installment_number + 1 = s.months
                    THEN s.opening_balance - s.principal_component
                ELSE (s.opening_balance - s.principal_component)
                     / (s.months - s.installment_number)
            END, 2
        )
    FROM schedule s
    WHERE s.installment_number < s.months
)
SELECT
    loan_id,
    installment_number,
    opening_balance,
    principal_component,
    ROUND(opening_balance * interest_rate / 100.0, 2) AS interest_component,
    ROUND(opening_balance * management_rate / 100.0, 2) AS management_fee_component,
    ROUND(
        principal_component
        + ROUND(opening_balance * interest_rate / 100.0, 2)
        + ROUND(opening_balance * management_rate / 100.0, 2),
        2
    ) AS installment_amount,
    ROUND(opening_balance - principal_component, 2) AS remaining_balance
FROM schedule;

-- Persist the operational payment schedule.
UPDATE payments p
SET
    amount = s.installment_amount,
    principal_component = s.principal_component,
    interest_component = s.interest_component,
    management_fee_component = s.management_fee_component,
    scheduled_interest = s.interest_component,
    scheduled_management_fee = s.management_fee_component,
    cycle_interest_due = s.interest_component,
    cycle_interest_remaining = s.interest_component,
    cycle_management_fee_due = s.management_fee_component,
    cycle_management_fee_remaining = s.management_fee_component,
    outstanding_after = s.remaining_balance
FROM tmp_contractual_schedule s
WHERE p.loan_id = s.loan_id
  AND p.installment_number = s.installment_number
  AND COALESCE(p.amount_paid, 0) = 0
  AND COALESCE(p.paid, false) = false;

-- Persist the public payment_schedules representation.
UPDATE payment_schedules ps
SET
    installment_amount = s.installment_amount,
    principal_amount = s.principal_component,
    interest_amount = s.interest_component,
    management_fee_amount = s.management_fee_component,
    remaining_balance = s.remaining_balance
FROM tmp_contractual_schedule s
WHERE ps.loan_id = s.loan_id
  AND ps.installment_number = s.installment_number
  AND COALESCE(ps.amount_paid, 0) = 0;

-- Synchronize loan-level contractual totals.
WITH totals AS (
    SELECT
        loan_id,
        ROUND(SUM(principal_component), 2) AS principal_total,
        ROUND(SUM(interest_component), 2) AS interest_total,
        ROUND(SUM(management_fee_component), 2) AS management_total
    FROM tmp_contractual_schedule
    GROUP BY loan_id
)
UPDATE loans l
SET
    total_interest = t.interest_total,
    management_fee = t.management_total,
    total_repayable = ROUND(t.principal_total + t.interest_total + t.management_total, 2),
    interest_outstanding = GREATEST(
        ROUND(t.interest_total - COALESCE(l.interest_paid, 0), 2),
        0
    ),
    management_fee_outstanding = GREATEST(
        ROUND(t.management_total - COALESCE(l.management_fee_paid, 0), 2),
        0
    ),
    outstanding_balance = t.principal_total
FROM totals t
WHERE l.id = t.loan_id;

-- Make the first unpaid installment visible at loan level.
WITH first_installment AS (
    SELECT DISTINCT ON (p.loan_id)
        p.loan_id,
        p.due_date,
        p.amount
    FROM payments p
    JOIN tmp_contractual_schedule s
      ON s.loan_id = p.loan_id
     AND s.installment_number = p.installment_number
    WHERE COALESCE(p.paid, false) = false
    ORDER BY p.loan_id, p.installment_number
)
UPDATE loans l
SET
    next_due_date = f.due_date,
    next_payment_date = f.due_date,
    next_installment_amount = f.amount
FROM first_installment f
WHERE l.id = f.loan_id;

-- Review the affected loans before committing.
SELECT
    l.id,
    l.reference_number,
    l.total_interest,
    l.management_fee,
    l.total_repayable,
    l.outstanding_balance,
    l.next_installment_amount
FROM loans l
JOIN (SELECT DISTINCT loan_id FROM tmp_contractual_schedule) x
  ON x.loan_id = l.id
ORDER BY l.id;

-- IMPORTANT: change COMMIT to ROLLBACK if the review above is not correct.
COMMIT;