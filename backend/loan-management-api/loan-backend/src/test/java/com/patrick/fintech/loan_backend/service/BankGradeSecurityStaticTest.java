package com.patrick.fintech.loan_backend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class BankGradeSecurityStaticTest {

    private static String source(String relative) throws Exception {
        Path path = Path.of(
                "src/main/java/com/patrick/fintech/loan_backend",
                relative
        );

        if (!Files.exists(path)) {
            throw new IllegalStateException(
                    "Source not found: " + path.toAbsolutePath()
            );
        }

        return Files.readString(
                path,
                StandardCharsets.UTF_8
        );
    }

    @Test
    void publicPaymentScheduleCannotUseInternalLoanId()
            throws Exception {

        String controller =
                source("controller/PaymentScheduleController.java");

        String service =
                source("service/PaymentScheduleService.java");

        /*
         * The public payment-schedule endpoint must never expose
         * a direct /{loanId} lookup.
         */
        assertFalse(
                controller.contains("/{loanId}"),
                "Public payment schedule must not expose internal loan IDs"
        );

        /*
         * Public ownership must be established using the borrower's
         * reference and phone number.
         */
        assertTrue(
                controller.contains("@RequestParam String reference"),
                "Payment schedule must require a borrower reference"
        );

        assertTrue(
                controller.contains("@RequestParam String phone"),
                "Payment schedule must require borrower phone verification"
        );

        /*
         * The service must resolve the loan through an ownership-aware
         * repository query.
         */
        assertTrue(
                service.contains(
                        "findByReferenceNumberAndBorrower_PhoneHash"
                ),
                "Payment schedule must use an ownership-scoped repository query"
        );

        /*
         * Prevent accidental reintroduction of the old insecure API.
         */
        assertFalse(
                service.contains(
                        "public List<PaymentScheduleResponse> getSchedule(Long loanId)"
                ),
                "Old unprotected loanId payment schedule method must not exist"
        );
    }

    @Test
    void publicSecurityIsAnExplicitAllowList()
            throws Exception {

        String security =
                source("config/SecurityConfig.java");

        /*
         * Never allow every /api/public/** endpoint automatically.
         */
        assertFalse(
                security.contains(
                        "requestMatchers(\"/api/public/**\")"
                ),
                "Broad /api/public/** permit-all rule must not exist"
        );

        /*
         * Payment schedule is intentionally public, but only because
         * the controller itself performs borrower ownership verification.
         */
        assertTrue(
                security.contains(
                        "/api/public/payment-schedule"
                ),
                "Payment schedule public endpoint must be explicitly configured"
        );

        /*
         * All routes not explicitly permitted must still reach the
         * authentication/authorization rules.
         */
        assertTrue(
                security.contains(".anyRequest()"),
                "Security configuration must define a final anyRequest rule"
        );
    }

    @Test
    void forwardedHeadersAreNotTrustedByDefault()
            throws Exception {

        Path properties =
                Path.of(
                        "src/main/resources/application.properties"
                );

        assertTrue(
                Files.exists(properties),
                "application.properties must exist"
        );

        String content =
                Files.readString(
                        properties,
                        StandardCharsets.UTF_8
                );

        /*
         * The application must not blindly trust client-supplied
         * X-Forwarded-* headers.
         */
        assertTrue(
                content.contains(
                        "server.forward-headers-strategy=none"
                ),
                "Forwarded headers must not be trusted by default"
        );
    }
}