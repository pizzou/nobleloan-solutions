package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.regulatory.BnrSummaryReport;
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

    @Mock
    RegulatoryReportingService regulatoryReportingService;

    private FinancialReconciliationService service;
    private Organization organization;

    @BeforeEach
    void setUp() {
        service = new FinancialReconciliationService(
                journalEntryRepository,
                chartOfAccountRepository,
                loanRepository, regulatoryReportingService);

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
        loan.setStatus(com.patrick.fintech.loan_backend.model.LoanStatus.ACTIVE);
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

    @Test
    void numericLoanReferencesDoNotCrossMatchInLoanDiagnostic() {
        ChartOfAccount loansReceivable = account("1100", "Loans Receivable");
        ChartOfAccount cash = account("1000", "Cash and Bank");

        JournalEntry loanTenPayment = entry(11L, "PAYMENT_RECEIVED", "PAYMENT-10", false);
        loanTenPayment.setReference("PAYMENT-10");
        loanTenPayment.addLine(line(cash, "100.00", "0.00"));
        JournalLine repayment = line(loansReceivable, "0.00", "100.00");
        repayment.setDescription("Principal repayment — 10");
        loanTenPayment.addLine(repayment);

        Loan loanOne = activeLoan(1L, "1", new BigDecimal("500.00"), new BigDecimal("500.00"));
        Loan loanTen = activeLoan(10L, "10", new BigDecimal("900.00"), new BigDecimal("900.00"));

        when(chartOfAccountRepository.findByOrganization_IdOrderByCodeAsc(1L))
                .thenReturn(List.of(loansReceivable, cash));
        when(journalEntryRepository.findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAscIdAsc(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(LocalDate.class),
                org.mockito.ArgumentMatchers.eq(LocalDate.now())))
                .thenReturn(List.of(loanTenPayment));
        when(loanRepository.findByOrganization_Id(1L))
                .thenReturn(List.of(loanOne, loanTen));

        var diagnostics = service.diagnoseLoanSubledger(1L);

        var loanOneDiagnostic = diagnostics.stream()
                .filter(item -> Long.valueOf(1L).equals(item.loanId()))
                .findFirst()
                .orElseThrow();

        assertEquals(new BigDecimal("0.00"), loanOneDiagnostic.glPrincipal());
        assertEquals(new BigDecimal("-500.00"), loanOneDiagnostic.principalDifference());
    }

    @Test
    void organizationReceivableReconciliationUsesActualGlAccountBalance() {
        ChartOfAccount loansReceivable = account("1100", "Loans Receivable");
        ChartOfAccount cash = account("1000", "Cash and Bank");

        JournalEntry openingOne = entry(12L, "LEGACY_LOAN_OPENING", "LOAN:1", false);
        openingOne.setReference("1");
        JournalLine onePrincipal = line(loansReceivable, "100.00", "0.00");
        onePrincipal.setDescription("Opening loan principal receivable — 1");
        openingOne.addLine(onePrincipal);
        openingOne.addLine(line(cash, "0.00", "100.00"));

        JournalEntry openingTen = entry(13L, "LEGACY_LOAN_OPENING", "LOAN:10", false);
        openingTen.setReference("10");
        JournalLine tenPrincipal = line(loansReceivable, "900.00", "0.00");
        tenPrincipal.setDescription("Opening loan principal receivable — 10");
        openingTen.addLine(tenPrincipal);
        openingTen.addLine(line(cash, "0.00", "900.00"));

        Loan loanOne = activeLoan(1L, "1", new BigDecimal("100.00"), new BigDecimal("100.00"));
        Loan loanTen = activeLoan(10L, "10", new BigDecimal("900.00"), new BigDecimal("900.00"));

        stub(List.of(loansReceivable, cash), List.of(openingOne, openingTen), List.of(loanOne, loanTen));

        var report = service.reconcile(1L, LocalDate.of(2026, 8, 24));

        assertTrue(report.subledger().get("1100").reconciles());
        assertEquals(new BigDecimal("1000.00"), report.subledger().get("1100").glBalance());
        assertEquals(new BigDecimal("1000.00"), report.subledger().get("1100").operationalBalance());
    }

    private Loan activeLoan(
            Long id,
            String reference,
            BigDecimal amount,
            BigDecimal outstanding) {
        Loan loan = new Loan();
        loan.setId(id);
        loan.setOrganization(organization);
        loan.setReferenceNumber(reference);
        loan.setStatus(com.patrick.fintech.loan_backend.model.LoanStatus.ACTIVE);
        loan.setAmount(amount);
        loan.setPrincipalPaid(BigDecimal.ZERO);
        loan.setOutstandingBalance(outstanding);
        loan.setTotalInterest(BigDecimal.ZERO);
        loan.setInterestPaid(BigDecimal.ZERO);
        loan.setInterestOutstanding(BigDecimal.ZERO);
        loan.setManagementFee(BigDecimal.ZERO);
        loan.setManagementFeePaid(BigDecimal.ZERO);
        loan.setManagementFeeOutstanding(BigDecimal.ZERO);
        loan.setApplicationFee(BigDecimal.ZERO);
        loan.setApplicationFeePaid(BigDecimal.ZERO);
        return loan;
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

        BigDecimal bnrOutstanding = loans.stream()
                .filter(loan -> loan != null
                        && loan.getStatus() != com.patrick.fintech.loan_backend.model.LoanStatus.PENDING
                        && loan.getStatus() != com.patrick.fintech.loan_backend.model.LoanStatus.UNDER_REVIEW
                        && loan.getStatus() != com.patrick.fintech.loan_backend.model.LoanStatus.REJECTED
                        && loan.getStatus() != com.patrick.fintech.loan_backend.model.LoanStatus.CANCELLED)
                .map(Loan::getOutstandingBalanceDecimal)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        when(regulatoryReportingService.buildBnrSummary(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(RegulatoryReportingService.ReportPeriod.CUSTOM),
                org.mockito.ArgumentMatchers.any(LocalDate.class),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 8, 24))))
                .thenReturn(BnrSummaryReport.builder()
                        .outstandingPrincipal(bnrOutstanding)
                        .build());
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