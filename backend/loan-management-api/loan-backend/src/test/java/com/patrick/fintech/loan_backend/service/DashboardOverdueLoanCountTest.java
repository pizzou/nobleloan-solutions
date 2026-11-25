package com.patrick.fintech.loan_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardOverdueLoanCountTest {
    private Loan loan(boolean imported, LoanStatus storedStatus, LocalDate disbursed) {
        Loan loan = new Loan();
        loan.setImported(imported);
        loan.setStatus(storedStatus);
        loan.setDisbursedAt(disbursed.atStartOfDay());
        loan.setDurationMonths(6);
        return loan;
    }

    @Test
    void countsTrueContractOverdueRegardlessOfImportedOrStaleStoredStatus() {
        LocalDate today = LocalDate.now();
        Loan importedOverdue = loan(true, LoanStatus.OVERDUE, today.minusMonths(6).minusDays(10));
        Loan nativeOverdue = loan(false, LoanStatus.OVERDUE, today.minusMonths(6).minusDays(5));
        Loan graceButFlaggedOverdue = loan(true, LoanStatus.OVERDUE, today.minusMonths(6));
        Loan activeButActuallyOverdue = loan(true, LoanStatus.ACTIVE, today.minusMonths(6).minusDays(8));
        assertEquals(3, DashboardService.countOverdueLoans(List.of(
                importedOverdue, nativeOverdue, graceButFlaggedOverdue, activeButActuallyOverdue)));
    }
}
