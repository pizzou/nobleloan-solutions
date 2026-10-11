package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.KycCheck;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.KycCheckRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ComplianceServiceOrganizationScopeTest {
    private KycCheckRepository kycRepository;
    private BorrowerRepository borrowerRepository;
    private ComplianceService service;

    @BeforeEach
    void setUp() {
        kycRepository = mock(KycCheckRepository.class);
        borrowerRepository = mock(BorrowerRepository.class);
        service = new ComplianceService(
                kycRepository,
                borrowerRepository,
                mock(AuditService.class),
                mock(ExternalComplianceProvider.class));
    }

    @Test
    void historyCannotReadBorrowerFromAnotherOrganization() {
        Organization foreign = Organization.builder().id(10L).build();
        Borrower borrower = Borrower.builder().id(100L).organization(foreign).build();
        when(borrowerRepository.findById(100L)).thenReturn(Optional.of(borrower));

        assertThrows(AccessDeniedException.class,
                () -> service.getHistoryForBorrower(100L, 20L));
        verifyNoInteractions(kycRepository);
    }

    @Test
    void statusCannotReadBorrowerFromAnotherOrganization() {
        Organization foreign = Organization.builder().id(10L).build();
        Borrower borrower = Borrower.builder().id(100L).organization(foreign).build();
        when(borrowerRepository.findById(100L)).thenReturn(Optional.of(borrower));

        assertThrows(AccessDeniedException.class,
                () -> service.isKycCurrentlyClear(100L, 20L));
        verifyNoInteractions(kycRepository);
    }

    @Test
    void manualReviewCannotChangeAnotherOrganizationsKycCheck() {
        Organization foreign = Organization.builder().id(10L).build();
        KycCheck check = KycCheck.builder()
                .id(300L)
                .organization(foreign)
                .result(KycCheck.CheckResult.MANUAL_REVIEW)
                .build();
        when(kycRepository.findById(300L)).thenReturn(Optional.of(check));

        assertThrows(AccessDeniedException.class,
                () -> service.manualReview(
                        300L, 20L, "reviewer", KycCheck.CheckResult.REJECTED, "test"));
        verify(borrowerRepository, never()).findById(anyLong());
    }
}
