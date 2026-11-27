-- Bank-grade PostgreSQL role separation bootstrap for Noble Loan Solutions.
-- Run only as a PostgreSQL administrator. Passwords are supplied by Compose
-- variables and are never stored in this file.
--
-- Runtime account : loansaas_app      (least-privilege CRUD)
-- Migration account: loansaas_migrator (owns schema objects, runs Flyway)
-- Admin account  : loansaas            (cluster/database administration only)

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
ALTER ROLE loansaas_app NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS;

SELECT format('CREATE ROLE loansaas_migrator LOGIN PASSWORD %L', :'MIGRATOR_PASSWORD')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'loansaas_migrator')\gexec
ALTER ROLE loansaas_migrator LOGIN PASSWORD :'MIGRATOR_PASSWORD';
ALTER ROLE loansaas_migrator NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT NOREPLICATION NOBYPASSRLS;

-- This is the database name used by docker-compose.yml and application.properties.
ALTER DATABASE loansaas_nobleloansolutions OWNER TO loansaas_migrator;
\connect loansaas_nobleloansolutions

-- Isolated test database only: this database volume is brand new and contains
-- no legacy application tables. We intentionally do not run blanket REASSIGN
-- OWNED here because it can include shared objects from other databases.

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON DATABASE loansaas_nobleloansolutions FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;

GRANT CONNECT ON DATABASE loansaas_nobleloansolutions TO loansaas_migrator;
GRANT USAGE, CREATE ON SCHEMA public TO loansaas_migrator;

GRANT CONNECT ON DATABASE loansaas_nobleloansolutions TO loansaas_app;
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

-- Deliberately keep the administrative `loansaas` login intact for controlled
-- maintenance, backup/restore, and role bootstrap. It must not be used by the app.
