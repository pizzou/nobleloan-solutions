package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.BankAccount;
import com.patrick.fintech.loan_backend.model.ChartOfAccount;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.BankAccountRepository;
import com.patrick.fintech.loan_backend.repository.ChartOfAccountRepository;
import com.patrick.fintech.loan_backend.repository.JournalLineRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class BankAccountServiceTest {
    @Test
    void linksMainBankToExistingLoanLedgerWithoutPostingAnOpeningEntry() {
        var bankAccounts = mock(BankAccountRepository.class);
        var chart = mock(ChartOfAccountRepository.class);
        var accounting = mock(AccountingService.class);
        var service = new BankAccountService(bankAccounts, chart, mock(JournalLineRepository.class),
                accounting, mock(FinancialApprovalService.class));
        var organization = Organization.builder().id(1L).build();
        var ledger = ChartOfAccount.builder().id(2L).code("1000").organization(organization).build();
        when(chart.lockMainBankLedger(1L)).thenReturn(Optional.of(ledger));
        when(bankAccounts.save(any(BankAccount.class))).thenAnswer(call -> {
            BankAccount account = call.getArgument(0);
            account.setId(3L);
            return account;
        });

        var account = service.create(organization, null, "Main bank", "BANK", "Example Bank", "example-account",
                BigDecimal.ZERO, "Accountant", true);

        assertThat(account.getGlAccount()).isSameAs(ledger);
        assertThat(account.getBankName()).isNull();
        assertThat(account.getAccountNumber()).isNull();
        verify(accounting).ensureChartOfAccounts(organization);
        verifyNoMoreInteractions(accounting);

        when(bankAccounts.existsByOrganization_IdAndGlAccount_Id(1L, 2L)).thenReturn(true);
        assertThatThrownBy(() -> service.create(organization, null, "Second bank", "BANK", "Bank", null,
                BigDecimal.ZERO, "Accountant", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already linked");
    }
}
