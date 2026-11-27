package com.patrick.fintech.loan_backend.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class LegacyPenaltyOpeningPolicyTest {
    private static final LocalDate DUE = LocalDate.of(2026, 8, 31);
    private static final LocalDate IMPORT = LocalDate.of(2026, 11, 12);
    private static final BigDecimal PRINCIPAL = new BigDecimal("33500000.00");

    private BigDecimal resolve(BigDecimal assessed, BigDecimal paid,
                               BigDecimal outstanding, boolean overdue) {
        return LegacyPenaltyOpeningPolicy.resolveAssessed(
                assessed, paid, outstanding, overdue, DUE, IMPORT, PRINCIPAL);
    }

    @Test
    void absentPenaltyAmountOnLongOverdueLoanRequiresEvidence() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> resolve(null, null, null, true));
        assertTrue(ex.getMessage().contains("verified total assessed"));
    }

    @Test
    void explicitlyDocumentedZeroIsNotInventedIntoHistoricalIncome() {
        assertEquals(0, resolve(BigDecimal.ZERO, null, null, true).compareTo(BigDecimal.ZERO));
    }

    @Test
    void reconstructsAssessedFromVerifiedPaidAndOutstandingComponents() {
        assertEquals(0, resolve(null, new BigDecimal("100000.00"),
                new BigDecimal("250000.00"), true).compareTo(new BigDecimal("350000.00")));
    }

    @Test
    void paidHistoricalPenaltiesWithoutOutstandingAreNotArrears() {
        assertEquals(0, resolve(new BigDecimal("825000.00"),
                new BigDecimal("825000.00"), BigDecimal.ZERO, true)
                .compareTo(new BigDecimal("825000.00")));
    }

    @Test
    void rejectsPaidGreaterThanAssessed() {
        assertThrows(IllegalArgumentException.class,
                () -> resolve(new BigDecimal("100000.00"), new BigDecimal("200000.00"), null, true));
    }

    @Test
    void rejectsConflictingAssessedPaidAndOutstanding() {
        assertThrows(IllegalArgumentException.class,
                () -> resolve(new BigDecimal("400000.00"), new BigDecimal("100000.00"),
                        new BigDecimal("250000.00"), true));
    }

    @Test
    void nonOverdueUnassessedLoanDefaultsToZero() {
        assertEquals(0, resolve(null, null, null, false).compareTo(BigDecimal.ZERO));
    }

    @Test
    void doesNotInventPenaltyWithinGracePeriod() {
        BigDecimal result = LegacyPenaltyOpeningPolicy.resolveAssessed(null, null, null,
                true, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12), PRINCIPAL);
        assertEquals(0, result.compareTo(BigDecimal.ZERO));
    }
}
