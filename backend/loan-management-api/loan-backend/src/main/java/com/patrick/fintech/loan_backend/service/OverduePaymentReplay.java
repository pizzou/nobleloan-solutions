package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.util.FinancialPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * Read-only chronological replay of verified opening balances, contractual
 * monthly charges and immutable receipts. No repository or ledger writes.
 * Dates and charges must be verified by the caller; never infer missing
 * pre-import receipts from aggregate paid totals.
 */
public final class OverduePaymentReplay {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final PaymentAllocationService allocator = new PaymentAllocationService();

    public record Opening(LocalDate date, BigDecimal principal, BigDecimal interest,
                          BigDecimal management, BigDecimal penalty, BigDecimal extension) {}
    public record Charge(LocalDate date, BigDecimal interest, BigDecimal management) {}
    public record Receipt(long id, LocalDate date, BigDecimal amount) {}
    public record Applied(Receipt receipt, PaymentAllocationService.Allocation allocation) {}
    public record Result(BigDecimal principal, BigDecimal interest, BigDecimal management,
                         BigDecimal penalty, BigDecimal extension, List<Applied> receipts) {}

    public Result replay(Opening opening, LocalDate maturity, BigDecimal monthlyPenaltyRate,
                         LocalDate asOf, List<Charge> charges, List<Receipt> receipts) {
        if (opening == null || opening.date() == null || maturity == null || asOf == null
                || asOf.isBefore(opening.date()) || charges == null || receipts == null)
            throw new IllegalArgumentException("Verified opening, maturity, dates and events are required");
        BigDecimal principal = nonnegative(opening.principal());
        BigDecimal interest = nonnegative(opening.interest());
        BigDecimal management = nonnegative(opening.management());
        BigDecimal penalty = nonnegative(opening.penalty());
        BigDecimal extension = nonnegative(opening.extension());
        BigDecimal rate = nonnegative(monthlyPenaltyRate);
        List<Charge> orderedCharges = new ArrayList<>(charges);
        List<Receipt> orderedReceipts = new ArrayList<>(receipts);
        var ids = new HashSet<Long>();
        for (Charge c : orderedCharges) {
            if (c == null || c.date() == null || !c.date().isAfter(opening.date()) || c.date().isAfter(asOf))
                throw new IllegalArgumentException("Charge outside replay interval");
            nonnegative(c.interest()); nonnegative(c.management());
        }
        for (Receipt r : orderedReceipts) {
            if (r == null || r.id() <= 0 || !ids.add(r.id()) || r.date() == null
                    || !r.date().isAfter(opening.date()) || r.date().isAfter(asOf)
                    || nonnegative(r.amount()).signum() <= 0)
                throw new IllegalArgumentException("Invalid or duplicate receipt");
        }
        orderedCharges.sort(Comparator.comparing(Charge::date));
        orderedReceipts.sort(Comparator.comparing(Receipt::date).thenComparingLong(Receipt::id));
        List<Applied> applied = new ArrayList<>();
        int ci = 0, ri = 0;
        LocalDate firstPenalty = maturity.plusDays(FinancialPolicy.PENALTY_GRACE_DAYS + 1L);
        BigDecimal accruedExact = BigDecimal.ZERO;
        BigDecimal accruedRounded = ZERO;
        for (LocalDate date = opening.date().plusDays(1); !date.isAfter(asOf); date = date.plusDays(1)) {
            while (ci < orderedCharges.size() && orderedCharges.get(ci).date().equals(date)) {
                Charge c = orderedCharges.get(ci++);
                interest = interest.add(nonnegative(c.interest()));
                management = management.add(nonnegative(c.management()));
            }
            // Daily accrual uses principal before today's receipts; cumulative
            // rounding avoids multiplying a rounded daily charge by many days.
            if (!date.isBefore(firstPenalty) && principal.signum() > 0) {
                accruedExact = accruedExact.add(principal.multiply(rate)
                        .divide(BigDecimal.valueOf(100L * date.lengthOfMonth()), 20, RoundingMode.HALF_UP));
                BigDecimal rounded = accruedExact.setScale(2, RoundingMode.HALF_UP);
                penalty = penalty.add(rounded.subtract(accruedRounded));
                accruedRounded = rounded;
            }
            while (ri < orderedReceipts.size() && orderedReceipts.get(ri).date().equals(date)) {
                Receipt receipt = orderedReceipts.get(ri++);
                var a = date.isAfter(maturity)
                        ? allocator.allocateOverdue(receipt.amount(), penalty, extension, interest, management, principal)
                        : allocator.allocate(receipt.amount(), penalty, extension, interest, management, principal);
                principal = principal.subtract(a.principal());
                interest = interest.subtract(a.interest());
                management = management.subtract(a.managementFee());
                penalty = penalty.subtract(a.penalty());
                extension = extension.subtract(a.extensionFee());
                applied.add(new Applied(receipt, a));
            }
        }
        return new Result(principal, interest, management, penalty, extension, List.copyOf(applied));
    }

    private static BigDecimal nonnegative(BigDecimal value) {
        if (value == null || value.signum() < 0)
            throw new IllegalArgumentException("Verified nonnegative amount required");
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
