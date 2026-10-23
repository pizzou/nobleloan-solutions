-- All new loan references (online origination AND legacy imports) now share
-- ONE globally unique, concurrency-safe numeric sequence: NLS-000001, ...
-- Existing loan references and posted/accounting data are NOT renumbered.
-- This migration is intentionally small for a low-memory production server.

ALTER TABLE public.loans
    ADD COLUMN IF NOT EXISTS legacy_source_reference VARCHAR(100),
    ADD COLUMN IF NOT EXISTS legacy_import_fingerprint VARCHAR(64);

-- The source identifier and row digest are separate from the official public
-- reference. Constraints ensure an import retry cannot duplicate the loan.
CREATE UNIQUE INDEX IF NOT EXISTS uq_loans_legacy_source_per_org
    ON public.loans (organization_id, LOWER(BTRIM(legacy_source_reference)))
    WHERE legacy_source_reference IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_loans_legacy_fingerprint_per_org
    ON public.loans (organization_id, legacy_import_fingerprint)
    WHERE legacy_import_fingerprint IS NOT NULL;

CREATE SEQUENCE IF NOT EXISTS public.nls_loan_reference_seq
    AS BIGINT INCREMENT BY 1 MINVALUE 1 START WITH 1 CACHE 1;

-- Resume AFTER the highest existing NLS-<digits>, whatever its zero padding.
-- Ignore old opaque NLS-LEGACY-... and NLS-<hex> references; they remain valid.
-- Flyway applies the migration once, before application services are exposed.
DO $$
DECLARE
    v_max NUMERIC;
BEGIN
    SELECT COALESCE(MAX(SUBSTRING(BTRIM(reference_number) FROM 5)::NUMERIC), 0)
      INTO v_max
      FROM public.loans
     WHERE BTRIM(reference_number) ~* '^NLS-[0-9]+$';

    IF v_max >= 9223372036854775807 THEN
        RAISE EXCEPTION 'Existing NLS loan number is beyond BIGINT sequence capacity';
    END IF;

    IF v_max > 0 THEN
        PERFORM setval('public.nls_loan_reference_seq', v_max::BIGINT, true);
    ELSE
        PERFORM setval('public.nls_loan_reference_seq', 1, false);
    END IF;
END $$;

-- Production deploys can use a separate, least-privilege loansaas_app role.
-- The Flyway owner must explicitly authorize sequence usage in that case.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'loansaas_app') THEN
        GRANT USAGE, SELECT ON SEQUENCE public.nls_loan_reference_seq TO loansaas_app;
    END IF;
END $$;
