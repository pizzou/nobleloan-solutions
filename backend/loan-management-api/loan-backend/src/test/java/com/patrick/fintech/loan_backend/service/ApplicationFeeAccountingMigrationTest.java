package com.patrick.fintech.loan_backend.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationFeeAccountingMigrationTest {

    @Test
    void v108MakesApplicationFeeIncomeAndRepairsLegacyDisbursements() throws Exception {
        Path migration = Paths.get(
                "src", "main", "resources", "db", "migration",
                "V108__bank_grade_application_fee_income.sql");
        String sql = Files.readString(migration).toLowerCase();

        assertTrue(sql.contains("'4100' then 'income'"));
        assertTrue(sql.contains("'4100' then 'credit'"));
        assertTrue(sql.contains("application_fee_paid"));
        assertTrue(sql.contains("application_fee_income_reclassification"));
        assertTrue(sql.contains("'3010'"));
        assertTrue(sql.contains("'4100'"));
        assertTrue(sql.contains("loan_journal_is_recycled"));
    }
}
