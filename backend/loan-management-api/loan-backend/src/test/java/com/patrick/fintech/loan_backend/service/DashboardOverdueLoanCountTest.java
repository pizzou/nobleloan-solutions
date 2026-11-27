package com.patrick.fintech.loan_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardOverdueLoanCountTest {
    @Test
    void countsImportedOverdueLoansEvenWithoutPaymentScheduleRecords() {
        Loan imported = new Loan();
        imported.setImported(true);
        imported.setStatus(LoanStatus.OVERDUE);
        Loan nativeLoan = new Loan();
        nativeLoan.setImported(false);
        nativeLoan.setStatus(LoanStatus.OVERDUE);
        Loan active = new Loan();
        active.setStatus(LoanStatus.ACTIVE);
        assertEquals(2, DashboardService.countOverdueLoans(List.of(imported, nativeLoan, active)));
    }
}
