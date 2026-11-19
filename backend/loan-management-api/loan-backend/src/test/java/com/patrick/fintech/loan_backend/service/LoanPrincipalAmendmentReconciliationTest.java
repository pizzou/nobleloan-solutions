package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanPrincipalAmendmentReconciliationTest {
    @Mock LoanRepository loans;
    @Mock LoanPrincipalAmendmentRepository requests;
    @Mock AuditService audit;
    @Mock AccountingService accounting;
    @Mock PaymentRepository payments;
    @Mock PaymentScheduleRepository schedules;
    @Mock HolidayService holidayService;

    LoanPrincipalAmendmentService service;
    Loan loan;
    User owner;

    private static BigDecimal n(String value) { return new BigDecimal(value); }

    @BeforeEach
    void setup() {
        service = new LoanPrincipalAmendmentService(loans, requests, audit, accounting,
                payments, schedules, holidayService);
        Organization org = Organization.builder().id(1L).build();
        Role role = new Role();
        role.setName("BUSINESS_OWNER");
        owner = User.builder().id(9L).organization(org).role(role).build();
        loan = Loan.builder().id(17L).organization(org).referenceNumber("NLS-000017")
                .amount(n("1000000.00")).disbursedAmount(n("1000000.00"))
                .disbursedAt(LocalDateTime.of(2026, 10, 1, 9, 0))
                .interestRate(n("5.00")).managementFeeRate(n("5.00"))
                .interestRateType("MONTHLY").durationMonths(1)
                .totalInterest(n("50000.00")).managementFee(n("50000.00"))
                .totalRepayable(n("1100000.00"))
                .principalPaid(n("1000000.00"))
                .interestPaid(n("0.00")).managementFeePaid(n("0.00"))
                .outstandingBalance(n("0.00"))
                .interestOutstanding(n("50000.00"))
                .managementFeeOutstanding(n("50000.00"))
                .penaltiesAssessed(n("0.00")).penaltiesPaid(n("0.00"))
                .extensionFeePaid(n("0.00")).extensionFeeOutstanding(n("0.00"))
                .totalPaid(n("1050000.00")).status(LoanStatus.ACTIVE).build();
        lenient().when(loans.findVisibleByIdForUpdate(17L, 1L, true))
                .thenReturn(Optional.of(loan));
        lenient().when(payments.findByLoanId(17L)).thenReturn(List.of());
        lenient().when(schedules.findByLoanIdOrderByInstallmentNumberAsc(17L))
                .thenReturn(List.of());
    }

    private LoanPrincipalAmendmentRequest approved(BigDecimal interest, BigDecimal management) {
        LoanPrincipalAmendmentRequest item = new LoanPrincipalAmendmentRequest();
        item.setId(51L);
        item.setOrganizationId(1L);
        item.setLoanId(17L);
        item.setRequesterId(4L);
        item.setReviewerId(9L);
        item.setStatus(LoanPrincipalAmendmentRequest.Status.APPROVED_FOR_RECONCILIATION);
        item.setCorrectionType(LoanPrincipalAmendmentRequest.CorrectionType.CONTRACT_TERMS);
        item.setLoanReference("NLS-000017");
        item.setOldPrincipal(n("1000000.00"));
        item.setCorrectedPrincipal(n("1000000.00"));
        item.setOriginalDisbursedAmount(n("1000000.00"));
        item.setOriginalTotalPaid(n("1050000.00"));
        item.setOldDisbursementDate(LocalDate.of(2026, 10, 1));
        item.setCorrectedDisbursementDate(LocalDate.of(2026, 10, 1));
        item.setOldInterestRate(n("5.00"));
        item.setCorrectedInterestRate(interest);
        item.setOldManagementFeeRate(n("5.00"));
        item.setCorrectedManagementFeeRate(management);
        return item;
    }

    @Test
    void ownerApprovedNegotiationSettlesLoanAndCreatesSingleBalancedAdjustmentRequest() {
        var item = approved(n("2.50"), n("2.50"));
        when(requests.lock(1L, 17L, 51L)).thenReturn(Optional.of(item));

        service.apply(17L, 51L, owner);

        assertEquals(n("0.00"), loan.getInterestOutstandingDecimal());
        assertEquals(n("0.00"), loan.getManagementFeeOutstandingDecimal());
        assertEquals(n("25000.00"), loan.getInterestPaidDecimal());
        assertEquals(n("25000.00"), loan.getManagementFeePaidDecimal());
        assertEquals(n("1050000.00"), loan.getTotalRepayableDecimal());
        assertEquals(LoanStatus.PAID, loan.getStatus());
        assertEquals(LoanPrincipalAmendmentRequest.Status.APPLIED, item.getStatus());
        verify(accounting).postApprovedLoanAmendment(eq(loan), eq(51L),
                eq(n("0.00")), eq(n("-50000.00")), eq(n("-50000.00")),
                eq(n("0.00")), eq(n("-25000.00")), eq(n("-25000.00")),
                eq(n("-50000.00")));

        assertThrows(IllegalStateException.class, () -> service.apply(17L, 51L, owner));
        verify(accounting, times(1)).postApprovedLoanAmendment(any(), anyLong(),
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void refusesReconciliationWhenRepaymentHistoryChangedAfterRequest() {
        var item = approved(n("2.50"), n("2.50"));
        item.setOriginalTotalPaid(n("1000000.00"));
        when(requests.lock(1L, 17L, 51L)).thenReturn(Optional.of(item));

        assertThrows(IllegalStateException.class, () -> service.apply(17L, 51L, owner));
        verifyNoInteractions(accounting);
    }
}
