package com.patrick.fintech.loan_backend.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single authoritative payment allocation order for the lending platform.
 *
 * Allocation is always:
 * penalty -> interest -> management fee -> extension fee -> principal
 * -> overpayment.
 *
 * The caller must supply the complete due-and-unpaid amounts across
 * installments and preserve the original allocation in the cash ledger.
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

        // Collect assessed penalties first, followed by ALL due unpaid
        // interest and management fees. Extension charges also precede
        // principal. The caller must supply aggregated due fee balances
        // rather than the charges of only one installment.
        BigDecimal penalty = take(remaining, penaltyDue);
        remaining = money(remaining.subtract(penalty));

        BigDecimal interest = take(remaining, interestDue);
        remaining = money(remaining.subtract(interest));

        BigDecimal managementFee = take(remaining, managementFeeDue);
        remaining = money(remaining.subtract(managementFee));

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
