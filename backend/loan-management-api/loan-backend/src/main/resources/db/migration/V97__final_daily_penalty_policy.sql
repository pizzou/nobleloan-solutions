-- V97__final_daily_penalty_policy.sql
--
-- Final NobleLoan penalty policy:
--   * 3 calendar-day grace period after contractual due date
--   * 10% of outstanding principal per chargeable day
--   * no monthly penalty calculation
--
-- Existing loan_products historically used 15% as the product default.
-- The application engine now uses DAILY_PENALTY_RATE as the authoritative
-- calculation. Align active product configuration as well so the dashboard,
-- public portal and operational configuration cannot advertise 15% monthly.

UPDATE loan_products
SET penalty_percent = 10.00
WHERE penalty_percent IS NULL
   OR penalty_percent = 15.00;

ALTER TABLE loan_products
    ALTER COLUMN penalty_percent SET DEFAULT 10.00;

COMMENT ON COLUMN loan_products.penalty_percent IS
    'Daily overdue penalty percentage applied to outstanding principal after the platform 3-day grace period.';
