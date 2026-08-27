package com.patrick.fintech.loan_backend.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BankGradeFinancialIntegrityMigrationTest {

    @Test
    void v86CreatesUniquenessBoundariesAndFinancialInvariantGuard() throws Exception {
        Path migration = Path.of(
                "src/main/resources/db/migration/V86__bank_grade_financial_integrity.sql");
        String sql = Files.readString(migration).toLowerCase();

        assertTrue(sql.contains("uq_loans_org_reference_bank_grade"));
        assertTrue(sql.contains("uq_borrowers_org_national_id_hash_bank_grade"));
        assertTrue(sql.contains("enforce_loan_financial_invariants"));
        assertTrue(sql.contains("principal_paid"));
        assertTrue(sql.contains("interest_outstanding"));
        assertTrue(sql.contains("management_fee_outstanding"));
        assertTrue(sql.contains("application_fee_paid"));
        assertTrue(sql.contains("deferrable initially deferred"));
    }
}
