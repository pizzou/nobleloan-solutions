-- Bank-grade PostgreSQL role separation bootstrap.
--
-- Run as a PostgreSQL administrator:
-- psql -v APP_PASSWORD='...' -v MIGRATOR_PASSWORD='...' -f bank-grade-roles.sql
--
-- The application role is runtime-only. Flyway uses the separate migrator
-- role. Never commit real passwords to this file.

\set ON_ERROR_STOP on

\if :{?APP_PASSWORD}
\else
  \echo 'APP_PASSWORD is required'
  \quit 1
\endif

\if :{?MIGRATOR_PASSWORD}
\else
  \echo 'MIGRATOR_PASSWORD is required'
  \quit 1
\endif

SELECT format('CREATE ROLE loansaas_app LOGIN PASSWORD %L', :'APP_PASSWORD')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'loansaas_app')\gexec

ALTER ROLE loansaas_app LOGIN PASSWORD :'APP_PASSWORD';

SELECT format('CREATE ROLE loansaas_migrator LOGIN PASSWORD %L', :'MIGRATOR_PASSWORD')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'loansaas_migrator')\gexec

ALTER ROLE loansaas_migrator LOGIN PASSWORD :'MIGRATOR_PASSWORD';

ALTER ROLE loansaas_app NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
ALTER ROLE loansaas_migrator NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT;

ALTER DATABASE loansaas OWNER TO loansaas_migrator;

\connect loansaas

-- Existing deployments created with the historical `loansaas` owner can be
-- migrated without changing table definitions. REASSIGN OWNED changes only
-- object ownership; it does not delete data or objects.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'loansaas') THEN
        EXECUTE 'REASSIGN OWNED BY loansaas TO loansaas_migrator';
    END IF;
END
$$;

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON DATABASE loansaas FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;

GRANT CONNECT ON DATABASE loansaas TO loansaas_migrator;
GRANT USAGE, CREATE ON SCHEMA public TO loansaas_migrator;

GRANT CONNECT ON DATABASE loansaas TO loansaas_app;
GRANT USAGE ON SCHEMA public TO loansaas_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO loansaas_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO loansaas_app;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO loansaas_app;

ALTER DEFAULT PRIVILEGES FOR ROLE loansaas_migrator IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO loansaas_app;
ALTER DEFAULT PRIVILEGES FOR ROLE loansaas_migrator IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO loansaas_app;
ALTER DEFAULT PRIVILEGES FOR ROLE loansaas_migrator IN SCHEMA public
    GRANT EXECUTE ON FUNCTIONS TO loansaas_app;

REVOKE CREATE ON SCHEMA public FROM loansaas_app;

-- After the application is switched to loansaas_app, the historical owner
-- account must no longer be usable as a runtime login.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'loansaas') THEN
        EXECUTE 'ALTER ROLE loansaas NOLOGIN';
    END IF;
END
$$;
