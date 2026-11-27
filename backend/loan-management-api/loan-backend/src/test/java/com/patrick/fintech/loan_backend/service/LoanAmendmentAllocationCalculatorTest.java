package com.patrick.fintech.loan_backend.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class LoanAmendmentAllocationCalculatorTest {
    private static BigDecimal money(String value) { return new BigDecimal(value); }

    @Test
    void negotiatedOneMillionLoanSettlesAtOneMillionAndFiftyThousand() {
        var allocation = LoanAmendmentAllocationCalculator.allocate(
                money("1000000"), money("25000"), money("25000"), money("1050000"));
        assertEquals(money("25000.00"), allocation.interestPaid());
        assertEquals(money("25000.00"), allocation.managementFeePaid());
        assertEquals(money("1000000.00"), allocation.principalPaid());
        assertEquals(money("0.00"), allocation.totalOutstanding());
        assertEquals(money("0.00"), allocation.unallocated());
    }

    @Test
    void partiallyPaidLoanKeepsExplicitObligation() {
        var allocation = LoanAmendmentAllocationCalculator.allocate(
                money("1000000"), money("25000"), money("25000"), money("250000"));
        assertEquals(money("200000.00"), allocation.principalPaid());
        assertEquals(money("800000.00"), allocation.principalOutstanding());
        assertEquals(money("0.00"), allocation.interestOutstanding());
        assertEquals(money("0.00"), allocation.managementFeeOutstanding());
    }

    @Test
    void overpaymentIsNeverSilentlyAppliedToPrincipal() {
        var allocation = LoanAmendmentAllocationCalculator.allocate(
                money("1000000"), money("25000"), money("25000"), money("1100000"));
        assertEquals(money("50000.00"), allocation.unallocated());
        assertEquals(money("0.00"), allocation.totalOutstanding());
    }

    @Test
    void rejectsNegativeCashOrObligations() {
        assertThrows(IllegalArgumentException.class,
                () -> LoanAmendmentAllocationCalculator.allocate(
                        money("1000000"), money("25000"), money("25000"), money("-1")));
    }
}
