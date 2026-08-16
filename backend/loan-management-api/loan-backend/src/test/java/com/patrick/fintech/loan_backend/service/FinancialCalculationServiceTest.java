package com.patrick.fintech.loan_backend.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinancialCalculationServiceTest {

    private final FinancialCalculationService service = new FinancialCalculationService();

    @Test
    void monthlyDailyRateUsesActualCalendarDays() {
        BigDecimal rate = service.dailyRate(
                new BigDecimal("10.00"),
                "MONTHLY",
                LocalDate.of(2026, 1, 15));

        assertEquals(new BigDecimal("0.003225806452"), rate);
    }

    @Test
    void annualDailyRateUses365Days() {
        BigDecimal rate = service.dailyRate(
                new BigDecimal("12.00"),
                "ANNUAL",
                LocalDate.of(2026, 1, 15));

        assertEquals(new BigDecimal("0.000328767123"), rate);
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
    void interestAndPenaltyUseCalendarDayRates() {
        BigDecimal dailyRate = service.dailyRate(
                new BigDecimal("10.00"),
                "MONTHLY",
                LocalDate.of(2026, 1, 10));
        BigDecimal interest = service.interest(new BigDecimal("1000.00"), dailyRate, 3);
        BigDecimal penalty = service.penalty(
                new BigDecimal("1000.00"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 3));

        assertEquals(new BigDecimal("9.68"), interest);
        assertEquals(new BigDecimal("9.68"), penalty);
    }
}
