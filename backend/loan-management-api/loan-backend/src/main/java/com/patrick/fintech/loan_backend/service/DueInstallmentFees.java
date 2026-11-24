package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Payment;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Computes the unpaid fees that are actually due on a payment date.
 * Future contractual installments remain scheduled, not accelerated.
 * Imported historical arrears are preserved at loan level because legacy
 * imports may not have a complete per-month payment schedule.
 */
public final class DueInstallmentFees {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private DueInstallmentFees() {}

    public record Due(BigDecimal interest, BigDecimal management) {}

    public static Due calculate(Loan loan, List<Payment> installments, LocalDate today) {
        if (loan == null || today == null) {
            return new Due(ZERO, ZERO);
        }
        BigDecimal interest = ZERO;
        BigDecimal management = ZERO;
        if (installments != null) {
            for (Payment p : installments) {
                if (p == null || p.getDueDate() == null || p.getDueDate().isAfter(today)) {
                    continue;
                }
                interest = interest.add(money(p.getCycleInterestRemainingDecimal()));
                management = management.add(money(p.getCycleManagementFeeRemainingDecimal()));
            }
        }

        BigDecimal interestOutstanding = money(loan.getInterestOutstandingDecimal());
        BigDecimal managementOutstanding = money(loan.getManagementFeeOutstandingDecimal());

        // Only collapse to one historical arrears bucket when no separate
        // multi-instalment history exists. Otherwise use individual dues.
        if (Boolean.TRUE.equals(loan.getImported())
                && (installments == null || installments.size() <= 1)
                && loan.getNextDueDate() != null
                && !loan.getNextDueDate().isAfter(today)) {
            // Historical loan-level balances may cover many overdue months.
            interest = interest.max(interestOutstanding);
            management = management.max(managementOutstanding);
        }

        // The loan totals are the authoritative cap; never accelerate or
        // create a second contractual receivable.
        return new Due(interest.min(interestOutstanding),
                management.min(managementOutstanding));
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount == null ? ZERO
                : amount.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
