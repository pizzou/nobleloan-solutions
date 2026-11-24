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

    @Test
    void additionalDisbursementCannotBeMisclassifiedAsHistoricalNoncashCorrection() {
        var item = approved(n("5.00"), n("5.00"));
        item.setCorrectionType(LoanPrincipalAmendmentRequest.CorrectionType.ADDITIONAL_DISBURSEMENT);
        item.setCorrectedPrincipal(n("1500000.00"));
        when(requests.lock(1L, 17L, 51L)).thenReturn(Optional.of(item));

        var error = assertThrows(IllegalStateException.class,
                () -> service.apply(17L, 51L, owner));
        assertTrue(error.getMessage().contains("cash-controlled disbursement"));
        verifyNoInteractions(accounting);
        verify(loans, never()).saveAndFlush(any());
    }

    @Test
    void refusesAutomaticRepricingOfPartiallyPaidSchedule() {
        var item = approved(n("2.50"), n("2.50"));
        PaymentSchedule partial = new PaymentSchedule();
        partial.setAmountPaid(n("10000.00"));
        partial.setStatus(PaymentSchedule.ScheduleStatus.PARTIAL);
        when(schedules.findByLoanIdOrderByInstallmentNumberAsc(17L))
                .thenReturn(List.of(partial));
        when(requests.lock(1L, 17L, 51L)).thenReturn(Optional.of(item));

        assertThrows(IllegalStateException.class, () -> service.apply(17L, 51L, owner));
        verify(loans, never()).saveAndFlush(any());
        verify(requests, never()).saveAndFlush(any());
    }
    @Test
    void staffRequestAppearsInOwnerQueueAndOwnerDecisionPersistsWithoutMutatingLoan() {
        Role staffRole = new Role();
        staffRole.setName("MANAGER");
        User maker = User.builder().id(4L).organization(owner.getOrganization())
                .role(staffRole).build();
        LoanPrincipalAmendmentService.Proposal proposal =
                new LoanPrincipalAmendmentService.Proposal(
                        "NLS-000017", n("1000000.00"),
                        LoanPrincipalAmendmentRequest.CorrectionType.CONTRACT_TERMS,
                        LocalDate.of(2026, 9, 30), n("4.00"), n("4.00"),
                        "Correct signed loan terms and date as documented",
                        "CONTRACT-2026-17");
        var submitted = service.submit(17L, proposal, maker);
        assertEquals(LoanPrincipalAmendmentRequest.Status.PENDING_REVIEW,
                submitted.getStatus());
        assertEquals(4L, submitted.getRequesterId());
        verify(requests).saveAndFlush(submitted);
        verifyNoInteractions(accounting);

        submitted.setId(52L);
        when(requests.findByOrganizationIdAndStatusOrderByCreatedAtAsc(
                1L, LoanPrincipalAmendmentRequest.Status.PENDING_REVIEW))
                .thenReturn(List.of(submitted));
        assertEquals(List.of(submitted), service.pending(owner));

        when(requests.lock(1L, 17L, 52L)).thenReturn(Optional.of(submitted));
        var approved = service.decide(17L, 52L, true,
                new LoanPrincipalAmendmentService.Decision(
                        "NLS-000017", "Reviewed the signed loan contract and verified the requested changes"),
                owner);
        assertEquals(LoanPrincipalAmendmentRequest.Status.APPROVED_FOR_RECONCILIATION,
                approved.getStatus());
        assertEquals(9L, approved.getReviewerId());
        assertEquals(n("1000000.00"), loan.getAmountDecimal());
        assertEquals(n("5.00"), loan.getInterestRateDecimal());
        assertEquals(n("5.00"), loan.getManagementFeeRateDecimal());
        assertEquals(LocalDate.of(2026, 10, 1), loan.getDisbursedAt().toLocalDate());
        verifyNoInteractions(accounting);
        verify(loans, never()).saveAndFlush(any());
    }

    @Test
    void independentApprovalDoesNotCreateAdditionalCashDisbursement() {
        var item = approved(n("5.00"), n("5.00"));
        item.setStatus(LoanPrincipalAmendmentRequest.Status.PENDING_REVIEW);
        item.setCorrectionType(LoanPrincipalAmendmentRequest.CorrectionType.ADDITIONAL_DISBURSEMENT);
        item.setCorrectedPrincipal(n("1500000.00"));
        when(requests.lock(1L, 17L, 51L)).thenReturn(Optional.of(item));

        var reviewed = service.decide(17L, 51L, true,
                new LoanPrincipalAmendmentService.Decision(
                        "NLS-000017", "Approved for separate cash-controlled reconciliation"),
                owner);
        assertEquals(LoanPrincipalAmendmentRequest.Status.APPROVED_FOR_RECONCILIATION,
                reviewed.getStatus());
        verifyNoInteractions(accounting);
        verify(loans, never()).saveAndFlush(any());
        assertThrows(IllegalStateException.class, () -> service.apply(17L, 51L, owner));
    }

}
