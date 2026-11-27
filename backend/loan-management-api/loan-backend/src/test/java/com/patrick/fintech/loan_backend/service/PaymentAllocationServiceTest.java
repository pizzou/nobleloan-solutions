package com.patrick.fintech.loan_backend.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentAllocationServiceTest {

    private final PaymentAllocationService service = new PaymentAllocationService();

    @Test
    void allocatesPaymentInContractualOrderWithoutCapitalizingFeesOrPenalty() {
        PaymentAllocationService.Allocation result = service.allocate(
                new BigDecimal("1200000.00"),
                new BigDecimal("50000.00"),
                new BigDecimal("100000.00"),
                new BigDecimal("100000.00"),
                new BigDecimal("100000.00"),
                new BigDecimal("500000.00"));

        assertEquals(new BigDecimal("100000.00"), result.interest());
        assertEquals(new BigDecimal("100000.00"), result.managementFee());
        assertEquals(new BigDecimal("500000.00"), result.principal());
        assertEquals(new BigDecimal("100000.00"), result.extensionFee());
        assertEquals(new BigDecimal("50000.00"), result.penalty());
        assertEquals(new BigDecimal("350000.00"), result.overpayment());

        BigDecimal totalAllocated = result.interest()
                .add(result.managementFee())
                .add(result.principal())
                .add(result.extensionFee())
                .add(result.penalty())
                .add(result.overpayment());
        assertEquals(new BigDecimal("1200000.00"), totalAllocated);
    }

    @Test
    void partialPaymentSettlesPenaltyThenSplitsTheRemainderEqually() {
        PaymentAllocationService.Allocation result = service.allocate(
                new BigDecimal("125.00"),
                new BigDecimal("30.00"),
                new BigDecimal("40.00"),
                new BigDecimal("50.00"),
                new BigDecimal("50.00"),
                new BigDecimal("500.00"));

        assertEquals(new BigDecimal("47.50"), result.interest());
        assertEquals(new BigDecimal("47.50"), result.managementFee());
        assertEquals(new BigDecimal("0.00"), result.principal());
        assertEquals(new BigDecimal("0.00"), result.extensionFee());
        assertEquals(new BigDecimal("30.00"), result.penalty());
        assertEquals(new BigDecimal("0.00"), result.overpayment());
    }

    @Test
    void oddCentGoesToInterestWhenBothChargesAreDue() {
        var result = service.allocate(new BigDecimal("100.01"), new BigDecimal("20.00"), null,
                new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("500.00"));
        assertEquals(new BigDecimal("40.01"), result.interest());
        assertEquals(new BigDecimal("40.00"), result.managementFee());
    }

    @Test
    void redirectsAnUnusedShareToTheOtherChargeBeforePrincipal() {
        var result = service.allocate(new BigDecimal("95.01"), null, null,
                new BigDecimal("100.00"), new BigDecimal("20.00"), new BigDecimal("500.00"));
        assertEquals(new BigDecimal("75.01"), result.interest());
        assertEquals(new BigDecimal("20.00"), result.managementFee());
        assertEquals(new BigDecimal("0.00"), result.principal());
    }

    @Test
    void historicalCorrectionRetainsTheApprovedManagementFirstPolicy() {
        var result = service.allocateHistoricalCorrection(new BigDecimal("12100000.00"),
                new BigDecimal("5583333.33"), null, new BigDecimal("6300000.00"),
                new BigDecimal("6300000.00"), new BigDecimal("33500000.00"));
        assertEquals(new BigDecimal("6300000.00"), result.managementFee());
        assertEquals(new BigDecimal("216666.67"), result.interest());
        assertEquals(new BigDecimal("0.00"), result.principal());
    }

    @Test
    void nullAndNegativeInputsCannotCreateNegativeAllocations() {
        PaymentAllocationService.Allocation result = service.allocate(
                new BigDecimal("-1.00"),
                new BigDecimal("-2.00"),
                null,
                new BigDecimal("10.00"),
                new BigDecimal("10.00"),
                new BigDecimal("10.00"));

        assertEquals(new BigDecimal("0.00"), result.interest());
        assertEquals(new BigDecimal("0.00"), result.managementFee());
        assertEquals(new BigDecimal("0.00"), result.principal());
        assertEquals(new BigDecimal("0.00"), result.extensionFee());
        assertEquals(new BigDecimal("0.00"), result.penalty());
        assertEquals(new BigDecimal("0.00"), result.overpayment());
    }
}
