package com.patrick.fintech.loan_backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Resolves VERIFIED legacy penalty opening amounts without inventing historic
 * charges from a single principal snapshot. The legacy spreadsheet may have
 * paid penalties, unpaid penalties, or a total assessed amount.
 *
 * Historical opening amounts belong to receivables/equity, never a new
 * disbursement, cash receipt, or current-month penalty income.
 */
public final class LegacyPenaltyOpeningPolicy {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private LegacyPenaltyOpeningPolicy() {}

    /**
     * @return total historical penalties assessed (paid + still outstanding).
     * Missing amounts on already overdue loans are rejected for verification;
     * an explicitly entered zero is accepted as a documented zero assessment.
     */
    public static BigDecimal resolveAssessed(
            BigDecimal assessed,
            BigDecimal paid,
            BigDecimal outstanding,
            boolean overdue,
            LocalDate oldestUnpaidDue,
            LocalDate asOf,
            BigDecimal outstandingPrincipal) {
        BigDecimal historicalPaid = paid == null ? ZERO : paid;
        if (historicalPaid.signum() < 0
                || (outstanding != null && outstanding.signum() < 0)
                || (assessed != null && assessed.signum() < 0)) {
            throw new IllegalArgumentException("Historical penalty amounts cannot be negative");
        }

        BigDecimal resolved = assessed;
        if (resolved == null && outstanding != null) {
            // Assessed = already collected + independently verified remaining.
            resolved = historicalPaid.add(outstanding);
        }

        if (resolved == null && overdue
                && outstandingPrincipal != null && outstandingPrincipal.signum() > 0
                && oldestUnpaidDue != null && asOf != null
                && !oldestUnpaidDue.plusDays(4).isAfter(asOf)) {
            throw new IllegalArgumentException(
                    "Overdue legacy loan has no historical penalties_assessed or penalty_outstanding. "
                    + "Enter the verified total assessed (including previously paid penalties), "
                    + "or explicitly enter 0 if records confirm no historical penalty was assessed. "
                    + "The system will not invent pre-import charges from days overdue.");
        }

        if (resolved == null) {
            resolved = historicalPaid;
        }

        if (resolved.compareTo(historicalPaid) < 0) {
            throw new IllegalArgumentException(
                    "Historical penalties_assessed cannot be lower than penalties_paid");
        }
        if (outstanding != null
                && resolved.subtract(historicalPaid).compareTo(outstanding) != 0) {
            throw new IllegalArgumentException(
                    "Historical penalty_outstanding must equal penalties_assessed minus penalties_paid");
        }
        return resolved;
    }
}
