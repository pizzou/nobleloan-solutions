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
void genericLoanStatusCannotContainDirectWriteOffTransition() throws Exception {
    String source = Files.readString(
            backendSource("service/LoanService.java")
    );

    assertTrue(
            source.contains("Direct loan status changes to WRITTEN_OFF are prohibited"),
            "LoanService must explicitly prohibit direct WRITTEN_OFF status changes"
    );
}

// @Test
// void disbursementMustHaveProviderBackedKycClearance() throws Exception {
//     String source = Files.readString(
//             backendSource("service/LoanService.java")
//     );

//     assertTrue(
//             source.contains("complianceService.isKycCurrentlyClear"),
//             "Loan disbursement must enforce KYC clearance"
//     );

//     assertFalse(
//             source.contains("// if (!complianceService.isKycCurrentlyClear"),
//             "KYC clearance must not be disabled by commenting out the enforcement"
//     );
// }

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
