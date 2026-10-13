
package com.patrick.fintech.loan_backend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class WorkflowAndDisbursementControlStaticTest {

    private static Path source(String relative) {
        Path direct = Path.of(
                "src/main/java/com/patrick/fintech/loan_backend",
                relative);

        if (Files.exists(direct)) {
            return direct;
        }

        Path repo = Path.of(
                "backend/loan-management-api/loan-backend/src/main/java/com/patrick/fintech/loan_backend",
                relative);

        if (Files.exists(repo)) {
            return repo;
        }

        throw new AssertionError("Source file not found: " + relative);
    }

    private static String readSource(String relative) throws Exception {
        return Files.readString(source(relative), StandardCharsets.UTF_8);
    }


private static String disbursementEndpointAuthorization(String controller) {
    Pattern mappingPattern = Pattern.compile(
            "(?s)((?:\\s*@[^\\n]*(?:\\n|$))*)"
            + "\\s*(?:public|protected|private)\\s+"
            + "[^;{}]*?\\b([A-Za-z_$][\\w$]*)\\s*\\(");

    Matcher matcher = mappingPattern.matcher(controller);

    while (matcher.find()) {
        String annotations = matcher.group(1);
        String methodName = matcher.group(2);

        boolean disbursementMapping =
                annotations.contains("/disburse")
                || annotations.contains("/disbursement")
                || methodName.toLowerCase().contains("disburse");

        if (disbursementMapping) {
            return annotations;
        }
    }

    throw new AssertionError(
            "Could not locate the disbursement endpoint by route mapping. "
            + "Inspect LoanController.java and use its exact mapping.");
}

@Test
void disbursementIsNotBusinessOwnerOnlyAtBothBoundaries() throws Exception {
    String controller = readSource("controller/LoanController.java");
    String service = readSource("service/LoanService.java");

    String authorization = disbursementEndpointAuthorization(controller);

    assertFalse(
            authorization.contains("hasRole('BUSINESS_OWNER')"),
            "The disbursement endpoint must not be Business Owner-only");

    assertTrue(
            authorization.contains("hasAnyRole"),
            "The endpoint must explicitly authorize permitted disbursement roles");

    assertTrue(
            service.contains(
                    "requireNonBusinessOwnerForDisbursement(officer);"),
            "LoanService must independently reject Business Owner disbursement");
}


    @Test
    void approvedLoansCreateDurableDisbursementTask() throws Exception {
        String service = readSource("service/LoanService.java");
        String taskService = readSource("service/WorkflowTaskService.java");

        assertTrue(
                service.contains(
                        "workflowTaskService.createDisbursementTask(saved, approvedBy);"),
                "Final approval must create a disbursement task");

        assertTrue(
                taskService.contains("findFirstActiveByOrganizationAndRole"),
                "Task assignment must resolve an active user with the required role "
                + "in the same organization");

        assertTrue(
                service.contains(
                        "workflowTaskService.completeDisbursementTaskForLoan(saved.getId(), officer);"),
                "Successful disbursement must complete the associated task");
    }

    @Test
    void recycleBinHasThirtyDayRestoreWindowAndPermanentPurge() throws Exception {
        String service = readSource("service/LoanAdministrationService.java");
        String controller = readSource("controller/LoanController.java");

        String migration = Files.readString(
                Path.of(
                        "src/main/resources/db/migration/"
                        + "V106__loan_recycle_bin_and_reporting_exclusion.sql"),
                StandardCharsets.UTF_8);

        assertTrue(
                service.contains("RECYCLE_DAYS = 30"),
                "Recycle retention must remain 30 days");

        assertTrue(
                service.contains("restoreWithConfirmation"),
                "The recycle bin must have a restore service path");

        assertTrue(
                service.contains("purgeExpiredRecycledLoans"),
                "Expired recycled loans must have a permanent-purge path");

        assertTrue(
                controller.contains("/recycle-bin"),
                "The controller must expose the recycle-bin endpoint");

        assertTrue(
                migration.contains("purge_after"),
                "The recycle-bin schema must persist the purge deadline");
    }

    @Test
    void businessOwnerCanReactivateDeactivatedUser() throws Exception {
        String controller = readSource("controller/UserController.java");

        assertTrue(
                controller.contains(
                        "@PreAuthorize(\"hasAnyRole('ADMIN','BUSINESS_OWNER')\")"),
                "Business Owner must retain permission to reactivate a deactivated staff account");
    }

    @Test
    void mtnWebhookUsesProductionPropertyName() throws Exception {
        String controller = readSource("controller/PaymentWebhookController.java");

        assertTrue(
                controller.contains("@Value(\"${mtn.momo.webhook-secret:}\")"),
                "MTN webhook verification must use the configured production property");

        assertFalse(
                controller.contains("mtn.mobile-money.webhook-secret"),
                "The obsolete MTN webhook property name must not remain");
    }

    @Test
    void taskAssignmentHasAfterCommitNotification() throws Exception {
        String listener = readSource(
                "service/WorkflowTaskNotificationListener.java");

        assertTrue(
                listener.contains("TransactionPhase.AFTER_COMMIT"),
                "Task notifications must wait until the transaction commits");
    }
}
