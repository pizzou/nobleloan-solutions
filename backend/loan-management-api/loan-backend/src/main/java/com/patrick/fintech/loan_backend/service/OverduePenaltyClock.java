package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.util.FinancialPolicy;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * One calendar-day convention for late charges across payment posting,
 * daily accrual and audit.
 *
 * A loan due on Aug 31 has Sep 1, 2 and 3 as full grace days,
 * and Sep 4 as its first chargeable day. The as-of date is inclusive:
 * a receipt posted on Nov 19 settles the penalty calculated through
 * Nov 19, before the principal is reduced by that receipt.
 */
public final class OverduePenaltyClock {
    private OverduePenaltyClock() {}

    public static LocalDate firstChargeableDay(LocalDate oldestUnpaidDueDate) {
        if (oldestUnpaidDueDate == null) {
            return null;
        }
        return oldestUnpaidDueDate.plusDays(FinancialPolicy.PENALTY_GRACE_DAYS + 1L);
    }

    public static long daysOverdue(LocalDate oldestUnpaidDueDate, LocalDate asOf) {
        if (oldestUnpaidDueDate == null || asOf == null) {
            return 0L;
        }
        return Math.max(0L, ChronoUnit.DAYS.between(oldestUnpaidDueDate, asOf));
    }

    public static long chargeableDays(LocalDate oldestUnpaidDueDate, LocalDate asOf) {
        LocalDate first = firstChargeableDay(oldestUnpaidDueDate);
        if (first == null || asOf == null || first.isAfter(asOf)) {
            return 0L;
        }
        return ChronoUnit.DAYS.between(first, asOf) + 1L;
    }
}
