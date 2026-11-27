package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.util.FinancialPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Deterministic, side-effect-free financial projection for an amendment.
 * Recording an approval is separate from applying this plan to live ledgers.
 * Journal posting must verify the existing GL and payment activity first.
 */
public final class LoanAmendmentReconciliationPlanner {
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private LoanAmendmentReconciliationPlanner() {}

    public record ContractTotals(BigDecimal principal, BigDecimal interest,
                                 BigDecimal managementFee, BigDecimal repayable) {}

    public record Plan(ContractTotals contract,
                       LoanAmendmentAllocationCalculator.Allocation allocation,
                       BigDecimal principalReceivableChange,
                       BigDecimal interestReceivableChange,
                       BigDecimal managementReceivableChange) {}

    public static ContractTotals contract(BigDecimal principal, int months,
                                          BigDecimal monthlyInterestRate,
                                          BigDecimal monthlyManagementRate) {
        if (principal == null || principal.signum() <= 0 || months < 1 || months > 120
                || monthlyInterestRate == null || monthlyManagementRate == null
                || monthlyInterestRate.signum() < 0 || monthlyManagementRate.signum() < 0
                || monthlyInterestRate.compareTo(new BigDecimal("5")) > 0
                || monthlyManagementRate.compareTo(new BigDecimal("5")) > 0) {
            throw new IllegalArgumentException("Invalid contractual principal, duration or negotiated rates");
        }

        BigDecimal balance = money(principal);
        BigDecimal interest = ZERO;
        BigDecimal management = ZERO;
        for (int installment = 1; installment <= months; installment++) {
            FinancialPolicy.ScheduleLine line = FinancialPolicy.contractualScheduleLine(
                    balance, months - installment + 1, monthlyInterestRate, monthlyManagementRate);
            interest = money(interest.add(money(line.interest())));
            management = money(management.add(money(line.managementFee())));
            balance = money(line.remainingBalance());
        }
        if (balance.signum() != 0) {
            throw new IllegalStateException("Contract schedule does not amortize the principal");
        }
        return new ContractTotals(money(principal), interest, management,
                money(principal.add(interest).add(management)));
    }

    public static Plan plan(ContractTotals target, BigDecimal verifiedRepaymentsForContract,
                            BigDecimal oldPrincipalOutstanding,
                            BigDecimal oldInterestOutstanding,
                            BigDecimal oldManagementOutstanding) {
        if (target == null) throw new IllegalArgumentException("Target contract is required");
        var allocation = LoanAmendmentAllocationCalculator.allocate(
                target.principal(), target.interest(), target.managementFee(),
                verifiedRepaymentsForContract);
        BigDecimal oldPrincipal = nonnegative(oldPrincipalOutstanding);
        BigDecimal oldInterest = nonnegative(oldInterestOutstanding);
        BigDecimal oldManagement = nonnegative(oldManagementOutstanding);
        return new Plan(target, allocation,
                money(allocation.principalOutstanding().subtract(oldPrincipal)),
                money(allocation.interestOutstanding().subtract(oldInterest)),
                money(allocation.managementFeeOutstanding().subtract(oldManagement)));
    }

    private static BigDecimal nonnegative(BigDecimal value) {
        BigDecimal normalized = money(value);
        if (normalized.signum() < 0) throw new IllegalArgumentException("Existing receivable cannot be negative");
        return normalized;
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) throw new IllegalArgumentException("Money value is required");
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
