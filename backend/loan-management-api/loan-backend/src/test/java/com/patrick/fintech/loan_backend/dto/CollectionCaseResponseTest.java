package com.patrick.fintech.loan_backend.dto;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.CollectionCase;
import com.patrick.fintech.loan_backend.model.Loan;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class CollectionCaseResponseTest {
    @Test
    void includesBorrowerAndHistoricalPaidAmountsWithoutReturningEntities() {
        Borrower borrower = Borrower.builder().id(7L).firstName("Test")
                .lastName("Borrower").build();
        Loan loan = Loan.builder().id(19L).referenceNumber("NLS-000019")
                .borrower(borrower).currency("RWF").imported(true)
                .startDate(LocalDate.of(2026, 1, 1))
                .nextDueDate(LocalDate.of(2026, 3, 1))
                .amount(new BigDecimal("2000000"))
                .outstandingBalance(new BigDecimal("1000000"))
                .totalRepayable(new BigDecimal("2350000"))
                .totalPaid(new BigDecimal("1200000"))
                .principalPaid(new BigDecimal("1000000"))
                .interestPaid(new BigDecimal("150000"))
                .managementFeePaid(new BigDecimal("50000")).build();
        CollectionCase entry = CollectionCase.builder().id(3L).loan(loan)
                .daysPastDue(15).totalOutstanding(new BigDecimal("1150000")).build();
        CollectionCaseResponse result = CollectionCaseResponse.from(entry);
        assertThat(result.loan().borrower().firstName()).isEqualTo("Test");
        assertThat(result.loan().borrower().lastName()).isEqualTo("Borrower");
        assertThat(result.loan().totalPaid()).isEqualByComparingTo("1200000");
        assertThat(result.loan().interestPaid()).isEqualByComparingTo("150000");
        assertThat(result.totalOutstanding()).isEqualByComparingTo("1150000");
        assertThat(result.loan().nextDueDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    }
}
