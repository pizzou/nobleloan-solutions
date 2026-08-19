package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.util.FinancialPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinancialPolicyTest {

    @Test
    void platformRatesAreCentralized() {
        assertEquals(new BigDecimal("5.00"), FinancialPolicy.MONTHLY_INTEREST_RATE);
        assertEquals(new BigDecimal("5.00"), FinancialPolicy.MONTHLY_MANAGEMENT_FEE_RATE);
        assertEquals(new BigDecimal("2.00"), FinancialPolicy.PROCESSING_FEE_RATE);
        assertEquals(new BigDecimal("15.00"), FinancialPolicy.MONTHLY_PENALTY_RATE);
        assertEquals(new BigDecimal("10.00"), FinancialPolicy.EXTENSION_FEE_RATE);
    }

    @Test
    void fivePercentMonthlyRateOnTenMillionIsFiveHundredThousandForA31DayMonth() {
        BigDecimal principal = new BigDecimal("10000000.00");

        BigDecimal monthlyAmount = principal
                .multiply(FinancialPolicy.MONTHLY_MANAGEMENT_FEE_RATE)
                .divide(new BigDecimal("100"));

        assertEquals(new BigDecimal("500000.00"), monthlyAmount.setScale(2));

        BigDecimal combinedMonthlyRate = FinancialPolicy.MONTHLY_INTEREST_RATE
                .add(FinancialPolicy.MONTHLY_MANAGEMENT_FEE_RATE);

        assertEquals(new BigDecimal("10.00"), combinedMonthlyRate);
    }

    @Test
    void monthlyRateUsesActualDaysInEachCalendarMonth() {
        BigDecimal january = FinancialPolicy.dailyRateFraction(
                FinancialPolicy.MONTHLY_INTEREST_RATE,
                LocalDate.of(2026, 1, 10));
        BigDecimal february = FinancialPolicy.dailyRateFraction(
                FinancialPolicy.MONTHLY_INTEREST_RATE,
                LocalDate.of(2026, 2, 10));

        assertEquals(new BigDecimal("0.0016129032258064516"), january);
        assertEquals(new BigDecimal("0.0017857142857142857"), february);
    }

    @Test
    void fivePercentIsARecurringRateAndTermTotalsCanExceedFivePercentOfOriginalPrincipal() {
        BigDecimal first = FinancialPolicy.accrueDaily(
                new BigDecimal("10000000.00"),
                LocalDate.of(2026, 8, 17),
                LocalDate.of(2026, 9, 17),
                FinancialPolicy.MONTHLY_MANAGEMENT_FEE_RATE);

        BigDecimal second = FinancialPolicy.accrueDaily(
                new BigDecimal("6666666.67"),
                LocalDate.of(2026, 9, 17),
                LocalDate.of(2026, 10, 19),
                FinancialPolicy.MONTHLY_MANAGEMENT_FEE_RATE);

        BigDecimal third = FinancialPolicy.accrueDaily(
                new BigDecimal("3333333.33"),
                LocalDate.of(2026, 10, 19),
                LocalDate.of(2026, 11, 17),
                FinancialPolicy.MONTHLY_MANAGEMENT_FEE_RATE);

        BigDecimal scheduledTermTotal = first.add(second).add(third)
                .setScale(2, java.math.RoundingMode.HALF_UP);

        assertEquals(new BigDecimal("1016487.45"), scheduledTermTotal);
    }

    @Test
    void processingFeeIsOneTimeAndExtensionFeeIsBasedOnOutstandingPrincipal() {
        assertEquals(new BigDecimal("20000.00"),
                FinancialPolicy.processingFee(new BigDecimal("1000000.00")));
        assertEquals(new BigDecimal("100000.00"),
                FinancialPolicy.extensionFee(new BigDecimal("1000000.00")));
    }

    @Test
    void accrualAcrossMonthBoundaryUsesEachMonthActualDayCount() {
        BigDecimal result = FinancialPolicy.accrueDaily(
                new BigDecimal("1000000.00"),
                LocalDate.of(2026, 1, 30),
                LocalDate.of(2026, 2, 2),
                FinancialPolicy.MONTHLY_INTEREST_RATE);

        // Jan 30 + Jan 31 = 2 days at 5%/31; Feb 1 = 1 day at 5%/28.
        assertEquals(new BigDecimal("5011.52"), result);
    }
}
