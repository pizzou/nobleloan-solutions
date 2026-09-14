package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.util.FinancialPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Final regression tests for the platform-wide lending policy.
 *
 * These tests deliberately exercise the canonical policy rather than individual
 * controllers so that interest, management fee, extension fee and penalty rules
 * cannot silently diverge between loan creation, schedules, payments and reports.
 */
class FinalFinancialPolicyInvariantTest {

    @Test
    void contractualChargesUseOpeningOutstandingPrincipal() {
        BigDecimal balance = new BigDecimal("1000000.00");

        assertEquals(
                new BigDecimal("50000.00"),
                FinancialPolicy.contractualMonthlyCharge(
                        balance,
                        FinancialPolicy.MONTHLY_INTEREST_RATE));

        assertEquals(
                new BigDecimal("50000.00"),
                FinancialPolicy.contractualMonthlyCharge(
                        balance,
                        FinancialPolicy.MONTHLY_MANAGEMENT_FEE_RATE));
    }

    @Test
    void extensionFeeIsExactlyTenPercentOfOutstandingPrincipal() {
        assertEquals(
                new BigDecimal("123456.79"),
                FinancialPolicy.extensionFee(
                        new BigDecimal("1234567.89")));
    }

    @Test
    void penaltyIsZeroForThreeGraceDaysThenTenPercentPerMonthProratedDaily() {
        BigDecimal outstanding = new BigDecimal("1000000.00");

        LocalDate firstChargeableDate = LocalDate.of(2026, 1, 5);

        assertEquals(
                new BigDecimal("0.00"),
                FinancialPolicy.dailyPenalty(
                        outstanding,
                        0,
                        FinancialPolicy.MONTHLY_PENALTY_RATE,
                        firstChargeableDate));

        assertEquals(
                new BigDecimal("0.00"),
                FinancialPolicy.dailyPenalty(
                        outstanding,
                        3,
                        FinancialPolicy.MONTHLY_PENALTY_RATE,
                        firstChargeableDate));

        // January has 31 calendar days.
        // 10% monthly / 31 = 0.322580645...% per chargeable day.
        //
        // Day 4 = RWF 1,000,000 × 10% / 31 = RWF 3,225.81
        assertEquals(
                new BigDecimal("3225.81"),
                FinancialPolicy.dailyPenalty(
                        outstanding,
                        4,
                        FinancialPolicy.MONTHLY_PENALTY_RATE,
                        firstChargeableDate));

        // Two chargeable days:
        // 2 × 3,225.806451... = 6,451.612903...
        // HALF_UP = 6,451.61
        assertEquals(
                new BigDecimal("6451.61"),
                FinancialPolicy.dailyPenalty(
                        outstanding,
                        5,
                        FinancialPolicy.MONTHLY_PENALTY_RATE,
                        firstChargeableDate));

        // Three chargeable days:
        // 3 × 3,225.806451... = 9,677.419354...
        // HALF_UP = 9,677.42
        assertEquals(
                new BigDecimal("9677.42"),
                FinancialPolicy.dailyPenalty(
                        outstanding,
                        6,
                        FinancialPolicy.MONTHLY_PENALTY_RATE,
                        firstChargeableDate));
    }

    @Test
    void decliningThreeMonthScheduleUsesOutstandingBalanceForEveryMonth() {
        BigDecimal balance = new BigDecimal("1000000.00");
        BigDecimal interest = BigDecimal.ZERO;
        BigDecimal management = BigDecimal.ZERO;

        for (int i = 1; i <= 3; i++) {
            FinancialPolicy.ScheduleLine line =
                    FinancialPolicy.contractualScheduleLine(
                            balance,
                            3 - i + 1,
                            FinancialPolicy.MONTHLY_INTEREST_RATE,
                            FinancialPolicy.MONTHLY_MANAGEMENT_FEE_RATE);

            interest = interest.add(line.interest());
            management = management.add(line.managementFee());
            balance = line.remainingBalance();
        }

        assertEquals(new BigDecimal("100000.00"), interest);
        assertEquals(new BigDecimal("100000.00"), management);
        assertEquals(new BigDecimal("0.00"), balance);
    }
}