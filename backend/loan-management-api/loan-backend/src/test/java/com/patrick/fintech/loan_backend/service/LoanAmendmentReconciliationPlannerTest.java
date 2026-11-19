package com.patrick.fintech.loan_backend.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class LoanAmendmentReconciliationPlannerTest {
    private static BigDecimal n(String value) { return new BigDecimal(value); }

    @Test
    void oneMonthNegotiationShowsExactReceivableCorrections() {
        var contract = LoanAmendmentReconciliationPlanner.contract(
                n("1000000"), 1, n("2.5"), n("2.5"));
        assertEquals(n("25000.00"), contract.interest());
        assertEquals(n("25000.00"), contract.managementFee());
        assertEquals(n("1050000.00"), contract.repayable());

        var plan = LoanAmendmentReconciliationPlanner.plan(contract, n("1050000"),
                n("0"), n("50000"), n("50000"));
        assertEquals(n("0.00"), plan.allocation().totalOutstanding());
        assertEquals(n("-50000.00"), plan.interestReceivableChange());
        assertEquals(n("-50000.00"), plan.managementReceivableChange());
    }

    @Test
    void correctsPrincipalBeforeAnyRepayment() {
        var contract = LoanAmendmentReconciliationPlanner.contract(
                n("20000000"), 1, n("5"), n("5"));
        var plan = LoanAmendmentReconciliationPlanner.plan(contract, n("0"),
                n("15000000"), n("750000"), n("750000"));
        assertEquals(n("5000000.00"), plan.principalReceivableChange());
        assertEquals(n("250000.00"), plan.interestReceivableChange());
        assertEquals(n("250000.00"), plan.managementReceivableChange());
    }

    @Test
    void refusesUnverifiedNegativeReceivables() {
        var contract = LoanAmendmentReconciliationPlanner.contract(
                n("1000000"), 1, n("5"), n("5"));
        assertThrows(IllegalArgumentException.class, () ->
                LoanAmendmentReconciliationPlanner.plan(contract, n("0"), n("-1"), n("0"), n("0")));
    }
}
