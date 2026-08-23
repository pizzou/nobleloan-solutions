package com.patrick.fintech.loan_backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

public final class FinancialPolicy {

        public static final BigDecimal MONTHLY_INTEREST_RATE = new BigDecimal("5.00");
        public static final BigDecimal MONTHLY_MANAGEMENT_FEE_RATE = new BigDecimal("5.00");
        public static final BigDecimal PROCESSING_FEE_RATE = new BigDecimal("2.00");
        public static final BigDecimal MONTHLY_PENALTY_RATE = new BigDecimal("15.00");
        public static final BigDecimal EXTENSION_FEE_RATE = new BigDecimal("10.00");

        private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
        private static final int RATE_SCALE = 16;
        private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

        private FinancialPolicy() {
        }

        /**
         * Calendar-day fraction helper reserved for penalty/other daily products.
         * DO NOT use this method for contractual loan interest or management fees.
         */
        public static BigDecimal dailyRateFraction(
                        BigDecimal monthlyRatePercent,
                        LocalDate date) {

                if (monthlyRatePercent == null
                                || monthlyRatePercent.signum() <= 0
                                || date == null) {
                        return BigDecimal.ZERO;
                }

                int daysInMonth = YearMonth.from(date).lengthOfMonth();

                return monthlyRatePercent
                                .divide(ONE_HUNDRED, RATE_SCALE, ROUNDING)
                                .divide(BigDecimal.valueOf(daysInMonth), RATE_SCALE, ROUNDING);
        }

        public static BigDecimal contractualMonthlyCharge(
                        BigDecimal openingPrincipal,
                        BigDecimal monthlyRatePercent) {

                if (openingPrincipal == null
                                || openingPrincipal.signum() <= 0
                                || monthlyRatePercent == null
                                || monthlyRatePercent.signum() < 0) {
                        return BigDecimal.ZERO.setScale(2, ROUNDING);
                }

                return openingPrincipal
                                .multiply(monthlyRatePercent)
                                .divide(ONE_HUNDRED, 2, ROUNDING);
        }

        public static BigDecimal accrueScheduledMonthly(
                        BigDecimal openingPrincipal,
                        BigDecimal monthlyRatePercent) {
                return contractualMonthlyCharge(openingPrincipal, monthlyRatePercent);
        }

        /**
         * Accrues a monthly percentage on calendar-day basis with Noble Loan's
         * minimum-one-chargeable-day rule for a first repayment.
         *
         * Same-day repayment (for example 10:00 -> 10:01) is one chargeable day.
         * This method is for earned/accrued interest and must not be used to
         * reconstruct the contractual monthly schedule.
         */
        public static BigDecimal accrueDailyMinimumOneDay(
                        BigDecimal principal,
                        LocalDate startDate,
                        LocalDate paymentDate,
                        BigDecimal monthlyRatePercent) {

                if (principal == null || principal.signum() <= 0
                                || startDate == null || paymentDate == null
                                || monthlyRatePercent == null || monthlyRatePercent.signum() <= 0) {
                        return BigDecimal.ZERO.setScale(2, ROUNDING);
                }

                LocalDate endExclusive = paymentDate;
                if (!startDate.isBefore(endExclusive)) {
                        endExclusive = startDate.plusDays(1);
                }

                return accrueDaily(
                                principal,
                                startDate,
                                endExclusive,
                                monthlyRatePercent);
        }

        /**
         * Daily accrual helper retained for penalties and other explicitly daily
         * products. Contractual loan interest and management fees must use
         * accrueScheduledMonthly()/contractualMonthlyCharge().
         */
        public static BigDecimal accrueDaily(
                        BigDecimal principal,
                        LocalDate startDate,
                        LocalDate endDate,
                        BigDecimal monthlyRatePercent) {

                if (principal == null
                                || principal.signum() <= 0
                                || startDate == null
                                || endDate == null
                                || !startDate.isBefore(endDate)
                                || monthlyRatePercent == null
                                || monthlyRatePercent.signum() <= 0) {
                        return BigDecimal.ZERO.setScale(2, ROUNDING);
                }

                BigDecimal total = BigDecimal.ZERO;
                LocalDate cursor = startDate;

                while (cursor.isBefore(endDate)) {
                        total = total.add(
                                        principal.multiply(
                                                        dailyRateFraction(monthlyRatePercent, cursor)));
                        cursor = cursor.plusDays(1);
                }

                return total.setScale(2, ROUNDING);
        }

        public static ScheduleLine contractualScheduleLine(
                        BigDecimal openingPrincipal,
                        int remainingInstallments,
                        BigDecimal monthlyInterestRatePercent,
                        BigDecimal monthlyManagementFeeRatePercent) {

                if (openingPrincipal == null || openingPrincipal.signum() < 0) {
                        throw new IllegalArgumentException("Opening principal cannot be negative");
                }
                if (remainingInstallments <= 0) {
                        throw new IllegalArgumentException(
                                        "Remaining installments must be greater than zero");
                }
                if (monthlyInterestRatePercent == null
                                || monthlyInterestRatePercent.signum() < 0) {
                        throw new IllegalArgumentException("Interest rate cannot be negative");
                }
                if (monthlyManagementFeeRatePercent == null
                                || monthlyManagementFeeRatePercent.signum() < 0) {
                        throw new IllegalArgumentException(
                                        "Management fee rate cannot be negative");
                }

                BigDecimal opening = openingPrincipal.setScale(2, ROUNDING);

                BigDecimal principalComponent = remainingInstallments == 1
                                ? opening
                                : opening
                                                .divide(
                                                                BigDecimal.valueOf(remainingInstallments),
                                                                16,
                                                                ROUNDING)
                                                .setScale(2, ROUNDING);

                BigDecimal interest = contractualMonthlyCharge(
                                opening,
                                monthlyInterestRatePercent);

                BigDecimal managementFee = contractualMonthlyCharge(
                                opening,
                                monthlyManagementFeeRatePercent);

                BigDecimal installment = principalComponent
                                .add(interest)
                                .add(managementFee)
                                .setScale(2, ROUNDING);

                BigDecimal remainingBalance = opening
                                .subtract(principalComponent)
                                .max(BigDecimal.ZERO)
                                .setScale(2, ROUNDING);

                return new ScheduleLine(
                                principalComponent,
                                interest,
                                managementFee,
                                installment,
                                remainingBalance);
        }

        public record ScheduleLine(
                        BigDecimal principal,
                        BigDecimal interest,
                        BigDecimal managementFee,
                        BigDecimal installment,
                        BigDecimal remainingBalance) {
        }

        public static BigDecimal processingFee(BigDecimal principal) {
                if (principal == null || principal.signum() <= 0) {
                        return BigDecimal.ZERO.setScale(2, ROUNDING);
                }

                return principal
                                .multiply(PROCESSING_FEE_RATE)
                                .divide(ONE_HUNDRED, 2, ROUNDING);
        }

        public static BigDecimal extensionFee(BigDecimal outstandingPrincipal) {
                if (outstandingPrincipal == null || outstandingPrincipal.signum() <= 0) {
                        return BigDecimal.ZERO.setScale(2, ROUNDING);
                }

                return outstandingPrincipal
                                .multiply(EXTENSION_FEE_RATE)
                                .divide(ONE_HUNDRED, 2, ROUNDING);
        }
}