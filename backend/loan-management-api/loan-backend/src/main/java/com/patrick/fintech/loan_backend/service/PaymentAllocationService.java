package com.patrick.fintech.loan_backend.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single authoritative payment allocation order for the lending platform.
 *
 * Allocation is always:
 * overdue penalties -> equal settlement of due interest and management fees
 * -> extension fees -> principal -> overpayment.
 *
 * Outstanding charges must be settled before principal can be reduced.
 * The caller is responsible for supplying only valid, already-due charges;
 * future unearned charges must not be accelerated by this method.
 *
 * This class is deliberately side-effect free. Persistence, accounting and
 * audit updates remain the responsibility of PaymentService.
 */
@Service
public class PaymentAllocationService {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    public Allocation allocate(
            BigDecimal paymentAmount,
            BigDecimal penaltyDue,
            BigDecimal extensionFeeDue,
            BigDecimal interestDue,
            BigDecimal managementFeeDue,
            BigDecimal principalDue) {

        BigDecimal remaining = money(paymentAmount).max(ZERO);

        BigDecimal penalty = take(remaining, penaltyDue);
        remaining = money(remaining.subtract(penalty));

        // Interest and management fees have equal priority. Split available
        // money evenly while both receivables remain due; if one is fully
        // settled, use the unneeded share to clear the other before principal.
        BigDecimal interestOutstanding = money(interestDue).max(ZERO);
        BigDecimal managementOutstanding = money(managementFeeDue).max(ZERO);
        BigDecimal feeBudget = remaining.min(
                money(interestOutstanding.add(managementOutstanding)));
        BigDecimal half = feeBudget.divide(
                BigDecimal.valueOf(2), SCALE, RoundingMode.HALF_DOWN);
        BigDecimal interest = half.min(interestOutstanding);
        BigDecimal managementFee = money(feeBudget.subtract(half))
                .min(managementOutstanding);
        BigDecimal stillAvailable = money(
                feeBudget.subtract(interest).subtract(managementFee));
        BigDecimal furtherInterest = stillAvailable.min(
                money(interestOutstanding.subtract(interest)));
        interest = money(interest.add(furtherInterest));
        stillAvailable = money(stillAvailable.subtract(furtherInterest));
        BigDecimal furtherManagement = stillAvailable.min(
                money(managementOutstanding.subtract(managementFee)));
        managementFee = money(managementFee.add(furtherManagement));
        remaining = money(remaining.subtract(interest).subtract(managementFee));

        BigDecimal extensionFee = take(remaining, extensionFeeDue);
        remaining = money(remaining.subtract(extensionFee));

        BigDecimal principal = take(remaining, principalDue);
        remaining = money(remaining.subtract(principal));

        return new Allocation(
                penalty,
                extensionFee,
                interest,
                managementFee,
                principal,
                remaining.max(ZERO));
    }

    private BigDecimal take(BigDecimal available, BigDecimal due) {
        BigDecimal safeAvailable = money(available).max(ZERO);
        BigDecimal safeDue = money(due).max(ZERO);
        return safeAvailable.min(safeDue);
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return value.setScale(SCALE, ROUNDING);
    }

    public record Allocation(
            BigDecimal penalty,
            BigDecimal extensionFee,
            BigDecimal interest,
            BigDecimal managementFee,
            BigDecimal principal,
            BigDecimal overpayment) {
    }
}
