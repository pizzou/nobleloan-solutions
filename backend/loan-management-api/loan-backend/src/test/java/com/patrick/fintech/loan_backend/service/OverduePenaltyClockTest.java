package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.util.FinancialPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OverduePenaltyClockTest {
    private Loan loan(LocalDate disbursement, int duration) {
        return Loan.builder().disbursedAt(disbursement.atStartOfDay())
                .durationMonths(duration).build();
    }

    @Test
    void sixMonthContractMaturesBeforeGraceThenPenaltiesBegin() {
        Loan loan = loan(LocalDate.of(2026, 3, 19), 6);
        assertEquals(LocalDate.of(2026, 9, 19),
                OverduePenaltyClock.contractualMaturity(loan));
        assertEquals(LocalDate.of(2026, 9, 23),
                OverduePenaltyClock.firstChargeableDay(loan));
        assertEquals(0L, OverduePenaltyClock.daysOverdue(loan, LocalDate.of(2026, 9, 22)));
        assertEquals(1L, OverduePenaltyClock.daysOverdue(loan, LocalDate.of(2026, 9, 23)));
        assertEquals(64L, OverduePenaltyClock.daysOverdue(loan, LocalDate.of(2026, 11, 25)));
        assertEquals(64L, OverduePenaltyClock.chargeableDays(loan, LocalDate.of(2026, 11, 25)));
    }

    @Test
    void marchTwentySixSixMonthLoanGraceEndsSeptemberTwentyNine() {
        Loan loan = loan(LocalDate.of(2026, 3, 26), 6);
        assertEquals(LocalDate.of(2026, 9, 26), OverduePenaltyClock.contractualMaturity(loan));
        assertEquals(LocalDate.of(2026, 9, 30), OverduePenaltyClock.firstChargeableDay(loan));
        for (int day = 26; day <= 29; day++) {
            assertEquals(0L, OverduePenaltyClock.daysOverdue(loan, LocalDate.of(2026, 9, day)));
            assertEquals(0L, OverduePenaltyClock.chargeableDays(loan, LocalDate.of(2026, 9, day)));
        }
        assertEquals(1L, OverduePenaltyClock.daysOverdue(loan, LocalDate.of(2026, 9, 30)));
        assertEquals(2L, OverduePenaltyClock.daysOverdue(loan, LocalDate.of(2026, 10, 1)));
        assertEquals(57L, OverduePenaltyClock.daysOverdue(loan, LocalDate.of(2026, 11, 25)));
        assertEquals(57L, OverduePenaltyClock.chargeableDays(loan, LocalDate.of(2026, 11, 25)));
    }

    @Test
    void dateAfterMaturityBeforeGraceEndsIsNotOverdue() {
        assertEquals(0L, OverduePenaltyClock.daysOverdue(
                LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 25)));
    }

    @Test
    void monthlyInstallmentDateDoesNotStartPenaltyBeforeContractMaturity() {
        Loan loan = loan(LocalDate.of(2026, 3, 19), 6);
        loan.setNextDueDate(LocalDate.of(2026, 4, 19));
        assertEquals(0L, OverduePenaltyClock.daysOverdue(loan, LocalDate.of(2026, 8, 31)));
        assertEquals(LocalDate.of(2026, 9, 23), OverduePenaltyClock.firstChargeableDay(loan));
    }

    @Test
    void penaltyUsesCalendarMonthDaysFromMaturityNotEarlyInstallment() {
        Loan loan = loan(LocalDate.of(2026, 3, 19), 6);
        BigDecimal principal = new BigDecimal("33500000.00");
        BigDecimal actual = FinancialPolicy.historicalDailyPenalty(
                principal,
                OverduePenaltyClock.firstChargeableDay(loan),
                LocalDate.of(2026, 11, 19),
                new BigDecimal("10.00"),
                day -> principal);
        // Sep 23-30: 8 days, Oct 1-31: 31 days, Nov 1-19: 19 days.
        BigDecimal expected = new BigDecimal("893333.333333333333")
                .add(new BigDecimal("3350000.00"))
                .add(new BigDecimal("2121666.666666666667"))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        assertEquals(expected, actual);
    }
}
