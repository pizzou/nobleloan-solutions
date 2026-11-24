package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.PaymentTransaction;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Calculates a borrower-specific correction from persisted contracts and
 * immutable cash transactions. No monetary amount is supplied by the caller.
 *
 * This is the deterministic calculation stage of portfolio reprocessing.
 * Results must not be persisted as a loan-level overwrite: each reallocated
 * receipt and each original penalty income/equity source require matched,
 * balanced, idempotent GL correction entries first.
 */
@Service
@RequiredArgsConstructor
public class LoanPolicyRecalculationService {
    private final LoanRepository loanRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentAllocationService allocationService;

    public record Delta(BigDecimal principal, BigDecimal interest,
                        BigDecimal management, BigDecimal penaltyOutstanding) {}
    public record Preview(String reference, LocalDate asOf,
                          LoanPolicyRecalculationEngine.Result corrected,
                          Delta adjustment, boolean needsCorrection,
                          String postingStatus) {}

    @Transactional(readOnly = true)
    public Preview preview(Long loanId, Long organizationId, LocalDate asOf) {
        if (loanId == null || organizationId == null || asOf == null)
            throw new IllegalArgumentException("Loan, organization and as-of date required");
        Loan loan = loanRepository.findById(loanId)
                .filter(l -> l.getOrganization() != null
                        && organizationId.equals(l.getOrganization().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Loan not found in organization"));
        List<PaymentTransaction> receipts = transactionRepository.findByLoanIdOrderByCreatedAtDesc(loanId);
        LoanPolicyRecalculationEngine.Result calculated =
                new LoanPolicyRecalculationEngine(allocationService).calculate(loan, receipts, asOf);
        Delta change = new Delta(
                money(calculated.principalOutstanding().subtract(money(loan.getOutstandingBalanceDecimal()))),
                money(calculated.interestOutstanding().subtract(money(loan.getInterestOutstandingDecimal()))),
                money(calculated.managementOutstanding().subtract(money(loan.getManagementFeeOutstandingDecimal()))),
                money(calculated.penaltyOutstanding().subtract(
                        money(loan.getPenaltiesAssessedDecimal())
                                .subtract(money(loan.getPenaltiesPaidDecimal())))));
        boolean different = change.principal().signum() != 0
                || change.interest().signum() != 0
                || change.management().signum() != 0
                || change.penaltyOutstanding().signum() != 0;
        return new Preview(loan.getReferenceNumber(), asOf, calculated, change,
                different, "READ_ONLY_PREVIEW_NO_JOURNAL_OR_BALANCE_MUTATION");
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }
}
