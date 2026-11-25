package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.util.FinancialPolicy;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Institution-wide loan delinquency rule:
 * contractual maturity = actual disbursement + agreed term in calendar months.
 * Three full grace days follow maturity. Overdue and penalty days begin on
 * maturity + four calendar days. Each chargeable day includes the as-of date.
 *
 * Installment due dates are repayment schedule obligations, not the start of
 * contractual penalty accrual or overdue loan classification.
 */
public final class OverduePenaltyClock {
    private OverduePenaltyClock() {}

    public static LocalDate contractualMaturity(Loan loan) {
        if (loan == null) return null;
        LocalDate disbursed = loan.getDisbursedAt() != null
                ? loan.getDisbursedAt().toLocalDate()
                : loan.getDisbursedAtTimestamp() != null
                        ? loan.getDisbursedAtTimestamp().toLocalDate()
                        : loan.getStartDate();
        Integer duration = loan.getDurationMonths();
        if (disbursed != null && duration != null && duration > 0) {
            return disbursed.plusMonths(duration);
        }
        return loan.getMaturityDate();
    }

    public static LocalDate firstChargeableDay(Loan loan) {
        return firstChargeableDay(contractualMaturity(loan));
    }

    public static LocalDate firstChargeableDay(LocalDate maturityDate) {
        return maturityDate == null ? null :
                maturityDate.plusDays(FinancialPolicy.PENALTY_GRACE_DAYS + 1L);
    }

    /** Days overdue begin on the first chargeable day, after three FULL grace days. */
    public static long daysOverdue(Loan loan, LocalDate asOf) {
        return daysOverdue(contractualMaturity(loan), asOf);
    }

    public static long chargeableDays(Loan loan, LocalDate asOf) {
        return chargeableDays(contractualMaturity(loan), asOf);
    }

    public static long chargeableDays(LocalDate maturityDate, LocalDate asOf) {
        LocalDate first = firstChargeableDay(maturityDate);
        return first == null || asOf == null || first.isAfter(asOf)
                ? 0L : ChronoUnit.DAYS.between(first, asOf) + 1L;
    }

    public static long daysOverdue(LocalDate maturityDate, LocalDate asOf) {
        // Day one is maturity + 4 calendar days (after three full grace days).
        // Example: disbursed 2026-03-26, six months -> maturity 2026-09-26;
        // grace 27-29 Sep; overdue day 1 on 2026-09-30.
        return chargeableDays(maturityDate, asOf);
    }
}
