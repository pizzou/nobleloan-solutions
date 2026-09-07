package com.patrick.fintech.loan_backend.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductionSecurityHardeningStaticTest {

    private static Path source(String relative) {
        Path p = Path.of("src/main/java/com/patrick/fintech/loan_backend", relative);
        if (Files.exists(p)) return p;
        return Path.of("backend/loan-management-api/loan-backend/src/main/java/com/patrick/fintech/loan_backend", relative);
    }

    @Test
    void genericLoanStatusCannotContainDirectWriteOffTransition() throws Exception {
        String source = Files.readString(source("service/LoanService.java"));
        assertTrue(source.contains("Direct loan status changes to WRITTEN_OFF are prohibited"));
    }

    @Test
    void disbursementMustHaveProviderBackedKycClearance() throws Exception {
        String source = Files.readString(source("service/LoanService.java"));
        assertTrue(source.contains("complianceService.isKycCurrentlyClear"));
        assertFalse(source.contains("// if (!complianceService.isKycCurrentlyClear"));
    }

    @Test
    void frontendMustNotPersistBearerTokenInLocalStorage() throws Exception {
        Path p = Path.of("../../../../frontend/loan-management-ui/services/api.ts").normalize();
        if (!Files.exists(p)) p = Path.of("frontend/loan-management-ui/services/api.ts");
        String source = Files.readString(p);
        assertFalse(source.contains("localStorage.getItem(\"token\")"));
        assertFalse(source.contains("localStorage.setItem(\"token\""));
    }
}
