package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.DashboardStats;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DashboardService {

    private final LoanRepository loanRepository;
    private final PaymentRepository paymentRepository;
    private final BorrowerRepository borrowerRepository;

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(
                    2,
                    RoundingMode.HALF_UP
            );

    private static final BigDecimal ONE_HUNDRED =
            new BigDecimal("100.00");

    // ================================================================
    // DASHBOARD STATISTICS
    // ================================================================

    public DashboardStats getStats(
            Long orgId
    ) {

        if (orgId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }

        LocalDate today =
                LocalDate.now();

        LocalDate firstOfMonth =
                today.withDayOfMonth(1);

        // ============================================================
        // BASIC COUNTS
        // ============================================================

        long totalLoans =
                loanRepository.countByOrganization_Id(
                        orgId
                );

        long activeLoans =
                loanRepository.countByOrganization_IdAndStatus(
                        orgId,
                        LoanStatus.ACTIVE
                );

        long pendingLoans =
                loanRepository.countByOrganization_IdAndStatus(
                        orgId,
                        LoanStatus.PENDING
                );

        long completedLoans =
                loanRepository.countByOrganization_IdAndStatus(
                        orgId,
                        LoanStatus.PAID
                );

        long defaultedLoans =
                loanRepository.countByOrganization_IdAndStatus(
                        orgId,
                        LoanStatus.DEFAULTED
                );

        // ============================================================
        // BORROWERS
        //
        // Use the repository method already used by the application
        // instead of introducing another BorrowerRepository method.
        // ============================================================

        long totalBorrowers =
                borrowerRepository
                        .findByOrganization_Id(
                                orgId
                        )
                        .size();

        // ============================================================
        // OVERDUE PAYMENTS
        // ============================================================

        List<Payment> overduePayments =
                paymentRepository
                        .findByOrganization_IdAndPaidFalseAndDueDateBefore(
                                orgId,
                                today
                        );

        if (overduePayments == null) {
            overduePayments = List.of();
        }

        long overdueLoans =
                overduePayments
                        .stream()
                        .filter(
                                payment ->
                                        payment != null
                                                && payment.getLoan() != null
                        )
                        .map(
                                payment ->
                                        payment.getLoan().getId()
                        )
                        .filter(
                                loanId ->
                                        loanId != null
                        )
                        .distinct()
                        .count();

        long latePaymentsCount =
                overduePayments.size();

        // ============================================================
        // LOAD ORGANIZATION LOANS
        // ============================================================

        List<Loan> loans =
                loanRepository.findByOrganization_Id(
                        orgId
                );

        if (loans == null) {
            loans = List.of();
        }

        // ============================================================
        // PORTFOLIO TOTALS
        // ============================================================

        BigDecimal totalDisbursed =
                ZERO;

        BigDecimal totalOutstanding =
                ZERO;

        BigDecimal activePortfolioPrincipal =
                ZERO;

        BigDecimal atRiskPrincipal =
                ZERO;

        for (Loan loan : loans) {

            if (loan == null) {
                continue;
            }

            LoanStatus status =
                    loan.getStatus();

            BigDecimal amount =
                    money(
                            loan.getAmountDecimal()
                    );

            BigDecimal outstanding =
                    money(
                            loan.getOutstandingBalanceDecimal()
                    );

            /*
             * A loan is considered disbursed once it has passed
             * the pending stage.
             */
            boolean disbursed =
                    status == LoanStatus.ACTIVE
                            || status == LoanStatus.OVERDUE
                            || status == LoanStatus.PAID
                            || status == LoanStatus.CLOSED
                            || status == LoanStatus.DEFAULTED
                            || status == LoanStatus.WRITTEN_OFF
                            || status == LoanStatus.RESTRUCTURED;

            if (disbursed) {

                totalDisbursed =
                        money(
                                totalDisbursed.add(
                                        amount
                                )
                        );
            }

            /*
             * Current outstanding portfolio.
             *
             * Restructured loans remain part of the outstanding
             * portfolio until paid/closed/written off.
             */
            boolean outstandingLoan =
                    status == LoanStatus.ACTIVE
                            || status == LoanStatus.OVERDUE
                            || status == LoanStatus.RESTRUCTURED;

            if (outstandingLoan) {

                totalOutstanding =
                        money(
                                totalOutstanding.add(
                                        outstanding
                                )
                        );

                activePortfolioPrincipal =
                        money(
                                activePortfolioPrincipal.add(
                                        outstanding
                                )
                        );
            }

            /*
             * Portfolio at risk is the outstanding amount of loans
             * that are overdue, defaulted, or restructured.
             */
            boolean atRisk =
                    status == LoanStatus.OVERDUE
                            || status == LoanStatus.DEFAULTED
                            || status == LoanStatus.RESTRUCTURED;

            if (atRisk) {

                atRiskPrincipal =
                        money(
                                atRiskPrincipal.add(
                                        outstanding
                                )
                        );
            }
        }

        // ============================================================
        // PAYMENT COLLECTIONS
        // ============================================================

        List<Payment> organizationPayments =
                paymentRepository.findByLoan_Organization_Id(
                        orgId
                );

        if (organizationPayments == null) {
            organizationPayments = List.of();
        }

        BigDecimal totalCollected =
                ZERO;

        BigDecimal collectedThisMonth =
                ZERO;

        for (
                Payment payment :
                organizationPayments
        ) {

            if (payment == null) {
                continue;
            }

            if (!Boolean.TRUE.equals(
                    payment.getPaid()
            )) {
                continue;
            }

            BigDecimal paymentAmount =
                    money(
                            payment.getAmountPaidDecimal()
                    );

            totalCollected =
                    money(
                            totalCollected.add(
                                    paymentAmount
                            )
                    );

            LocalDate paidDate =
                    payment.getPaidDate();

            if (
                    paidDate != null
                            && !paidDate.isBefore(
                            firstOfMonth
                    )
                            && !paidDate.isAfter(
                            today
                    )
            ) {

                collectedThisMonth =
                        money(
                                collectedThisMonth.add(
                                        paymentAmount
                                )
                );
            }
        }

        // ============================================================
        // PORTFOLIO AT RISK %
        // ============================================================

        BigDecimal portfolioAtRiskPct =
                ZERO;

        if (
                activePortfolioPrincipal.compareTo(
                        ZERO
                ) > 0
        ) {

            portfolioAtRiskPct =
                    money(
                            atRiskPrincipal
                                    .multiply(
                                            ONE_HUNDRED
                                    )
                                    .divide(
                                            activePortfolioPrincipal,
                                            16,
                                            RoundingMode.HALF_UP
                                    )
                    );

            if (
                    portfolioAtRiskPct.compareTo(
                            ONE_HUNDRED
                    ) > 0
            ) {

                portfolioAtRiskPct =
                        ONE_HUNDRED.setScale(
                                2,
                                RoundingMode.HALF_UP
                        );
            }
        }

        // ============================================================
        // RECENT LOANS
        //
        // Avoid requiring another repository signature. We already
        // have the organization's loans loaded above.
        // ============================================================

        List<Loan> recentLoans =
                loans
                        .stream()
                        .filter(
                                loan ->
                                        loan != null
                                                && loan.getCreatedAt() != null
                        )
                        .sorted(
                                Comparator.comparing(
                                        Loan::getCreatedAt,
                                        Comparator.nullsLast(
                                                Comparator.reverseOrder()
                                        )
                                )
                        )
                        .limit(8)
                        .toList();

        // ============================================================
        // LOG
        // ============================================================

        log.debug(
                "Dashboard calculated. " +
                        "orgId={}, totalLoans={}, activeLoans={}, " +
                        "pendingLoans={}, overdueLoans={}, " +
                        "defaultedLoans={}, totalDisbursed={}, " +
                        "totalCollected={}, outstanding={}, " +
                        "collectedThisMonth={}, PAR={}",
                orgId,
                totalLoans,
                activeLoans,
                pendingLoans,
                overdueLoans,
                defaultedLoans,
                totalDisbursed,
                totalCollected,
                totalOutstanding,
                collectedThisMonth,
                portfolioAtRiskPct
        );

        // ============================================================
        // RESPONSE
        // ============================================================

        return DashboardStats.builder()

                .totalLoans(
                        totalLoans
                )

                .activeLoans(
                        activeLoans
                )

                .pendingLoans(
                        pendingLoans
                )

                .completedLoans(
                        completedLoans
                )

                .defaultedLoans(
                        defaultedLoans
                )

                .overdueLoans(
                        overdueLoans
                )

                .totalBorrowers(
                        totalBorrowers
                )

                .totalDisbursed(
                        totalDisbursed.doubleValue()
                )

                .totalCollected(
                        totalCollected.doubleValue()
                )

                .outstandingBalance(
                        totalOutstanding.doubleValue()
                )

                .collectedThisMonth(
                        collectedThisMonth.doubleValue()
                )

                .latePaymentsCount(
                        latePaymentsCount
                )

                .portfolioAtRiskPct(
                        portfolioAtRiskPct.doubleValue()
                )

                .recentLoans(
                        recentLoans
                )

                .build();
    }

    // ================================================================
    // MONEY
    // ================================================================

    private BigDecimal money(
            BigDecimal value
    ) {

        if (value == null) {

            return ZERO;
        }

        return value.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }
}