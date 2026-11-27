package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.JournalEntry;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.model.PaymentTransaction;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HistoricalRepaymentCorrectionServiceTest {

    @Test
    void appliesVerifiedReclassificationOnceWithoutChangingTheOriginalReceipt() {
        var loans = mock(LoanRepository.class);
        var receipts = mock(PaymentTransactionRepository.class);
        var accounting = mock(AccountingService.class);
        var jdbc = mock(JdbcTemplate.class);
        var calculation = new HistoricalRepaymentCalculation(new PaymentAllocationService());
        var service = new HistoricalRepaymentCorrectionService(loans, receipts, calculation, accounting, jdbc);

        var maturity = LocalDate.of(2026, 9, 26);
        var paidOn = LocalDate.of(2026, 11, 19);
        var cutoff = LocalDate.of(2026, 11, 25);
        var openingCutoff = LocalDate.of(2026, 11, 12);
        var org = Organization.builder().id(1L).build();
        var loan = new Loan();
        loan.setId(54L);
        loan.setOrganization(org);
        loan.setReferenceNumber("NLS-000054");
        loan.setMaturityDate(maturity);
        loan.setPenaltyRate(money("10.00"));
        loan.setAmount(money("33500000.00"));
        loan.setPrincipalPaid(money("8750000.00"));
        loan.setOutstandingBalance(money("24750000.00"));
        loan.setTotalInterest(money("10050000.00"));
        loan.setManagementFee(money("10050000.00"));
        loan.setInterestPaid(money("5425000.00"));
        loan.setManagementFeePaid(money("5425000.00"));
        loan.setPenaltiesAssessed(money("8870000.00"));
        loan.setPenaltiesPaid(BigDecimal.ZERO);
        loan.setTotalRepayable(money("62470000.00"));
        var row = Payment.builder().id(29L).paidDate(paidOn).amountPaid(money("12100000.00")).build();
        var receipt = PaymentTransaction.builder().id(5L).loan(loan).organization(org).installment(row)
                .amount(money("12100000.00")).managementFeeComponent(money("1675000.00"))
                .interestComponent(money("1675000.00")).principalComponent(money("8750000.00")).build();
        var opening = new HistoricalRepaymentCalculation.Opening(maturity, maturity, money("10.00"),
                money("33500000.00"), BigDecimal.ZERO, money("6300000.00"), money("6300000.00"), BigDecimal.ZERO);

        when(loans.findByIdForUpdate(54L)).thenReturn(Optional.of(loan));
        when(loans.countRecordedPaymentTransactions(54L)).thenReturn(1L);
        when(receipts.findByOrganization_IdAndId(1L, 5L)).thenReturn(Optional.of(receipt));
        when(jdbc.queryForList(contains("historical_receipt_reallocations"), eq(Long.class), eq(5L)))
                .thenReturn(List.of(), List.of(410L), List.of(410L));
        when(jdbc.queryForObject(contains("SELECT EXISTS"), eq(Boolean.class), any(Object[].class)))
                .thenReturn(true, false);
        when(jdbc.queryForList(contains("source_id IN"), eq(Long.class),
                eq(1L), eq("PAYTX:5"), eq("29"), eq(paidOn))).thenReturn(List.of(306L));
        when(jdbc.queryForList(contains("entry.source_type = ?"),
                eq(1L), eq("NLS-000054"), eq("LEGACY_PENALTY_OPENING"), eq(cutoff)))
                .thenReturn(List.of(balance("1175", "7705000.00"), balance("3010", "-7705000.00")));
        when(jdbc.queryForList(contains("entry.source_type = ?"),
                eq(1L), eq("NLS-000054"), eq("PENALTY_ACCRUAL"), eq(cutoff)))
                .thenReturn(List.of(balance("1175", "1165000.00"), balance("4100", "-1165000.00")));
        when(jdbc.queryForList(contains("line.journal_entry_id = ?"), eq(306L)))
                .thenReturn(List.of(balance("1000", "12100000.00"), balance("1100", "-8750000.00"),
                        balance("1150", "-1675000.00"), balance("1160", "-1675000.00")));
        when(accounting.postHistoricalReceiptReclassification(eq(loan), eq(5L), anyMap()))
                .thenReturn(JournalEntry.builder().id(410L).build());

        assertThat(service.applyVerifiedSingleReceipt(54L, 5L, opening, List.of(), cutoff,
                openingCutoff, money("8870000.00"))).isEqualTo(410L);
        assertThat(loan.getOutstandingBalanceDecimal()).isEqualByComparingTo("33500000.00");
        assertThat(loan.getPenaltiesAssessedDecimal()).isEqualByComparingTo("6253333.33");
        assertThat(loan.getPenaltiesPaidDecimal()).isEqualByComparingTo("5583333.33");
        assertThat(loan.getPenaltiesAssessedDecimal().subtract(loan.getPenaltiesPaidDecimal()))
                .isEqualByComparingTo("670000.00");
        assertThat(loan.getManagementFeeOutstandingDecimal()).isEqualByComparingTo("3041666.67");
        assertThat(loan.getInterestOutstandingDecimal()).isEqualByComparingTo("3041666.66");
        assertThat(receipt.getPrincipalComponent()).isEqualByComparingTo("8750000.00");
        assertThat(receipt.getAmount()).isEqualByComparingTo("12100000.00");
        assertThat(row.getPrincipalComponentDecimal()).isEqualByComparingTo("0.00");

        assertThat(service.applyVerifiedSingleReceipt(54L, 5L, opening, List.of(), cutoff,
                openingCutoff, money("8870000.00"))).isEqualTo(410L);
        assertThatThrownBy(() -> service.applyVerifiedSingleReceipt(54L, 5L, opening, List.of(), cutoff,
                openingCutoff, money("8870000.00"))).hasMessageContaining("uses a different allocation");
        verify(accounting, times(1)).postHistoricalReceiptReclassification(eq(loan), eq(5L), anyMap());
        verify(accounting, times(1)).postHistoricalPenaltyRestatement(
                loan, 5L, money("-2903333.33"), money("286666.66"));
        verify(jdbc, times(1)).update(contains("INSERT INTO historical_receipt_reallocations"), any(Object[].class));
    }

    private static Map<String, Object> balance(String code, String amount) {
        return Map.of("code", code, "balance", money(amount));
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }
}
