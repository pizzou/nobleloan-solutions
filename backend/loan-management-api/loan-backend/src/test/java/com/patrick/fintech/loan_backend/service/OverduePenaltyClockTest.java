package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.util.FinancialPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OverduePenaltyClockTest {
    @Test
    void threeFullGraceDaysThenCalendarDayAccrual() {
        LocalDate due = LocalDate.of(2026, 8, 31);
        LocalDate receiptDate = LocalDate.of(2026, 11, 19);
        assertEquals(80L, OverduePenaltyClock.daysOverdue(due, receiptDate));
        assertEquals(LocalDate.of(2026, 9, 4), OverduePenaltyClock.firstChargeableDay(due));
        assertEquals(77L, OverduePenaltyClock.chargeableDays(due, receiptDate));
        assertEquals(0L, OverduePenaltyClock.chargeableDays(due, due.plusDays(3)));
        assertEquals(1L, OverduePenaltyClock.chargeableDays(due, due.plusDays(4)));
    }

    @Test
    void sourcePenaltyMatchesActualChargeableDaysWithActualCalendarMonths() {
        LocalDate due = LocalDate.of(2026, 8, 31);
        LocalDate paid = LocalDate.of(2026, 11, 19);
        BigDecimal principal = new BigDecimal("33500000.00");
        BigDecimal assessed = FinancialPolicy.historicalDailyPenalty(
                principal,
                OverduePenaltyClock.firstChargeableDay(due),
                paid,
                new BigDecimal("10.00"),
                day -> principal);
        assertEquals(new BigDecimal("8486666.67"), assessed);
    }

    @Test
    void firstThreeOverdueDaysHaveZeroPenaltyAndMaturityAloneIsNotEnough() {
        LocalDate due = LocalDate.of(2026, 11, 19);
        assertEquals(0L, OverduePenaltyClock.chargeableDays(due, LocalDate.of(2026, 11, 22)));
        assertEquals(1L, OverduePenaltyClock.chargeableDays(due, LocalDate.of(2026, 11, 23)));
    }
}
