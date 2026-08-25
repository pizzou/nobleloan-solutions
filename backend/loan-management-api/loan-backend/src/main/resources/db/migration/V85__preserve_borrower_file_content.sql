-- V85: Preserve legacy borrower-file bytes when an older deployment used
-- PostgreSQL large-object (OID) storage for borrower_files.data.
--
-- IMPORTANT:
--   Do not DROP the column. If it is still an OID column, convert each large
--   object to bytea with lo_get() so existing document bytes are preserved.
--   If the current database already uses BYTEA, this migration is a no-op.
--
-- New application writes use BYTEA directly through BorrowerFile.data.

DO $$
DECLARE
    data_type_name TEXT;
BEGIN
    SELECT c.data_type
      INTO data_type_name
      FROM information_schema.columns c
     WHERE c.table_schema = current_schema()
       AND c.table_name = 'borrower_files'
       AND c.column_name = 'data';

    IF data_type_name = 'oid' THEN
        ALTER TABLE borrower_files
            ALTER COLUMN data TYPE BYTEA
            USING CASE
                    WHEN data IS NULL THEN NULL
                    ELSE lo_get(data)
                  END;
    ELSIF data_type_name IS NULL THEN
        ALTER TABLE borrower_files
            ADD COLUMN data BYTEA;
    END IF;
END $$;

-- Explicitly document the intended storage type for operators and migration
-- tooling. No destructive operation is performed here.
