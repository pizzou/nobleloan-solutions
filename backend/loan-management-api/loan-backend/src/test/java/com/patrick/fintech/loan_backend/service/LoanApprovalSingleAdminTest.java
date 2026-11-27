package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.LoanApprovalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanApprovalSingleAdminTest {
    @Mock LoanApprovalRepository approvalRepo;
    @Mock LoanService loanService;
    @Mock AuditService auditService;
    private LoanApprovalService service;
    private User admin;
    private Loan loan;
    private LoanApproval step;

    @BeforeEach
    void setup() {
        service = new LoanApprovalService(approvalRepo, loanService, auditService);
        ReflectionTestUtils.setField(service, "adminSelfApprovalEnabled", true);
        Organization org = Organization.builder().id(1L).build();
        Role role = new Role();
        role.setName("ADMIN");
        admin = User.builder().id(10L).role(role).organization(org).build();
        loan = Loan.builder().id(500L).organization(org).createdBy(admin)
                .referenceNumber("NLS-000500").status(LoanStatus.PENDING).build();
        step = LoanApproval.builder().id(101L).loan(loan).organization(org)
                .stepOrder(1).requiredRole("ADMIN").status("PENDING").build();
    }

    @Test
    void createsSingleAdminApprovalStepForNewAdminLoan() {
        when(approvalRepo.findByLoan_IdOrderByStepOrderAsc(500L))
                .thenReturn(List.of(), List.of(step));
        var created = service.initiateChain(loan);
        assertEquals(1, created.size());
        assertEquals("ADMIN", created.get(0).getRequiredRole());
        verify(approvalRepo).save(argThat(a -> "ADMIN".equals(a.getRequiredRole())));
    }

    @Test
    void creatorAdminMayExplicitlyApproveOwnNormalLoan() {
        when(loanService.getLoanForOrg(500L, 1L)).thenReturn(loan);
        when(approvalRepo.findByLoan_IdOrderByStepOrderAsc(500L)).thenReturn(List.of(step));
        var result = service.decide(500L, admin, "APPROVED", "Reviewed borrower documents");
        assertEquals("APPROVED", result.getStatus());
        assertSame(admin, result.getApprover());
        verify(loanService).approveLoan(eq(500L), eq(admin), anyString(), isNull(), isNull(), isNull());
    }

    @Test
    void disablingExceptionRestoresSeparateCreatorApproverRule() {
        ReflectionTestUtils.setField(service, "adminSelfApprovalEnabled", false);
        when(loanService.getLoanForOrg(500L, 1L)).thenReturn(loan);
        when(approvalRepo.findByLoan_IdOrderByStepOrderAsc(500L)).thenReturn(List.of(step));
        assertThrows(IllegalStateException.class,
                () -> service.decide(500L, admin, "APPROVED", "Test"));
        verify(loanService, never()).approveLoan(anyLong(), any(), anyString(), any(), any(), any());
    }

    @Test
    void ownerOnlyLoanCannotUseSingleAdminException() {
        loan.setBusinessOwnerOnly(true);
        when(loanService.getLoanForOrg(500L, 1L)).thenReturn(loan);
        when(approvalRepo.findByLoan_IdOrderByStepOrderAsc(500L)).thenReturn(List.of(step));
        assertThrows(IllegalStateException.class,
                () -> service.decide(500L, admin, "APPROVED", "Test"));
    }
}
