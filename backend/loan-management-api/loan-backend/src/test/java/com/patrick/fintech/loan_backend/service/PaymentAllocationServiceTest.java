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
    void partialPaymentSettlesOverduePenaltyThenInterestBeforePrincipal() {
        PaymentAllocationService.Allocation result = service.allocate(
                new BigDecimal("125.00"),
                new BigDecimal("30.00"),
                new BigDecimal("40.00"),
                new BigDecimal("50.00"),
                new BigDecimal("50.00"),
                new BigDecimal("500.00"));

        assertEquals(new BigDecimal("30.00"), result.penalty());
        assertEquals(new BigDecimal("47.50"), result.interest());
        assertEquals(new BigDecimal("47.50"), result.managementFee());
        assertEquals(new BigDecimal("0.00"), result.principal());
        assertEquals(new BigDecimal("0.00"), result.extensionFee());
        assertEquals(new BigDecimal("0.00"), result.overpayment());
    }

    @Test
    void penaltiesAndAllDueFeesTakePriorityOverPrincipal() {
        PaymentAllocationService.Allocation result = service.allocate(
                new BigDecimal("12100000.00"),
                new BigDecimal("4500000.00"),
                new BigDecimal("200000.00"),
                new BigDecimal("3350000.00"),
                new BigDecimal("3350000.00"),
                new BigDecimal("33500000.00"));

        assertEquals(new BigDecimal("4500000.00"), result.penalty());
        assertEquals(new BigDecimal("3350000.00"), result.interest());
        assertEquals(new BigDecimal("3350000.00"), result.managementFee());
        assertEquals(new BigDecimal("200000.00"), result.extensionFee());
        assertEquals(new BigDecimal("700000.00"), result.principal());
        assertEquals(new BigDecimal("0.00"), result.overpayment());
    }

    @Test
    void feeBalancesSharePartialFundsEquallyAfterPenalty() {
        PaymentAllocationService.Allocation result = service.allocate(
                new BigDecimal("12100000.00"),
                new BigDecimal("8870000.00"),
                BigDecimal.ZERO,
                new BigDecimal("4625000.00"),
                new BigDecimal("4625000.00"),
                new BigDecimal("24750000.00"));
        assertEquals(new BigDecimal("8870000.00"), result.penalty());
        assertEquals(new BigDecimal("1615000.00"), result.interest());
        assertEquals(new BigDecimal("1615000.00"), result.managementFee());
        assertEquals(new BigDecimal("0.00"), result.principal());
    }

    @Test
    void excessShareMovesToOtherFeeBeforePrincipal() {
        PaymentAllocationService.Allocation result = service.allocate(
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("10.00"),
                new BigDecimal("70.00"),
                new BigDecimal("100.00"));
        assertEquals(new BigDecimal("10.00"), result.interest());
        assertEquals(new BigDecimal("70.00"), result.managementFee());
        assertEquals(new BigDecimal("20.00"), result.principal());
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
