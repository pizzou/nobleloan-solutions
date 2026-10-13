package com.patrick.fintech.loan_backend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

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
        String scopeSchemaMigration = Files.readString(
                Path.of("src/main/resources/db/migration/V116__reversible_loan_recycle_reporting_scope.sql"),
                StandardCharsets.UTF_8).toLowerCase();
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/V119__repair_recycled_loan_ledger_scope.sql"),
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
        assertTrue(scopeSchemaMigration.contains("original_business_owner_only"),
                "Migration must preserve the loan's original reporting scope");
        assertTrue(scopeSchemaMigration.contains("recycle_loan_id"),
                "Migration must identify journal/settlement rows affected by recycle");
        assertTrue(scopeSchemaMigration.contains("idx_journal_entries_recycle_scope"),
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

    @Test
    void recycledLoanLedgerMustBeExcludedFromActiveAccountingForEveryRole() throws Exception {
        String journalModel = Files.readString(source("model/JournalEntry.java"), StandardCharsets.UTF_8);
        String journalRepository = Files.readString(source("repository/JournalEntryRepository.java"), StandardCharsets.UTF_8);
        String lineRepository = Files.readString(source("repository/JournalLineRepository.java"), StandardCharsets.UTF_8);
        String administrationService = Files.readString(source("service/LoanAdministrationService.java"), StandardCharsets.UTF_8);

        assertTrue(journalModel.contains("@Column(name = \"recycle_loan_id\")"),
                "The immutable journal must map the recycle marker already present in the schema");
        assertTrue(journalRepository.split("recycleLoanId IS NULL", -1).length >= 5,
                "Every visible journal-entry report query, including Business Owner queries, must exclude recycled entries");
        assertTrue(lineRepository.contains("e.recycleLoanId IS NULL"),
                "Account-ledger queries must exclude recycled journal lines for every role");
        assertTrue(lineRepository.contains("je.recycle_loan_id IS NULL"),
                "Native BNR aggregates and validation must exclude recycled journal entries");
        String settlementModel = Files.readString(source("model/PaymentSettlement.java"), StandardCharsets.UTF_8);
        String settlementService = Files.readString(source("service/PaymentSettlementService.java"), StandardCharsets.UTF_8);
        assertTrue(settlementModel.contains("name = \"recycle_loan_id\""),
                "Retained settlement evidence must expose its recycle marker to the operational reporting boundary");
        assertTrue(settlementService.contains("s.getRecycleLoanId() == null"),
                "Recycled settlements must not remain in the active settlement queue");
        assertTrue(settlementService.contains("settlement.getRecycleLoanId() != null"),
                "Direct settlement reconciliation must reject recycled-loan evidence until restore");
        assertTrue(administrationService.contains("recycle_loan_id = :loanId"),
                "Recycle must mark related immutable journal entries");
        assertTrue(administrationService.contains("recycle_loan_id = NULL"),
                "Restore must clear the marker and return entries to their original active reporting scope");
    }

    @Test
    void recycleBinPageMustAcceptTheUnwrappedApiArray() throws Exception {
        Path page = Path.of("../../../frontend/loan-management-ui/app/dashboard/loans/recycle-bin/page.tsx");
        if (!Files.exists(page)) {
            page = Path.of("../../../../frontend/loan-management-ui/app/dashboard/loans/recycle-bin/page.tsx");
        }
        if (!Files.exists(page)) {
            page = Path.of("frontend/loan-management-ui/app/dashboard/loans/recycle-bin/page.tsx");
        }
        // The backend Docker build context intentionally copies only pom.xml and src/.
        // In that isolated context the frontend source is not present, so skip this
        // cross-project static assertion rather than failing an otherwise valid backend
        // build. When run from the repository root or backend project in a full checkout,
        // one of the paths above resolves and the frontend behavior is still asserted.
        Assumptions.assumeTrue(Files.exists(page),
                "Frontend source is outside this backend-only build context; run this regression from the full repository checkout");
        String content = Files.readString(page, StandardCharsets.UTF_8);
        assertTrue(content.contains("Array.isArray(response)"),
                "The API helper returns the unwrapped array, not necessarily an object with data");
        assertTrue(content.contains("loanApi.restore(item.id, confirmation)"),
                "Restore action must remain connected to the restore API");
    }
}
