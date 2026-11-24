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
    void partialPaymentSplitsEqualFeesAfterPenaltyBeforePrincipal() {
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
    void multipleMonthsOfUnpaidChargesAndPenaltyTakePriorityOverPrincipal() {
        PaymentAllocationService.Allocation result = service.allocate(
                new BigDecimal("12100000.00"),
                new BigDecimal("1500000.00"),
                BigDecimal.ZERO,
                new BigDecimal("3000000.00"),
                new BigDecimal("3000000.00"),
                new BigDecimal("20000000.00"));

        assertEquals(new BigDecimal("1500000.00"), result.penalty());
        assertEquals(new BigDecimal("3000000.00"), result.interest());
        assertEquals(new BigDecimal("3000000.00"), result.managementFee());
        assertEquals(new BigDecimal("4600000.00"), result.principal());
        assertEquals(new BigDecimal("0.00"), result.overpayment());
    }

    @Test
    void insufficientPaymentNeverReducesPrincipalWhilePenaltyIsDue() {
        PaymentAllocationService.Allocation result = service.allocate(
                new BigDecimal("50000.00"),
                new BigDecimal("100000.00"),
                BigDecimal.ZERO,
                new BigDecimal("200000.00"),
                new BigDecimal("200000.00"),
                new BigDecimal("1000000.00"));
        assertEquals(new BigDecimal("50000.00"), result.penalty());
        assertEquals(new BigDecimal("0.00"), result.interest());
        assertEquals(new BigDecimal("0.00"), result.managementFee());
        assertEquals(new BigDecimal("0.00"), result.principal());
    }

    @Test
    void gakubaClaverPaymentCannotReducePrincipalUntilMaturedPenaltyAndPastDueFeesAreCleared() {
        // Confirmed portfolio audit: NLS-000054, immutable payment 12.1m,
        // contractual maturity 2026-09-26, first penalty date 2026-09-30.
        // These values represent the prepayment fee arrears and the
        // maturity-based penalty estimate through 2026-11-19.
        PaymentAllocationService.Allocation result = service.allocate(
                new BigDecimal("12100000.00"),
                new BigDecimal("5583333.33"),
                BigDecimal.ZERO,
                new BigDecimal("6300000.00"),
                new BigDecimal("6300000.00"),
                new BigDecimal("33500000.00"));

        assertEquals(new BigDecimal("5583333.33"), result.penalty());
        assertEquals(new BigDecimal("3258333.34"), result.interest());
        assertEquals(new BigDecimal("3258333.33"), result.managementFee());
        assertEquals(new BigDecimal("0.00"), result.principal());
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
}
