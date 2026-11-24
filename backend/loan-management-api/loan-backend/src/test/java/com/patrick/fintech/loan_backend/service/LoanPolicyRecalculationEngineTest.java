package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.PaymentTransaction;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LoanPolicyRecalculationEngineTest {
    private final LoanPolicyRecalculationEngine engine =
            new LoanPolicyRecalculationEngine(new PaymentAllocationService());

    @Test
    void gakubaReplayProducesExpectedNoncashAllocationOnOriginalReceipt() {
        Loan l = Loan.builder()
                .id(54L).referenceNumber("NLS-000054")
                .imported(true).status(LoanStatus.OVERDUE)
                .durationMonths(6).maturityDate(LocalDate.of(2026, 9, 26))
                .penaltyRate(new BigDecimal("10.00"))
                .outstandingBalance(new BigDecimal("24750000.00"))
                .interestOutstanding(new BigDecimal("4625000.00"))
                .managementFeeOutstanding(new BigDecimal("4625000.00"))
                .penaltiesPaid(BigDecimal.ZERO)
                .build();
        PaymentTransaction tx = PaymentTransaction.builder()
                .id(5L).loan(l).status(PaymentTransaction.TransactionStatus.POSTED)
                .reversed(false).amount(new BigDecimal("12100000.00"))
                .principalComponent(new BigDecimal("8750000.00"))
                .interestComponent(new BigDecimal("1675000.00"))
                .managementFeeComponent(new BigDecimal("1675000.00"))
                .penaltyComponent(BigDecimal.ZERO)
                .extensionFeeComponent(BigDecimal.ZERO)
                .unappliedAmount(BigDecimal.ZERO)
                .createdAt(LocalDateTime.of(2026, 11, 19, 14, 24))
                .build();
        LoanPolicyRecalculationEngine.Result result =
                engine.calculate(l, List.of(tx), LocalDate.of(2026, 11, 19));
        assertEquals(new BigDecimal("33500000.00"), result.principalOutstanding());
        assertEquals(new BigDecimal("3041666.66"), result.interestOutstanding());
        assertEquals(new BigDecimal("3041666.67"), result.managementOutstanding());
        assertEquals(new BigDecimal("5583333.33"), result.penaltyPaid());
        assertEquals(new BigDecimal("0.00"), result.penaltyOutstanding());
        assertEquals(new BigDecimal("0.00"), result.receipts().get(0).principal());
    }

    @Test
    void replayRefusesHistoricalPaymentsThatPredateMaturity() {
        Loan l = Loan.builder().id(10L).durationMonths(6)
                .maturityDate(LocalDate.of(2026, 9, 26))
                .outstandingBalance(new BigDecimal("500000.00")).build();
        PaymentTransaction tx = PaymentTransaction.builder().id(1L)
                .createdAt(LocalDateTime.of(2026, 9, 1, 12, 0))
                .status(PaymentTransaction.TransactionStatus.POSTED)
                .amount(new BigDecimal("100.00")).principalComponent(new BigDecimal("100.00"))
                .build();
        assertThrows(IllegalStateException.class,
                () -> engine.calculate(l, List.of(tx), LocalDate.of(2026, 11, 19)));
    }

    @Test
    void replayRefusesUnknownContractMaturity() {
        Loan l = Loan.builder().id(10L).imported(true)
                .durationMonths(6).build();
        assertThrows(IllegalStateException.class,
                () -> engine.calculate(l, List.of(), LocalDate.now()));
    }
}
