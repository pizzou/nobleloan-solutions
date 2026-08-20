WITH RECURSIVE candidates AS (
    SELECT
        l.id,
        l.reference_number,
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
        c.id AS loan_id,
        c.reference_number,
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
        s.reference_number,
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
), calculated AS (
    SELECT
        loan_id,
        reference_number,
        installment_number,
        opening_balance,
        principal_component,
        ROUND(opening_balance * interest_rate / 100.0, 2) AS interest_component,
        ROUND(opening_balance * management_rate / 100.0, 2) AS management_fee_component
    FROM schedule
)
SELECT
    loan_id,
    reference_number,
    COUNT(*) AS installments,
    ROUND(SUM(principal_component), 2) AS principal,
    ROUND(SUM(interest_component), 2) AS expected_interest,
    ROUND(SUM(management_fee_component), 2) AS expected_management_fee,
    ROUND(
        SUM(principal_component)
        + SUM(interest_component)
        + SUM(management_fee_component),
        2
    ) AS expected_contractual_repayment
FROM calculated
GROUP BY loan_id, reference_number
ORDER BY loan_id;