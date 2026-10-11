package com.patrick.fintech.loan_backend.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.ResultSet;

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
}
