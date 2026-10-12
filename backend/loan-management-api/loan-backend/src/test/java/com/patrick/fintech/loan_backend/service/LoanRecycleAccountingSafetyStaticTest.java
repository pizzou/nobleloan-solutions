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
    void recycleBinMustRestrictReportingScopeAndPreservePostedLedgerHistory() throws Exception {
        String service = Files.readString(source("service/LoanAdministrationService.java"), StandardCharsets.UTF_8);
        String journal = Files.readString(source("model/JournalEntry.java"), StandardCharsets.UTF_8);
        String repository = Files.readString(source("repository/JournalLineRepository.java"), StandardCharsets.UTF_8);
        String migration115 = Files.readString(Path.of("src/main/resources/db/migration/V115__preserve_ledger_history_for_recycled_loans.sql"), StandardCharsets.UTF_8).toLowerCase();
        String migration116 = Files.readString(Path.of("src/main/resources/db/migration/V116__reversible_loan_recycle_reporting_scope.sql"), StandardCharsets.UTF_8).toLowerCase();

        String deleteMethod = service.substring(
                service.indexOf("public void deleteWithConfirmation(\n            Long loanId,\n            String confirmation,\n            String reason"),
                service.indexOf("@Transactional(readOnly = true)\n    public List<LoanRecycleBinItem> listRecycleBin"));

        assertFalse(deleteMethod.contains("ensureNoFinancialOrProtectedHistory"),
                "A loan with posted activity must be recyclable; its accounting is restricted rather than destroyed");
        assertTrue(deleteMethod.contains("restrictLoanAccountingToBusinessOwner"),
                "Recycling must hide loan-originated accounting/settlements from normal reporting scope");
        assertTrue(service.contains("restoreLoanAccountingScope(organizationId, loanId)"),
                "Restore must put related journal/settlement scope back exactly as it was");
        assertTrue(service.contains("recycle_loan_id = :loanId"),
                "Per-loan scope marker must prevent restoring another recycled loan's journal rows");
        assertTrue(service.contains("LOAN_PURGE_BLOCKED_FINANCIAL_HISTORY"),
                "Permanent purge must stop and audit if legal or financial evidence still depends on the loan");
        assertFalse(journal.contains("@SQLRestriction("),
                "JournalEntry JPA queries must not automatically suppress rows by recycle state");
        assertFalse(repository.contains("NOT public.loan_journal_is_recycled("),
                "Native ledger aggregates must not filter immutable posted journal history via legacy recycle function");
        assertTrue(migration115.contains("set hidden_loan_id = null"),
                "Legacy hidden markers must be cleared by the historical migration");
        assertTrue(migration115.contains("select false"),
                "Legacy recycled-journal predicate must remain non-suppressing");
        assertTrue(migration116.contains("recycle_original_business_owner_only"),
                "Migration must preserve each journal/settlement's prior reporting scope");
        assertTrue(migration116.contains("recycle_loan_id"),
                "Migration must associate each temporarily restricted journal/settlement with its loan");
        assertTrue(service.contains("FROM esignature_requests es"),
                "Signed loan agreements must be retained as contractual evidence");
        assertTrue(service.contains("FROM credit_bureau_submission_records cbsr"),
                "Submitted credit-bureau records must not be silently erased by permanent purge");
        assertTrue(service.contains("FROM payment_settlements ps"),
                "Settlement evidence must be protected during permanent purge");
    }
}
