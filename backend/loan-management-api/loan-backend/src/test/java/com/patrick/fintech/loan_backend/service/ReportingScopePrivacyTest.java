package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportingScopePrivacyTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownerOnlyPendingLoanIsHiddenFromNonOwner() {
        Loan loan = privateLoan(LoanStatus.PENDING);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "loan-officer",
                        "not-a-secret",
                        List.of(new SimpleGrantedAuthority("ROLE_LOAN_OFFICER"))));

        assertFalse(ReportingScopeService.isVisibleToCurrentUser(loan));
    }

    @Test
    void ownerOnlyUnderReviewLoanIsHiddenFromManager() {
        Loan loan = privateLoan(LoanStatus.UNDER_REVIEW);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "manager",
                        "not-a-secret",
                        List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));

        assertFalse(ReportingScopeService.isVisibleToCurrentUser(loan));
    }

    @Test
    void ownerCanSeeOwnerOnlyLoanRegardlessOfStatus() {
        Loan loan = privateLoan(LoanStatus.PENDING);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "business-owner",
                        "not-a-secret",
                        List.of(new SimpleGrantedAuthority("ROLE_BUSINESS_OWNER"))));

        assertTrue(ReportingScopeService.isVisibleToCurrentUser(loan));
    }

    @Test
    void repositoryReportingQueriesMustNotExposePrivatePendingLoans() throws Exception {
        Path root = Path.of("src/main/java/com/patrick/fintech/loan_backend/repository");
        String loanRepository = Files.readString(root.resolve("LoanRepository.java"));
        String paymentRepository = Files.readString(root.resolve("PaymentRepository.java"));

        assertFalse(loanRepository.contains("OR l.status IN ('PENDING', 'UNDER_REVIEW')"),
                "Loan repository must not bypass owner-only scope for pending loans");
        assertFalse(paymentRepository.contains("OR l.status IN ('PENDING', 'UNDER_REVIEW')"),
                "Payment repository must not bypass owner-only scope for pending loans");

        int publicLookup = loanRepository.indexOf("findByReferenceNumberAndBorrower_PhoneHash(");
        int queryStart = loanRepository.lastIndexOf("@Query", publicLookup);
        int methodEnd = loanRepository.indexOf(";", publicLookup);
        assertTrue(publicLookup > 0 && queryStart >= 0 && methodEnd > publicLookup,
                "Borrower dashboard/schedule lookup must have an explicit repository query");
        String publicLookupQuery = loanRepository.substring(queryStart, methodEnd);
        assertTrue(publicLookupQuery.contains("COALESCE(l.businessOwnerOnly, false) = false"),
                "Public borrower endpoints must not expose Business Owner-only loans even with a valid reference and phone");
    }

    private Loan privateLoan(LoanStatus status) {
        Loan loan = new Loan();
        loan.setBusinessOwnerOnly(true);
        loan.setStatus(status);
        return loan;
    }
}
