package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.BankAccount;
import com.patrick.fintech.loan_backend.model.BankStatementLine;
import com.patrick.fintech.loan_backend.model.ChartOfAccount;
import com.patrick.fintech.loan_backend.model.JournalEntry;
import com.patrick.fintech.loan_backend.model.JournalLine;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.BankAccountRepository;
import com.patrick.fintech.loan_backend.repository.BankStatementLineRepository;
import com.patrick.fintech.loan_backend.repository.JournalLineRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BankReconciliationServiceTest {
    @Test
    void reservesPreviouslyMatchedJournalsAndLeavesAmbiguousEntriesUnmatched() {
        var statements = mock(BankStatementLineRepository.class);
        var accounts = mock(BankAccountRepository.class);
        var ledger = mock(JournalLineRepository.class);
        var service = new BankReconciliationService(statements, accounts, ledger);
        var date = LocalDate.of(2026, 11, 28);
        var organization = Organization.builder().id(1L).build();
        var account = BankAccount.builder().id(2L)
                .glAccount(ChartOfAccount.builder().id(3L).build()).build();
        var previouslyMatched = statement(1L, date.minusDays(1), "100.00");
        previouslyMatched.setReconciliationStatus("MATCHED");
        previouslyMatched.setMatchedJournalEntryId(10L);
        var repeated = statement(2L, date, "100.00");
        var ambiguous = statement(3L, date, "50.00");

        when(accounts.findByIdAndOrganizationIdForUpdate(2L, 1L)).thenReturn(Optional.of(account));
        when(statements.findByOrganization_IdAndBankAccount_IdAndTransactionDateBetweenOrderByTransactionDateAscIdAsc(
                1L, 2L, date, date)).thenReturn(List.of(repeated, ambiguous));
        when(statements.findByOrganization_IdAndBankAccount_IdAndReconciliationStatusOrderByTransactionDateAscIdAsc(
                1L, 2L, "MATCHED")).thenReturn(List.of(previouslyMatched));
        when(ledger.findVisibleLedgerForAccountAndOrganization(3L, 1L, true)).thenReturn(List.of(
                line(10L, date, "100.00", false),
                line(11L, date, "50.00", false),
                line(12L, date, "50.00", false),
                line(13L, date, "100.00", true)));

        var result = service.reconcile(organization, 2L, date, date, "Accountant");

        assertThat(result.matched()).isZero();
        assertThat(result.unmatchedItems()).extracting(item -> item.get("reason"))
                .containsExactly("No matching journal", "Multiple matching journals");
        assertThat(result.ledgerNet()).isEqualByComparingTo("200.00");
        org.mockito.Mockito.verify(statements, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any(BankStatementLine.class));
    }

    private BankStatementLine statement(long id, LocalDate date, String amount) {
        return BankStatementLine.builder().id(id).transactionDate(date)
                .amount(new BigDecimal(amount)).build();
    }

    private JournalLine line(long journalId, LocalDate date, String amount, boolean reversed) {
        return JournalLine.builder().journalEntry(JournalEntry.builder().id(journalId)
                .entryDate(date).reversed(reversed).build())
                .debit(new BigDecimal(amount)).credit(BigDecimal.ZERO).build();
    }
}
