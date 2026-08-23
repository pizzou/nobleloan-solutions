package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.util.FinancialPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinancialCalculationServiceTest {

    private final FinancialCalculationService service = new FinancialCalculationService();

    @Test
    void contractualMonthlyInterestIsIndependentOfCalendarDays() {
        BigDecimal january = FinancialPolicy.contractualMonthlyCharge(
                new BigDecimal("1000.00"),
                new BigDecimal("5.00"));

        BigDecimal february = FinancialPolicy.contractualMonthlyCharge(
                new BigDecimal("1000.00"),
                new BigDecimal("5.00"));

        BigDecimal march = FinancialPolicy.contractualMonthlyCharge(
                new BigDecimal("1000.00"),
                new BigDecimal("5.00"));

        assertEquals(new BigDecimal("50.00"), january);
        assertEquals(january, february);
        assertEquals(january, march);
    }

    @Test
    void paymentAllocationAlwaysAppliesPenaltyThenInterestThenPrincipal() {
        FinancialCalculationService.Allocation allocation = service.allocatePayment(
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                new BigDecimal("30.00"),
                new BigDecimal("500.00"));

        assertEquals(new BigDecimal("30.00"), allocation.interestPaid());
        assertEquals(new BigDecimal("60.00"), allocation.principalPaid());
        assertEquals(new BigDecimal("440.00"), allocation.newPrincipalBalance());
    }

    @Test
    void dailyCalculationIsUsedOnlyForExplicitPenaltyProducts() {
        BigDecimal penalty = FinancialPolicy.accrueDaily(
                new BigDecimal("1000.00"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 3),
                FinancialPolicy.MONTHLY_PENALTY_RATE);

        assertEquals(new BigDecimal("9.68"), penalty);
    }
}
