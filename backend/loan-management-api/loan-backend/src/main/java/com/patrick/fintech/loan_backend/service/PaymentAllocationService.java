package com.patrick.fintech.loan_backend.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Single authoritative payment allocation order for the lending platform.
 *
 * Allocation is always:
 * penalty -> management fee -> interest -> principal -> extension fee -> overpayment.
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

        BigDecimal remaining = money(paymentAmount).max(ZERO);

        BigDecimal penalty = take(remaining, penaltyDue);
        remaining = money(remaining.subtract(penalty));

        BigDecimal managementFee = take(remaining, managementFeeDue);
        remaining = money(remaining.subtract(managementFee));

        BigDecimal interest = take(remaining, interestDue);
        remaining = money(remaining.subtract(interest));

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

    public DueAllocation allocateDueInstallments(
            BigDecimal paymentAmount, BigDecimal penaltyDue, BigDecimal extensionFeeDue,
            BigDecimal principalDue, LocalDate paymentDate, List<InstallmentDue> installments) {

        var due = installments.stream()
                .filter(installment -> !installment.dueDate().isAfter(paymentDate))
                .sorted(Comparator.comparing(InstallmentDue::dueDate).thenComparingLong(InstallmentDue::id))
                .toList();
        BigDecimal interestDue = due.stream()
                .map(installment -> money(installment.interest()).max(ZERO))
                .reduce(ZERO, BigDecimal::add);
        BigDecimal managementDue = due.stream()
                .map(installment -> money(installment.managementFee()).max(ZERO))
                .reduce(ZERO, BigDecimal::add);
        Allocation total = allocate(paymentAmount, penaltyDue, extensionFeeDue, interestDue,
                managementDue, principalDue);

        BigDecimal interestRemaining = total.interest();
        BigDecimal managementRemaining = total.managementFee();
        List<InstallmentAllocation> breakdown = new ArrayList<>();
        for (InstallmentDue installment : due) {
            BigDecimal interest = take(interestRemaining, installment.interest());
            BigDecimal management = take(managementRemaining, installment.managementFee());
            if (interest.signum() > 0 || management.signum() > 0) {
                breakdown.add(new InstallmentAllocation(installment.id(), management, interest));
            }
            interestRemaining = interestRemaining.subtract(interest);
            managementRemaining = managementRemaining.subtract(management);
        }
        return new DueAllocation(total, List.copyOf(breakdown));
    }

    public record InstallmentDue(long id, LocalDate dueDate, BigDecimal managementFee, BigDecimal interest) {}
    public record InstallmentAllocation(long id, BigDecimal managementFee, BigDecimal interest) {}
    public record DueAllocation(Allocation total, List<InstallmentAllocation> installments) {}

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
