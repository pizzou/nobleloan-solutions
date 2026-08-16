package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.util.FinancialPolicy;
import com.patrick.fintech.loan_backend.util.MoneyMath;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
public class FinancialCalculationService {

    private static final int RATE_SCALE = 12;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal THREE_HUNDRED_SIXTY_FIVE = BigDecimal.valueOf(365);

    public BigDecimal money(Number value) {
        if (value == null) {
            return MoneyMath.ZERO;
        }
        return new BigDecimal(value.toString()).setScale(MoneyMath.SCALE, ROUNDING);
    }

    /**
     * Returns today's calendar-day rate for the supplied percentage rate.
     * Monthly rates use the actual number of days in the current month.
     */
    public BigDecimal dailyRate(Number rate, String rateType) {
        return dailyRate(rate, rateType, LocalDate.now());
    }

    public BigDecimal dailyRate(Number rate, String rateType, LocalDate date) {
        BigDecimal normalized = rate == null
                ? BigDecimal.ZERO
                : new BigDecimal(rate.toString());

        if (normalized.signum() <= 0 || date == null) {
            return BigDecimal.ZERO.setScale(RATE_SCALE, ROUNDING);
        }

        if ("ANNUAL".equalsIgnoreCase(rateType)) {
            return normalized
                    .divide(ONE_HUNDRED, RATE_SCALE, ROUNDING)
                    .divide(THREE_HUNDRED_SIXTY_FIVE, RATE_SCALE, ROUNDING);
        }

        return FinancialPolicy.dailyRateFraction(normalized, date)
                .setScale(RATE_SCALE, ROUNDING);
    }

    public BigDecimal interest(BigDecimal principal, BigDecimal dailyRate, long days) {
        if (principal == null || dailyRate == null || days <= 0) {
            return MoneyMath.ZERO;
        }
        return principal.multiply(dailyRate)
                .multiply(BigDecimal.valueOf(days))
                .setScale(MoneyMath.SCALE, ROUNDING);
    }

    /**
     * Calculates 15% monthly penalty across the actual calendar days ending today.
     */
    public BigDecimal penalty(BigDecimal principal, int daysLate) {
        if (principal == null || principal.signum() <= 0 || daysLate <= 0) {
            return MoneyMath.ZERO;
        }

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(daysLate);
        return FinancialPolicy.accrueDaily(
                principal,
                startDate,
                endDate,
                FinancialPolicy.MONTHLY_PENALTY_RATE);
    }

    public BigDecimal penalty(
            BigDecimal principal,
            LocalDate startDate,
            LocalDate endDate) {
        return FinancialPolicy.accrueDaily(
                principal,
                startDate,
                endDate,
                FinancialPolicy.MONTHLY_PENALTY_RATE);
    }

    public Allocation allocatePayment(
            BigDecimal payment,
            BigDecimal newPenalty,
            BigDecimal remainingInterest,
            BigDecimal principalBalance) {

        BigDecimal amount = money(payment);
        BigDecimal penalty = nonNegative(newPenalty);
        BigDecimal interest = nonNegative(remainingInterest);
        BigDecimal principal = nonNegative(principalBalance);

        BigDecimal afterPenalty = amount.subtract(penalty).max(BigDecimal.ZERO);
        BigDecimal interestPaid = afterPenalty.min(interest).setScale(MoneyMath.SCALE, ROUNDING);
        BigDecimal principalAvailable = afterPenalty.subtract(interestPaid).max(BigDecimal.ZERO);
        BigDecimal principalPaid = principalAvailable.min(principal).setScale(MoneyMath.SCALE, ROUNDING);
        BigDecimal newBalance = principal.subtract(principalPaid).max(BigDecimal.ZERO)
                .setScale(MoneyMath.SCALE, ROUNDING);

        return new Allocation(
                interestPaid,
                principalPaid,
                newBalance,
                principalAvailable.subtract(principalPaid).max(BigDecimal.ZERO)
                        .setScale(MoneyMath.SCALE, ROUNDING));
    }

    public BigDecimal nonNegative(BigDecimal value) {
        return money(value).max(BigDecimal.ZERO).setScale(MoneyMath.SCALE, ROUNDING);
    }

    public record Allocation(
            BigDecimal interestPaid,
            BigDecimal principalPaid,
            BigDecimal newPrincipalBalance,
            BigDecimal unappliedAfterInterestAndPenalty) {
    }
}
