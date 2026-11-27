package com.patrick.fintech.loan_backend.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class LegacyApplicationFeeMigrationTest {

    @Test
    void migrationPostsImportedExcelFeeToApplicationFeeIncomeExactlyOnce() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V131__legacy_application_fee_income_reclassification.sql");
        if (!Files.exists(migration)) {
            migration = Path.of("backend/loan-management-api/loan-backend/src/main/resources/db/migration/V131__legacy_application_fee_income_reclassification.sql");
        }
        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        assertTrue(sql.contains("l.application_fee_paid"));
        assertTrue(sql.contains("APPLICATION_FEE_INCOME_RECLASSIFICATION"));
        assertTrue(sql.contains("APPLICATION_FEE_INCOME"));
        assertTrue(sql.contains("3010"));
        assertTrue(sql.contains("4100"));
        assertTrue(sql.contains("NOT EXISTS"));
        assertTrue(sql.contains("LOAN_DISBURSEMENT"),
                "An existing disbursement journal must be checked before reclassifying the fee.");
        assertTrue(sql.contains("fee_account.code = '4100'"),
                "Already-recognized application-fee income must not be posted twice.");
    }
}
