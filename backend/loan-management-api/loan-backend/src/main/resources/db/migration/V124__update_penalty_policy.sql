

ALTER TABLE loans
    ALTER COLUMN penalty_rate SET DEFAULT 15.00;


ALTER TABLE loan_products
    ALTER COLUMN penalty_percent SET DEFAULT 15.00;


UPDATE loan_products
SET penalty_percent = 15.00
WHERE penalty_percent IS DISTINCT FROM 15.00;


-- ============================================================
-- 4. ADD PENALTY POLICY EFFECTIVE DATE
-- ============================================================

ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS
    penalty_policy_effective_date DATE;



UPDATE loans
SET
    penalty_rate = 15.00,
    penalty_policy_effective_date = CURRENT_DATE
WHERE penalty_rate IS DISTINCT FROM 15.00
   OR penalty_policy_effective_date IS NULL;


-- ============================================================
-- 6. DOCUMENT PENALTY RATE POLICY
-- ============================================================

COMMENT ON COLUMN loans.penalty_rate IS
'Monthly overdue penalty rate expressed as a percentage.
15.00 represents 15% per calendar month.
Penalty is prorated by actual calendar days.
Three full calendar days of grace apply.
New policy applies prospectively from the approved
penalty_policy_effective_date.';


-- ============================================================
-- 7. DOCUMENT LOAN PRODUCT PENALTY POLICY
-- ============================================================

COMMENT ON COLUMN loan_products.penalty_percent IS
'Default monthly overdue penalty percentage.
15.00 represents 15% per calendar month.
Penalty is prorated by actual calendar days.
Three full calendar days of grace apply.';


-- ============================================================
-- 8. DOCUMENT POLICY EFFECTIVE DATE
-- ============================================================

COMMENT ON COLUMN loans.penalty_policy_effective_date IS
'Approved effective date of the revised penalty policy.
Historical penalty assessments before this date must
remain unchanged. Existing loan contractual consent
and legal authorization must be verified before applying
the revised penalty rate.';


