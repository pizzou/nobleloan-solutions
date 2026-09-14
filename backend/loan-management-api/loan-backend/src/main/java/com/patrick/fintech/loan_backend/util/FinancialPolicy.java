package com.patrick.fintech.loan_backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.function.Function;

public final class FinancialPolicy {

    public static final BigDecimal MONTHLY_INTEREST_RATE =
            new BigDecimal("5.00");

    public static final BigDecimal MONTHLY_MANAGEMENT_FEE_RATE =
            new BigDecimal("5.00");

    public static final BigDecimal APPLICATION_FEE_RATE =
            new BigDecimal("2.00");

    /**
     * Contractual penalty rate.
     *
     * This is 10% PER MONTH, not 10% per day.
     * The amount charged for an individual day is calculated by
     * prorating this monthly rate over the actual number of calendar
     * days in that month.
     */
    public static final BigDecimal MONTHLY_PENALTY_RATE =
            new BigDecimal("10.00");

    /**
     * Legacy compatibility alias.
     *
     * Existing code may still reference DAILY_PENALTY_RATE.
     * It intentionally points to the monthly contractual rate.
     *
     * @deprecated use MONTHLY_PENALTY_RATE
     */
    @Deprecated
    public static final BigDecimal DAILY_PENALTY_RATE =
            MONTHLY_PENALTY_RATE;

    /**
     * Number of calendar grace days before penalty begins.
     *
     * Day 1, 2 and 3: no penalty.
     * Day 4: first chargeable day.
     */
    public static final int PENALTY_GRACE_DAYS = 3;

    /**
     * Extension fee charged once per extension request.
     */
    public static final BigDecimal EXTENSION_FEE_RATE =
            new BigDecimal("10.00");

    private static final BigDecimal ONE_HUNDRED =
            new BigDecimal("100");

    private static final int RATE_SCALE = 16;

    private static final RoundingMode ROUNDING =
            RoundingMode.HALF_UP;

    private FinancialPolicy() {
    }

    /**
     * Converts a monthly percentage into its daily fractional rate
     * using the actual calendar length of the supplied month.
     *
     * Example:
     *
     * 10% monthly in a 31-day month:
     *
     * 10 / 100 / 31
     *
     * 10% monthly in a 30-day month:
     *
     * 10 / 100 / 30
     *
     * 10% monthly in February with 28 days:
     *
     * 10 / 100 / 28
     */
    public static BigDecimal dailyRateFraction(
            BigDecimal monthlyRatePercent,
            LocalDate date) {

        if (monthlyRatePercent == null
                || monthlyRatePercent.signum() <= 0
                || date == null) {
            return BigDecimal.ZERO;
        }

        int daysInMonth =
                YearMonth.from(date).lengthOfMonth();

        return monthlyRatePercent
                .divide(ONE_HUNDRED, RATE_SCALE, ROUNDING)
                .divide(
                        BigDecimal.valueOf(daysInMonth),
                        RATE_SCALE,
                        ROUNDING);
    }

    /**
     * Calculates a contractual monthly charge.
     */
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

    /**
     * Compatibility alias for monthly accrual.
     */
    public static BigDecimal accrueScheduledMonthly(
            BigDecimal openingPrincipal,
            BigDecimal monthlyRatePercent) {

        return contractualMonthlyCharge(
                openingPrincipal,
                monthlyRatePercent);
    }

    /**
     * Accrues at least one day when the payment date is not after
     * the start date.
     */
    public static BigDecimal accrueDailyMinimumOneDay(
            BigDecimal principal,
            LocalDate startDate,
            LocalDate paymentDate,
            BigDecimal monthlyRatePercent) {

        if (principal == null
                || principal.signum() <= 0
                || startDate == null
                || paymentDate == null
                || monthlyRatePercent == null
                || monthlyRatePercent.signum() <= 0) {

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
     * Calculates daily accrual between startDate inclusive and
     * endDate exclusive.
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
                            dailyRateFraction(
                                    monthlyRatePercent,
                                    cursor)));

            cursor = cursor.plusDays(1);
        }

        return total.setScale(2, ROUNDING);
    }

    /**
     * Calculates overdue penalty using the default contractual
     * penalty rate of 10% per month.
     *
     * There are three calendar grace days.
     * Penalty starts on day four.
     */
    public static BigDecimal dailyPenalty(
            BigDecimal outstandingPrincipal,
            int daysLate) {

        return dailyPenalty(
                outstandingPrincipal,
                daysLate,
                MONTHLY_PENALTY_RATE,
                LocalDate.now());
    }

    /**
     * Calculates overdue penalty using a supplied monthly penalty rate.
     */
    public static BigDecimal dailyPenalty(
            BigDecimal outstandingPrincipal,
            int daysLate,
            BigDecimal monthlyPenaltyRatePercent) {

        return dailyPenalty(
                outstandingPrincipal,
                daysLate,
                monthlyPenaltyRatePercent,
                LocalDate.now());
    }

    /**
     * Calculates overdue penalty with an explicit first chargeable date.
     *
     * The monthly penalty rate is prorated by the actual calendar
     * days of each charge month.
     *
     * Example:
     *
     * Principal = 1,000,000
     * Monthly penalty = 10%
     * First chargeable day = January 5
     *
     * January has 31 days:
     *
     * Daily rate = 10% / 31
     */
    public static BigDecimal dailyPenalty(
            BigDecimal outstandingPrincipal,
            int daysLate,
            BigDecimal monthlyPenaltyRatePercent,
            LocalDate firstChargeableDate) {

        if (outstandingPrincipal == null
                || outstandingPrincipal.signum() <= 0
                || monthlyPenaltyRatePercent == null
                || monthlyPenaltyRatePercent.signum() <= 0
                || daysLate <= PENALTY_GRACE_DAYS) {

            return money(BigDecimal.ZERO);
        }

        int chargeableDays =
                daysLate - PENALTY_GRACE_DAYS;

        LocalDate start =
                firstChargeableDate != null
                        ? firstChargeableDate
                        : LocalDate.now();

        BigDecimal total = BigDecimal.ZERO;

        for (int i = 0; i < chargeableDays; i++) {

            LocalDate chargeDate =
                    start.plusDays(i);

            total = total.add(
                    outstandingPrincipal.multiply(
                            dailyRateFraction(
                                    monthlyPenaltyRatePercent,
                                    chargeDate)));
        }

        return money(total);
    }

    /**
     * Calculates the maximum cumulative penalty allowed by the
     * existing financial policy.
     *
     * Interest plus penalty must not exceed outstanding principal.
     */
    public static BigDecimal penaltyCeiling(
            BigDecimal outstandingPrincipal,
            BigDecimal qualifyingInterest) {

        BigDecimal principal =
                money(outstandingPrincipal);

        BigDecimal interest =
                money(qualifyingInterest);

        if (principal.signum() <= 0) {
            return money(BigDecimal.ZERO);
        }

        return money(
                principal
                        .subtract(interest)
                        .max(BigDecimal.ZERO));
    }

    /**
     * Applies the cumulative penalty ceiling to a newly calculated
     * penalty amount.
     */
    public static BigDecimal capPenalty(
            BigDecimal outstandingPrincipal,
            BigDecimal qualifyingInterest,
            BigDecimal alreadyAssessedPenalty,
            BigDecimal newlyCalculatedPenalty) {

        BigDecimal ceiling =
                penaltyCeiling(
                        outstandingPrincipal,
                        qualifyingInterest);

        BigDecimal assessed =
                money(alreadyAssessedPenalty);

        BigDecimal fresh =
                money(newlyCalculatedPenalty);

        BigDecimal room =
                ceiling
                        .subtract(assessed)
                        .max(BigDecimal.ZERO);

        return money(fresh.min(room));
    }

    /**
     * Calculates historical penalty using the actual principal balance
     * that existed on each individual chargeable date.
     *
     * This is the production overload required when principal changes
     * during the overdue period.
     *
     * Example:
     *
     * Jan 5  -> 1,000,000 principal
     * Jan 6  ->   700,000 principal
     * Jan 7  ->   700,000 principal
     *
     * The penalty is calculated separately for each date.
     */
    public static BigDecimal historicalDailyPenalty(
            BigDecimal currentOutstandingPrincipal,
            LocalDate firstChargeableDate,
            LocalDate asOf,
            Function<LocalDate, BigDecimal> principalBalanceAtDate) {

        return historicalDailyPenalty(
                currentOutstandingPrincipal,
                firstChargeableDate,
                asOf,
                MONTHLY_PENALTY_RATE,
                principalBalanceAtDate);
    }

    /**
     * Calculates historical penalty using a date-sensitive principal
     * balance function and a configurable monthly penalty rate.
     *
     * The supplied function must return the outstanding principal
     * applicable to the requested charge date.
     */
    public static BigDecimal historicalDailyPenalty(
            BigDecimal currentOutstandingPrincipal,
            LocalDate firstChargeableDate,
            LocalDate asOf,
            BigDecimal monthlyPenaltyRatePercent,
            Function<LocalDate, BigDecimal> principalBalanceAtDate) {

        if (currentOutstandingPrincipal == null
                || currentOutstandingPrincipal.signum() <= 0
                || firstChargeableDate == null
                || asOf == null
                || monthlyPenaltyRatePercent == null
                || monthlyPenaltyRatePercent.signum() <= 0
                || principalBalanceAtDate == null
                || firstChargeableDate.isAfter(asOf)) {

            return money(BigDecimal.ZERO);
        }

        BigDecimal total = BigDecimal.ZERO;

        LocalDate cursor = firstChargeableDate;

        while (!cursor.isAfter(asOf)) {

            BigDecimal balance =
                    principalBalanceAtDate.apply(cursor);

            if (balance != null
                    && balance.signum() > 0) {

                total = total.add(
                        balance.multiply(
                                dailyRateFraction(
                                        monthlyPenaltyRatePercent,
                                        cursor)));
            }

            cursor = cursor.plusDays(1);
        }

        return money(total);
    }

    /**
     * Compatibility overload for callers that have one fixed
     * principal balance for the entire historical period.
     *
     * This delegates to the date-sensitive implementation.
     */
    public static BigDecimal historicalDailyPenalty(
            BigDecimal currentOutstandingPrincipal,
            LocalDate firstChargeableDate,
            LocalDate asOf,
            BigDecimal monthlyPenaltyRatePercent,
            BigDecimal principalBalanceAtDate) {

        if (principalBalanceAtDate == null) {
            return money(BigDecimal.ZERO);
        }

        return historicalDailyPenalty(
                currentOutstandingPrincipal,
                firstChargeableDate,
                asOf,
                monthlyPenaltyRatePercent,
                date -> principalBalanceAtDate);
    }

    /**
     * Compatibility overload using the default monthly penalty rate
     * and a fixed historical principal balance.
     */
    public static BigDecimal historicalDailyPenalty(
            BigDecimal currentOutstandingPrincipal,
            LocalDate firstChargeableDate,
            LocalDate asOf,
            BigDecimal principalBalanceAtDate) {

        return historicalDailyPenalty(
                currentOutstandingPrincipal,
                firstChargeableDate,
                asOf,
                MONTHLY_PENALTY_RATE,
                principalBalanceAtDate);
    }

    private static BigDecimal contractualDailyPercentage(
            BigDecimal principal,
            BigDecimal ratePercent) {

        return money(
                principal
                        .multiply(ratePercent)
                        .divide(
                                ONE_HUNDRED,
                                RATE_SCALE,
                                ROUNDING));
    }

    private static BigDecimal money(BigDecimal value) {

        return (value == null
                ? BigDecimal.ZERO
                : value).setScale(2, ROUNDING);
    }

   
    public static BigDecimal dailyPenaltyForDays(
            BigDecimal outstandingPrincipal,
            int chargeableDays) {

        return dailyPenaltyForDays(
                outstandingPrincipal,
                chargeableDays,
                MONTHLY_PENALTY_RATE,
                LocalDate.now());
    }

    
    public static BigDecimal dailyPenaltyForDays(
            BigDecimal outstandingPrincipal,
            int chargeableDays,
            BigDecimal monthlyPenaltyRatePercent,
            LocalDate firstChargeableDate) {

        if (outstandingPrincipal == null
                || outstandingPrincipal.signum() <= 0
                || chargeableDays <= 0
                || monthlyPenaltyRatePercent == null
                || monthlyPenaltyRatePercent.signum() <= 0) {

            return money(BigDecimal.ZERO);
        }

        LocalDate start =
                firstChargeableDate != null
                        ? firstChargeableDate
                        : LocalDate.now();

        BigDecimal total = BigDecimal.ZERO;

        for (int i = 0; i < chargeableDays; i++) {

            LocalDate date =
                    start.plusDays(i);

            total = total.add(
                    outstandingPrincipal.multiply(
                            dailyRateFraction(
                                    monthlyPenaltyRatePercent,
                                    date)));
        }

        return money(total);
    }

    /**
     * Calculates one contractual declining-principal schedule line.
     */
    public static ScheduleLine contractualScheduleLine(
            BigDecimal openingPrincipal,
            int remainingInstallments,
            BigDecimal monthlyInterestRatePercent,
            BigDecimal monthlyManagementFeeRatePercent) {

        if (openingPrincipal == null
                || openingPrincipal.signum() < 0) {

            throw new IllegalArgumentException(
                    "Opening principal cannot be negative");
        }

        if (remainingInstallments <= 0) {

            throw new IllegalArgumentException(
                    "Remaining installments must be greater than zero");
        }

        if (monthlyInterestRatePercent == null
                || monthlyInterestRatePercent.signum() < 0) {

            throw new IllegalArgumentException(
                    "Interest rate cannot be negative");
        }

        if (monthlyManagementFeeRatePercent == null
                || monthlyManagementFeeRatePercent.signum() < 0) {

            throw new IllegalArgumentException(
                    "Management fee rate cannot be negative");
        }

        BigDecimal opening =
                openingPrincipal.setScale(2, ROUNDING);

        BigDecimal principalComponent =
                remainingInstallments == 1
                        ? opening
                        : opening
                                .divide(
                                        BigDecimal.valueOf(
                                                remainingInstallments),
                                        16,
                                        ROUNDING)
                                .setScale(2, ROUNDING);

        BigDecimal interest =
                contractualMonthlyCharge(
                        opening,
                        monthlyInterestRatePercent);

        BigDecimal managementFee =
                contractualMonthlyCharge(
                        opening,
                        monthlyManagementFeeRatePercent);

        BigDecimal installment =
                principalComponent
                        .add(interest)
                        .add(managementFee)
                        .setScale(2, ROUNDING);

        BigDecimal remainingBalance =
                opening
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

    /**
     * Calculates the one-time application fee.
     */
    public static BigDecimal applicationFee(
            BigDecimal principal) {

        if (principal == null
                || principal.signum() <= 0) {

            return BigDecimal.ZERO.setScale(2, ROUNDING);
        }

        return principal
                .multiply(APPLICATION_FEE_RATE)
                .divide(
                        ONE_HUNDRED,
                        2,
                        ROUNDING);
    }

    /**
     * Calculates the one-time extension fee from outstanding principal.
     */
    public static BigDecimal extensionFee(
            BigDecimal outstandingPrincipal) {

        if (outstandingPrincipal == null
                || outstandingPrincipal.signum() <= 0) {

            return BigDecimal.ZERO.setScale(2, ROUNDING);
        }

        return outstandingPrincipal
                .multiply(EXTENSION_FEE_RATE)
                .divide(
                        ONE_HUNDRED,
                        2,
                        ROUNDING);
    }
}