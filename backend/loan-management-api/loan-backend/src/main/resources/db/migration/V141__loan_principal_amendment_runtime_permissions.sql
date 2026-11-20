-- V139 created the amendment ledger using the Flyway migrator role.
-- Production application connections use a separate least-privilege role.
-- Without these grants the history and correction endpoints fail with 42501.
-- Preserve all amendment records and audit triggers; change privileges only.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'loansaas_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE
            ON TABLE public.loan_principal_amendment_requests TO loansaas_app;
        GRANT USAGE, SELECT
            ON SEQUENCE public.loan_principal_amendment_requests_id_seq TO loansaas_app;
    END IF;
END $$;
