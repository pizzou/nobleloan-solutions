package com.patrick.fintech.loan_backend.dto;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.CollectionCase;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.User;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Narrow collections API response; never serializes JPA entities or raw identity documents. */
public record CollectionCaseResponse(
        Long id,
        LoanSnapshot loan,
        AgentSnapshot assignedAgent,
        CollectionCase.CollectionBucket bucket,
        CollectionCase.CollectionStatus status,
        CollectionCase.Priority priority,
        Integer daysPastDue,
        BigDecimal overdueAmount,
        BigDecimal totalOutstanding,
        LocalDate lastContactDate,
        LocalDate nextActionDate,
        LocalDate promiseToPayDate,
        BigDecimal promiseToPayAmount,
        String resolutionNotes) {
    public record BorrowerSnapshot(Long id, String firstName, String lastName, String phone) {}
    public record LoanSnapshot(Long id, String referenceNumber, String currency,
                               BigDecimal outstandingBalance, BigDecimal totalPaid,
                               BigDecimal principalPaid, BigDecimal interestPaid,
                               BigDecimal managementFeePaid, LocalDate nextDueDate,
                               LocalDate startDate, Boolean imported, BorrowerSnapshot borrower) {}
    public record AgentSnapshot(Long id, String name) {}

    public static CollectionCaseResponse from(CollectionCase c) {
        Loan l = c.getLoan();
        Borrower b = l == null ? null : l.getBorrower();
        User u = c.getAssignedAgent();
        BorrowerSnapshot borrower = b == null ? null :
                new BorrowerSnapshot(b.getId(), b.getFirstName(), b.getLastName(), b.getPhone());
        LoanSnapshot loan = l == null ? null : new LoanSnapshot(
                l.getId(), l.getReferenceNumber(), l.getCurrency(),
                l.getOutstandingBalance(), l.getTotalPaid(), l.getPrincipalPaid(),
                l.getInterestPaid(), l.getManagementFeePaid(), l.getNextDueDate(),
                l.getStartDate(), l.getImported(), borrower);
        BigDecimal aggregateOutstanding = l != null && l.getTotalRepayable() != null
                ? l.getTotalRepayable().subtract(
                        l.getTotalPaid() == null ? BigDecimal.ZERO : l.getTotalPaid())
                        .max(BigDecimal.ZERO)
                : c.getTotalOutstandingDecimal();
        return new CollectionCaseResponse(c.getId(), loan,
                u == null ? null : new AgentSnapshot(u.getId(), u.getName()),
                c.getBucket(), c.getStatus(), c.getPriority(), c.getDaysPastDue(),
                c.getOverdueAmountDecimal(), aggregateOutstanding,
                c.getLastContactDate(), c.getNextActionDate(), c.getPromiseToPayDate(),
                c.getPromiseToPayAmountDecimal(), c.getResolutionNotes());
    }
}
