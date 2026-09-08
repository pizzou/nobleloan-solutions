package com.patrick.fintech.loan_backend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class BankGradeSecurityStaticTest {

    private static String source(String relative) throws Exception {
        Path path = Path.of("src/main/java/com/patrick/fintech/loan_backend", relative);
        if (!Files.exists(path)) {
            throw new IllegalStateException("Source not found: " + path);
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void publicPaymentScheduleCannotUseInternalLoanId() throws Exception {
        String controller = source("controller/PaymentScheduleController.java");
        String service = source("service/PaymentScheduleService.java");

        assertFalse(controller.contains("/{loanId}"));
        assertTrue(controller.contains("@RequestParam String reference"));
        assertTrue(controller.contains("@RequestParam String phone"));
        assertTrue(service.contains("findByReferenceNumberAndBorrower_PhoneHash"));
        assertFalse(service.contains("public List<PaymentScheduleResponse> getSchedule(Long loanId)"));
    }

    @Test
    void publicSecurityIsAnExplicitAllowList() throws Exception {
        String security = source("config/SecurityConfig.java");
        assertFalse(security.contains("requestMatchers(\"/api/public/**\")"));
        assertTrue(security.contains("/api/public/payment-schedule"));
        assertTrue(security.contains(".anyRequest()"));
    }

    @Test
    void frontendHasNoProductionLocalhostFallback() throws Exception {
        Path frontend = Path.of("../../../../frontend/loan-management-ui/lib/apiBase.ts");
        if (!Files.exists(frontend)) {
            frontend = Path.of("../../../frontend/loan-management-ui/lib/apiBase.ts");
        }
        assertTrue(Files.exists(frontend), "Frontend API configuration source must be present");
        String apiBase = Files.readString(frontend, StandardCharsets.UTF_8);
        assertFalse(apiBase.contains("http://localhost:8080/api"));
        assertTrue(apiBase.contains("Refusing to fall back to localhost"));
    }

    @Test
    void forwardedHeadersAreNotTrustedByDefault() throws Exception {
        String properties = Files.readString(
                Path.of("src/main/resources/application.properties"),
                StandardCharsets.UTF_8);
        assertTrue(properties.contains("server.forward-headers-strategy=none"));
    }
}
