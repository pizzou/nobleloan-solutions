package com.patrick.fintech.loan_backend.service;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.ResultSet;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PostgreSqlMigrationIntegrationTest {

    @Test
    void completeFlywayChainCreatesRequiredFinancialControls() throws Exception {
        Assumptions.assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "Docker is required for the PostgreSQL integration test");

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();

            Flyway flyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .cleanDisabled(true)
                    .callbacks(new com.patrick.fintech.loan_backend.config.LegacyLoanFeeMigrationPreflight())
                    .load();

            flyway.migrate();

            try (var connection = postgres.createConnection("");
                    var tables = connection.getMetaData().getTables(null, "public", "financial_approvals", null)) {
                assertThat(tables.next()).isTrue();
            }

            try (var connection = postgres.createConnection("");
                    var statement = connection.createStatement();
                    ResultSet result = statement.executeQuery(
                            "select count(*) from information_schema.columns "
                                    + "where table_schema='public' and table_name='audit_logs' "
                                    + "and column_name in ('previous_hash','entry_hash','module')")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isEqualTo(3L);
            }

            try (var connection = postgres.createConnection("");
                    var statement = connection.createStatement();
                    ResultSet result = statement.executeQuery(
                            "select count(*) from pg_indexes "
                                    + "where schemaname='public' and indexname='uk_fin_approval_pending_operation'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isEqualTo(1L);
            }
        }
    }

    @Test
    void existingDatabaseCanUpgradeFromAnEarlierSupportedSchemaVersion() throws Exception {
        Assumptions.assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "Docker is required for the PostgreSQL integration test");

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();

            // Build a real schema at an earlier migration boundary, as would
            // exist on an older deployment, then run the exact same remaining
            // migration chain used by a normal production upgrade.
            Flyway earlier = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("60"))
                    .cleanDisabled(true)
                    .validateOnMigrate(true)
                    .callbacks(new com.patrick.fintech.loan_backend.config.LegacyLoanFeeMigrationPreflight())
                    .load();
            earlier.migrate();

            long organizationId;
            long borrowerId;
            try (var connection = postgres.createConnection("");
                    var statement = connection.createStatement()) {
                try (ResultSet result = statement.executeQuery(
                        "insert into organizations(name, country, default_currency, timezone, locale) "
                                + "values ('Upgrade Fixture Finance', 'RW', 'RWF', 'Africa/Kigali', 'en-RW') "
                                + "returning id")) {
                    assertThat(result.next()).isTrue();
                    organizationId = result.getLong(1);
                }
                try (var borrowerInsert = connection.prepareStatement(
                        "insert into borrowers(organization_id, first_name, last_name, phone_hash, national_id_hash, status, kyc_status) "
                                + "values (?, 'Upgrade', 'Borrower', 'upgrade-phone-hash', 'upgrade-national-id-hash', 'ACTIVE', 'PENDING') returning id")) {
                    borrowerInsert.setLong(1, organizationId);
                    try (ResultSet result = borrowerInsert.executeQuery()) {
                        assertThat(result.next()).isTrue();
                        borrowerId = result.getLong(1);
                    }
                }
                try (var loanInsert = connection.prepareStatement(
                        "insert into loans(organization_id, borrower_id, reference_number, loan_type, status, amount, "
                                + "interest_rate, duration_months, currency, total_paid, outstanding_balance, total_repayable, "
                                + "application_fee_rate, application_fee, disbursed_amount) "
                                + "values (?, ?, 'UPGRADE-FIXTURE-001', 'BUSINESS', 'ACTIVE', 1000000, 5, 3, 'RWF', "
                                + "0, 1000000, 1300000, 2, 20000, 980000)")) {
                    loanInsert.setLong(1, organizationId);
                    loanInsert.setLong(2, borrowerId);
                    assertThat(loanInsert.executeUpdate()).isEqualTo(1);
                }
            }

            try (var connection = postgres.createConnection("");
                    var statement = connection.createStatement();
                    ResultSet result = statement.executeQuery(
                            "select count(*) from flyway_schema_history where success=true and version is not null")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isEqualTo(60L);
            }

            Flyway upgrade = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .cleanDisabled(true)
                    .validateOnMigrate(true)
                    .callbacks(new com.patrick.fintech.loan_backend.config.LegacyLoanFeeMigrationPreflight())
                    .load();
            upgrade.validate();
            upgrade.migrate();

            try (var connection = postgres.createConnection("");
                    var statement = connection.createStatement();
                    ResultSet result = statement.executeQuery(
                            "select count(*) from flyway_schema_history where success=true and version is not null")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isEqualTo(114L);
            }

            try (var connection = postgres.createConnection("");
                    var tables = connection.getMetaData().getTables(null, "public", "loan_deletion_tombstones", null)) {
                assertThat(tables.next()).isTrue();
            }
            try (var connection = postgres.createConnection("");
                    var statement = connection.createStatement();
                    ResultSet result = statement.executeQuery(
                            "select count(*) from loans where reference_number='UPGRADE-FIXTURE-001' "
                                    + "and organization_id=" + organizationId + " and borrower_id=" + borrowerId)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isEqualTo(1L);
            }
            try (var connection = postgres.createConnection("");
                    var statement = connection.createStatement();
                    ResultSet result = statement.executeQuery(
                            "select amount, outstanding_balance, application_fee_paid "
                                    + "from loans where reference_number='UPGRADE-FIXTURE-001'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBigDecimal("amount").compareTo(new BigDecimal("1000000"))).isZero();
                assertThat(result.getBigDecimal("outstanding_balance").compareTo(new BigDecimal("1000000"))).isZero();
                assertThat(result.getBigDecimal("application_fee_paid").compareTo(new BigDecimal("20000"))).isZero();
            }
        }
    }
}
