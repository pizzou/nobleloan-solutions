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
import java.util.List;
import java.util.Map;

import static com.patrick.fintech.loan_backend.model.ChartOfAccount.AccountType.*;
import static com.patrick.fintech.loan_backend.model.ChartOfAccount.NormalBalance.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BnrFinancialStatementParityTest {
    @Test
    void matchesLedgerReportsAfterReversalsAndNoncashCorrections() {
        var accounts = mock(ChartOfAccountRepository.class);
        var journals = mock(JournalEntryRepository.class);
        var loans = mock(LoanRepository.class);
        var accounting = new AccountingService(accounts, journals, mock(JournalLineRepository.class),
                loans, mock(PaymentRepository.class), mock(AccountingPeriodService.class));
        var bnr = new BnrFinancialStatementService(accounts, journals, loans);
        var start = LocalDate.of(2026, 3, 1);
        var end = start.plusDays(30);
        var cash = account(1L, "1000", ASSET, DEBIT);
        var interestReceivable = account(2L, "1150", ASSET, DEBIT);
        var managementReceivable = account(3L, "1160", ASSET, DEBIT);
        var income = account(4L, "4100", INCOME, CREDIT);
        when(accounts.findByOrganization_IdOrderByCodeAsc(1L))
                .thenReturn(List.of(cash, interestReceivable, managementReceivable, income));

        var priorIncome = entry(start.minusDays(2), "OTHER_INCOME", false, cash, income, "100.00");
        var original = entry(start.minusDays(1), "LOAN_DISBURSEMENT", true, cash, income, "40.00");
        var reversal = entry(start, "REVERSAL", false, income, cash, "40.00");
        var accrual = entry(start.plusDays(1), "PENALTY_ACCRUAL", false, interestReceivable, income, "5.50");
        var correction = entry(start.plusDays(2), "HISTORICAL_RECEIPT_REALLOCATION", false,
                managementReceivable, interestReceivable, "3.25");
        when(journals.findVisibleByOrganizationIdAndEntryDateBetween(1L,
                AccountingService.LEDGER_HISTORY_START, end, false))
                .thenReturn(List.of(priorIncome, original, reversal, accrual, correction));
        when(journals.findVisibleByOrganizationIdAndEntryDateBetween(1L, start, end, false))
                .thenReturn(List.of(reversal, accrual, correction));

        var statement = bnr.buildFinancialStatement(1L, start, end, false);
        var position = (Map<?, ?>) statement.get("statementOfFinancialPosition");
        var pnl = (Map<?, ?>) statement.get("incomeStatement");
        var trial = (Map<?, ?>) statement.get("trialBalance");
        try (var scope = ReportingScopeService.useScope(ReportingScopeService.Scope.NORMAL)) {
            assertThat(position.get("totalEquity")).isEqualTo(accounting.getBalanceSheet(1L, end).get("totalEquity"));
            assertThat(position.get("totalAssets")).isEqualTo(accounting.getBalanceSheet(1L, end).get("totalAssets"));
            assertThat(position.get("unclosedProfit")).isEqualTo(new BigDecimal("105.50"));
            assertThat(position.get("balanced")).isEqualTo(true);
            assertThat(trial.get("debit")).isEqualTo(accounting.getTrialBalance(1L, end).get("totalDebit"));
            assertThat(trial.get("credit")).isEqualTo(accounting.getTrialBalance(1L, end).get("totalCredit"));
            assertThat(pnl.get("netIncome")).isEqualTo(accounting.getProfitAndLoss(1L, start, end).get("netIncome"));
            assertThat(pnl.get("netIncome")).isEqualTo(new BigDecimal("-34.50"));
        }
    }

    private ChartOfAccount account(long id, String code, ChartOfAccount.AccountType type,
                                   ChartOfAccount.NormalBalance normalBalance) {
        return ChartOfAccount.builder().id(id).code(code).type(type).normalBalance(normalBalance).build();
    }

    private JournalEntry entry(LocalDate date, String type, boolean reversed,
                               ChartOfAccount debit, ChartOfAccount credit, String amount) {
        return JournalEntry.builder().entryDate(date).sourceType(type).reversed(reversed).lines(List.of(
                JournalLine.builder().account(debit).debit(new BigDecimal(amount)).build(),
                JournalLine.builder().account(credit).credit(new BigDecimal(amount)).build())).build();
    }
}
