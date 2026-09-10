package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.util.FinancialPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

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
    void penaltyIsZeroForThreeGraceDaysThenTenPercentPerDay() {
        BigDecimal outstanding = new BigDecimal("1000000.00");

        assertEquals(
                new BigDecimal("0.00"),
                FinancialPolicy.dailyPenalty(outstanding, 0));
        assertEquals(
                new BigDecimal("0.00"),
                FinancialPolicy.dailyPenalty(outstanding, 3));
        assertEquals(
                new BigDecimal("100000.00"),
                FinancialPolicy.dailyPenalty(outstanding, 4));
        assertEquals(
                new BigDecimal("200000.00"),
                FinancialPolicy.dailyPenalty(outstanding, 5));
        assertEquals(
                new BigDecimal("300000.00"),
                FinancialPolicy.dailyPenalty(outstanding, 6));
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
