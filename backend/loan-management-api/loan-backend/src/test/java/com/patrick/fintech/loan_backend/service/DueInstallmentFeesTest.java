package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Payment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DueInstallmentFeesTest {
    @Test
    void threeMissedMonthsAreCollectedBeforePrincipalButNotFutureMonth() {
        LocalDate now = LocalDate.of(2027, 4, 1);
        Loan loan = Loan.builder().interestOutstanding(new BigDecimal("400000.00"))
                .managementFeeOutstanding(new BigDecimal("400000.00"))
                .imported(false).build();
        List<Payment> schedule = List.of(
                due(now.minusMonths(3)),
                due(now.minusMonths(2)),
                due(now.minusMonths(1)),
                due(now.plusMonths(1)));
        DueInstallmentFees.Due result = DueInstallmentFees.calculate(loan, schedule, now);
        assertEquals(new BigDecimal("300000.00"), result.interest());
        assertEquals(new BigDecimal("300000.00"), result.management());
    }

    @Test
    void importedLegacyHistoricalArrearsCanCoverMultipleMonthsWithOneReceiptRow() {
        LocalDate now = LocalDate.of(2027, 4, 1);
        Loan loan = Loan.builder().imported(true).nextDueDate(now.minusDays(80))
                .interestOutstanding(new BigDecimal("600000.00"))
                .managementFeeOutstanding(new BigDecimal("600000.00")).build();
        DueInstallmentFees.Due result =
                DueInstallmentFees.calculate(loan, List.of(), now);
        assertEquals(new BigDecimal("600000.00"), result.interest());
        assertEquals(new BigDecimal("600000.00"), result.management());
    }

    @Test
    void importedNotYetDueDoesNotAccelerateContractualFees() {
        LocalDate now = LocalDate.of(2027, 4, 1);
        Loan loan = Loan.builder().imported(true).nextDueDate(now.plusDays(20))
                .interestOutstanding(new BigDecimal("600000.00"))
                .managementFeeOutstanding(new BigDecimal("600000.00")).build();
        DueInstallmentFees.Due result = DueInstallmentFees.calculate(loan, List.of(), now);
        assertEquals(new BigDecimal("0.00"), result.interest());
        assertEquals(new BigDecimal("0.00"), result.management());
    }

    @Test
    void atVerifiedContractMaturityAllRemainingInterestAndManagementAreDue() {
        LocalDate maturity = LocalDate.of(2026, 9, 26);
        Loan loan = Loan.builder().imported(true)
                .maturityDate(maturity)
                .disbursedAt(java.time.LocalDateTime.of(2026, 3, 26, 10, 0))
                .durationMonths(6)
                .nextDueDate(LocalDate.of(2026, 10, 25))
                .interestOutstanding(new BigDecimal("6300000.00"))
                .managementFeeOutstanding(new BigDecimal("6300000.00")).build();
        DueInstallmentFees.Due before = DueInstallmentFees.calculate(loan,
                List.of(), maturity.minusDays(1));
        assertEquals(new BigDecimal("0.00"), before.interest());
        assertEquals(new BigDecimal("0.00"), before.management());

        DueInstallmentFees.Due atMaturity = DueInstallmentFees.calculate(loan,
                List.of(), maturity);
        assertEquals(new BigDecimal("6300000.00"), atMaturity.interest());
        assertEquals(new BigDecimal("6300000.00"), atMaturity.management());
    }

    @Test
    void inconsistentImportedContractDoesNotAccelerateFeesFromUnverifiedMaturity() {
        LocalDate date = LocalDate.of(2026, 9, 26);
        Loan loan = Loan.builder().imported(true)
                .maturityDate(date)
                .disbursedAt(java.time.LocalDateTime.of(2026, 4, 26, 10, 0))
                .durationMonths(6)
                .nextDueDate(date.plusMonths(1))
                .interestOutstanding(new BigDecimal("500000.00"))
                .managementFeeOutstanding(new BigDecimal("500000.00")).build();
        DueInstallmentFees.Due fees = DueInstallmentFees.calculate(loan,
                List.of(), date.plusDays(1));
        assertEquals(new BigDecimal("0.00"), fees.interest());
        assertEquals(new BigDecimal("0.00"), fees.management());
    }

    private Payment due(LocalDate date) {
        return Payment.builder().dueDate(date)
                .cycleInterestRemaining(new BigDecimal("100000.00"))
                .cycleManagementFeeRemaining(new BigDecimal("100000.00"))
                .build();
    }
}
