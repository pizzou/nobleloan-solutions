package com.patrick.fintech.loan_backend.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HistoricalRepaymentCalculationTest {

    @Test
    void replaysOverdueImportedReceiptAndAccruesOnUnreducedPrincipal() {
        var calculator = new HistoricalRepaymentCalculation(new PaymentAllocationService());
        LocalDate maturity = LocalDate.of(2026, 9, 26);
        LocalDate receiptDate = LocalDate.of(2026, 11, 19);
        var opening = new HistoricalRepaymentCalculation.Opening(
                maturity, maturity, new BigDecimal("10.00"), new BigDecimal("33500000.00"), BigDecimal.ZERO,
                new BigDecimal("6300000.00"), new BigDecimal("6300000.00"), BigDecimal.ZERO);
        var receipt = new HistoricalRepaymentCalculation.Receipt(
                5L, receiptDate, new BigDecimal("12100000.00"));

        var result = calculator.replay(opening, List.of(), List.of(receipt), receiptDate.plusDays(5));
        var allocation = result.allocations().getFirst().components();

        assertEquals(new BigDecimal("5583333.33"), allocation.penalty());
        assertEquals(new BigDecimal("6300000.00"), allocation.managementFee());
        assertEquals(new BigDecimal("216666.67"), allocation.interest());
        assertEquals(new BigDecimal("0.00"), allocation.principal());
        assertEquals(new BigDecimal("33500000.00"), result.principal());
        assertEquals(new BigDecimal("6141666.67"), result.penaltyAccrued());
        assertEquals(new BigDecimal("558333.34"), result.penalty());

        var original = new HistoricalRepaymentCalculation.Allocation(5L, receiptDate, receipt.amount(),
                new PaymentAllocationService.Allocation(BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("1675000.00"), new BigDecimal("1675000.00"),
                        new BigDecimal("8750000.00"), BigDecimal.ZERO));
        var adjustment = calculator.receiptReclassification(original, result.allocations().getFirst());
        assertEquals(new BigDecimal("8750000.00"), adjustment.get("1100"));
        assertEquals(new BigDecimal("1458333.33"), adjustment.get("1150"));
        assertEquals(new BigDecimal("-4625000.00"), adjustment.get("1160"));
        assertEquals(new BigDecimal("-5583333.33"), adjustment.get("1175"));
        assertEquals(new BigDecimal("0.00"), adjustment.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(false, adjustment.containsKey("1000"));
    }

    @Test
    void usesTheLoanRateAndDoesNotChargeDuringGrace() {
        var calculator = new HistoricalRepaymentCalculation(new PaymentAllocationService());
        LocalDate maturity = LocalDate.of(2026, 11, 1);
        var opening = new HistoricalRepaymentCalculation.Opening(
                maturity, maturity, new BigDecimal("7.5"), new BigDecimal("1000000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        assertEquals(new BigDecimal("0.00"),
                calculator.replay(opening, List.of(), List.of(), maturity.plusDays(3)).penaltyAccrued());
        assertEquals(new BigDecimal("2500.00"),
                calculator.replay(opening, List.of(), List.of(), maturity.plusDays(4)).penaltyAccrued());
    }
}
