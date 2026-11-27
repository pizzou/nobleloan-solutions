package com.patrick.fintech.loan_backend.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
    void partialPaymentSettlesPenaltyThenManagementFeeThenInterest() {
        PaymentAllocationService.Allocation result = service.allocate(
                new BigDecimal("125.00"),
                new BigDecimal("30.00"),
                new BigDecimal("40.00"),
                new BigDecimal("50.00"),
                new BigDecimal("50.00"),
                new BigDecimal("500.00"));

        assertEquals(new BigDecimal("45.00"), result.interest());
        assertEquals(new BigDecimal("50.00"), result.managementFee());
        assertEquals(new BigDecimal("0.00"), result.principal());
        assertEquals(new BigDecimal("0.00"), result.extensionFee());
        assertEquals(new BigDecimal("30.00"), result.penalty());
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
    void settlesManagementFeesForAllDueMonthsBeforeInterestAndPrincipal() {
        LocalDate paymentDate = LocalDate.of(2026, 11, 19);
        BigDecimal monthCharge = new BigDecimal("3150000.00");
        var first = new PaymentAllocationService.InstallmentDue(
                1, paymentDate.minusMonths(2), monthCharge, monthCharge);
        var second = new PaymentAllocationService.InstallmentDue(
                2, paymentDate.minusMonths(1), monthCharge, monthCharge);
        var future = new PaymentAllocationService.InstallmentDue(
                3, paymentDate.plusDays(1), monthCharge, monthCharge);

        var result = service.allocateDueInstallments(new BigDecimal("12100000.00"),
                new BigDecimal("5583333.33"), BigDecimal.ZERO, new BigDecimal("33500000.00"),
                paymentDate, List.of(future, second, first));

        assertEquals(new BigDecimal("5583333.33"), result.total().penalty());
        assertEquals(new BigDecimal("6300000.00"), result.total().managementFee());
        assertEquals(new BigDecimal("216666.67"), result.total().interest());
        assertEquals(new BigDecimal("0.00"), result.total().principal());
        assertEquals(List.of(
                new PaymentAllocationService.InstallmentAllocation(1, monthCharge, new BigDecimal("216666.67")),
                new PaymentAllocationService.InstallmentAllocation(2, monthCharge, new BigDecimal("0.00"))),
                result.installments());
    }
}
