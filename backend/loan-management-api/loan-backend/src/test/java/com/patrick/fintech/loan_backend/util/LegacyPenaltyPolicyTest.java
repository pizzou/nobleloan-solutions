package com.patrick.fintech.loan_backend.util;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyPenaltyPolicyTest {

    @Test
    void legacyImportsUseTenPercentMonthly() {
        assertEquals(new BigDecimal("10.00"), FinancialPolicy.penaltyRateFor(true));
    }

    @Test
    void newLoansUseFifteenPercentMonthly() {
        assertEquals(new BigDecimal("15.00"), FinancialPolicy.penaltyRateFor(false));
        assertEquals(new BigDecimal("15.00"), FinancialPolicy.penaltyRateFor(null));
    }

    @Test
    void noChargeDuringFirstThreeFullGraceDays() {
        var principal = new BigDecimal("1000000");
        var novemberFourth = LocalDate.of(2026, 11, 4);
        assertEquals(new BigDecimal("0.00"), FinancialPolicy.dailyPenalty(
                principal, 3, FinancialPolicy.penaltyRateFor(false), novemberFourth));
        assertEquals(new BigDecimal("0.00"), FinancialPolicy.dailyPenalty(
                principal, 3, FinancialPolicy.penaltyRateFor(true), novemberFourth));
    }

    @Test
    void dayFourUsesCorrectMonthlyRateAndCalendarDays() {
        var principal = new BigDecimal("1000000");
        var novemberFourth = LocalDate.of(2026, 11, 4);
        assertEquals(new BigDecimal("5000.00"), FinancialPolicy.dailyPenalty(
                principal, 4, FinancialPolicy.penaltyRateFor(false), novemberFourth));
        assertEquals(new BigDecimal("3333.33"), FinancialPolicy.dailyPenalty(
                principal, 4, FinancialPolicy.penaltyRateFor(true), novemberFourth));
    }
}
