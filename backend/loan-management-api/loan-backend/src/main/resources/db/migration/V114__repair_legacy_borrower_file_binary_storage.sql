-- ============================================================================
-- V114 - Repair legacy borrower-file large-object storage
-- ============================================================================
--
-- The application stores BorrowerFile.data as PostgreSQL BYTEA. Some older
-- deployments incorrectly used an OID/large-object column. The old migration
-- attempted to detect OID through information_schema, but PostgreSQL can expose
-- that legacy type as USER-DEFINED. This migration uses pg_catalog's actual
-- atttypid and converts OID -> BYTEA with lo_get() without dropping file bytes.
--
-- BYTEA installations are a no-op. Invalid/orphaned OIDs are rejected rather
-- than silently destroying documents.
-- ============================================================================

DO $$
DECLARE
    data_type_oid OID;
    invalid_count BIGINT;
BEGIN
    IF to_regclass('public.borrower_files') IS NULL THEN
        RAISE EXCEPTION 'Required table public.borrower_files does not exist';
    END IF;

    SELECT a.atttypid
      INTO data_type_oid
      FROM pg_catalog.pg_attribute a
      JOIN pg_catalog.pg_class c ON c.oid = a.attrelid
      JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'public'
       AND c.relname = 'borrower_files'
       AND a.attname = 'data'
       AND a.attnum > 0
       AND NOT a.attisdropped;

    IF data_type_oid IS NULL THEN
        ALTER TABLE public.borrower_files ADD COLUMN data BYTEA;
        RETURN;
    END IF;

    IF data_type_oid = 'oid'::regtype::oid THEN
        SELECT count(*)
          INTO invalid_count
          FROM public.borrower_files f
         WHERE f.data IS NOT NULL
           AND NOT EXISTS (
               SELECT 1
                 FROM pg_catalog.pg_largeobject_metadata lom
                WHERE lom.oid = f.data::oid
           );

        IF invalid_count > 0 THEN
            RAISE EXCEPTION
                'Cannot safely convert public.borrower_files.data: % rows reference missing PostgreSQL large objects',
                invalid_count;
        END IF;

        ALTER TABLE public.borrower_files
            ALTER COLUMN data TYPE BYTEA
            USING CASE
                WHEN data IS NULL THEN NULL
                ELSE lo_get(data::oid)
            END;
    ELSIF data_type_oid <> 'bytea'::regtype::oid THEN
        RAISE EXCEPTION
            'Unsupported public.borrower_files.data type OID %; expected BYTEA or OID',
            data_type_oid;
    END IF;
END $$;
