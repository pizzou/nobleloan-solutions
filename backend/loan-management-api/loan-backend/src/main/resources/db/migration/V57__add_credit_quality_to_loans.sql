
ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS credit_quality VARCHAR(20);


-- 2. Backfill existing loans using days_overdue.
UPDATE loans
SET credit_quality =
    CASE
        WHEN COALESCE(days_overdue, 0) <= 0
            THEN 'CURRENT'

        WHEN days_overdue BETWEEN 1 AND 89
            THEN 'WATCH'

        WHEN days_overdue BETWEEN 90 AND 179
            THEN 'SUBSTANDARD'

        WHEN days_overdue BETWEEN 180 AND 359
            THEN 'DOUBTFUL'

        ELSE 'WRITTEN_OFF'
    END
WHERE credit_quality IS NULL;


-- 3. Set the database default.
ALTER TABLE loans
    ALTER COLUMN credit_quality SET DEFAULT 'CURRENT';


-- 4. Hibernate expects this field to be non-null.
ALTER TABLE loans
    ALTER COLUMN credit_quality SET NOT NULL;


-- 5. Protect the allowed enum values.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_loans_credit_quality'
    ) THEN
        ALTER TABLE loans
            ADD CONSTRAINT chk_loans_credit_quality
            CHECK (
                credit_quality IN (
                    'CURRENT',
                    'WATCH',
                    'SUBSTANDARD',
                    'DOUBTFUL',
                    'WRITTEN_OFF'
                )
            );
    END IF;
END
$$;


-- 6. Index for credit-quality queries.
CREATE INDEX IF NOT EXISTS idx_loans_credit_quality
    ON loans (credit_quality);


-- ================================================================
-- END V57
-- ================================================================

