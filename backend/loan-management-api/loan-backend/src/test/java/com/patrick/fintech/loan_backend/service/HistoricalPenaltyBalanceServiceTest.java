package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.model.PaymentTransaction;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import com.patrick.fintech.loan_backend.repository.PaymentTransactionRepository;
import com.patrick.fintech.loan_backend.util.FinancialPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoricalPenaltyBalanceServiceTest {
    @Mock PaymentTransactionRepository transactionRepository;
    @Mock PaymentRepository paymentRepository;
    @InjectMocks HistoricalPenaltyBalanceService service;

    @Test
    void reconstructsActualPrincipalFromPostedReceiptsNotScheduledComponents() {
        Loan loan = Loan.builder().id(54L).amount(new BigDecimal("33500000.00"))
                .penaltyRate(new BigDecimal("10.00")).build();
        LocalDate first = LocalDate.of(2026, 11, 18);
        LocalDate asOf = LocalDate.of(2026, 11, 21);

        PaymentTransaction posted = PaymentTransaction.builder()
                .amount(new BigDecimal("12100000.00"))
                .principalComponent(new BigDecimal("8750000.00"))
                .createdAt(LocalDateTime.of(2026, 11, 19, 14, 24))
                .status(PaymentTransaction.TransactionStatus.POSTED)
                .reversed(false).build();
        PaymentTransaction reversed = PaymentTransaction.builder()
                .principalComponent(new BigDecimal("1000000.00"))
                .createdAt(LocalDateTime.of(2026, 11, 20, 10, 0))
                .status(PaymentTransaction.TransactionStatus.REVERSED)
                .reversed(true).build();
        when(transactionRepository.findByLoanIdOrderByCreatedAtDesc(54L))
                .thenReturn(List.of(reversed, posted));

        BigDecimal calculated = service.assessedToDate(
                loan, new BigDecimal("24750000.00"), first, asOf);
        BigDecimal expected = FinancialPolicy.historicalDailyPenalty(
                new BigDecimal("24750000.00"), first, asOf,
                new BigDecimal("10.00"),
                day -> !day.isAfter(LocalDate.of(2026, 11, 19))
                        ? new BigDecimal("33500000.00")
                        : new BigDecimal("24750000.00"));
        assertEquals(expected, calculated);
    }

    @Test
    void chargeThroughPaymentDayUsesPrincipalBeforeSameDayReceipt() {
        Loan loan = Loan.builder().id(54L).amount(new BigDecimal("33500000.00"))
                .penaltyRate(new BigDecimal("10.00")).build();
        PaymentTransaction paid = PaymentTransaction.builder()
                .principalComponent(new BigDecimal("8750000.00"))
                .createdAt(LocalDateTime.of(2026, 11, 19, 14, 24))
                .status(PaymentTransaction.TransactionStatus.POSTED)
                .reversed(false).build();
        when(transactionRepository.findByLoanIdOrderByCreatedAtDesc(54L))
                .thenReturn(List.of(paid));
        BigDecimal assessed = service.assessedToDate(loan,
                new BigDecimal("24750000.00"),
                LocalDate.of(2026, 11, 19),
                LocalDate.of(2026, 11, 19));
        BigDecimal expected = FinancialPolicy.historicalDailyPenalty(
                new BigDecimal("24750000.00"),
                LocalDate.of(2026, 11, 19),
                LocalDate.of(2026, 11, 19),
                new BigDecimal("10.00"), day -> new BigDecimal("33500000.00"));
        assertEquals(expected, assessed);
    }

    @Test
    void ignoresUnpaidSchedulePrincipalWhenHistoricalTransactionsAreUnavailable() {
        Loan loan = Loan.builder().id(55L).amount(new BigDecimal("1000000.00"))
                .penaltyRate(new BigDecimal("15.00")).build();
        LocalDate first = LocalDate.of(2026, 11, 4);
        LocalDate asOf = LocalDate.of(2026, 11, 5);
        Payment futureSchedule = Payment.builder()
                .principalComponent(new BigDecimal("500000.00"))
                .amountPaid(BigDecimal.ZERO)
                .dueDate(LocalDate.of(2026, 12, 1))
                .build();
        when(transactionRepository.findByLoanIdOrderByCreatedAtDesc(55L))
                .thenReturn(List.of());
        when(paymentRepository.findByLoanId(55L)).thenReturn(List.of(futureSchedule));

        BigDecimal calculated = service.assessedToDate(
                loan, new BigDecimal("1000000.00"), first, asOf);
        BigDecimal expected = FinancialPolicy.historicalDailyPenalty(
                new BigDecimal("1000000.00"), first, asOf,
                new BigDecimal("15.00"),
                day -> new BigDecimal("1000000.00"));
        assertEquals(expected, calculated);
    }
}
