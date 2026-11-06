package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.util.FinancialPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for legacy (10%) and system-created (15%) monthly
 * overdue penalty policy. No Spring or database context required.
 */
class LegacyPenaltyPolicyTest {

    private static final BigDecimal PRINCIPAL = new BigDecimal("1000000.00");

    @Test
    void legacyImportsReceiveTenPercentMonthlyRate() {
        assertEquals(new BigDecimal("10.00"), FinancialPolicy.penaltyRateFor(true));
    }

    @Test
    void newSystemLoansReceiveFifteenPercentMonthlyRate() {
        assertEquals(new BigDecimal("15.00"), FinancialPolicy.penaltyRateFor(false));
        assertEquals(new BigDecimal("15.00"), FinancialPolicy.penaltyRateFor(null));
    }

    @Test
    void neitherLoanTypeAccruesDuringThreeFullGraceDays() {
        LocalDate fourthDay = LocalDate.of(2026, 1, 5);
        for (BigDecimal rate : new BigDecimal[] {
                FinancialPolicy.penaltyRateFor(true),
                FinancialPolicy.penaltyRateFor(false) }) {
            for (int days = 0; days <= FinancialPolicy.PENALTY_GRACE_DAYS; days++) {
                assertEquals(new BigDecimal("0.00"),
                        FinancialPolicy.dailyPenalty(PRINCIPAL, days, rate, fourthDay));
            }
        }
    }

    @Test
    void firstPenaltyDayUsesTheLoansMonthlyRateAndActualMonthLength() {
        LocalDate firstChargeableDay = LocalDate.of(2026, 1, 5); // January has 31 days
        assertEquals(new BigDecimal("3225.81"),
                FinancialPolicy.dailyPenalty(PRINCIPAL, 4,
                        FinancialPolicy.penaltyRateFor(true), firstChargeableDay));
        assertEquals(new BigDecimal("4838.71"),
                FinancialPolicy.dailyPenalty(PRINCIPAL, 4,
                        FinancialPolicy.penaltyRateFor(false), firstChargeableDay));
    }

    @Test
    void februaryIsProratedByTwentyEightDays() {
        LocalDate firstChargeableDay = LocalDate.of(2026, 2, 5);
        assertEquals(new BigDecimal("3571.43"),
                FinancialPolicy.dailyPenalty(PRINCIPAL, 4,
                        FinancialPolicy.penaltyRateFor(true), firstChargeableDay));
        assertEquals(new BigDecimal("5357.14"),
                FinancialPolicy.dailyPenalty(PRINCIPAL, 4,
                        FinancialPolicy.penaltyRateFor(false), firstChargeableDay));
    }

    @Test
    void principalBalanceZeroDoesNotAccruePenalty() {
        assertEquals(new BigDecimal("0.00"),
                FinancialPolicy.dailyPenalty(BigDecimal.ZERO, 20,
                        FinancialPolicy.penaltyRateFor(true), LocalDate.of(2026, 1, 5)));
    }
}
