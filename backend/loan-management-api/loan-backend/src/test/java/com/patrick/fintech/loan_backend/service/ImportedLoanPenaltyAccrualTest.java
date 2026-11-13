package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import com.patrick.fintech.loan_backend.util.FinancialPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies current-period penalty catch-up does not misstate legacy opening
 * income.
 */
class ImportedLoanPenaltyAccrualTest {
    private final ChartOfAccountRepository coaRepo = mock(ChartOfAccountRepository.class);
    private final JournalEntryRepository journalRepo = mock(JournalEntryRepository.class);
    private final JournalLineRepository lineRepo = mock(JournalLineRepository.class);
    private final LoanRepository loanRepo = mock(LoanRepository.class);
    private final PaymentRepository paymentRepo = mock(PaymentRepository.class);
    private final AccountingPeriodService periodService = mock(AccountingPeriodService.class);
    private final AccountingService accounting = spy(new AccountingService(
            coaRepo, journalRepo, lineRepo, loanRepo, paymentRepo, periodService));

    private Loan loan(LocalDate importedOn) {
        Loan l = new Loan();
        l.setId(54L);
        l.setReferenceNumber("NLS-000054");
        l.setOrganization(Organization.builder().id(1L).build());
        l.setImported(true);
        l.setStatus(LoanStatus.OVERDUE);
        l.setCreatedAt(importedOn.atTime(10, 26));
        l.setNextDueDate(importedOn.minusDays(73));
        l.setOutstandingBalance(new BigDecimal("33500000.00"));
        l.setPenaltyRate(FinancialPolicy.LEGACY_MONTHLY_PENALTY_RATE);
        l.setPenaltiesAssessed(BigDecimal.ZERO);
        return l;
    }

    @Test
    void onlyDaysAfterImportAccrueAndPriorOpeningPenaltyIsNotDuplicated() {
        LocalDate today = LocalDate.now();
        Loan loan = loan(today.minusDays(1));
        when(paymentRepo.findByLoanId(54L)).thenReturn(List.of());
        when(loanRepo.countRecordedPaymentTransactions(54L)).thenReturn(0L);
        when(lineRepo.sumPostedPenaltyAccrualSinceImport(eq(1L), eq("54-%"), eq(today.minusDays(1))))
                .thenReturn(BigDecimal.ZERO);
        when(journalRepo.findFirstByOrganization_IdAndSourceTypeAndSourceId(
                eq(1L), eq("PENALTY_ACCRUAL"), eq("54-" + today)))
                .thenReturn(Optional.empty());
        JournalEntry entry = new JournalEntry();
        doReturn(entry).when(accounting).postPenaltyAccrual(same(loan), any(BigDecimal.class));

        assertSame(entry, accounting.postImportedLoanPostImportPenaltyAccrual(loan, today));

        BigDecimal expected = FinancialPolicy.historicalDailyPenalty(
                new BigDecimal("33500000.00"), today, today,
                FinancialPolicy.LEGACY_MONTHLY_PENALTY_RATE, date -> new BigDecimal("33500000.00"));
        verify(accounting).postPenaltyAccrual(same(loan), eq(expected));
    }

    @Test
    void refusesCatchUpAfterPaymentTransactions() {
        Loan loan = loan(LocalDate.now().minusDays(2));
        when(paymentRepo.findByLoanId(54L)).thenReturn(List.of());
        when(loanRepo.countRecordedPaymentTransactions(54L)).thenReturn(1L);
        assertNull(accounting.postImportedLoanPostImportPenaltyAccrual(loan, LocalDate.now()));
        verify(accounting, never()).postPenaltyAccrual(any(), any());
    }

    @Test
    void refusesToDoublePostToday() {
        LocalDate today = LocalDate.now();
        Loan loan = loan(today.minusDays(2));
        when(paymentRepo.findByLoanId(54L)).thenReturn(List.of());
        when(lineRepo.sumPostedPenaltyAccrualSinceImport(eq(1L), eq("54-%"), eq(today.minusDays(2))))
                .thenReturn(BigDecimal.ZERO);
        when(journalRepo.findFirstByOrganization_IdAndSourceTypeAndSourceId(
                eq(1L), eq("PENALTY_ACCRUAL"), eq("54-" + today)))
                .thenReturn(Optional.of(new JournalEntry()));
        assertNull(accounting.postImportedLoanPostImportPenaltyAccrual(loan, today));
        verify(accounting, never()).postPenaltyAccrual(any(), any());
    }

    @Test
    void refusesWrongImportedPenaltyRate() {
        Loan loan = loan(LocalDate.now().minusDays(2));
        loan.setPenaltyRate(new BigDecimal("15.00"));
        when(paymentRepo.findByLoanId(54L)).thenReturn(List.of());
        assertNull(accounting.postImportedLoanPostImportPenaltyAccrual(loan, LocalDate.now()));
        verify(accounting, never()).postPenaltyAccrual(any(), any());
    }

    @Test
    void catchesUpAllHistoricalOverdueDaysAgainstOpeningEquity() {
        LocalDate today = LocalDate.now();
        Loan loan = loan(today.minusDays(1));
        loan.setNextDueDate(today.minusDays(74));
        when(paymentRepo.findByLoanId(54L)).thenReturn(List.of());
        when(loanRepo.countRecordedPaymentTransactions(54L)).thenReturn(0L);
        when(lineRepo.sumPostedPenaltyAccrualSinceImport(eq(1L), eq("54-%"), eq(today.minusDays(1))))
                .thenReturn(BigDecimal.ZERO);
        when(journalRepo.findFirstByOrganization_IdAndSourceTypeAndSourceId(
                eq(1L), eq("LEGACY_PENALTY_OPENING"), eq("LOAN:54:HISTORICAL-PENALTY")))
                .thenReturn(Optional.empty());
        JournalEntry entry = new JournalEntry();
        doReturn(entry).when(accounting).postAtDate(any(), any(), eq("LEGACY_PENALTY_OPENING"),
                eq("LOAN:54:HISTORICAL-PENALTY"), anyString(), anyString(), eq(today), anyList());

        BigDecimal expected = FinancialPolicy.historicalDailyPenalty(
                new BigDecimal("33500000.00"), today.minusDays(70), today.minusDays(1),
                FinancialPolicy.LEGACY_MONTHLY_PENALTY_RATE, day -> new BigDecimal("33500000.00"));
        assertSame(entry, accounting.postImportedLoanHistoricalPenaltyOpeningCatchUp(loan, today));
        assertEquals(0, expected.compareTo(loan.getPenaltiesAssessedDecimal()));
        assertEquals(0, expected.compareTo(loan.getTotalRepayableDecimal()));
        verify(accounting).postAtDate(any(), any(), eq("LEGACY_PENALTY_OPENING"),
                anyString(), anyString(), anyString(), eq(today), argThat(lines -> lines.size() == 2
                        && lines.stream().anyMatch(line -> line.getDebit().compareTo(expected) == 0)
                        && lines.stream().anyMatch(line -> line.getCredit().compareTo(expected) == 0)));
    }

    @Test
    void historicalOpeningNeverDuplicatesExistingPostedSource() {
        LocalDate today = LocalDate.now();
        Loan loan = loan(today.minusDays(1));
        when(paymentRepo.findByLoanId(54L)).thenReturn(List.of());
        when(journalRepo.findFirstByOrganization_IdAndSourceTypeAndSourceId(
                eq(1L), eq("LEGACY_PENALTY_OPENING"), eq("LOAN:54:HISTORICAL-PENALTY")))
                .thenReturn(Optional.of(new JournalEntry()));
        assertNull(accounting.postImportedLoanHistoricalPenaltyOpeningCatchUp(loan, today));
        verify(accounting, never()).postAtDate(any(), any(), anyString(), anyString(), anyString(),
                anyString(), any(), anyList());
    }

    @Test
    void preservesExistingHistoricalPenaltiesWhenGreaterThanEstimate() {
        LocalDate today = LocalDate.now();
        Loan loan = loan(today.minusDays(1));
        loan.setPenaltiesAssessed(new BigDecimal("50000000.00"));
        loan.setTotalRepayable(new BigDecimal("51000000.00"));
        when(paymentRepo.findByLoanId(54L)).thenReturn(List.of());
        when(lineRepo.sumPostedPenaltyAccrualSinceImport(anyLong(), anyString(), any()))
                .thenReturn(BigDecimal.ZERO);

        assertNull(accounting.postImportedLoanHistoricalPenaltyOpeningCatchUp(loan, today));
        assertEquals(new BigDecimal("50000000.00"), loan.getPenaltiesAssessedDecimal());
        assertEquals(new BigDecimal("51000000.00"), loan.getTotalRepayableDecimal());
        verify(accounting, never()).postAtDate(any(), any(), anyString(), anyString(), anyString(),
                anyString(), any(), anyList());
    }
}
