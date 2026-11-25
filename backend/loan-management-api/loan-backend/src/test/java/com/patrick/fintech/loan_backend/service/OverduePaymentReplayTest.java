package com.patrick.fintech.loan_backend.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OverduePaymentReplayTest {
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static final LocalDate START = LocalDate.of(2026, 8, 31);
    private final OverduePaymentReplay engine = new OverduePaymentReplay();
    private OverduePaymentReplay.Opening opening() {
        return new OverduePaymentReplay.Opening(START, n("3000000"), n("0"), n("0"), n("0"), n("0"));
    }
    @Test
    void allDueMonthsAndPenaltyArePaidBeforePrincipal() {
        var charges = List.of(new OverduePaymentReplay.Charge(START.plusDays(1), n("150000"), n("150000")),
                new OverduePaymentReplay.Charge(START.plusMonths(1), n("150000"), n("150000")));
        var receipt = new OverduePaymentReplay.Receipt(1, START.plusMonths(1), n("500000"));
        var result = engine.replay(opening(), START, n("10"), receipt.date(), charges, List.of(receipt));
        var a = result.receipts().get(0).allocation();
        assertEquals(n("270000.00"), a.penalty());
        assertEquals(n("230000.00"), a.interest());
        assertEquals(n("0.00"), a.principal());
        assertEquals(n("3000000.00"), result.principal());
        assertEquals(n("70000.00"), result.interest());
        assertEquals(n("300000.00"), result.management());
    }
    @Test
    void noPenaltyDuringGraceAndLaterReceiptsUseReducedPrincipal() {
        var first = new OverduePaymentReplay.Receipt(1, START.plusDays(3), n("1000000"));
        var result = engine.replay(opening(), START, n("15"), START.plusDays(4), List.of(), List.of(first));
        assertEquals(n("0.00"), result.receipts().get(0).allocation().penalty());
        assertEquals(n("2000000.00"), result.principal());
        assertEquals(n("10000.00"), result.penalty());
    }
    @Test
    void rejectsDuplicateReceipt() {
        var receipt = new OverduePaymentReplay.Receipt(1, START.plusDays(1), n("100"));
        assertThrows(IllegalArgumentException.class, () ->
                engine.replay(opening(), START, n("10"), START.plusDays(4), List.of(), List.of(receipt, receipt)));
    }
}
