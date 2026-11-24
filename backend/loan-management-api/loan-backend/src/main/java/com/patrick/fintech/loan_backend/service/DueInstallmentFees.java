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

        // Once the contractual loan ends, every remaining contractual month
        // has matured. A borrower paying after maturity must clear all accrued
        // interest and management charges (equally if dues are equal) after
        // eligible penalties, before any principal. Never charge an additional
        // month merely because the borrower paid late.
        LocalDate maturity = com.patrick.fintech.loan_backend.util.LoanMaturityPolicy
                .maturityDate(loan);
        if (maturity != null && !today.isBefore(maturity)) {
            return new Due(interestOutstanding, managementOutstanding);
        }

        // Only collapse to one historical arrears bucket when no separate
        // multi-instalment history exists. Otherwise use individual dues.
        if (Boolean.TRUE.equals(loan.getImported())
                && (installments == null || installments.size() <= 1)
                && com.patrick.fintech.loan_backend.util.LoanMaturityPolicy.maturityDate(loan) == null
                && loan.getNextDueDate() != null
                && !loan.getNextDueDate().isAfter(today)) {
            // Historical loan-level balances may cover many overdue months.
            interest = interest.max(interestOutstanding);
            management = management.max(managementOutstanding);
        }

        // Imported historical portfolios sometimes contain a single future
        // receipt placeholder or no schedule at all. In that case due fees
        // must be recovered from the verified contractual term, not by charging
        // only the one placeholder month and sending the rest to principal.
        // Full contractual totals are split across signed monthly periods,
        // never beyond maturity and never beyond the loan's unpaid balances.
        if (Boolean.TRUE.equals(loan.getImported())
                && (installments == null || installments.size() <= 1)
                && loan.getDisbursedAt() != null
                && loan.getDurationMonths() != null
                && loan.getDurationMonths() > 0
                && com.patrick.fintech.loan_backend.util.LoanMaturityPolicy.maturityDate(loan) != null) {
            LocalDate disbursed = loan.getDisbursedAt().toLocalDate();
            int monthsDue = 0;
            for (int month = 1; month <= loan.getDurationMonths(); month++) {
                if (!disbursed.plusMonths(month).isAfter(today)) monthsDue++;
            }
            if (monthsDue > 0) {
                BigDecimal dueInterest = installmentContractualDue(
                        loan.getTotalInterestDecimal(), loan.getInterestPaidDecimal(),
                        monthsDue, loan.getDurationMonths());
                BigDecimal dueManagement = installmentContractualDue(
                        loan.getManagementFeeDecimal(), loan.getManagementFeePaidDecimal(),
                        monthsDue, loan.getDurationMonths());
                interest = interest.max(dueInterest);
                management = management.max(dueManagement);
            }
        }

        // The loan totals are the authoritative cap; never accelerate or
        // create a second contractual receivable.
        return new Due(interest.min(interestOutstanding),
                management.min(managementOutstanding));
    }

    private static BigDecimal installmentContractualDue(BigDecimal total, BigDecimal paid,
            int monthsDue, int durationMonths) {
        if (total == null || durationMonths <= 0 || monthsDue <= 0) return ZERO;
        BigDecimal matured = money(total).multiply(BigDecimal.valueOf(monthsDue))
                .divide(BigDecimal.valueOf(durationMonths), 2, RoundingMode.HALF_UP);
        return money(matured.subtract(money(paid))).max(ZERO);
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount == null ? ZERO
                : amount.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
