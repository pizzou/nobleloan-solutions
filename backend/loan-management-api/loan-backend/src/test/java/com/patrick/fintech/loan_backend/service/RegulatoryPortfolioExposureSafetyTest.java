package com.patrick.fintech.loan_backend.service;

import org.junit.jupiter.api.Test;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import java.time.LocalDateTime;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Source-level guard against counting requested (but not disbursed)
 * principal as BNR portfolio-at-risk, NPL or provision exposure.
 *
 * Real monetary reconciliation still requires integration/acceptance tests
 * against the accounting ledger and historical loans.
 */
class RegulatoryPortfolioExposureSafetyTest {

    @Test
    void pendingAndRejectedApplicationsNeverEnterFundedPortfolio() {
        for (LoanStatus status : new LoanStatus[] { LoanStatus.PENDING, LoanStatus.REJECTED }) {
            Loan loan = new Loan();
            loan.setStatus(status);
            assertFalse(RegulatoryReportingService.isCurrentPortfolioLoan(loan));
        }
    }

    @Test
    void systemLoanMustBeDisbursedBeforeEnteringReceivables() {
        Loan loan = new Loan();
        loan.setStatus(LoanStatus.ACTIVE);
        loan.setImported(false);

        assertFalse(RegulatoryReportingService.isCurrentPortfolioLoan(loan));

        loan.setDisbursedAt(LocalDateTime.of(2026, 11, 5, 0, 0));
        assertTrue(RegulatoryReportingService.isCurrentPortfolioLoan(loan));
    }

    @Test
    void historicalImportedLoanDoesNotRequireDisbursementTimestamp() {
        Loan loan = new Loan();
        loan.setStatus(LoanStatus.DEFAULTED);
        loan.setImported(true);

        assertTrue(RegulatoryReportingService.isCurrentPortfolioLoan(loan));
    }

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
