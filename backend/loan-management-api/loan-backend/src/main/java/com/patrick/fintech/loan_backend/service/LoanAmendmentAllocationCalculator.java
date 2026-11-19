package com.patrick.fintech.loan_backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure calculation used to preview the repayment impact of a contractual
 * correction. This class never changes recorded payments or ledger entries.
 * Historical cash must be independently verified before any posting.
 */
public final class LoanAmendmentAllocationCalculator {
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private LoanAmendmentAllocationCalculator() {}

    public record Allocation(
            BigDecimal principalPaid,
            BigDecimal interestPaid,
            BigDecimal managementFeePaid,
            BigDecimal principalOutstanding,
            BigDecimal interestOutstanding,
            BigDecimal managementFeeOutstanding,
            BigDecimal unallocated,
            BigDecimal totalOutstanding) {}

    /**
     * Allocates a verified repayment pool to interest, management fee, then
     * principal, matching PaymentAllocationService's contractual priority.
     * Penalties, extension fees and application fees must be accounted for by
     * the caller before passing the repayment pool.
     */
    public static Allocation allocate(BigDecimal principal, BigDecimal totalInterest,
                                      BigDecimal totalManagementFee, BigDecimal repaymentPool) {
        BigDecimal amount = money(principal);
        BigDecimal interest = money(totalInterest);
        BigDecimal management = money(totalManagementFee);
        BigDecimal remaining = money(repaymentPool);
        if (amount.signum() <= 0 || interest.signum() < 0 || management.signum() < 0
                || remaining.signum() < 0) {
            throw new IllegalArgumentException("Financial reconciliation requires nonnegative obligations and repayment");
        }

        BigDecimal paidInterest = remaining.min(interest);
        remaining = remaining.subtract(paidInterest);
        BigDecimal paidManagement = remaining.min(management);
        remaining = remaining.subtract(paidManagement);
        BigDecimal paidPrincipal = remaining.min(amount);
        remaining = remaining.subtract(paidPrincipal);

        BigDecimal duePrincipal = amount.subtract(paidPrincipal);
        BigDecimal dueInterest = interest.subtract(paidInterest);
        BigDecimal dueManagement = management.subtract(paidManagement);
        return new Allocation(paidPrincipal, paidInterest, paidManagement,
                duePrincipal, dueInterest, dueManagement, remaining,
                duePrincipal.add(dueInterest).add(dueManagement).setScale(2));
    }

    private static BigDecimal money(BigDecimal amount) {
        if (amount == null) throw new IllegalArgumentException("Amount is required");
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
