package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.ChartOfAccount;
import com.patrick.fintech.loan_backend.model.JournalEntry;
import com.patrick.fintech.loan_backend.model.JournalLine;
import com.patrick.fintech.loan_backend.repository.ChartOfAccountRepository;
import com.patrick.fintech.loan_backend.repository.JournalEntryRepository;
import com.patrick.fintech.loan_backend.repository.JournalLineRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountingCashFlowTest {
    @Test
    void reconcilesActualCashAndOnlyGrossesUpVerifiedWithheldFees() {
        var accounts = mock(ChartOfAccountRepository.class);
        var journalLines = mock(JournalLineRepository.class);
        var accounting = new AccountingService(accounts, mock(JournalEntryRepository.class), journalLines,
                mock(LoanRepository.class), mock(PaymentRepository.class), mock(AccountingPeriodService.class));
        var cash = account(1L, "1000");
        var principal = account(2L, "1100");
        var fee = account(3L, "4100");
        var from = LocalDate.of(2026, 11, 1);
        var entries = List.of(
                entry(from.minusDays(1), "OWNER_CONTRIBUTION", line(cash, "100", "0", "")),
                entry(from, "LOAN_DISBURSEMENT", line(cash, "0", "60", ""), line(cash, "0", "30", ""),
                        line(principal, "100", "0", ""), line(fee, "0", "10", "Application fee income")),
                entry(from, "LOAN_DISBURSEMENT", line(cash, "0", "30", ""), line(principal, "45", "0", ""),
                        line(fee, "0", "15", "Penalty income")),
                entry(from, "PAYMENT_RECEIVED", line(cash, "20", "0", "")),
                entry(from, "REFUND_PAYMENT", line(cash, "0", "5", "")),
                entry(from, "OWNER_CONTRIBUTION", line(cash, "3", "0", "")),
                entry(from.plusDays(1), "PAYMENT_RECEIVED", line(cash, "999", "0", "")));
        var cashLines = new ArrayList<JournalLine>();
        for (var entry : entries) {
            for (var line : entry.getLines()) {
                line.setJournalEntry(entry);
                if (line.getAccount() == cash) cashLines.add(line);
            }
        }
        when(accounts.findByOrganization_IdAndCode(1L, "1000")).thenReturn(Optional.of(cash));
        when(journalLines.findVisibleByAccount_IdAndOrganization_Id(1L, 1L, true)).thenReturn(cashLines);

        var result = accounting.getCashFlow(1L, from, from);
        assertThat(result).containsEntry("openingCash", new BigDecimal("100.00"))
                .containsEntry("cashFromRepayments", new BigDecimal("20.00"))
                .containsEntry("cashFromFees", new BigDecimal("10.00"))
                .containsEntry("netCashDisbursed", new BigDecimal("120.00"))
                .containsEntry("cashRefundedToBorrowers", new BigDecimal("5.00"))
                .containsEntry("otherCashFlowsNet", new BigDecimal("3.00"))
                .containsEntry("netChangeInCash", new BigDecimal("-102.00"))
                .containsEntry("actualClosingCash", new BigDecimal("-2.00"))
                .containsEntry("unverifiedDisbursementBreakdowns", 1)
                .containsEntry("reconciles", true);
    }

    private ChartOfAccount account(long id, String code) {
        return ChartOfAccount.builder().id(id).code(code).normalBalance(ChartOfAccount.NormalBalance.DEBIT).build();
    }

    private JournalEntry entry(LocalDate date, String source, JournalLine... lines) {
        return JournalEntry.builder().entryDate(date).sourceType(source).lines(List.of(lines)).build();
    }

    private JournalLine line(ChartOfAccount account, String debit, String credit, String description) {
        return JournalLine.builder().account(account).debit(new BigDecimal(debit))
                .credit(new BigDecimal(credit)).description(description).build();
    }
}
