package com.patrick.fintech.loan_backend.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single authoritative payment allocation order for the lending platform.
 *
 * The existing non-overdue allocation is:
 * interest -> management fee -> principal -> extension/restructuring fee
 * -> penalty -> overpayment.
 *
 * Interest and management fee are the first contractual charges. Principal
 * is reduced immediately from the remaining payment. Extension fees and
 * penalties remain separate receivables and never increase principal.
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

        BigDecimal interest = take(remaining, interestDue);
        remaining = money(remaining.subtract(interest));

        BigDecimal managementFee = take(remaining, managementFeeDue);
        remaining = money(remaining.subtract(managementFee));

        BigDecimal principal = take(remaining, principalDue);
        remaining = money(remaining.subtract(principal));

        BigDecimal extensionFee = take(remaining, extensionFeeDue);
        remaining = money(remaining.subtract(extensionFee));

        BigDecimal penalty = take(remaining, penaltyDue);
        remaining = money(remaining.subtract(penalty));

        return new Allocation(
                penalty,
                extensionFee,
                interest,
                managementFee,
                principal,
                remaining.max(ZERO));
    }

    /**
     * Overdue-only waterfall. Caller supplies all verified due charges as of
     * the actual receipt date, not today's balances or future installments.
     * Extension fee retains its existing priority after principal.
     * No side effects: historical replay can use the same calculation.
     */
    public Allocation allocateOverdue(
            BigDecimal paymentAmount,
            BigDecimal penaltyDue,
            BigDecimal extensionFeeDue,
            BigDecimal interestDue,
            BigDecimal managementFeeDue,
            BigDecimal principalDue) {
        BigDecimal remaining = money(paymentAmount).max(ZERO);
        BigDecimal penalty = take(remaining, penaltyDue);
        remaining = remaining.subtract(penalty);
        BigDecimal interest = take(remaining, interestDue);
        remaining = remaining.subtract(interest);
        BigDecimal management = take(remaining, managementFeeDue);
        remaining = remaining.subtract(management);
        BigDecimal principal = take(remaining, principalDue);
        remaining = remaining.subtract(principal);
        BigDecimal extension = take(remaining, extensionFeeDue);
        remaining = remaining.subtract(extension);
        return new Allocation(penalty, extension, interest, management, principal, remaining);
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
