package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.PaymentTransaction;
import com.patrick.fintech.loan_backend.util.FinancialPolicy;
import com.patrick.fintech.loan_backend.util.LoanMaturityPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic, read-only loan policy replay for already-recorded receipts.
 * It returns corrected figures. It NEVER overwrites an immutable receipt,
 * changes a journal, or writes a loan; the accounting reconciliation workflow
 * must create and approve offsetting journal entries separately.
 *
 * The conservative replay below supports matured contracts with complete
 * payment-transaction history after the import opening position. Unsupported
 * cases MUST be reported instead of guessing a missing contractual balance.
 */
public final class LoanPolicyRecalculationEngine {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final PaymentAllocationService allocationService;

    public LoanPolicyRecalculationEngine(PaymentAllocationService allocationService) {
        this.allocationService = Objects.requireNonNull(allocationService);
    }

    public record CorrectedReceipt(Long transactionId, LocalDate date,
                                   BigDecimal amount, BigDecimal penalty,
                                   BigDecimal interest, BigDecimal managementFee,
                                   BigDecimal extensionFee, BigDecimal principal,
                                   BigDecimal overpayment) {}

    public record Result(Long loanId, String reference, LocalDate firstPenaltyDate,
                         BigDecimal principalOutstanding, BigDecimal interestOutstanding,
                         BigDecimal managementOutstanding, BigDecimal penaltyAssessed,
                         BigDecimal penaltyPaid, BigDecimal penaltyOutstanding,
                         List<CorrectedReceipt> receipts) {}

    public Result calculate(Loan loan, List<PaymentTransaction> recorded, LocalDate asOf) {
        if (loan == null || loan.getId() == null || asOf == null || recorded == null) {
            throw new IllegalArgumentException("Loan, receipt history, and as-of date required");
        }
        LocalDate firstPenalty = LoanMaturityPolicy.firstPenaltyDate(loan);
        LocalDate maturity = LoanMaturityPolicy.maturityDate(loan);
        if (maturity == null || firstPenalty == null) {
            throw new IllegalStateException("Verified contract maturity is required for financial replay");
        }
        if (loan.getStatus() == com.patrick.fintech.loan_backend.model.LoanStatus.WRITTEN_OFF) {
            throw new IllegalStateException("Written-off loan requires a separate recovery ledger");
        }
        List<PaymentTransaction> receipts = recorded.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(PaymentTransaction::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PaymentTransaction::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        BigDecimal recordedPrincipal = ZERO, recordedInterest = ZERO, recordedManagement = ZERO;
        BigDecimal recordedPenalty = ZERO;
        for (PaymentTransaction tx : receipts) {
            if (tx.getId() == null || tx.getCreatedAt() == null || tx.getAmount() == null
                    || tx.getCreatedAt().toLocalDate().isAfter(asOf)
                    || tx.getCreatedAt().toLocalDate().isBefore(maturity)
                    || tx.getStatus() != PaymentTransaction.TransactionStatus.POSTED
                    || Boolean.TRUE.equals(tx.getReversed())
                    || money(tx.getUnappliedAmount()).signum() != 0
                    || money(tx.getExtensionFeeComponent()).signum() != 0) {
                throw new IllegalStateException("Incomplete, early, reversed, or complex repayment "
                        + "requires verified transaction-level replay before automatic adjustment");
            }
            BigDecimal totalAllocation = money(tx.getPrincipalComponent())
                    .add(money(tx.getInterestComponent()))
                    .add(money(tx.getManagementFeeComponent()))
                    .add(money(tx.getPenaltyComponent()));
            if (totalAllocation.compareTo(money(tx.getAmount())) != 0) {
                throw new IllegalStateException("Receipt " + tx.getId()
                        + " does not balance to its original cash amount");
            }
            recordedPrincipal = recordedPrincipal.add(money(tx.getPrincipalComponent()));
            recordedInterest = recordedInterest.add(money(tx.getInterestComponent()));
            recordedManagement = recordedManagement.add(money(tx.getManagementFeeComponent()));
            recordedPenalty = recordedPenalty.add(money(tx.getPenaltyComponent()));
        }
        if (money(loan.getPenaltiesPaidDecimal()).subtract(recordedPenalty).signum() != 0
                || money(loan.getExtensionFeeOutstandingDecimal()).signum() != 0
                || money(loan.getExtensionFeePaidDecimal()).signum() != 0) {
            throw new IllegalStateException("Pre-import penalty settlements or extension "
                    + "transactions require additional verified history");
        }

        BigDecimal principal = money(loan.getOutstandingBalanceDecimal()).add(recordedPrincipal);
        BigDecimal interest = money(loan.getInterestOutstandingDecimal()).add(recordedInterest);
        BigDecimal management = money(loan.getManagementFeeOutstandingDecimal()).add(recordedManagement);
        if (principal.signum() <= 0 || interest.signum() < 0 || management.signum() < 0) {
            throw new IllegalStateException("Historical contractual opening balances are inconsistent");
        }

        BigDecimal penaltyAssessed = ZERO, penaltyPaid = ZERO;
        LocalDate nextAccrualDate = firstPenalty;
        java.util.ArrayList<CorrectedReceipt> corrected = new java.util.ArrayList<>();

        for (PaymentTransaction tx : receipts) {
            LocalDate paidOn = tx.getCreatedAt().toLocalDate();
            if (!nextAccrualDate.isAfter(paidOn)) {
                penaltyAssessed = penaltyAssessed.add(FinancialPolicy.historicalDailyPenalty(
                        principal, nextAccrualDate, paidOn, loan.getPenaltyRateDecimal(),
                        date -> principal));
                nextAccrualDate = paidOn.plusDays(1);
            }
            BigDecimal availablePenalty = money(penaltyAssessed.subtract(penaltyPaid));
            PaymentAllocationService.Allocation a = allocationService.allocate(
                    money(tx.getAmount()), availablePenalty, ZERO, interest, management, principal);
            if (a.overpayment().signum() != 0) {
                throw new IllegalStateException("Refundable surplus requires separate settlement accounting");
            }
            penaltyPaid = money(penaltyPaid.add(a.penalty()));
            interest = money(interest.subtract(a.interest()));
            management = money(management.subtract(a.managementFee()));
            principal = money(principal.subtract(a.principal()));
            corrected.add(new CorrectedReceipt(tx.getId(), paidOn, money(tx.getAmount()),
                    a.penalty(), a.interest(), a.managementFee(), a.extensionFee(),
                    a.principal(), a.overpayment()));
        }
        if (!nextAccrualDate.isAfter(asOf) && !asOf.isBefore(firstPenalty)) {
            penaltyAssessed = money(penaltyAssessed.add(FinancialPolicy.historicalDailyPenalty(
                    principal, nextAccrualDate, asOf, loan.getPenaltyRateDecimal(),
                    date -> principal)));
        }
        return new Result(loan.getId(), loan.getReferenceNumber(), firstPenalty,
                money(principal), money(interest), money(management),
                money(penaltyAssessed), money(penaltyPaid),
                money(penaltyAssessed.subtract(penaltyPaid)),
                List.copyOf(corrected));
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }
}
