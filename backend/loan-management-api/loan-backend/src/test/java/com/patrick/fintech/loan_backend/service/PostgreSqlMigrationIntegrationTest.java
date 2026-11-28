package com.patrick.fintech.loan_backend.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


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
            verifyHistoricalReallocationConstraints(postgres);
            verifyPaymentReviewEvidence(postgres);
        }
    }

    private void verifyPaymentReviewEvidence(PostgreSQLContainer<?> postgres) throws Exception {
        try (var connection = postgres.createConnection(""); var statement = connection.createStatement()) {
            String insert = """
                    INSERT INTO payment_reviews
                        (organization_id, loan_id, request_key, amount, method, reference,
                         submitted_by, submitted_by_name, submitted_at, status)
                    SELECT id, 50, 'review-key', 100, 'BANK_TRANSFER', 'BANK-50',
                           10, 'Test staff', CURRENT_TIMESTAMP, 'PENDING'
                    FROM organizations WHERE name = 'Payment evidence fixture'
                    """;
            statement.executeUpdate("INSERT INTO organizations (name, slug) VALUES ('Payment evidence fixture', 'payment-evidence-fixture')");
            assertThat(statement.executeUpdate(insert)).isEqualTo(1);
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE payment_reviews SET amount = 200"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("Only the first decision");
            assertThatThrownBy(() -> statement.executeUpdate("""
                    UPDATE payment_reviews SET status = 'REJECTED', decided_by = 11, decided_at = CURRENT_TIMESTAMP
                    """)).isInstanceOf(SQLException.class).hasMessageContaining("requires a reason");
            assertThat(statement.executeUpdate("""
                    UPDATE payment_reviews SET status = 'APPROVED', decided_by = 11, decided_at = CURRENT_TIMESTAMP
                    """)).isEqualTo(1);
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE payment_reviews SET decision_reason = 'edited'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("Only the first decision");
            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM payment_reviews"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("cannot be deleted");
        }
    }

    private void verifyHistoricalReallocationConstraints(PostgreSQLContainer<?> postgres) throws Exception {
        String schema = "historical_posting_fixture";
        try (var connection = postgres.createConnection(""); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            statement.execute("CREATE TABLE " + schema + ".payment_transactions (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE " + schema + ".journal_entries (id BIGINT PRIMARY KEY)");
        }

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("142")
                .target("143")
                .load()
                .migrate();

        try (var connection = postgres.createConnection(""); var statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            statement.executeUpdate("INSERT INTO payment_transactions(id) VALUES (5), (6)");
            statement.executeUpdate("INSERT INTO journal_entries(id) VALUES (410), (411)");
            String insert = """
                    INSERT INTO historical_receipt_reallocations
                        (receipt_id, journal_entry_id, as_of_date, amount, penalty, management_fee,
                         interest, principal, extension_fee, unapplied)
                    VALUES
                    """;
            String allocation = "(5, 410, DATE '2026-11-25', 12100000, 5583333.33, 6300000, 216666.67, 0, 0, 0)";
            assertThat(statement.executeUpdate(insert + allocation)).isEqualTo(1);
            assertThatThrownBy(() -> statement.executeUpdate(insert + allocation))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23505"));

            assertThatThrownBy(() -> statement.executeUpdate(insert
                    + "(6, 411, DATE '2026-11-25', 12100000, 0, 0, 0, 0, 0, 0)"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE historical_receipt_reallocations SET as_of_date = DATE '2026-11-26' WHERE receipt_id = 5"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Historical receipt reallocations are immutable");

            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM historical_receipt_reallocations")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(1L);
            }
        }
    }
}
