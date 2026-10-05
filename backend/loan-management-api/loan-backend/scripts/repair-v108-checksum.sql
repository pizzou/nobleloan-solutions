-- ONE-TIME PRODUCTION RECOVERY FOR FLYWAY V108 CHECKSUM MISMATCH
--
-- Use this only after confirming the deployed database reports:
--   version 108
--   applied checksum 1357083897
-- and the application source resolves V108 to checksum -1941552845.
--
-- This does NOT execute V108 again. It only repairs Flyway's schema-history
-- checksum so validation can proceed. V109 reasserts the application-fee
-- accounting state idempotently.
--
-- Do NOT use flyway clean and do NOT disable Flyway validation.

BEGIN;

UPDATE flyway_schema_history
SET checksum = -1941552845
WHERE version = '108'
  AND checksum = 1357083897;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM flyway_schema_history
        WHERE version = '108'
          AND checksum = -1941552845
    ) THEN
        RAISE EXCEPTION
            'V108 checksum was not repaired. Expected old checksum 1357083897.';
    END IF;
END $$;

COMMIT;
