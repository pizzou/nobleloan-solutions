package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.ChartOfAccount;
import com.patrick.fintech.loan_backend.model.JournalEntry;
import com.patrick.fintech.loan_backend.model.JournalLine;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.ChartOfAccountRepository;
import com.patrick.fintech.loan_backend.repository.JournalEntryRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialReconciliationServiceTest {

    @Mock
    JournalEntryRepository journalEntryRepository;

    @Mock
    ChartOfAccountRepository chartOfAccountRepository;

    @Mock
    LoanRepository loanRepository;

    private FinancialReconciliationService service;
    private Organization organization;

    @BeforeEach
    void setUp() {
        service = new FinancialReconciliationService(
                journalEntryRepository,
                chartOfAccountRepository,
                loanRepository);

        organization = new Organization();
        organization.setId(1L);
    }

    @Test
    void balancedJournalAndSubledgerPass() {
        ChartOfAccount loansReceivable = account("1100", "Loans Receivable");
        ChartOfAccount cash = account("1000", "Cash and Bank");

        JournalEntry disbursement = entry(1L, "LOAN_DISBURSEMENT", "10", false);
        disbursement.addLine(line(loansReceivable, "1000.00", "0.00"));
        disbursement.addLine(line(cash, "0.00", "1000.00"));

        Loan loan = new Loan();
        loan.setStatus(com.patrick.fintech.loan_backend.model.LoanStatus.ACTIVE);
        loan.setAmount(new BigDecimal("1000.00"));
        loan.setPrincipalPaid(new BigDecimal("0.00"));
        loan.setOutstandingBalance(new BigDecimal("1000.00"));
        loan.setTotalInterest(new BigDecimal("0.00"));
        loan.setInterestPaid(new BigDecimal("0.00"));
        loan.setInterestOutstanding(new BigDecimal("0.00"));
        loan.setManagementFee(new BigDecimal("0.00"));
        loan.setManagementFeePaid(new BigDecimal("0.00"));
        loan.setManagementFeeOutstanding(new BigDecimal("0.00"));
        loan.setApplicationFee(new BigDecimal("0.00"));
        loan.setApplicationFeePaid(new BigDecimal("0.00"));

        stub(List.of(loansReceivable, cash), List.of(disbursement), List.of(loan));

        var report = service.reconcile(1L, LocalDate.of(2026, 8, 24));

        assertTrue(report.balanced());
        assertEquals(new BigDecimal("1000.00"), report.subledger().get("1100").operationalBalance());
        assertEquals(new BigDecimal("1000.00"), report.subledger().get("1100").glBalance());
    }

    @Test
    void unbalancedJournalFails() {
        ChartOfAccount loansReceivable = account("1100", "Loans Receivable");
        ChartOfAccount cash = account("1000", "Cash and Bank");

        JournalEntry entry = entry(2L, "MANUAL", "2", false);
        entry.addLine(line(loansReceivable, "1000.00", "0.00"));
        entry.addLine(line(cash, "0.00", "900.00"));

        stub(List.of(loansReceivable, cash), List.of(entry), List.of());

        var report = service.reconcile(1L, LocalDate.of(2026, 8, 24));

        assertFalse(report.balanced());
        assertTrue(report.unbalancedEntryCount() > 0);
    }

    @Test
    void duplicateActiveSourceFails() {
        ChartOfAccount loansReceivable = account("1100", "Loans Receivable");
        ChartOfAccount cash = account("1000", "Cash and Bank");

        JournalEntry first = entry(3L, "PAYMENT_RECEIVED", "55", false);
        first.addLine(line(cash, "100.00", "0.00"));
        first.addLine(line(loansReceivable, "0.00", "100.00"));

        JournalEntry duplicate = entry(4L, "PAYMENT_RECEIVED", "55", false);
        duplicate.addLine(line(cash, "100.00", "0.00"));
        duplicate.addLine(line(loansReceivable, "0.00", "100.00"));

        stub(List.of(loansReceivable, cash), List.of(first, duplicate), List.of());

        var report = service.reconcile(1L, LocalDate.of(2026, 8, 24));

        assertFalse(report.balanced());
        assertEquals(1, report.duplicateActiveSourceCount());
    }

    @Test
    void interestReceivableMustAgreeWithLoanSubledger() {
        ChartOfAccount interestReceivable = account("1150", "Interest Receivable");
        ChartOfAccount cash = account("1000", "Cash and Bank");

        JournalEntry entry = entry(5L, "INTEREST_ACCRUAL", "77", false);
        entry.addLine(line(interestReceivable, "250.00", "0.00"));
        entry.addLine(line(cash, "0.00", "250.00"));

        Loan loan = new Loan();
        loan.setInterestOutstanding(new BigDecimal("200.00"));

        stub(List.of(interestReceivable, cash), List.of(entry), List.of(loan));

        var report = service.reconcile(1L, LocalDate.of(2026, 8, 24));

        assertFalse(report.balanced());
        assertFalse(report.subledger().get("1150").reconciles());
        assertEquals(new BigDecimal("50.00"), report.subledger().get("1150").difference());
    }

    @Test
    void principalInvariantFailureIsReported() {
        ChartOfAccount loansReceivable = account("1100", "Loans Receivable");
        ChartOfAccount cash = account("1000", "Cash and Bank");

        JournalEntry entry = entry(8L, "LOAN_DISBURSEMENT", "99", false);
        entry.addLine(line(loansReceivable, "1000.00", "0.00"));
        entry.addLine(line(cash, "0.00", "1000.00"));

        Loan loan = new Loan();
        loan.setStatus(com.patrick.fintech.loan_backend.model.LoanStatus.ACTIVE);
        loan.setAmount(new BigDecimal("1000.00"));
        loan.setPrincipalPaid(new BigDecimal("800.00"));
        loan.setOutstandingBalance(new BigDecimal("300.00"));

        stub(List.of(loansReceivable, cash), List.of(entry), List.of(loan));

        var report = service.reconcile(1L, LocalDate.of(2026, 8, 24));

        assertFalse(report.balanced());
        assertTrue(report.issues().stream()
                .anyMatch(issue -> "PRINCIPAL_RECONCILIATION_FAILED".equals(issue.code())));
    }

    @Test
    void interestAndManagementFeeInvariantsAreCheckedIndependently() {
        ChartOfAccount loansReceivable = account("1100", "Loans Receivable");
        ChartOfAccount interestReceivable = account("1150", "Interest Receivable");
        ChartOfAccount managementReceivable = account("1160", "Management Fee Receivable");
        ChartOfAccount cash = account("1000", "Cash and Bank");

        JournalEntry entry = entry(9L, "MANUAL", "100", false);
        entry.addLine(line(cash, "100.00", "0.00"));
        entry.addLine(line(loansReceivable, "0.00", "100.00"));

        Loan loan = new Loan();
        loan.setStatus(com.patrick.fintech.loan_backend.model.LoanStatus.ACTIVE);
        loan.setAmount(new BigDecimal("1000.00"));
        loan.setPrincipalPaid(new BigDecimal("0.00"));
        loan.setOutstandingBalance(new BigDecimal("1000.00"));
        loan.setTotalInterest(new BigDecimal("500.00"));
        loan.setInterestPaid(new BigDecimal("100.00"));
        loan.setInterestOutstanding(new BigDecimal("300.00"));
        loan.setManagementFee(new BigDecimal("400.00"));
        loan.setManagementFeePaid(new BigDecimal("100.00"));
        loan.setManagementFeeOutstanding(new BigDecimal("200.00"));
        loan.setApplicationFee(new BigDecimal("20.00"));
        loan.setApplicationFeePaid(new BigDecimal("20.00"));

        stub(List.of(loansReceivable, interestReceivable, managementReceivable, cash),
                List.of(entry), List.of(loan));

        var report = service.reconcile(1L, LocalDate.of(2026, 8, 24));

        assertFalse(report.balanced());
        assertTrue(report.issues().stream()
                .anyMatch(issue -> "INTEREST_RECONCILIATION_FAILED".equals(issue.code())));
        assertTrue(report.issues().stream()
                .anyMatch(issue -> "MANAGEMENT_FEE_RECONCILIATION_FAILED".equals(issue.code())));
    }

    @Test
    void importBatchMarkerMakesHistoricalLoanFinanciallyOriginated() {
        ChartOfAccount loansReceivable = account("1100", "Loans Receivable");
        ChartOfAccount cash = account("1000", "Cash and Bank");

        JournalEntry entry = entry(10L, "LEGACY_LOAN_OPENING", "IMPORT-1", false);
        entry.addLine(line(loansReceivable, "1000.00", "0.00"));
        entry.addLine(line(cash, "0.00", "1000.00"));

        Loan loan = new Loan();
        loan.setStatus(com.patrick.fintech.loan_backend.model.LoanStatus.PENDING);
        loan.setImportBatchId(77L);
        loan.setAmount(new BigDecimal("1000.00"));
        loan.setPrincipalPaid(new BigDecimal("0.00"));
        loan.setOutstandingBalance(new BigDecimal("1000.00"));
        loan.setApplicationFee(new BigDecimal("20.00"));
        loan.setApplicationFeePaid(new BigDecimal("20.00"));

        stub(List.of(loansReceivable, cash), List.of(entry), List.of(loan));

        var report = service.reconcile(1L, LocalDate.of(2026, 8, 24));

        assertEquals(1, report.loanCount());
    }

    @Test
    void reversedEntriesAreStillPartOfTheMathematicalLedgerAndCanOffsetOriginals() {
        ChartOfAccount loansReceivable = account("1100", "Loans Receivable");
        ChartOfAccount cash = account("1000", "Cash and Bank");

        JournalEntry original = entry(6L, "MANUAL", "88", false);
        original.addLine(line(loansReceivable, "500.00", "0.00"));
        original.addLine(line(cash, "0.00", "500.00"));

        JournalEntry reversal = entry(7L, "REVERSAL", "88-R", false);
        reversal.addLine(line(loansReceivable, "0.00", "500.00"));
        reversal.addLine(line(cash, "500.00", "0.00"));

        stub(List.of(loansReceivable, cash), List.of(original, reversal), List.of());

        var report = service.reconcile(1L, LocalDate.of(2026, 8, 24));

        assertTrue(report.balanced());
    }

    private void stub(
            List<ChartOfAccount> accounts,
            List<JournalEntry> entries,
            List<Loan> loans) {
        when(chartOfAccountRepository.findByOrganization_IdOrderByCodeAsc(1L))
                .thenReturn(accounts);
        when(journalEntryRepository.findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAscIdAsc(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(LocalDate.class),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 8, 24))))
                .thenReturn(entries);
        when(loanRepository.findByOrganization_Id(1L)).thenReturn(loans);
    }

    private ChartOfAccount account(String code, String name) {
        return ChartOfAccount.builder()
                .id(code.hashCode() & 0x7fffffffL)
                .organization(organization)
                .code(code)
                .name(name)
                .type(ChartOfAccount.AccountType.ASSET)
                .normalBalance(ChartOfAccount.NormalBalance.DEBIT)
                .active(true)
                .build();
    }

    private JournalEntry entry(Long id, String sourceType, String sourceId, boolean reversed) {
        return JournalEntry.builder()
                .id(id)
                .organization(organization)
                .entryDate(LocalDate.of(2026, 8, 24))
                .sourceType(sourceType)
                .sourceId(sourceId)
                .reversed(reversed)
                .build();
    }

    private JournalLine line(ChartOfAccount account, String debit, String credit) {
        return JournalLine.builder()
                .account(account)
                .debit(new BigDecimal(debit))
                .credit(new BigDecimal(credit))
                .build();
    }
}
