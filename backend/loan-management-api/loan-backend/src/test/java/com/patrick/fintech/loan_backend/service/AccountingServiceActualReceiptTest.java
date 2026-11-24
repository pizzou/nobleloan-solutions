package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.model.PaymentTransaction;
import com.patrick.fintech.loan_backend.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountingServiceActualReceiptTest {
    @Mock PaymentTransactionRepository transactionRepository;
    @InjectMocks AccountingService accounting;

    @Test
    void uncollectedScheduleDoesNotCountAsPaidInterestOrManagement() {
        Payment scheduled = Payment.builder()
                .id(901L)
                .amountPaid(BigDecimal.ZERO)
                .interestComponent(new BigDecimal("1675000.00"))
                .managementFeeComponent(new BigDecimal("1675000.00"))
                .build();
        when(transactionRepository.findByInstallmentIdOrderByCreatedAtAsc(901L))
                .thenReturn(List.of());

        BigDecimal interest = ReflectionTestUtils.invokeMethod(
                accounting, "actualPaidChargeForInstallment", scheduled, true);
        BigDecimal management = ReflectionTestUtils.invokeMethod(
                accounting, "actualPaidChargeForInstallment", scheduled, false);
        assertEquals(new BigDecimal("0.00"), interest);
        assertEquals(new BigDecimal("0.00"), management);
    }

    @Test
    void postedReceiptsOnlyExcludeReversedAndUnpostedPayments() {
        Payment installment = Payment.builder().id(902L)
                .amountPaid(new BigDecimal("1200.00"))
                .interestComponent(new BigDecimal("800.00"))
                .managementFeeComponent(new BigDecimal("400.00"))
                .build();
        PaymentTransaction posted = PaymentTransaction.builder()
                .interestComponent(new BigDecimal("300.00"))
                .managementFeeComponent(new BigDecimal("300.00"))
                .status(PaymentTransaction.TransactionStatus.POSTED)
                .reversed(false).build();
        PaymentTransaction reversed = PaymentTransaction.builder()
                .interestComponent(new BigDecimal("500.00"))
                .managementFeeComponent(new BigDecimal("500.00"))
                .status(PaymentTransaction.TransactionStatus.REVERSED)
                .reversed(true).build();
        when(transactionRepository.findByInstallmentIdOrderByCreatedAtAsc(902L))
                .thenReturn(List.of(posted, reversed));

        assertEquals(new BigDecimal("300.00"), ReflectionTestUtils.invokeMethod(
                accounting, "actualPaidChargeForInstallment", installment, true));
        assertEquals(new BigDecimal("300.00"), ReflectionTestUtils.invokeMethod(
                accounting, "actualPaidChargeForInstallment", installment, false));
    }
}
