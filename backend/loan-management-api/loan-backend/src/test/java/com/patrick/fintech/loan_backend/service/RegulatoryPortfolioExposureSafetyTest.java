package com.patrick.fintech.loan_backend.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level guard against counting requested (but not disbursed)
 * principal as BNR portfolio-at-risk, NPL or provision exposure.
 *
 * Real monetary reconciliation still requires integration/acceptance tests
 * against the accounting ledger and historical loans.
 */
class RegulatoryPortfolioExposureSafetyTest {

    @Test
    void regulatoryRiskUsesTheEligibleFundedPortfolioPopulation() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/patrick/fintech/loan_backend/service/RegulatoryReportingService.java"));

        assertTrue(source.contains("BigDecimal exposure = includedInGrossPortfolio ? outstanding : ZERO;"));
        assertTrue(source.contains("if (dpd > 0 && exposure.signum() > 0)"));
        assertTrue(source.contains("parAmount = add(parAmount, exposure);"));
        assertTrue(source.contains("if (includedInGrossPortfolio && isNpl(loan))"));
        assertTrue(source.contains("nplAmount = add(nplAmount, exposure);"));
        assertTrue(source.contains("if (includedInGrossPortfolio && status == LoanStatus.DEFAULTED)"));
        assertTrue(source.contains("BigDecimal provisionBase = exposure.max(ZERO);"));
    }

    @Test
    void writtenOffHistoricalAmountRemainsAvailableForDisclosure() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/patrick/fintech/loan_backend/service/RegulatoryReportingService.java"));
        assertTrue(source.contains("writtenOffAmount = add(writtenOffAmount, outstanding);"));
    }
}
