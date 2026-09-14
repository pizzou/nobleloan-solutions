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

public BigDecimal money(Number value) {
    if (value == null) {
        return MoneyMath.ZERO;
    }
    return new BigDecimal(value.toString()).setScale(MoneyMath.SCALE, ROUNDING);
}


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

    if (rateType != null && !"MONTHLY".equalsIgnoreCase(rateType)) {
        throw new IllegalArgumentException(
                "Only MONTHLY contractual pricing is supported in production");
    }

    /*
     * Monthly pricing always uses the actual calendar length of the date's month.
     */
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


public BigDecimal penalty(BigDecimal principal, int daysLate) {
    return penalty(principal, daysLate, LocalDate.now());
}


public BigDecimal penalty(
        BigDecimal principal,
        int daysLate,
        LocalDate firstChargeableDate) {

    return FinancialPolicy.dailyPenalty(
            principal,
            daysLate,
            FinancialPolicy.MONTHLY_PENALTY_RATE,
            firstChargeableDate);
}

public BigDecimal penalty(
        BigDecimal principal,
        LocalDate startDate,
        LocalDate endDate) {

    long totalDays = java.time.temporal.ChronoUnit.DAYS.between(
            startDate,
            endDate);

    if (totalDays <= FinancialPolicy.PENALTY_GRACE_DAYS) {
        return MoneyMath.ZERO;
    }

    long chargeableDays =
            totalDays - FinancialPolicy.PENALTY_GRACE_DAYS;

    LocalDate firstChargeableDate =
            startDate.plusDays(
                    FinancialPolicy.PENALTY_GRACE_DAYS + 1L);

    return FinancialPolicy.dailyPenaltyForDays(
            principal,
            (int) chargeableDays,
            FinancialPolicy.MONTHLY_PENALTY_RATE,
            firstChargeableDate);
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

    BigDecimal afterPenalty =
            amount.subtract(penalty).max(BigDecimal.ZERO);

    BigDecimal interestPaid =
            afterPenalty.min(interest)
                    .setScale(MoneyMath.SCALE, ROUNDING);

    BigDecimal principalAvailable =
            afterPenalty.subtract(interestPaid)
                    .max(BigDecimal.ZERO);

    BigDecimal principalPaid =
            principalAvailable.min(principal)
                    .setScale(MoneyMath.SCALE, ROUNDING);

    BigDecimal newBalance =
            principal.subtract(principalPaid)
                    .max(BigDecimal.ZERO)
                    .setScale(MoneyMath.SCALE, ROUNDING);

    return new Allocation(
            interestPaid,
            principalPaid,
            newBalance,
            principalAvailable
                    .subtract(principalPaid)
                    .max(BigDecimal.ZERO)
                    .setScale(MoneyMath.SCALE, ROUNDING));
}

public BigDecimal nonNegative(BigDecimal value) {
    return money(value)
            .max(BigDecimal.ZERO)
            .setScale(MoneyMath.SCALE, ROUNDING);
}

public record Allocation(
        BigDecimal interestPaid,
        BigDecimal principalPaid,
        BigDecimal newPrincipalBalance,
        BigDecimal unappliedAfterInterestAndPenalty) {
}


}
