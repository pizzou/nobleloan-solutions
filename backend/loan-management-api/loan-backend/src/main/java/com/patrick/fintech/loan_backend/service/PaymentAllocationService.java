package com.patrick.fintech.loan_backend.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single authoritative payment allocation order for the lending platform.
 *
 * Allocation is always:
 * penalty -> interest and management fee equally -> principal -> extension fee -> overpayment.
 * Fees and penalties are separate receivables and never increase principal.
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

        return allocate(paymentAmount, penaltyDue, extensionFeeDue, interestDue,
                managementFeeDue, principalDue, false);
    }

    /**
     * Replays approved historical corrections under their original allocation rule.
     */
    Allocation allocateHistoricalCorrection(
            BigDecimal paymentAmount,
            BigDecimal penaltyDue,
            BigDecimal extensionFeeDue,
            BigDecimal interestDue,
            BigDecimal managementFeeDue,
            BigDecimal principalDue) {
        return allocate(paymentAmount, penaltyDue, extensionFeeDue, interestDue,
                managementFeeDue, principalDue, true);
    }

    private Allocation allocate(
            BigDecimal paymentAmount,
            BigDecimal penaltyDue,
            BigDecimal extensionFeeDue,
            BigDecimal interestDue,
            BigDecimal managementFeeDue,
            BigDecimal principalDue,
            boolean historicalManagementFirst) {
        BigDecimal remaining = money(paymentAmount).max(ZERO);

        BigDecimal penalty = take(remaining, penaltyDue);
        remaining = money(remaining.subtract(penalty));

        BigDecimal interestBalance = money(interestDue).max(ZERO);
        BigDecimal managementBalance = money(managementFeeDue).max(ZERO);
        BigDecimal chargePayment = remaining.min(interestBalance.add(managementBalance));
        BigDecimal managementShare = historicalManagementFirst
                ? chargePayment
                : chargePayment.divide(BigDecimal.TWO, SCALE, ROUNDING);
        // Apply any unused share to the other charge before reducing principal.
        BigDecimal managementFee = managementShare.max(chargePayment.subtract(interestBalance))
                .min(managementBalance);
        BigDecimal interest = chargePayment.subtract(managementFee);
        remaining = remaining.subtract(chargePayment);

        BigDecimal principal = take(remaining, principalDue);
        remaining = money(remaining.subtract(principal));

        BigDecimal extensionFee = take(remaining, extensionFeeDue);
        remaining = money(remaining.subtract(extensionFee));

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
