package com.patrick.fintech.loan_backend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Static guardrails for the owner-only loan recycle/reporting boundary.
 * These tests intentionally distinguish hiding from non-owner reports from
 * deleting/reversing posted journal entries.
 */
class LoanRecycleAccountingSafetyStaticTest {

    private static Path source(String relative) {
        Path direct = Path.of("src/main/java/com/patrick/fintech/loan_backend", relative);
        if (Files.exists(direct)) return direct;
        Path repo = Path.of("backend/loan-management-api/loan-backend/src/main/java/com/patrick/fintech/loan_backend", relative);
        if (Files.exists(repo)) return repo;
        throw new AssertionError("Source file not found: " + relative);
    }

    @Test
    void recycleMustRestrictRelatedAccountingToOwnerAndRestoreOriginalScope() throws Exception {
        String service = Files.readString(source("service/LoanAdministrationService.java"), StandardCharsets.UTF_8);
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/V116__reversible_loan_recycle_reporting_scope.sql"),
                StandardCharsets.UTF_8).toLowerCase();
        String tombstoneBackfillMigration = Files.readString(
                Path.of("src/main/resources/db/migration/V118__repair_legacy_loan_recycle_tombstones.sql"),
                StandardCharsets.UTF_8).toLowerCase();

        assertTrue(service.contains("restrictLoanAccountingToBusinessOwner(organizationId, loanId, reference)"),
                "Recycling a loan must restrict its accounting scope, not leave its entries visible to all staff");
        assertTrue(service.contains("loan.setBusinessOwnerOnly(true)"),
                "The loan itself must become owner-only while recycled");
        assertTrue(service.contains("restoreLoanAccountingScope(organizationId, loanId)"),
                "Restore must reinstate each journal/settlement's original visibility");
        assertTrue(service.contains("purgeAfter == null || !purgeAfter.isAfter(LocalDateTime.now())"),
                "A missing purge deadline must not silently extend a loan's restore window");
        assertTrue(service.contains("recycle_original_business_owner_only"),
                "Original row-level reporting scope must be preserved for lossless restore");
        assertTrue(service.contains("business_owner_only = TRUE"),
                "Related posted journals and settlements must be restricted to the owner");
        assertTrue(migration.contains("original_business_owner_only"),
                "Migration must preserve the loan's original reporting scope");
        assertTrue(migration.contains("recycle_loan_id"),
                "Migration must identify journal/settlement rows affected by recycle");
        assertTrue(migration.contains("idx_journal_entries_recycle_scope"),
                "Recycled-ledger scope lookups must be indexed");
        assertTrue(tombstoneBackfillMigration.contains("insert into public.loan_deletion_tombstones"),
                "Legacy recycled loans without tombstones must get a restorable scope record in a forward-only migration");
        assertTrue(migration.contains("update public.journal_entries"),
                "Existing recycled loans' journal entries must be restricted during migration");
        assertTrue(migration.contains("update public.payment_settlements"),
                "Existing recycled loans' settlements must be restricted during migration");
        assertTrue(migration.contains("'payment-' || p.id::text"),
                "Legacy PAYMENT-prefixed journal source IDs must be included in owner-only scope migration");
    }

    @Test
    void bankAccountBalancesMustRespectOrganizationAndOwnerOnlyScope() throws Exception {
        String service = Files.readString(source("service/BankAccountService.java"), StandardCharsets.UTF_8);
        assertTrue(service.contains("findVisibleByAccount_IdAndOrganization_Id"),
                "Bank-account balances must not use the unscoped journal-line query");
        assertTrue(service.contains("ReportingScopeService.includeBusinessOwnerOnly()"),
                "Bank-account balances must follow the current user's reporting scope");
        assertTrue(service.contains("organizationId"),
                "Bank-account balances must remain organization-scoped");
    }

    @Test
    void recycleMustNotDeletePostedJournalEvidence() throws Exception {
        String service = Files.readString(source("service/LoanAdministrationService.java"), StandardCharsets.UTF_8);
        String journal = Files.readString(source("model/JournalEntry.java"), StandardCharsets.UTF_8);
        String repository = Files.readString(source("repository/JournalLineRepository.java"), StandardCharsets.UTF_8);

        assertFalse(service.contains("DELETE FROM journal_entries"),
                "Recycle must not delete posted journal evidence");
        assertFalse(service.contains("DELETE FROM journal_lines"),
                "Recycle must not delete debit/credit lines");
        assertFalse(journal.contains("@SQLRestriction("),
                "JPA must not globally suppress ledger rows based on recycle state");
        assertTrue(service.contains("FROM esignature_requests es"),
                "Signed loan agreements must be protected from ordinary purge");
        assertTrue(service.contains("FROM credit_bureau_submission_records cbsr"),
                "Submitted credit-bureau records must be protected from ordinary purge");
        assertTrue(service.contains("UPDATE payment_settlements ps"),
                "Settlement evidence must remain in the owner-only reporting scope after loan purge");
        assertTrue(service.contains("hasNonPurgeableLegalOrRegulatoryHistory(loanId)"),
                "Ordinary loan-row purge should be blocked only by legally/regulatorily protected source evidence");
        int gateStart = service.indexOf("private boolean hasNonPurgeableLegalOrRegulatoryHistory");
        int gateEnd = service.indexOf("private void ensureNoPendingPaymentTransaction", gateStart);
        assertTrue(gateStart >= 0 && gateEnd > gateStart, "Protected-evidence purge guard must remain explicit");
        String purgeGuard = service.substring(gateStart, gateEnd);
        assertFalse(purgeGuard.contains("FROM journal_entries"),
                "Posted journal evidence is retained and must not prevent operational loan-row purge");
        assertFalse(purgeGuard.contains("FROM payment_settlements"),
                "Settlements are retained owner-only and must not prevent operational loan-row purge");
        assertFalse(purgeGuard.contains("FROM payments p"),
                "Payment operational rows may be purged while their posted ledger entries remain");
        assertFalse(repository.contains("NOT public.loan_journal_is_recycled("),
                "Ledger aggregates must not use the obsolete global recycle exclusion");
    }
}
