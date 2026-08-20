package com.patrick.fintech.loan_backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Single source of truth for the platform's loan pricing rules.
 *
 * Rates are stored as percentage values, not fractions:
 * 5.00 means 5%, 15.00 means 15%.
 * Recurring monthly rates accrue on a calendar-day basis.
 */
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

    /** Returns a fraction such as 0.001666... for 5% in a 30-day month. */
    public static BigDecimal dailyRateFraction(BigDecimal monthlyRatePercent, LocalDate date) {
        if (monthlyRatePercent == null || monthlyRatePercent.signum() <= 0 || date == null) {
            return BigDecimal.ZERO;
        }
        int daysInMonth = YearMonth.from(date).lengthOfMonth();
        return monthlyRatePercent
                .divide(ONE_HUNDRED, RATE_SCALE, ROUNDING)
                .divide(BigDecimal.valueOf(daysInMonth), RATE_SCALE, ROUNDING);
    }

    /**
     * Calculates a contractual scheduled charge for one monthly installment.
     *
     * A rate declared as 5.00 means exactly 5% of the opening outstanding
     * principal for that contractual month. This is intentionally separate
     * from {@link #accrueDaily}, which is used for elapsed-day accrual between
     * payment events. Scheduled installments must not gain or lose value just
     * because a monthly period crosses from a 31-day month into a 30-day month.
     */
    public static BigDecimal accrueScheduledMonthly(
            BigDecimal openingPrincipal,
            BigDecimal monthlyRatePercent) {

        if (openingPrincipal == null || openingPrincipal.signum() <= 0
                || monthlyRatePercent == null || monthlyRatePercent.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return openingPrincipal
                .multiply(monthlyRatePercent)
                .divide(ONE_HUNDRED, 2, ROUNDING);
    }

    /**
     * Accrues a monthly percentage daily over [startDate, endDate), using the
     * actual calendar length of every month crossed by the interval.
     */
    public static BigDecimal accrueDaily(
            BigDecimal principal,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal monthlyRatePercent) {

        if (principal == null || principal.signum() <= 0
                || startDate == null || endDate == null
                || !startDate.isBefore(endDate)
                || monthlyRatePercent == null || monthlyRatePercent.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal total = BigDecimal.ZERO;
        LocalDate cursor = startDate;
        while (cursor.isBefore(endDate)) {
            total = total.add(principal.multiply(dailyRateFraction(monthlyRatePercent, cursor)));
            cursor = cursor.plusDays(1);
        }

        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal processingFee(BigDecimal principal) {
        if (principal == null || principal.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return principal
                .multiply(PROCESSING_FEE_RATE)
                .divide(ONE_HUNDRED, 2, ROUNDING);
    }

    public static BigDecimal extensionFee(BigDecimal outstandingPrincipal) {
        if (outstandingPrincipal == null || outstandingPrincipal.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return outstandingPrincipal
                .multiply(EXTENSION_FEE_RATE)
                .divide(ONE_HUNDRED, 2, ROUNDING);
    }
}