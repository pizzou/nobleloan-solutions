package com.patrick.fintech.loan_backend.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyInstallmentDueDateResolverTest {
    @Test void threeVerifiedSettledInstallmentsDeriveFourthDue() {
        var result = LegacyInstallmentDueDateResolver.resolve("2026-03-20", "2026-04-20", "4", "", "3",
                true, true, "AC", "AD");
        assertTrue(result.valid(), result.error());
        assertEquals("2026-07-20", result.nextDueDate());
    }

    @Test void canAcceptFutureVerifiedDateWhenPriorInstallmentsAreSettled() {
        var result = LegacyInstallmentDueDateResolver.resolve("2026-06-12", "2026-07-27", "6",
                "2026-11-20", "", true, true, "AC", "AD");
        assertTrue(result.valid(), result.error());
        assertEquals("2026-11-20", result.nextDueDate());
    }

    @Test void partialPaymentsAloneDoNotProveFullInstallmentSettlement() {
        var result = LegacyInstallmentDueDateResolver.resolve("2026-03-20", "2026-04-20", "4",
                "", "", true, true, "AC", "AD");
        assertFalse(result.valid());
        assertEquals("", result.nextDueDate());
        assertTrue(result.error().contains("AC"));
        assertTrue(result.error().contains("AD"));
    }

    @Test void refuseDateBeforeLoanDisbursement() {
        var result = LegacyInstallmentDueDateResolver.resolve("2026-07-04", "2026-08-04", "4",
                "2026-04-10", "", true, true, "AC", "AD");
        assertFalse(result.valid());
    }

    @Test void refuseContradictoryVerifiedDateAndCount() {
        var result = LegacyInstallmentDueDateResolver.resolve("2026-03-20", "2026-04-20", "4",
                "2026-11-20", "3", true, true, "AC", "AD");
        assertFalse(result.valid());
        assertTrue(result.error().contains("disagree"));
    }

    @Test void rejectAllInstallmentsSettledWhileOutstanding() {
        var result = LegacyInstallmentDueDateResolver.resolve("2026-03-20", "2026-04-20", "4",
                "", "4", true, true, "AC", "AD");
        assertFalse(result.valid());
    }

    @Test void noPastPaymentsUsesFirstDueDate() {
        var result = LegacyInstallmentDueDateResolver.resolve("2026-10-01", "2026-11-01", "4",
                "", "", false, true, "AC", "AD");
        assertTrue(result.valid(), result.error());
        assertEquals("2026-11-01", result.nextDueDate());
    }

    @Test void doNotUseAmountOfPaidPenaltiesToInferSettledInstallments() {
        // The caller sends partiallyPaid=false if only historical penalties were paid.
        var result = LegacyInstallmentDueDateResolver.resolve("2026-10-01", "2026-11-01", "4",
                "", "", false, true, "AC", "AD");
        assertEquals("2026-11-01", result.nextDueDate());
    }
}
