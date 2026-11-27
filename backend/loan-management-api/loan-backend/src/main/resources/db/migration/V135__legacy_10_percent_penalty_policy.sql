-- Legacy imports: 10% monthly penalty. New system loans: 15% monthly.
-- Migration applied once by Flyway AFTER V134.
-- Do not edit the previously executed V124 penalty migration.
-- Important: existing historical penalties, payments, outstanding balances,
-- financial journals, status and maturity dates are not altered.

UPDATE loans
SET penalty_rate = 10.00
WHERE (COALESCE(imported, FALSE) = TRUE OR import_batch_id IS NOT NULL)
  AND penalty_rate IS DISTINCT FROM 10.00;

-- New system loans keep the database default of 15%.
ALTER TABLE loans ALTER COLUMN penalty_rate SET DEFAULT 15.00;

COMMENT ON COLUMN loans.penalty_rate IS
'Monthly overdue penalty percentage: imported legacy loans 10.00%; system-created loans 15.00%. Penalty is prorated by actual calendar days; three calendar grace days apply before new accrual. Previously assessed or paid historical penalties remain unchanged.';
