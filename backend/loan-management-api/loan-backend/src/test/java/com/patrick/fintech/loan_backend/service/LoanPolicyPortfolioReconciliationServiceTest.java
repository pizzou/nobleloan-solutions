package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoanPolicyPortfolioReconciliationServiceTest {

    @Test
    void oneInvalidBorrowerCannotPreventOtherEligibleLoanCorrection() {
        LoanRepository loans = mock(LoanRepository.class);
        LoanPolicyRecalculationService child = mock(LoanPolicyRecalculationService.class);
        LoanPolicyPortfolioReconciliationService portfolio =
                new LoanPolicyPortfolioReconciliationService(loans, child);

        Organization org = Organization.builder().id(1L).build();
        User owner = User.builder().id(42L).organization(org).build();
        Loan valid = Loan.builder().id(54L).organization(org).imported(true)
                .referenceNumber("NLS-000054").build();
        Loan invalid = Loan.builder().id(99L).organization(org).imported(true)
                .referenceNumber("NLS-000099").build();
        when(loans.findAll()).thenReturn(List.of(valid, invalid));

        var delta = new LoanPolicyRecalculationService.Delta(
                new BigDecimal("8750000.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
        var corrected = new LoanPolicyRecalculationEngine.Result(54L,
                "NLS-000054", LocalDate.of(2026, 9, 30),
                new BigDecimal("33500000.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, List.of());

        when(child.preview(eq(54L), eq(1L), any()))
                .thenReturn(new LoanPolicyRecalculationService.Preview(
                        "NLS-000054", LocalDate.now(), corrected,
                        delta, true, "READ_ONLY_PREVIEW_NO_JOURNAL_OR_BALANCE_MUTATION"));
        when(child.apply(54L, 1L, owner))
                .thenReturn(new LoanPolicyRecalculationService.Applied(
                        "NLS-000054", 54L, 999L, corrected, delta,
                        "APPLIED_NONCASH_POLICY_RECONCILIATION"));
        when(child.preview(eq(99L), eq(1L), any()))
                .thenThrow(new IllegalStateException("GL 1175 does not reconcile"));

        var result = portfolio.run(1L, owner, true);
        assertEquals(2, result.items().size());
        assertEquals(1, result.corrected());
        assertEquals(1, result.skipped());
        verify(child).apply(54L, 1L, owner);
        verify(child, never()).apply(eq(99L), anyLong(), any());
    }

    @Test
    void portfolioPreviewNeverPostsAccountingCorrection() {
        LoanRepository loans = mock(LoanRepository.class);
        LoanPolicyRecalculationService child = mock(LoanPolicyRecalculationService.class);
        LoanPolicyPortfolioReconciliationService portfolio =
                new LoanPolicyPortfolioReconciliationService(loans, child);
        Organization org = Organization.builder().id(1L).build();
        User owner = User.builder().id(42L).organization(org).build();
        when(loans.findAll()).thenReturn(List.of(Loan.builder().id(54L)
                .organization(org).referenceNumber("NLS-000054").imported(true).build()));
        when(child.preview(eq(54L), eq(1L), any()))
                .thenThrow(new IllegalStateException("Cannot infer incomplete history"));
        var result = portfolio.run(1L, owner, false);
        assertEquals(0, result.corrected());
        assertEquals(1, result.skipped());
        verify(child, never()).apply(anyLong(), anyLong(), any());
    }
}
