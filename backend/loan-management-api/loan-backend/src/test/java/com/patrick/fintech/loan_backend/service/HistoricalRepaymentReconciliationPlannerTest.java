package com.patrick.fintech.loan_backend.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HistoricalRepaymentReconciliationPlannerTest {
    private static BigDecimal m(String value) { return new BigDecimal(value); }

    @Test
    void loan54ReceiptReclassificationUsesContractMaturityNotInstallmentDueDate() {
        var result = HistoricalRepaymentReconciliationPlanner.replay(
                LocalDate.of(2026, 9, 26), LocalDate.of(2026, 11, 24),
                m("33500000.00"), m("10.00"),
                List.of(new HistoricalRepaymentReconciliationPlanner.Charge(
                        LocalDate.of(2026, 9, 26), m("6300000.00"), m("6300000.00"))),
                List.of(new HistoricalRepaymentReconciliationPlanner.Receipt(
                        5L, LocalDate.of(2026, 11, 19), m("12100000.00"))));
        assertEquals(m("6141666.67"), result.assessedPenalty());
        assertEquals(m("5583333.33"), result.paidPenalty());
        assertEquals(m("558333.34"), result.outstandingPenalty());
        assertEquals(m("33500000.00"), result.outstandingPrincipal());

        var receipt = result.allocations().get(0);
        assertEquals(m("12100000.00"), receipt.cash());
        assertEquals(m("5583333.33"), receipt.penalty());
        assertEquals(m("3258333.33"), receipt.interest());
        assertEquals(m("3258333.34"), receipt.management());
        assertEquals(m("0.00"), receipt.principal());
        assertEquals(m("0.00"), receipt.unapplied());
        assertEquals(receipt.cash(), receipt.penalty().add(receipt.interest())
                .add(receipt.management()).add(receipt.principal())
                .add(receipt.extension()).add(receipt.unapplied()));
    }

    @Test
    void beforeMaturityNoPenaltyAndFeesSplitEvenly() {
        var result = HistoricalRepaymentReconciliationPlanner.replay(
                LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 22),
                m("1000000.00"), m("10.00"),
                List.of(new HistoricalRepaymentReconciliationPlanner.Charge(
                        LocalDate.of(2026, 11, 19), m("200000.00"), m("200000.00"))),
                List.of(new HistoricalRepaymentReconciliationPlanner.Receipt(
                        21L, LocalDate.of(2026, 11, 22), m("300000.00"))));
        assertEquals(m("0.00"), result.assessedPenalty());
        assertEquals(m("150000.00"), result.allocations().get(0).interest());
        assertEquals(m("150000.00"), result.allocations().get(0).management());
        assertEquals(m("0.00"), result.allocations().get(0).principal());
    }

    @Test
    void penaltyPaidAtFirstReceiptThenNewPenaltyAccruesOnRemainingPrincipal() {
        var result = HistoricalRepaymentReconciliationPlanner.replay(
                LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 10),
                m("1000000.00"), m("10.00"), List.of(),
                List.of(new HistoricalRepaymentReconciliationPlanner.Receipt(
                        100L, LocalDate.of(2026, 11, 6), m("100000.00"))));
        assertTrue(result.paidPenalty().signum() > 0);
        assertTrue(result.outstandingPrincipal().compareTo(m("1000000.00")) < 0);
        assertTrue(result.outstandingPenalty().signum() > 0);
        assertEquals(1, result.allocations().size());
    }
}
