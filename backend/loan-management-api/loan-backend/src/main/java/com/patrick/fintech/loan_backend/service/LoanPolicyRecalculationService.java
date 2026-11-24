package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.model.JournalEntry;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.model.PaymentTransaction;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import com.patrick.fintech.loan_backend.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

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
    private final PaymentRepository paymentRepository;
    private final AccountingService accountingService;
    private final AuditService auditService;

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

    public record Applied(String reference, Long loanId, Long journalId,
                          LoanPolicyRecalculationEngine.Result corrected,
                          Delta adjustment, String status) {}

    /**
     * Recompute amounts only from source contracts and posted cash receipts,
     * then append a balanced non-cash adjustment and update operational
     * balances atomically. No user provides any replacement money amounts.
     *
     * Restricted to imported loans with complete supported history; abnormal
     * cases fail without writes. The immutable transactions and original
     * PAYMENT_RECEIVED journals remain unchanged.
     */
    @Transactional
    public Applied apply(Long loanId, Long organizationId, User actor) {
        if (actor == null || actor.getOrganization() == null
                || !Objects.equals(organizationId, actor.getOrganization().getId()))
            throw new IllegalStateException("Authenticated organization required");
        Loan loan = loanRepository.findByIdForUpdate(loanId)
                .filter(l -> l.getOrganization() != null
                        && organizationId.equals(l.getOrganization().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Loan not found in organization"));
        if (!Boolean.TRUE.equals(loan.getImported()) || loan.getCreatedAt() == null
                || loan.getMaturityDate() == null)
            throw new IllegalStateException("Only imported loans with verified maturity can be replayed");
        List<PaymentTransaction> receipts = transactionRepository.findByLoanIdOrderByCreatedAtDesc(loanId);
        List<Payment> schedule = paymentRepository.findByLoanId(loanId);
        // The payment row is a cumulative imported installment, not one row
        // for each immutable cash receipt. Support a single posted receipt
        // until multi-receipt schedule history has been fully verified.
        if (receipts.size() > 1 || schedule.size() > 1)
            throw new IllegalStateException(
                    "Complex historical payment schedule requires transaction-level reconciliation");
        if (receipts.size() == 1 && (schedule.size() != 1
                || receipts.get(0).getInstallment() == null
                || !Objects.equals(schedule.get(0).getId(), receipts.get(0).getInstallment().getId())
                || !money(schedule.get(0).getAmountPaidDecimal()).equals(money(receipts.get(0).getAmount()))))
            throw new IllegalStateException(
                    "Stored installment and immutable receipt do not match; no automatic change");
        if (receipts.stream().anyMatch(tx -> tx.getCreatedAt() == null
                || tx.getCreatedAt().toLocalDate().isBefore(loan.getCreatedAt().toLocalDate())))
            throw new IllegalStateException("Imported payment has incomplete source-history dates");

        LocalDate asOf = LocalDate.now();
        LoanPolicyRecalculationEngine.Result corrected =
                new LoanPolicyRecalculationEngine(allocationService)
                        .calculate(loan, receipts, asOf);

        BigDecimal originalPrincipalBeforeReceipts = money(loan.getOutstandingBalanceDecimal())
                .add(receipts.stream().map(tx -> money(tx.getPrincipalComponent()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add));

        Delta adjustment = new Delta(
                money(corrected.principalOutstanding().subtract(money(loan.getOutstandingBalanceDecimal()))),
                money(corrected.interestOutstanding().subtract(money(loan.getInterestOutstandingDecimal()))),
                money(corrected.managementOutstanding().subtract(money(loan.getManagementFeeOutstandingDecimal()))),
                money(corrected.penaltyOutstanding().subtract(
                        money(loan.getPenaltiesAssessedDecimal())
                                .subtract(money(loan.getPenaltiesPaidDecimal())))));

        if (adjustment.principal().signum() == 0 && adjustment.interest().signum() == 0
                && adjustment.management().signum() == 0
                && adjustment.penaltyOutstanding().signum() == 0) {
            return new Applied(loan.getReferenceNumber(), loanId, null, corrected,
                    adjustment, "ALREADY_CONSISTENT");
        }

        BigDecimal principalPaid = money(loan.getAmountDecimal())
                .subtract(corrected.principalOutstanding());
        BigDecimal interestPaid = money(loan.getTotalInterestDecimal())
                .subtract(corrected.interestOutstanding());
        BigDecimal managementPaid = money(loan.getManagementFeeDecimal())
                .subtract(corrected.managementOutstanding());
        BigDecimal paidTotal = principalPaid.add(interestPaid)
                .add(managementPaid).add(corrected.penaltyPaid())
                .add(money(loan.getExtensionFeePaidDecimal()));
        if (principalPaid.signum() < 0 || interestPaid.signum() < 0
                || managementPaid.signum() < 0
                || paidTotal.subtract(money(loan.getTotalPaidDecimal())).abs()
                        .compareTo(new BigDecimal("0.01")) > 0) {
            throw new IllegalStateException("Replayed payment components cannot reconcile to "
                    + "the immutable cash amount and imported opening payments");
        }
        // Must post the balanced journal first, so a posting rejection leaves
        // both GL and operational loan aggregates unchanged.
        JournalEntry journal = accountingService.postPolicyRecalculation(
                loan, corrected, originalPrincipalBeforeReceipts, asOf);
        if (journal == null)
            throw new IllegalStateException("Correction was nonzero but no accounting journal was posted");

        loan.setOutstandingBalance(corrected.principalOutstanding());
        loan.setPrincipalPaid(principalPaid);
        loan.setInterestOutstanding(corrected.interestOutstanding());
        loan.setInterestPaid(interestPaid);
        loan.setManagementFeeOutstanding(corrected.managementOutstanding());
        loan.setManagementFeePaid(managementPaid);
        loan.setPenaltiesAssessed(corrected.penaltyAssessed());
        loan.setPenaltiesPaid(corrected.penaltyPaid());
        loan.setTotalRepayable(money(loan.getTotalPaidDecimal())
                .add(corrected.principalOutstanding())
                .add(corrected.interestOutstanding())
                .add(corrected.managementOutstanding())
                .add(corrected.penaltyOutstanding())
                .add(money(loan.getExtensionFeeOutstandingDecimal())));
        if (receipts.size() == 1) {
            LoanPolicyRecalculationEngine.CorrectedReceipt a = corrected.receipts().get(0);
            Payment row = schedule.get(0);
            BigDecimal priorInterestArrears = money(loan.getInterestOutstandingDecimal())
                    .add(money(a.interest()));
            BigDecimal priorManagementArrears = money(loan.getManagementFeeOutstandingDecimal())
                    .add(money(a.managementFee()));
            row.setPrincipalComponent(a.principal());
            row.setInterestComponent(a.interest());
            row.setManagementFeeComponent(a.managementFee());
            row.setPenaltyPaid(a.penalty());
            row.setPenalty(corrected.penaltyAssessed());
            row.setCycleInterestDue(priorInterestArrears);
            row.setCycleInterestRemaining(corrected.interestOutstanding());
            row.setCycleManagementFeeDue(priorManagementArrears);
            row.setCycleManagementFeeRemaining(corrected.managementOutstanding());
            row.setOutstandingAfter(corrected.principalOutstanding());
            paymentRepository.save(row);
        }
        loanRepository.saveAndFlush(loan);
        auditService.log(loan.getOrganization(), actor, "AUTOMATED_LOAN_POLICY_RECALC",
                "Loan", String.valueOf(loanId),
                "Recomputed all supported contract-maturity penalties and "
                        + "repayment allocations from immutable cash transactions; "
                        + "noncash GL journal=" + journal.getId()
                        + "; original receipts were not changed");
        return new Applied(loan.getReferenceNumber(), loanId, journal.getId(),
                corrected, adjustment, "APPLIED_NONCASH_POLICY_RECONCILIATION");
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }
}
