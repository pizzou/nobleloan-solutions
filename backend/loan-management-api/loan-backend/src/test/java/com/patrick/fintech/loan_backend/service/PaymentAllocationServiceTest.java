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
    void partialPaymentSettlesInterestThenManagementFeeThenPrincipal() {
        PaymentAllocationService.Allocation result = service.allocate(
                new BigDecimal("125.00"),
                new BigDecimal("30.00"),
                new BigDecimal("40.00"),
                new BigDecimal("50.00"),
                new BigDecimal("50.00"),
                new BigDecimal("500.00"));

        assertEquals(new BigDecimal("50.00"), result.interest());
        assertEquals(new BigDecimal("50.00"), result.managementFee());
        assertEquals(new BigDecimal("25.00"), result.principal());
        assertEquals(new BigDecimal("0.00"), result.extensionFee());
        assertEquals(new BigDecimal("0.00"), result.penalty());
        assertEquals(new BigDecimal("0.00"), result.overpayment());
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
    @Test
    void overduePaymentSettlesPenaltyAndAllDueChargesBeforePrincipal() {
        var result = service.allocateOverdue(new BigDecimal("12100000"),
                new BigDecimal("4000000"), BigDecimal.ZERO,
                new BigDecimal("5025000"), new BigDecimal("5025000"),
                new BigDecimal("33500000"));
        assertEquals(new BigDecimal("4000000.00"), result.penalty());
        assertEquals(new BigDecimal("5025000.00"), result.interest());
        assertEquals(new BigDecimal("3075000.00"), result.managementFee());
        assertEquals(new BigDecimal("0.00"), result.principal());
        assertEquals(new BigDecimal("0.00"), result.overpayment());
    }

    @Test
    void overduePaymentSmallerThanPenaltyDoesNotReducePrincipal() {
        var result = service.allocateOverdue(new BigDecimal("100"),
                new BigDecimal("200"), BigDecimal.ZERO, new BigDecimal("50"),
                new BigDecimal("50"), new BigDecimal("500"));
        assertEquals(new BigDecimal("100.00"), result.penalty());
        assertEquals(new BigDecimal("0.00"), result.interest());
        assertEquals(new BigDecimal("0.00"), result.managementFee());
        assertEquals(new BigDecimal("0.00"), result.principal());
    }

    @Test
    void overduePaymentPreservesCashAndExtensionPriority() {
        var result = service.allocateOverdue(new BigDecimal("1000"),
                new BigDecimal("100"), new BigDecimal("20"), new BigDecimal("200"),
                new BigDecimal("150"), new BigDecimal("400"));
        assertEquals(new BigDecimal("130.00"), result.overpayment());
        assertEquals(new BigDecimal("1000.00"), result.penalty().add(result.interest())
                .add(result.managementFee()).add(result.principal())
                .add(result.extensionFee()).add(result.overpayment()));
    }
}
