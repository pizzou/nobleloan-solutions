package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.util.FinancialPolicy;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Deterministic financial replay from a verified opening position.
 * No persistence or accounting side effects. Opening balances and dates must
 * be established from the original contract and verified historical records.
 */
@Service
public class HistoricalRepaymentCalculation {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final PaymentAllocationService allocator;

    public HistoricalRepaymentCalculation(PaymentAllocationService allocator) {
        this.allocator = allocator;
    }

    public record Opening(LocalDate asOf, LocalDate maturity, BigDecimal monthlyPenaltyRate,
            BigDecimal principal, BigDecimal penalty, BigDecimal interest,
            BigDecimal management, BigDecimal extension) {}
    public record Charge(LocalDate date, BigDecimal interest, BigDecimal management) {}
    public record Receipt(long id, LocalDate date, BigDecimal amount) {}
    public record Allocation(long receiptId, LocalDate date, BigDecimal amount,
            PaymentAllocationService.Allocation components) {}
    public record Result(BigDecimal principal, BigDecimal penalty, BigDecimal interest,
            BigDecimal management, BigDecimal extension, BigDecimal penaltyAccrued,
            List<Allocation> allocations) {}

    public Result replay(Opening opening, List<Charge> charges, List<Receipt> receipts, LocalDate asOf) {
        if (opening == null || opening.asOf() == null || opening.maturity() == null
                || asOf == null || asOf.isBefore(opening.asOf())) {
            throw new IllegalArgumentException("Verified opening and contractual dates are required");
        }
        BigDecimal principal = nonNegative(opening.principal());
        BigDecimal penalty = nonNegative(opening.penalty());
        BigDecimal interest = nonNegative(opening.interest());
        BigDecimal management = nonNegative(opening.management());
        BigDecimal extension = nonNegative(opening.extension());
        BigDecimal accrued = ZERO;
        BigDecimal exactAccrual = BigDecimal.ZERO;
        BigDecimal rate = opening.monthlyPenaltyRate();
        if (rate == null || rate.signum() < 0) {
            throw new IllegalArgumentException("Verified monthly penalty rate is required");
        }
        LocalDate firstPenalty = opening.maturity().plusDays(FinancialPolicy.PENALTY_GRACE_DAYS + 1L);
        List<Charge> orderedCharges = new ArrayList<>(charges);
        List<Receipt> orderedReceipts = new ArrayList<>(receipts);
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (Charge charge : orderedCharges) {
            validateDate(charge.date(), opening.asOf(), asOf);
            nonNegative(charge.interest());
            nonNegative(charge.management());
        }
        for (Receipt receipt : orderedReceipts) {
            validateDate(receipt.date(), opening.asOf(), asOf);
            if (!ids.add(receipt.id()) || nonNegative(receipt.amount()).signum() <= 0) {
                throw new IllegalArgumentException("Duplicate or non-positive receipt");
            }
        }
        orderedCharges.sort(Comparator.comparing(Charge::date));
        orderedReceipts.sort(Comparator.comparing(Receipt::date).thenComparingLong(Receipt::id));
        List<Allocation> allocations = new ArrayList<>();
        int c = 0, r = 0;
        // Opening is the verified closing position of opening.asOf().
        // Accrue against opening-of-day principal before receipts on that date.
        for (LocalDate day = opening.asOf().plusDays(1); !day.isAfter(asOf); day = day.plusDays(1)) {
            if (!day.isBefore(firstPenalty) && principal.signum() > 0) {
                exactAccrual = exactAccrual.add(principal.multiply(FinancialPolicy.dailyRateFraction(rate, day)));
                BigDecimal rounded = exactAccrual.setScale(2, RoundingMode.HALF_UP);
                penalty = penalty.add(rounded.subtract(accrued));
                accrued = rounded;
            }
            while (c < orderedCharges.size() && orderedCharges.get(c).date().equals(day)) {
                Charge charge = orderedCharges.get(c++);
                interest = interest.add(nonNegative(charge.interest()));
                management = management.add(nonNegative(charge.management()));
            }
            while (r < orderedReceipts.size() && orderedReceipts.get(r).date().equals(day)) {
                Receipt receipt = orderedReceipts.get(r++);
                var allocation = allocator.allocateHistoricalCorrection(receipt.amount(), penalty, extension, interest, management, principal);
                penalty = penalty.subtract(allocation.penalty());
                management = management.subtract(allocation.managementFee());
                interest = interest.subtract(allocation.interest());
                principal = principal.subtract(allocation.principal());
                extension = extension.subtract(allocation.extensionFee());
                allocations.add(new Allocation(receipt.id(), day, nonNegative(receipt.amount()), allocation));
            }
        }
        return new Result(principal, penalty, interest, management, extension, accrued, List.copyOf(allocations));
    }

    /**
     * Signed debits required to reclassify one previously posted receipt.
     * Cash already posted for the original receipt and is never part of this plan.
     */
    public Map<String, BigDecimal> receiptReclassification(
            Allocation original, Allocation corrected) {
        if (original.receiptId() != corrected.receiptId()
                || !original.date().equals(corrected.date())
                || nonNegative(original.amount()).compareTo(nonNegative(corrected.amount())) != 0
                || allocationTotal(original.components()).compareTo(original.amount()) != 0
                || allocationTotal(corrected.components()).compareTo(corrected.amount()) != 0) {
            throw new IllegalArgumentException("Both allocations must reconcile to the same original receipt");
        }

        var before = original.components();
        var after = corrected.components();
        return Map.of(
                "1100", before.principal().subtract(after.principal()),
                "1150", before.interest().subtract(after.interest()),
                "1160", before.managementFee().subtract(after.managementFee()),
                "1170", before.extensionFee().subtract(after.extensionFee()),
                "1175", before.penalty().subtract(after.penalty()),
                "2100", before.overpayment().subtract(after.overpayment()));
    }

    private static BigDecimal allocationTotal(PaymentAllocationService.Allocation allocation) {
        return nonNegative(allocation.principal()).add(nonNegative(allocation.interest()))
                .add(nonNegative(allocation.managementFee())).add(nonNegative(allocation.extensionFee()))
                .add(nonNegative(allocation.penalty())).add(nonNegative(allocation.overpayment()));
    }

    private static void validateDate(LocalDate date, LocalDate opening, LocalDate asOf) {
        if (date == null || !date.isAfter(opening) || date.isAfter(asOf)) {
            throw new IllegalArgumentException("Event outside verified replay interval");
        }
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("Verified non-negative amount required");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }
}
