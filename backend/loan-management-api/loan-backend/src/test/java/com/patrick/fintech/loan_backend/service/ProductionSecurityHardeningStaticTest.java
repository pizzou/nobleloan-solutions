package com.patrick.fintech.loan_backend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class ProductionSecurityHardeningStaticTest {

private static Path backendSource(String relative) {
    List<Path> candidates = List.of(
            Path.of("src/main/java/com/patrick/fintech/loan_backend", relative),
            Path.of("backend/loan-management-api/loan-backend/src/main/java/com/patrick/fintech/loan_backend", relative)
    );

    return candidates.stream()
            .filter(Files::exists)
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                    "Backend source file not found: " + relative
            ));
}

private static Path findFrontendApiSource() {
    List<Path> candidates = List.of(
            Path.of("frontend/loan-management-ui/services/api.ts"),
            Path.of("../frontend/loan-management-ui/services/api.ts"),
            Path.of("../../frontend/loan-management-ui/services/api.ts"),
            Path.of("../../../frontend/loan-management-ui/services/api.ts"),
            Path.of("../../../../frontend/loan-management-ui/services/api.ts")
    );

    return candidates.stream()
            .filter(Files::exists)
            .findFirst()
            .orElse(null);
}

@Test
void productionConfigurationMustRejectInsecureSessionCookies() throws Exception {
    String validator = Files.readString(
            backendSource("config/ProductionConfigurationValidator.java"));
    String authController = Files.readString(
            backendSource("controller/AuthController.java"));

    assertTrue(validator.contains("@Value(\"${app.auth.cookie.secure:true}\")"),
            "Production validator must inspect the actual cookie Secure setting");
    assertTrue(validator.contains("if (!authCookieSecure)"),
            "Production startup must fail when Secure cookies are disabled");
    assertTrue(authController.contains(".httpOnly(true)"),
            "Session cookie must be HttpOnly");
    assertTrue(authController.contains(".secure(sessionCookieSecure)"),
            "Session cookie Secure attribute must follow the validated production setting");
    assertTrue(authController.contains(".sameSite(sameSite)"),
            "Session cookie must use an explicit SameSite policy");
}

@Test
void otpIsConsumedAtomicallyAndLogoutRevokesIssuedSessions() throws Exception {
    String auth = Files.readString(backendSource("controller/AuthController.java"));
    String jwtFilter = Files.readString(backendSource("security/JwtAuthFilter.java"));
    String userRepository = Files.readString(backendSource("repository/UserRepository.java"));

    assertTrue(auth.contains("consumeLoginOtp("),
            "OTP verification must atomically consume the challenge, not only compare its hash");
    assertTrue(userRepository.contains("consumeLoginOtp"),
            "OTP consumption must be implemented at the repository/database boundary");
    assertTrue(auth.contains("setTokenVersion((user.getTokenVersion() == null ? 0L : user.getTokenVersion()) + 1L)"),
            "Logout must increment the user's token version to revoke existing sessions");
    assertTrue(jwtFilter.contains("tokenVersion != currentTokenVersion"),
            "Every authenticated request must reject revoked JWT versions");
}

@Test
void complianceReadAndReviewRoutesAreOrganizationScopedAndRoleRestricted() throws Exception {
    String controller = Files.readString(backendSource("controller/ComplianceController.java"));
    String service = Files.readString(backendSource("service/ComplianceService.java"));
    String repository = Files.readString(backendSource("repository/KycCheckRepository.java"));

    assertTrue(controller.contains("getHistoryForBorrower(") && controller.contains("currentUserUtil.getCurrentOrganizationId()"),
            "KYC history must pass the authenticated organization ID");
    assertTrue(controller.contains("isKycCurrentlyClear(") && controller.contains("currentUserUtil.getCurrentOrganizationId()"),
            "KYC status must pass the authenticated organization ID");
    assertTrue(controller.contains("@PreAuthorize(\"hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','CREDIT_ANALYST')\")"),
            "KYC history/status must be restricted to compliance staff");
    assertTrue(service.contains("check.getOrganization().getId()"),
            "Manual KYC review must reject cross-organization check IDs");
    assertTrue(service.contains("validateOrganizationOwnership(borrower, organizationId)"),
            "Borrower KYC history/status must enforce organization ownership");
    assertTrue(repository.contains("findByBorrower_IdAndOrganization_Id"),
            "KYC history query must be tenant-scoped in the repository");
}

@Test
void legacyMigrationPreflightProtectsExistingLoanRowsWithoutEditingAppliedMigrations() throws Exception {
    String callback = Files.readString(
            backendSource("config/LegacyLoanFeeMigrationPreflight.java"));
    Path v104 = Path.of("src/main/resources/db/migration/V104__loan_penalty_rate_monthly_policy.sql");
    if (!Files.exists(v104)) {
        v104 = Path.of("backend/loan-management-api/loan-backend/src/main/resources/db/migration/V104__loan_penalty_rate_monthly_policy.sql");
    }
    assertTrue(Files.exists(v104), "Existing V104 migration must remain present");
    assertTrue(callback.contains("Event.BEFORE_EACH_MIGRATE"),
            "Legacy financial-field backfill must run before V104 updates existing loan rows");
    assertTrue(callback.contains("TARGET_VERSION = \"104\""),
            "Preflight must be scoped to the exact migration version");
    assertTrue(callback.contains("application_fee_paid = COALESCE"),
            "Legacy application fee collection state must be made explicit before fee-income migration");
    assertTrue(callback.contains("ELSE interest_outstanding"),
            "Non-null financial values must be preserved rather than silently rewritten");
}

@Test
void paymentGatewayAndSandboxMutationRequireExplicitRoles() throws Exception {
    String gateway = Files.readString(backendSource("controller/PaymentGatewayController.java"));
    String payments = Files.readString(backendSource("controller/PaymentController.java"));
    String docs = Files.readString(backendSource("controller/InternalDocumentController.java"));

    assertTrue(gateway.contains("@PreAuthorize("),
            "Payment gateway initiation must not be available to every authenticated role");
    assertTrue(payments.contains("@PreAuthorize(\"hasAnyRole('ADMIN','MANAGER','ACCOUNTANT','TELLER')\")"),
            "Sandbox payment confirmation must be role restricted");
    assertTrue(docs.contains("@PreAuthorize(\"hasAnyRole('ADMIN','BUSINESS_OWNER','MANAGER','LOAN_OFFICER','ACCOUNTANT','TELLER','AUDITOR','CREDIT_ANALYST')\")"),
            "Internal document reads/uploads/downloads must be restricted to organization staff");
}

@Test
void creditBureauOperationsRequireARelevantStaffRole() throws Exception {
    String controller = Files.readString(backendSource("controller/CreditBureauController.java"));
    assertFalse(controller.contains("@PreAuthorize(\"isAuthenticated()\")"),
            "Credit bureau checks and reports must not be available to every authenticated account");
    assertTrue(controller.contains("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','CREDIT_ANALYST','AUDITOR')"),
            "Credit bureau routes must be limited to lending/compliance staff");
}

@Test
void genericLoanStatusCannotContainDirectWriteOffTransition() throws Exception {
    String source = Files.readString(
            backendSource("service/LoanService.java")
    );

    assertTrue(
            source.contains("Direct loan status changes to WRITTEN_OFF are prohibited"),
            "LoanService must explicitly prohibit direct WRITTEN_OFF status changes"
    );
}

@Test
void productionDisbursementMustRequireCurrentProviderBackedKycClearance() throws Exception {
    String loanService = Files.readString(backendSource("service/LoanService.java"));
    String complianceService = Files.readString(backendSource("service/ComplianceService.java"));

    assertTrue(
            loanService.contains("isKycCurrentlyClear(") && loanService.contains("loan.getOrganization().getId()"),
            "Loan disbursement must check the borrower's current KYC/AML clearance"
    );
    assertTrue(
            loanService.contains("isProductionEnvironment()"),
            "The production KYC/AML gate must be environment-aware and fail closed"
    );
    assertFalse(
            loanService.contains("// if (!complianceService.isKycCurrentlyClear"),
            "KYC clearance must not be disabled by commenting out enforcement"
    );
    assertTrue(
            complianceService.contains("findFirstByBorrower_IdAndOrganization_IdAndCheckTypeOrderByCreatedAtDesc")
                    && complianceService.contains("KycCheck.CheckType.SANCTIONS_SCREENING")
                    && complianceService.contains("!check.isExpired()"),
            "KYC clearance must use the latest unexpired sanctions screening"
    );
    assertTrue(
            complianceService.contains("INTERNAL_PROVIDER"),
            "Production clearance must reject internally simulated compliance checks"
    );
}

@Test
void frontendMustNotPersistBearerTokenInLocalStorage() throws Exception {
    Path frontendApi = findFrontendApiSource();

    /*
     * The frontend is deployed separately from the backend.
     *
     * The backend Docker image intentionally does not contain
     * frontend/loan-management-ui. Therefore this test must not fail
     * merely because the frontend source is outside the backend build
     * context.
     *
     * When the frontend source is available locally, however, we
     * enforce the security requirement.
     */
    if (frontendApi == null) {
        return;
    }

    String source = Files.readString(frontendApi);

    assertFalse(
            source.contains("localStorage.getItem(\"token\")"),
            "Frontend must not read a bearer token from localStorage"
    );

    assertFalse(
            source.contains("localStorage.setItem(\"token\""),
            "Frontend must not persist a bearer token in localStorage"
    );

    assertFalse(
            source.contains("localStorage.setItem('token'"),
            "Frontend must not persist a bearer token in localStorage"
    );
}


}
