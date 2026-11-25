package com.patrick.fintech.loan_backend.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HistoricalRepaymentReconciliationPlannerTest {
    private static BigDecimal m(String value) { return new BigDecimal(value); }

    @Test
    void fullContractReplayKeepsOriginalCashAndAllocatesAcrossAllEarnedFees() {
        // First payment occurs before contract maturity; the later receipt
        // must settle all due arrears and chargeable penalties before principal.
        var result = HistoricalRepaymentReconciliationPlanner.replayFullContract(
                LocalDate.of(2026, 3, 26),
                LocalDate.of(2026, 9, 26),
                LocalDate.of(2026, 11, 25),
                m("33500000.00"), m("10.00"),
                List.of(
                        new HistoricalRepaymentReconciliationPlanner.DatedCharge(
                                LocalDate.of(2026, 4, 26), m("1675000.00"), m("1675000.00"), m("0.00")),
                        new HistoricalRepaymentReconciliationPlanner.DatedCharge(
                                LocalDate.of(2026, 9, 26), m("1675000.00"), m("1675000.00"), m("0.00"))),
                List.of(
                        new HistoricalRepaymentReconciliationPlanner.Receipt(
                                1L, LocalDate.of(2026, 4, 26), m("3350000.00")),
                        new HistoricalRepaymentReconciliationPlanner.Receipt(
                                2L, LocalDate.of(2026, 11, 19), m("12100000.00"))));
        assertEquals(2, result.allocations().size());
        var early = result.allocations().get(0);
        assertEquals(m("0.00"), early.penalty());
        assertEquals(m("1675000.00"), early.interest());
        assertEquals(m("1675000.00"), early.management());
        assertEquals(m("0.00"), early.principal());
        var later = result.allocations().get(1);
        assertTrue(later.penalty().signum() > 0);
        assertEquals(m("1675000.00"), later.interest());
        assertEquals(m("1675000.00"), later.management());
        assertEquals(m("0.00"), later.extension());
        assertEquals(m("0.00"), later.principal());
        assertEquals(m("12100000.00"), later.penalty().add(later.interest())
                .add(later.management()).add(later.principal())
                .add(later.extension()).add(later.unapplied()));
        assertEquals(m("33500000.00"), result.principalOutstanding());
    }

    @Test
    void fullContractReplaySplitsPartialFeePaymentEquallyAndRespectsExtension() {
        var result = HistoricalRepaymentReconciliationPlanner.replayFullContract(
                LocalDate.of(2026, 3, 26), LocalDate.of(2026, 9, 26),
                LocalDate.of(2026, 9, 29), m("1000000.00"), m("10.00"),
                List.of(new HistoricalRepaymentReconciliationPlanner.DatedCharge(
                        LocalDate.of(2026, 9, 26), m("200000.00"), m("200000.00"), m("50000.00"))),
                List.of(new HistoricalRepaymentReconciliationPlanner.Receipt(
                        20L, LocalDate.of(2026, 9, 29), m("300000.00"))));
        var receipt = result.allocations().get(0);
        assertEquals(m("0.00"), receipt.penalty());
        assertEquals(m("150000.00"), receipt.interest());
        assertEquals(m("150000.00"), receipt.management());
        assertEquals(m("0.00"), receipt.extension());
        assertEquals(m("0.00"), receipt.principal());
        assertEquals(m("50000.00"), result.extensionOutstanding());
        assertEquals(m("0.00"), result.penaltyAssessed());
    }

    @Test
    void fullContractReplayRejectsReceiptsWithoutVerifiedDates() {
        assertThrows(IllegalArgumentException.class, () ->
                HistoricalRepaymentReconciliationPlanner.replayFullContract(
                        LocalDate.of(2026, 3, 26), LocalDate.of(2026, 9, 26),
                        LocalDate.of(2026, 11, 25), m("1000000.00"), m("10.00"),
                        List.of(),
                        List.of(new HistoricalRepaymentReconciliationPlanner.Receipt(
                                1L, LocalDate.of(2026, 3, 25), m("100000.00")))));
    }

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
