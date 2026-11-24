package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.util.FinancialPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic financial reclassification PLANNER. No persistence, no SQL,
 * no revision of immutable cash receipts. A correction must be approved and
 * posted by an audited accounting workflow after source reconciliation.
 *
 * Replays valid contractual maturity, 3-day grace, daily penalties, posted
 * receipts and the same payment waterfall used by PaymentService.
 * Contractual interest/management obligations are supplied from VERIFIED
 * dated accruals, rather than guessed from elapsed days or loan tenor.
 */
public final class HistoricalRepaymentReconciliationPlanner {
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private HistoricalRepaymentReconciliationPlanner() {}

    public record Charge(LocalDate dueOn, BigDecimal interest, BigDecimal management) {}
    public record Receipt(Long immutableTransactionId, LocalDate receivedOn, BigDecimal amount) {}
    public record Allocation(Long immutableTransactionId, LocalDate receivedOn,
                             BigDecimal cash, BigDecimal penalty, BigDecimal interest,
                             BigDecimal management, BigDecimal extension, BigDecimal principal,
                             BigDecimal unapplied, BigDecimal principalAfter,
                             BigDecimal unpaidPenaltyAfter) {}
    public record Result(BigDecimal assessedPenalty, BigDecimal paidPenalty,
                         BigDecimal outstandingPenalty, BigDecimal outstandingPrincipal,
                         BigDecimal outstandingInterest, BigDecimal outstandingManagement,
                         List<Allocation> allocations) {}

    /**
     * The opening amount is the VERIFIED principal at maturity, not the
     * original amount if historical principal repayments reduced it earlier.
     *
     * Supplied charges must represent earned contractual interest/management
     * still payable at the beginning of the replay or on a later due date.
     * Penalty accrues on principal once per eligible calendar day, inclusive
     * of a receipt date before allocating that day's receipt.
     */
    public static Result replay(LocalDate contractMaturity, LocalDate asOf,
                                BigDecimal principalAtMaturity, BigDecimal penaltyMonthlyRate,
                                List<Charge> verifiedCharges, List<Receipt> actualReceipts) {
        Objects.requireNonNull(contractMaturity, "contractMaturity");
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(principalAtMaturity, "principalAtMaturity");
        Objects.requireNonNull(penaltyMonthlyRate, "penaltyMonthlyRate");
        if (contractMaturity.isAfter(asOf) || principalAtMaturity.signum() < 0
                || penaltyMonthlyRate.signum() < 0
                || verifiedCharges == null || actualReceipts == null) {
            throw new IllegalArgumentException("Invalid historical reconciliation inputs");
        }
        List<Charge> charges = verifiedCharges.stream()
                .sorted(Comparator.comparing(Charge::dueOn)).toList();
        List<Receipt> receipts = actualReceipts.stream()
                .sorted(Comparator.comparing(Receipt::receivedOn)
                        .thenComparing(Receipt::immutableTransactionId)).toList();
        for (Charge charge : charges) {
            if (charge.dueOn() == null || charge.interest() == null
                    || charge.management() == null || charge.interest().signum() < 0
                    || charge.management().signum() < 0) {
                throw new IllegalArgumentException("Invalid contractual charge");
            }
        }
        for (Receipt receipt : receipts) {
            if (receipt.immutableTransactionId() == null || receipt.receivedOn() == null
                    || receipt.amount() == null || receipt.amount().signum() <= 0
                    || receipt.receivedOn().isBefore(contractMaturity)
                    || receipt.receivedOn().isAfter(asOf)) {
                throw new IllegalArgumentException("Invalid historical cash receipt");
            }
        }

        BigDecimal principal = money(principalAtMaturity);
        BigDecimal unroundedPenaltyAccrual = ZERO;
        BigDecimal paidPenalty = ZERO;
        BigDecimal interestDue = ZERO;
        BigDecimal managementDue = ZERO;
        List<Allocation> allocations = new ArrayList<>();
        PaymentAllocationService waterfall = new PaymentAllocationService();
        LocalDate firstPenaltyDay = OverduePenaltyClock.firstChargeableDay(contractMaturity);
        int ci = 0, ri = 0;
        for (LocalDate date = contractMaturity;
             !date.isAfter(asOf); date = date.plusDays(1)) {
            while (ci < charges.size() && !charges.get(ci).dueOn().isAfter(date)) {
                Charge charge = charges.get(ci++);
                interestDue = money(interestDue.add(charge.interest()));
                managementDue = money(managementDue.add(charge.management()));
            }

            if (!date.isBefore(firstPenaltyDay) && principal.signum() > 0) {
                // Keep precision across daily accruals, rounding only at
                // settlement boundaries and report totals (never per day).
                unroundedPenaltyAccrual = unroundedPenaltyAccrual.add(
                        principal.multiply(FinancialPolicy.dailyRateFraction(
                                penaltyMonthlyRate, date)));
            }
            while (ri < receipts.size() && receipts.get(ri).receivedOn().equals(date)) {
                Receipt receipt = receipts.get(ri++);
                BigDecimal assessedPenaltyToDate = money(unroundedPenaltyAccrual);
                PaymentAllocationService.Allocation allocation = waterfall.allocate(
                        receipt.amount(), assessedPenaltyToDate.subtract(paidPenalty).max(ZERO),
                        ZERO, interestDue, managementDue, principal);
                paidPenalty = money(paidPenalty.add(allocation.penalty()));
                interestDue = money(interestDue.subtract(allocation.interest()));
                managementDue = money(managementDue.subtract(allocation.managementFee()));
                principal = money(principal.subtract(allocation.principal()));
                allocations.add(new Allocation(receipt.immutableTransactionId(),
                        date, money(receipt.amount()), allocation.penalty(),
                        allocation.interest(), allocation.managementFee(),
                        allocation.extensionFee(), allocation.principal(),
                        allocation.overpayment(), principal,
                        money(assessedPenaltyToDate.subtract(paidPenalty))));
            }
        }
        BigDecimal assessedPenalty = money(unroundedPenaltyAccrual);
        return new Result(assessedPenalty, paidPenalty,
                money(assessedPenalty.subtract(paidPenalty)), principal,
                interestDue, managementDue, List.copyOf(allocations));
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
