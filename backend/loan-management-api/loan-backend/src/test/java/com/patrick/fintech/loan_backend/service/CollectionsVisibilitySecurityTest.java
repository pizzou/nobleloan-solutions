package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.CollectionCase;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.CollectionActionRepository;
import com.patrick.fintech.loan_backend.repository.CollectionCaseRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * A privacy change must immediately affect existing collection cases, their
 * financial statistics, case history, and action endpoints. No re-sync required.
 */
class CollectionsVisibilitySecurityTest {
    private CollectionCaseRepository cases;
    private CollectionActionRepository actions;
    private CollectionsService service;

    @BeforeEach
    void setup() {
        cases = mock(CollectionCaseRepository.class);
        actions = mock(CollectionActionRepository.class);
        service = new CollectionsService(cases, actions, mock(LoanRepository.class),
                mock(UserRepository.class), mock(AuditService.class), mock(AccountingService.class));
        asRole("ROLE_COLLECTIONS_OFFICER");
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private static void asRole(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test-user", "n/a",
                        List.of(new SimpleGrantedAuthority(role))));
    }

    private static CollectionCase makeCase(long id, boolean ownerOnly) {
        Loan loan = new Loan();
        loan.setBusinessOwnerOnly(ownerOnly);
        Organization org = new Organization();
        org.setId(7L);
        CollectionCase cc = new CollectionCase();
        cc.setId(id);
        cc.setOrganization(org);
        cc.setLoan(loan);
        cc.setStatus(CollectionCase.CollectionStatus.OPEN);
        cc.setBucket(CollectionCase.CollectionBucket.DPD_1_30);
        cc.setOverdueAmount(new BigDecimal("100.00"));
        return cc;
    }

    @Test
    void collectionsQueueAndStatsExcludeOwnerOnlyCasesForNormalStaff() {
        when(cases.findByOrganization_Id(7L))
                .thenReturn(List.of(makeCase(1L, false), makeCase(2L, true)));
        assertEquals(1, service.getQueue(7L, null, null, null).size());
        assertEquals(1L, service.getStats(7L).get("totalOpenCases"));
        assertEquals(0, new BigDecimal("100.00")
                .compareTo((BigDecimal) service.getStats(7L).get("totalOverdueAmount")));
    }

    @Test
    void businessOwnerCanSeeNormalAndConfidentialCollections() {
        asRole("ROLE_BUSINESS_OWNER");
        when(cases.findByOrganization_Id(7L))
                .thenReturn(List.of(makeCase(1L, false), makeCase(2L, true)));
        assertEquals(2, service.getQueue(7L, null, null, null).size());
        assertEquals(2L, service.getStats(7L).get("totalOpenCases"));
    }

    @Test
    void directCaseHistoryAndWritesCannotBypassOwnerOnlyVisibility() {
        when(cases.findById(2L)).thenReturn(Optional.of(makeCase(2L, true)));
        assertThrows(IllegalArgumentException.class, () -> service.getCaseForOrg(2L, 7L));
        assertThrows(IllegalArgumentException.class, () -> service.getActionsForOrg(2L, 7L));
        assertThrows(IllegalArgumentException.class,
                () -> service.assignAgentForOrg(2L, 4L, "staff", 7L));
        assertThrows(IllegalArgumentException.class,
                () -> service.logActionForOrg(2L,
                        com.patrick.fintech.loan_backend.model.CollectionAction.ActionType.CALL,
                        "test", "staff", null, null, null, 7L));
        verifyNoInteractions(actions);
        asRole("ROLE_BUSINESS_OWNER");
        assertEquals(2L, service.getCaseForOrg(2L, 7L).getId());
    }
}
