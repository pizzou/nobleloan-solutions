package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.FinancialApproval;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.FinancialApprovalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialApprovalServiceTest {

    @Mock
    private FinancialApprovalRepository repository;

    private FinancialApprovalService service;
    private Organization organization;

    @BeforeEach
    void setUp() {
        service = new FinancialApprovalService(repository);
        organization = Organization.builder().id(7L).name("Test Org").build();
    }

    @Test
    void consumeApprovedMarksApprovalConsumedAfterSuccessfulFinancialOperation() {
        FinancialApproval approval = FinancialApproval.builder()
                .id(11L)
                .organization(organization)
                .operationType("CASH_WITHDRAWAL")
                .operationId("15|WITHDRAWAL|1000.00|9|Cash withdrawal")
                .status("APPROVED")
                .requiredLevel(1)
                .makerUserId(100L)
                .payloadHash("a".repeat(64))
                .build();

        when(repository.findForUpdate(11L, 7L)).thenReturn(Optional.of(approval));
        when(repository.save(any(FinancialApproval.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FinancialApproval consumed = service.consumeApproved(
                11L,
                organization,
                "CASH_WITHDRAWAL",
                "15|WITHDRAWAL|1000.00|9|Cash withdrawal");

        assertThat(consumed.getStatus()).isEqualTo("CONSUMED");
        assertThat(consumed.getConsumedAt()).isNotNull();
        verify(repository).save(approval);
    }

    @Test
    void consumedApprovalCannotBeReused() {
        FinancialApproval approval = FinancialApproval.builder()
                .id(12L)
                .organization(organization)
                .operationType("CASH_WITHDRAWAL")
                .operationId("15|WITHDRAWAL|1000.00|9|Cash withdrawal")
                .status("CONSUMED")
                .requiredLevel(1)
                .makerUserId(100L)
                .payloadHash("b".repeat(64))
                .build();

        when(repository.findForUpdate(12L, 7L)).thenReturn(Optional.of(approval));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.requireApproved(
                12L,
                organization,
                "CASH_WITHDRAWAL",
                "15|WITHDRAWAL|1000.00|9|Cash withdrawal"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPROVED");
    }
}
