package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.model.PaymentTransaction;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import com.patrick.fintech.loan_backend.repository.PaymentTransactionRepository;
import com.patrick.fintech.loan_backend.util.FinancialPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Common historical overdue-penalty basis used by payment posting and EOD.
 * Scheduled installment principal is not a cash repayment: only actual
 * posted receipts are used to reconstruct prior outstanding balances.
 */
@Service
@RequiredArgsConstructor
public class HistoricalPenaltyBalanceService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentRepository paymentRepository;

    public BigDecimal assessedToDate(
            Loan loan, BigDecimal currentPrincipal,
            LocalDate firstChargeableDate, LocalDate asOf) {

        if (loan == null || loan.getId() == null || currentPrincipal == null
                || firstChargeableDate == null || asOf == null
                || firstChargeableDate.isAfter(asOf)
                || currentPrincipal.signum() <= 0) {
            return ZERO;
        }

        List<PaymentTransaction> posted = transactionRepository
                .findByLoanIdOrderByCreatedAtDesc(loan.getId()).stream()
                .filter(t -> t != null && t.getCreatedAt() != null
                        && t.getStatus() == PaymentTransaction.TransactionStatus.POSTED
                        && !Boolean.TRUE.equals(t.getReversed()))
                .toList();

        // Older imported portfolios can have a payment record without an
        // immutable transaction ledger. Only actual paid rows may be used.
        List<Payment> historicalRows = posted.isEmpty()
                ? paymentRepository.findByLoanId(loan.getId()).stream()
                        .filter(p -> p != null && p.getPaidDate() != null
                                && money(p.getAmountPaidDecimal()).signum() > 0)
                        .toList()
                : List.of();

        BigDecimal initial = money(currentPrincipal);
        BigDecimal ceiling = loan.getAmountDecimal() == null
                ? initial : money(loan.getAmountDecimal()).max(initial);

        return FinancialPolicy.historicalDailyPenalty(
                initial, firstChargeableDate, asOf,
                loan.getPenaltyRateDecimal(),
                day -> {
                    BigDecimal balance = initial;
                    if (!posted.isEmpty()) {
                        for (PaymentTransaction receipt : posted) {
                            // Payment is posted after the day's overdue penalty is settled.
                            // Include the same payment date in prior-day principal.
                            if (!receipt.getCreatedAt().toLocalDate().isBefore(day)) {
                                balance = balance.add(money(receipt.getPrincipalComponent()));
                            }
                        }
                    } else {
                        for (Payment row : historicalRows) {
                            if (!row.getPaidDate().isBefore(day)) {
                                balance = balance.add(money(row.getPrincipalComponentDecimal()));
                            }
                        }
                    }
                    return money(balance.min(ceiling).max(ZERO));
                });
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? ZERO : value)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
