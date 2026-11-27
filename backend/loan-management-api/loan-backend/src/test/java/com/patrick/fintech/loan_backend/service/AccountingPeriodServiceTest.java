package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.AccountingPeriod;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.AccountingPeriodRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountingPeriodServiceTest {
    private final AccountingPeriodRepository periods = mock(AccountingPeriodRepository.class);
    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final AccountingPeriodService service = new AccountingPeriodService(periods, organizations);
    private final LocalDate historicalDate = LocalDate.of(2020, 3, 15);

    @Test
    void permitsHistoricalDisbursementAndRepaymentAfterFinanceOpensMonth() {
        when(periods.findByOrganization_IdAndYearAndMonthForUpdate(1L, 2020, 3)).thenReturn(Optional.empty());
        when(organizations.findById(1L)).thenReturn(Optional.of(Organization.builder().id(1L).build()));
        when(periods.save(any(AccountingPeriod.class))).thenAnswer(call -> call.getArgument(0));

        assertThatThrownBy(() -> service.assertPostingAllowed(1L, historicalDate, "LOAN_DISBURSEMENT"))
                .hasMessageContaining("2020-03 is not open for posting");

        AccountingPeriod opened = service.open(1L, 2020, 3, "Finance");
        assertThat(opened.getOpenedBy()).isEqualTo("Finance");
        when(periods.findByOrganization_IdAndYearAndMonthForUpdate(1L, 2020, 3)).thenReturn(Optional.of(opened));

        assertThatCode(() -> service.assertPostingAllowed(1L, historicalDate, "LOAN_DISBURSEMENT"))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.assertPostingAllowed(1L, historicalDate, "PAYMENT_RECEIVED"))
                .doesNotThrowAnyException();
        assertThat(service.open(1L, 2020, 3, "Second operator").getOpenedBy()).isEqualTo("Finance");
    }

    @Test
    void closedMonthRejectsOpeningAndBackdatedPosting() {
        when(organizations.findById(1L)).thenReturn(Optional.of(Organization.builder().id(1L).build()));
        var closed = AccountingPeriod.builder().status(AccountingPeriod.PeriodStatus.CLOSED).build();
        when(periods.findByOrganization_IdAndYearAndMonthForUpdate(1L, 2020, 3)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.open(1L, 2020, 3, "Finance")).hasMessageContaining("is closed");
        assertThatThrownBy(() -> service.assertPostingAllowed(1L, historicalDate, "LOAN_DISBURSEMENT"))
                .hasMessageContaining("is closed");
        verify(periods, never()).save(any(AccountingPeriod.class));
    }
}
