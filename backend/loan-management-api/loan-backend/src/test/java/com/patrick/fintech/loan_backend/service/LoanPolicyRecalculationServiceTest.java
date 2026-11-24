package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoanPolicyRecalculationServiceTest {
    private final LoanRepository loans = mock(LoanRepository.class);
    private final PaymentTransactionRepository receipts = mock(PaymentTransactionRepository.class);
    private final PaymentRepository schedule = mock(PaymentRepository.class);
    private final AccountingService accounting = mock(AccountingService.class);
    private final AuditService audit = mock(AuditService.class);
    private final LoanPolicyRecalculationService service =
            new LoanPolicyRecalculationService(loans, receipts,
                    new PaymentAllocationService(), schedule, accounting, audit);

    @Test
    void automaticallyReallocatesGakubaWithoutChangingCashReceipt() {
        Organization org = Organization.builder().id(1L).build();
        User actor = User.builder().id(1L).organization(org).build();
        Loan loan = Loan.builder()
                .id(54L).organization(org).referenceNumber("NLS-000054")
                .imported(true).status(LoanStatus.OVERDUE)
                .createdAt(LocalDateTime.of(2026, 11, 12, 10, 0))
                .durationMonths(6).maturityDate(LocalDate.of(2026, 9, 26))
                .amount(new BigDecimal("33500000.00"))
                .totalPaid(new BigDecimal("19600000.00"))
                .principalPaid(new BigDecimal("8750000.00"))
                .penaltyRate(new BigDecimal("10.00"))
                .outstandingBalance(new BigDecimal("24750000.00"))
                .totalInterest(new BigDecimal("10050000.00"))
                .interestPaid(new BigDecimal("5425000.00"))
                .interestOutstanding(new BigDecimal("4625000.00"))
                .managementFee(new BigDecimal("10050000.00"))
                .managementFeePaid(new BigDecimal("5425000.00"))
                .managementFeeOutstanding(new BigDecimal("4625000.00"))
                .penaltiesAssessed(new BigDecimal("8870000.00"))
                .penaltiesPaid(BigDecimal.ZERO).build();
        Payment installment = Payment.builder().id(29L).loan(loan)
                .amountPaid(new BigDecimal("12100000.00")).build();
        PaymentTransaction tx = PaymentTransaction.builder().id(5L).loan(loan)
                .installment(installment)
                .status(PaymentTransaction.TransactionStatus.POSTED)
                .reversed(false).amount(new BigDecimal("12100000.00"))
                .principalComponent(new BigDecimal("8750000.00"))
                .interestComponent(new BigDecimal("1675000.00"))
                .managementFeeComponent(new BigDecimal("1675000.00"))
                .penaltyComponent(BigDecimal.ZERO).extensionFeeComponent(BigDecimal.ZERO)
                .unappliedAmount(BigDecimal.ZERO)
                .createdAt(LocalDateTime.of(2026, 11, 19, 14, 24)).build();

        when(loans.findByIdForUpdate(54L)).thenReturn(Optional.of(loan));
        when(receipts.findByLoanIdOrderByCreatedAtDesc(54L)).thenReturn(List.of(tx));
        when(schedule.findByLoanId(54L)).thenReturn(List.of(installment));
        JournalEntry journal = new JournalEntry();
        journal.setId(999L);
        when(accounting.postPolicyRecalculation(eq(loan), any(), eq(new BigDecimal("33500000.00")),
                eq(LocalDate.now()))).thenReturn(journal);

        LoanPolicyRecalculationService.Applied result = service.apply(54L, 1L, actor);
        assertEquals("APPLIED_NONCASH_POLICY_RECONCILIATION", result.status());
        assertEquals(new BigDecimal("33500000.00"), loan.getOutstandingBalanceDecimal());
        assertEquals(new BigDecimal("0.00"), loan.getPrincipalPaidDecimal());
        assertEquals(new BigDecimal("0.00"), loan.getInterestOutstandingDecimal());
        assertEquals(new BigDecimal("0.00"), installment.getPrincipalComponentDecimal());
        assertEquals(new BigDecimal("12100000.00"), tx.getAmount());
        assertEquals(new BigDecimal("8750000.00"), tx.getPrincipalComponent());
        verify(accounting, times(1)).postPolicyRecalculation(eq(loan), any(),
                eq(new BigDecimal("33500000.00")), eq(LocalDate.now()));
        verify(loans, times(1)).saveAndFlush(loan);
        verify(receipts, never()).save(any());
    }

    @Test
    void rejectsMissingContractMaturityWithoutPostingAnyJournal() {
        Organization org = Organization.builder().id(1L).build();
        Loan loan = Loan.builder().id(54L).organization(org).imported(true)
                .createdAt(LocalDateTime.of(2026, 11, 12, 10, 0)).build();
        when(loans.findByIdForUpdate(54L)).thenReturn(Optional.of(loan));
        assertThrows(IllegalStateException.class,
                () -> service.apply(54L, 1L,
                        User.builder().id(1L).organization(org).build()));
        verifyNoInteractions(accounting);
    }
}
