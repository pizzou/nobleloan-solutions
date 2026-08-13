-- V56__add_loan_collections_fields.sql


ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS arrears_status VARCHAR(20);

ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS classified_at TIMESTAMP;

ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS collections_stage VARCHAR(20);


-- ================================================================
-- 2. BACKFILL EXISTING LOANS
-- ================================================================

UPDATE loans
SET arrears_status =
    CASE
        WHEN COALESCE(days_overdue, 0) > 0
            THEN 'PAST_DUE'
        ELSE 'NOT_DUE'
    END
WHERE arrears_status IS NULL;


UPDATE loans
SET collections_stage =
    CASE
        WHEN COALESCE(days_overdue, 0) <= 0
            THEN 'NORMAL'

        WHEN days_overdue BETWEEN 1 AND 89
            THEN 'REMINDER'

        WHEN days_overdue BETWEEN 90 AND 179
            THEN 'COLLECTION'

        WHEN days_overdue BETWEEN 180 AND 359
            THEN 'LEGAL'

        ELSE 'RECOVERY'
    END
WHERE collections_stage IS NULL;


-- ================================================================
-- 3. DATABASE DEFAULTS
-- ================================================================

ALTER TABLE loans
    ALTER COLUMN arrears_status SET DEFAULT 'NOT_DUE';

ALTER TABLE loans
    ALTER COLUMN collections_stage SET DEFAULT 'NORMAL';


-- ================================================================
-- 4. NOT NULL
-- ================================================================

ALTER TABLE loans
    ALTER COLUMN arrears_status SET NOT NULL;

ALTER TABLE loans
    ALTER COLUMN collections_stage SET NOT NULL;


-- ================================================================
-- 5. VALIDATE ARREARS STATUS
-- ================================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_loans_arrears_status'
    ) THEN
        ALTER TABLE loans
            ADD CONSTRAINT chk_loans_arrears_status
            CHECK (
                arrears_status IN (
                    'NOT_DUE',
                    'PAST_DUE'
                )
            );
    END IF;
END
$$;


-- ================================================================
-- 6. VALIDATE COLLECTIONS STAGE
-- ================================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_loans_collections_stage'
    ) THEN
        ALTER TABLE loans
            ADD CONSTRAINT chk_loans_collections_stage
            CHECK (
                collections_stage IN (
                    'NORMAL',
                    'REMINDER',
                    'COLLECTION',
                    'LEGAL',
                    'RECOVERY'
                )
            );
    END IF;
END
$$;


-- ================================================================
-- 7. INDEXES
-- ================================================================

CREATE INDEX IF NOT EXISTS idx_loans_arrears_status
    ON loans (arrears_status);

CREATE INDEX IF NOT EXISTS idx_loans_collections_stage
    ON loans (collections_stage);

CREATE INDEX IF NOT EXISTS idx_loans_classified_at
    ON loans (classified_at);


