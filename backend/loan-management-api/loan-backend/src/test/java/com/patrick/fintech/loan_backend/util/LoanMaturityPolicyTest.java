package com.patrick.fintech.loan_backend.util;

import com.patrick.fintech.loan_backend.model.Loan;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class LoanMaturityPolicyTest {
    @Test
    void sixMonthLoanDoesNotAccruePenaltyOnMonthlyInstalmentDates() {
        Loan loan = Loan.builder().durationMonths(6)
                .startDate(LocalDate.of(2027, 1, 1)).build();
        assertEquals(LocalDate.of(2027, 7, 1), LoanMaturityPolicy.maturityDate(loan));
        assertEquals(LocalDate.of(2027, 7, 5), LoanMaturityPolicy.firstPenaltyDate(loan));
        assertFalse(LoanMaturityPolicy.chargeable(loan, LocalDate.of(2027, 3, 23)));
        assertFalse(LoanMaturityPolicy.chargeable(loan, LocalDate.of(2027, 7, 4)));
        assertTrue(LoanMaturityPolicy.chargeable(loan, LocalDate.of(2027, 7, 5)));
    }

    @Test
    void explicitMaturityDateOverridesReconstructedTerm() {
        Loan loan = Loan.builder().durationMonths(6)
                .startDate(LocalDate.of(2027, 1, 1))
                .maturityDate(LocalDate.of(2027, 7, 3)).build();
        assertEquals(LocalDate.of(2027, 7, 7), LoanMaturityPolicy.firstPenaltyDate(loan));
    }

    @Test
    void importedLoanWithoutVerifiedMaturityCannotAccrueFromMonthlyDueDate() {
        Loan loan = Loan.builder().imported(true).durationMonths(6)
                .startDate(LocalDate.of(2027, 1, 1)).build();
        assertNull(LoanMaturityPolicy.maturityDate(loan));
        assertNull(LoanMaturityPolicy.firstPenaltyDate(loan));
        assertFalse(LoanMaturityPolicy.chargeable(loan, LocalDate.of(2027, 7, 15)));
    }
}
