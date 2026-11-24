package com.patrick.fintech.loan_backend.util;

import com.patrick.fintech.loan_backend.model.Loan;
import java.time.LocalDate;

/**
 * Governs when a contractual penalty can first accrue. Monthly instalments
 * may be overdue before contractual maturity, but penalties start only after
 * maturity and three complete calendar grace days.
 *
 * Imported records with no reliable maturity/term cannot be assigned an
 * inferred penalty date from next_due_date. They require a verified
 * contractual maturity date before further automatic penalty accrual.
 */
public final class LoanMaturityPolicy {

    private LoanMaturityPolicy() {}

    public static LocalDate maturityDate(Loan loan) {
        if (loan == null) {
            return null;
        }
        if (loan.getMaturityDate() != null) {
            return loan.getMaturityDate();
        }
        // Legacy start dates and durations may be inferred/imported rather
        // than verified against the signed contract. Never infer a new
        // chargeable penalty date from such incomplete imported data.
        if (Boolean.TRUE.equals(loan.getImported())) {
            return null;
        }
        Integer months = loan.getDurationMonths();
        if (months == null || months <= 0) {
            return null;
        }
        LocalDate disbursed = loan.getDisbursedAt() != null
                ? loan.getDisbursedAt().toLocalDate()
                : loan.getStartDate();
        return disbursed == null ? null : disbursed.plusMonths(months);
    }

    public static LocalDate firstPenaltyDate(Loan loan) {
        LocalDate maturity = maturityDate(loan);
        return maturity == null ? null
                : maturity.plusDays(FinancialPolicy.PENALTY_GRACE_DAYS + 1L);
    }

    public static boolean chargeable(Loan loan, LocalDate asOf) {
        LocalDate first = firstPenaltyDate(loan);
        return first != null && asOf != null && !asOf.isBefore(first);
    }
}
