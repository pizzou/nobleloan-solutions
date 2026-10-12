package com.patrick.fintech.loan_backend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class LoanRecycleAccountingSafetyStaticTest {

    private static Path source(String relative) {
        Path direct = Path.of("src/main/java/com/patrick/fintech/loan_backend", relative);
        if (Files.exists(direct)) return direct;
        Path repo = Path.of("backend/loan-management-api/loan-backend/src/main/java/com/patrick/fintech/loan_backend", relative);
        if (Files.exists(repo)) return repo;
        throw new AssertionError("Source file not found: " + relative);
    }

    @Test
    void recycleBinMustNotHidePostedLedgerHistory() throws Exception {
        String service = Files.readString(source("service/LoanAdministrationService.java"), StandardCharsets.UTF_8);
        String journal = Files.readString(source("model/JournalEntry.java"), StandardCharsets.UTF_8);
        String repository = Files.readString(source("repository/JournalLineRepository.java"), StandardCharsets.UTF_8);
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V115__preserve_ledger_history_for_recycled_loans.sql"), StandardCharsets.UTF_8).toLowerCase();

        assertTrue(service.contains("ensureNoFinancialOrProtectedHistory(organizationId, loanId, reference)"),
                "Loans with posted financial history must not use ordinary recycle/delete");
        assertTrue(service.contains("LOAN_PURGE_BLOCKED_FINANCIAL_HISTORY"),
                "Purge must stop and record an audit event if financial activity is detected");
        assertFalse(service.contains("hideLoanJournals(organizationId, loanId, reference)"),
                "Recycling a loan must never hide its posted journals");
        assertFalse(journal.contains("@SQLRestriction("),
                "JournalEntry JPA queries must not automatically suppress rows by recycle state");
        assertFalse(repository.contains("NOT public.loan_journal_is_recycled("),
                "Native ledger aggregates must not filter recycled loan journals");
        assertTrue(migration.contains("set hidden_loan_id = null"),
                "Migration must restore any historically suppressed ledger rows");
        assertTrue(migration.contains("select false"),
                "Legacy recycled-journal predicate must become a non-suppressing shim");
        assertTrue(service.contains("FROM esignature_requests es"),
                "Signed loan agreements must be retained as contractual evidence");
        assertTrue(service.contains("FROM credit_bureau_submission_records cbsr"),
                "Submitted credit-bureau records must not be purged as ordinary loan children");
        assertTrue(service.contains("FROM payment_settlements ps"),
                "Settlement evidence must prevent permanent loan purge");
    }
}
