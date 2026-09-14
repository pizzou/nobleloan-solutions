-- V104: snapshot the contractual overdue-penalty rate on each loan.
-- The stored value is a MONTHLY percentage; daily accrual prorates it by
-- the actual calendar days in each charge date month.

ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS penalty_rate NUMERIC(19, 9);

UPDATE loans
SET penalty_rate = 10.00
WHERE penalty_rate IS NULL;

ALTER TABLE loans
    ALTER COLUMN penalty_rate SET DEFAULT 10.00,
    ALTER COLUMN penalty_rate SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_loans_penalty_rate
    ON loans (organization_id, penalty_rate);

COMMENT ON COLUMN loans.penalty_rate IS
    'Contractual overdue penalty percentage per month; daily charge is prorated by actual calendar days after the 3-day grace period.';
