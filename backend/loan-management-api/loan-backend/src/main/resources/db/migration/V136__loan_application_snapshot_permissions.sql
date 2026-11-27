-- V134 introduced encrypted application snapshots. In production the least-
-- privilege application role is separate from the Flyway migration role.
-- Without this grant the DOCX endpoint fails with SQLSTATE 42501.
-- No loan data, historical balances, or encrypted answers are changed.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'loansaas_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE
            ON TABLE public.loan_application_snapshots TO loansaas_app;
        GRANT USAGE, SELECT
            ON SEQUENCE public.loan_application_snapshots_id_seq TO loansaas_app;
    END IF;
END $$;
