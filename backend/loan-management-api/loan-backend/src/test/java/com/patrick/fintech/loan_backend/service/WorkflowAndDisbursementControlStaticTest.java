package com.patrick.fintech.loan_backend.service;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class WorkflowAndDisbursementControlStaticTest {

    private static Path source(String relative) {
        Path direct = Path.of("src/main/java/com/patrick/fintech/loan_backend", relative);
        if (Files.exists(direct)) return direct;
        Path repo = Path.of("backend/loan-management-api/loan-backend/src/main/java/com/patrick/fintech/loan_backend", relative);
        if (Files.exists(repo)) return repo;
        throw new AssertionError("Source file not found: " + relative);
    }

    @Test
    void disbursementExcludesBusinessOwnerAndAccountingAtBothBoundaries() throws Exception {
        String controller = Files.readString(source("controller/LoanController.java"), StandardCharsets.UTF_8);
        String service = Files.readString(source("service/LoanService.java"), StandardCharsets.UTF_8);

        assertTrue(controller.contains("@PreAuthorize(\"hasAnyRole('LOAN_OFFICER','TELLER','MANAGER','ADMIN')\")"),
                "Loan disbursement HTTP endpoint must be restricted to authorized operational roles");
        assertTrue(service.contains("requireAuthorizedDisbursementOfficer(officer);"),
                "LoanService must enforce operational-role authorization even when called internally");
        assertTrue(service.contains("BUSINESS_OWNER"), "Business owners must be explicitly excluded");
        assertTrue(service.contains("accounting users cannot disburse loans"),
                "Accounting users must be explicitly excluded from execution");
    }

    @Test
    void bulkDisbursementIsRestrictedAndGenericTaskCompletionCannotFakeDisbursement() throws Exception {
        String bulkController = Files.readString(source("controller/BulkController.java"), StandardCharsets.UTF_8);
        String taskService = Files.readString(source("service/WorkflowTaskService.java"), StandardCharsets.UTF_8);

        assertTrue(bulkController.contains("@PreAuthorize(\"hasAnyRole('LOAN_OFFICER','TELLER','MANAGER','ADMIN')\")"),
                "Bulk disbursement endpoint must use the same operational-role boundary");
        assertTrue(taskService.contains("Disbursement tasks can only be completed by the successful loan disbursement workflow"),
                "A generic workflow task completion must not substitute for actual disbursement and accounting");
    }

    @Test
    void approvedLoansCreateDurableDisbursementTask() throws Exception {
        String service = Files.readString(source("service/LoanService.java"), StandardCharsets.UTF_8);
        String taskService = Files.readString(source("service/WorkflowTaskService.java"), StandardCharsets.UTF_8);

        assertTrue(service.contains("workflowTaskService.createDisbursementTask(saved, approvedBy);"),
                "Final approval must create a durable disbursement task");
        assertTrue(taskService.contains("findFirstActiveByOrganizationAndRole"),
                "Disbursement task must resolve an active operational user in the same organization");
        assertTrue(taskService.contains("List.of(\"LOAN_OFFICER\", \"TELLER\", \"MANAGER\", \"ADMIN\")"),
                "Task assignment must use operational roles, not Business Owner");
        assertTrue(service.contains("workflowTaskService.completeDisbursementTaskForLoan(saved.getId(), officer);"),
                "Successful disbursement must close the mandatory owner task atomically");
    }

    @Test
    void recycleBinHasThirtyDayRestoreWindowAndPermanentPurge() throws Exception {
        String service = Files.readString(source("service/LoanAdministrationService.java"), StandardCharsets.UTF_8);
        String controller = Files.readString(source("controller/LoanController.java"), StandardCharsets.UTF_8);
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/V106__loan_recycle_bin_and_reporting_exclusion.sql"),
                StandardCharsets.UTF_8);

        assertTrue(service.contains("RECYCLE_DAYS = 30"),
                "Recycle retention must remain 30 days");
        assertTrue(service.contains("restoreWithConfirmation"),
                "Business Owner must have a restore service path");
        assertTrue(service.contains("purgeExpiredRecycledLoans"),
                "Expired recycled loans must have an automatic purge path");
        assertTrue(controller.contains("/recycle-bin"),
                "Loan controller must expose the Business Owner recycle-bin list");
        assertTrue(migration.contains("purge_after"),
                "Recycle-bin schema must persist the permanent-purge deadline");
    }

    @Test
    void businessOwnerCanReactivateDeactivatedUser() throws Exception {
        String controller = Files.readString(source("controller/UserController.java"), StandardCharsets.UTF_8);
        assertTrue(controller.contains("@PreAuthorize(\"hasAnyRole('ADMIN','BUSINESS_OWNER')\")"),
                "Business Owner must be able to restore a deactivated staff account");
    }

    @Test
    void mtnWebhookUsesProductionPropertyName() throws Exception {
        String controller = Files.readString(
                source("controller/PaymentWebhookController.java"), StandardCharsets.UTF_8);
        assertTrue(controller.contains("@Value(\"${mtn.momo.webhook-secret:}\")"),
                "MTN webhook verification must read the same property configured by production");
        assertTrue(!controller.contains("mtn.mobile-money.webhook-secret"),
                "Obsolete MTN webhook property name must not remain");
    }

    @Test
    void taskAssignmentHasAfterCommitNotification() throws Exception {
        String listener = Files.readString(source("service/WorkflowTaskNotificationListener.java"), StandardCharsets.UTF_8);
        assertTrue(listener.contains("TransactionPhase.AFTER_COMMIT"),
                "Task notifications must not be sent for rolled-back task transactions");
    }
}
