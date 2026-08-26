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
        void scheduledMonthlyChargeIsExactlyFivePercentOfOpeningPrincipal() {
                BigDecimal principal = new BigDecimal("10000000.00");

                assertEquals(
                                new BigDecimal("500000.00"),
                                FinancialPolicy.accrueScheduledMonthly(
                                                principal,
                                                FinancialPolicy.MONTHLY_INTEREST_RATE));

                assertEquals(
                                new BigDecimal("500000.00"),
                                FinancialPolicy.accrueScheduledMonthly(
                                                principal,
                                                FinancialPolicy.MONTHLY_MANAGEMENT_FEE_RATE));
        }

        @Test
        void sixMonthDecliningPrincipalScheduleProduces1750000PerFivePercentCharge() {
                BigDecimal balance = new BigDecimal("10000000.00");
                BigDecimal totalInterest = BigDecimal.ZERO;

                for (int installment = 1; installment <= 6; installment++) {
                        BigDecimal principalComponent = installment == 6
                                        ? balance
                                        : balance.divide(new BigDecimal(7 - installment), 16,
                                                        java.math.RoundingMode.HALF_UP);

                        totalInterest = totalInterest.add(
                                        FinancialPolicy.accrueScheduledMonthly(
                                                        balance,
                                                        FinancialPolicy.MONTHLY_INTEREST_RATE));

                        balance = balance.subtract(principalComponent);
                }

                assertEquals(
                                new BigDecimal("1750000.00"),
                                totalInterest.setScale(2, java.math.RoundingMode.HALF_UP));
        }

        @Test
        void contractualMonthlyInterestDoesNotDependOnCalendarDayCount() {
                BigDecimal january = FinancialPolicy.contractualMonthlyCharge(
                                new BigDecimal("5000000.00"),
                                FinancialPolicy.MONTHLY_INTEREST_RATE);
                BigDecimal february = FinancialPolicy.contractualMonthlyCharge(
                                new BigDecimal("5000000.00"),
                                FinancialPolicy.MONTHLY_INTEREST_RATE);

                assertEquals(new BigDecimal("250000.00"), january);
                assertEquals(new BigDecimal("250000.00"), february);
        }

        @Test
        void dailyAccrualRemainsAvailableForPenaltyProductsOnly() {
                BigDecimal penalty = FinancialPolicy.accrueDaily(
                                new BigDecimal("1000000.00"),
                                LocalDate.of(2026, 1, 30),
                                LocalDate.of(2026, 2, 2),
                                FinancialPolicy.MONTHLY_PENALTY_RATE);

                // Daily accrual is retained only for explicitly daily products such as
                // penalties.
                assertEquals(new BigDecimal("15034.56"), penalty);
        }

        @Test
        void applicationFeeIsOneTimeAndExtensionFeeIsBasedOnOutstandingPrincipal() {
                assertEquals(new BigDecimal("20000.00"),
                                FinancialPolicy.applicationFee(new BigDecimal("1000000.00")));
                assertEquals(new BigDecimal("100000.00"),
                                FinancialPolicy.extensionFee(new BigDecimal("1000000.00")));
        }

        @Test
        void fiveMillionLoanHas250kInterestAnd250kManagementFeePerMonth() {
                BigDecimal principal = new BigDecimal("5000000.00");

                BigDecimal interest = FinancialPolicy.contractualMonthlyCharge(
                                principal, FinancialPolicy.MONTHLY_INTEREST_RATE);
                BigDecimal managementFee = FinancialPolicy.contractualMonthlyCharge(
                                principal, FinancialPolicy.MONTHLY_MANAGEMENT_FEE_RATE);

                assertEquals(new BigDecimal("250000.00"), interest);
                assertEquals(new BigDecimal("250000.00"), managementFee);
                assertEquals(new BigDecimal("500000.00"), interest.add(managementFee));
        }
}
