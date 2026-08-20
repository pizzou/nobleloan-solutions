BEGIN;

WITH ordered AS (
    SELECT
        p.id,
        p.loan_id,
        ROUND(
            SUM(COALESCE(p2.principal_component, 0))::numeric,
            2
        ) AS opening_balance,
        ROUND(
            SUM(COALESCE(p3.principal_component, 0))::numeric,
            2
        ) AS closing_balance,
        COALESCE(l.interest_rate, 5.00)::numeric AS interest_rate,
        COALESCE(l.management_fee_rate, 5.00)::numeric AS management_rate,
        COALESCE(p.principal_component, 0)::numeric AS principal_component
    FROM payments p
    JOIN loans l ON l.id = p.loan_id
    JOIN payments p2
      ON p2.loan_id = p.loan_id
     AND p2.installment_number >= p.installment_number
    LEFT JOIN payments p3
      ON p3.loan_id = p.loan_id
     AND p3.installment_number > p.installment_number
    WHERE COALESCE(l.imported, FALSE) = FALSE
      AND l.import_batch_id IS NULL
      AND COALESCE(p.paid, FALSE) = FALSE
      AND COALESCE(p.amount_paid, 0) = 0
    GROUP BY p.id, p.loan_id, l.interest_rate, l.management_fee_rate, p.principal_component
),
calc AS (
    SELECT
        id,
        ROUND(opening_balance * interest_rate / 100.00, 2) AS interest,
        ROUND(opening_balance * management_rate / 100.00, 2) AS management_fee,
        ROUND(principal_component +
              (opening_balance * interest_rate / 100.00) +
              (opening_balance * management_rate / 100.00), 2) AS installment_amount,
        ROUND(closing_balance, 2) AS outstanding_after
    FROM ordered
)
UPDATE payments p
SET interest_component = c.interest,
    management_fee_component = c.management_fee,
    scheduled_interest = c.interest,
    scheduled_management_fee = c.management_fee,
    cycle_interest_due = c.interest,
    cycle_interest_remaining = c.interest,
    cycle_management_fee_due = c.management_fee,
    cycle_management_fee_remaining = c.management_fee,
    amount = c.installment_amount,
    outstanding_after = c.outstanding_after
FROM calc c
WHERE p.id = c.id;

WITH totals AS (
    SELECT
        p.loan_id,
        ROUND(SUM(COALESCE(p.scheduled_interest, 0))::numeric, 2) AS total_interest,
        ROUND(SUM(COALESCE(p.scheduled_management_fee, 0))::numeric, 2) AS total_management_fee
    FROM payments p
    JOIN loans l ON l.id = p.loan_id
    WHERE COALESCE(l.imported, FALSE) = FALSE
      AND l.import_batch_id IS NULL
    GROUP BY p.loan_id
)
UPDATE loans l
SET total_interest = t.total_interest,
    management_fee = t.total_management_fee,
    total_repayable = ROUND(COALESCE(l.amount, 0) + t.total_interest + t.total_management_fee, 2),
    interest_outstanding = GREATEST(ROUND(t.total_interest - COALESCE(l.interest_paid, 0), 2), 0),
    management_fee_outstanding = GREATEST(ROUND(t.total_management_fee - COALESCE(l.management_fee_paid, 0), 2), 0)
FROM totals t
WHERE l.id = t.loan_id;

COMMIT;