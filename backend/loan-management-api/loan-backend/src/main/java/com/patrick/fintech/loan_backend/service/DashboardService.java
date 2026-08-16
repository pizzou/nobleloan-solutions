package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.DashboardStats;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
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
        private final OrganizationRepository organizationRepository;

        private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

        // ================================================================
        // DASHBOARD STATISTICS
        // ================================================================

        public DashboardStats getStats(Long orgId) {

                if (orgId == null) {
                        throw new IllegalArgumentException(
                                        "Organization ID is required");
                }

                /*
                 * IMPORTANT:
                 *
                 * LoanRepository.sumGrossDisbursedPrincipal(...)
                 * expects Organization, not Long.
                 *
                 * Load the managed organization once and use the same organization
                 * throughout this dashboard calculation.
                 */
                Organization organization = organizationRepository.findById(orgId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Organization not found: " + orgId));

                LocalDate today = LocalDate.now();

                LocalDate firstOfMonth = today.withDayOfMonth(1);

                // ============================================================
                // BASIC COUNTS
                // ============================================================

                long totalLoans = loanRepository.countByOrganization_Id(orgId);

                long activeLoans = loanRepository.countByOrganization_IdAndStatus(
                                orgId,
                                LoanStatus.ACTIVE);

                long pendingLoans = loanRepository.countByOrganization_IdAndStatus(
                                orgId,
                                LoanStatus.PENDING);

                long completedLoans = loanRepository.countByOrganization_IdAndStatus(
                                orgId,
                                LoanStatus.PAID);

                long defaultedLoans = loanRepository.countByOrganization_IdAndStatus(
                                orgId,
                                LoanStatus.DEFAULTED);

                // ============================================================
                // BORROWERS
                // ============================================================

                long totalBorrowers = borrowerRepository
                                .findByOrganization_Id(orgId)
                                .size();

                // ============================================================
                // OVERDUE PAYMENTS
                // ============================================================

                List<Payment> overduePayments = paymentRepository
                                .findByOrganization_IdAndPaidFalseAndDueDateBefore(
                                                orgId,
                                                today);

                if (overduePayments == null) {
                        overduePayments = List.of();
                }

                long overdueLoans = overduePayments.stream()
                                .filter(payment -> payment != null
                                                && payment.getLoan() != null)
                                .map(payment -> payment.getLoan().getId())
                                .filter(id -> id != null)
                                .distinct()
                                .count();

                long latePaymentsCount = overduePayments.size();

                // ============================================================
                // ORGANIZATION LOANS
                // ============================================================

                List<Loan> loans = loanRepository.findByOrganization_Id(orgId);

                if (loans == null) {
                        loans = List.of();
                }

                // ============================================================
                // TOTAL GROSS DISBURSED
                // ============================================================

                /*
                 * IMPORTANT:
                 *
                 * This repository method expects Organization.
                 *
                 * Do NOT use:
                 *
                 * sumGrossDisbursedPrincipal(orgId)
                 *
                 * Use:
                 *
                 * sumGrossDisbursedPrincipal(organization)
                 *
                 * The repository remains the source of truth for gross
                 * lifetime disbursement.
                 */
                BigDecimal totalDisbursed = money(
                                loanRepository.sumGrossDisbursedPrincipal(
                                                organization));

                // ============================================================
                // PORTFOLIO TOTALS
                // ============================================================

                BigDecimal totalOutstanding = ZERO;

                BigDecimal activePortfolioPrincipal = ZERO;

                BigDecimal atRiskPrincipal = ZERO;

                for (Loan loan : loans) {

                        if (loan == null) {
                                continue;
                        }

                        LoanStatus status = loan.getStatus();

                        BigDecimal outstanding = money(
                                        loan.getOutstandingBalanceDecimal());

                        /*
                         * Current outstanding portfolio.
                         *
                         * ACTIVE
                         * OVERDUE
                         * RESTRUCTURED
                         */
                        boolean outstandingLoan = status == LoanStatus.ACTIVE
                                        || status == LoanStatus.OVERDUE
                                        || status == LoanStatus.RESTRUCTURED;

                        if (outstandingLoan) {

                                totalOutstanding = money(
                                                totalOutstanding.add(
                                                                outstanding));

                                activePortfolioPrincipal = money(
                                                activePortfolioPrincipal.add(
                                                                outstanding));
                        }

                        /*
                         * Portfolio at risk.
                         *
                         * DEFAULTED is included because the principal remains
                         * exposed even though it is no longer an ordinary active loan.
                         */
                        boolean atRisk = status == LoanStatus.OVERDUE
                                        || status == LoanStatus.DEFAULTED
                                        || status == LoanStatus.RESTRUCTURED;

                        if (atRisk) {

                                atRiskPrincipal = money(
                                                atRiskPrincipal.add(
                                                                outstanding));
                        }
                }

                // ============================================================
                // PAYMENT COLLECTIONS
                // ============================================================

                List<Payment> organizationPayments = paymentRepository
                                .findByLoan_Organization_Id(orgId);

                if (organizationPayments == null) {
                        organizationPayments = List.of();
                }

                BigDecimal totalCollected = ZERO;

                BigDecimal collectedThisMonth = ZERO;

                for (Payment payment : organizationPayments) {

                        if (payment == null) {
                                continue;
                        }

                        if (!Boolean.TRUE.equals(
                                        payment.getPaid())) {
                                continue;
                        }

                        BigDecimal paymentAmount = money(
                                        payment.getAmountPaidDecimal());

                        totalCollected = money(
                                        totalCollected.add(
                                                        paymentAmount));

                        LocalDate paidDate = payment.getPaidDate();

                        if (paidDate != null
                                        && !paidDate.isBefore(firstOfMonth)
                                        && !paidDate.isAfter(today)) {

                                collectedThisMonth = money(
                                                collectedThisMonth.add(
                                                                paymentAmount));
                        }
                }

                // ============================================================
                // PORTFOLIO AT RISK %
                // ============================================================

                BigDecimal portfolioAtRiskPct = ZERO;

                if (activePortfolioPrincipal.compareTo(
                                ZERO) > 0) {

                        portfolioAtRiskPct = money(
                                        atRiskPrincipal
                                                        .multiply(ONE_HUNDRED)
                                                        .divide(
                                                                        activePortfolioPrincipal,
                                                                        16,
                                                                        RoundingMode.HALF_UP));

                        if (portfolioAtRiskPct.compareTo(
                                        ONE_HUNDRED) > 0) {

                                portfolioAtRiskPct = ONE_HUNDRED.setScale(
                                                2,
                                                RoundingMode.HALF_UP);
                        }
                }

                // ============================================================
                // RECENT LOANS
                // ============================================================

                List<Loan> recentLoans = loans.stream()
                                .filter(loan -> loan != null
                                                && loan.getCreatedAt() != null)
                                .sorted(
                                                Comparator.comparing(
                                                                Loan::getCreatedAt,
                                                                Comparator.nullsLast(
                                                                                Comparator.reverseOrder())))
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
                                portfolioAtRiskPct);

                // ============================================================
                // RESPONSE
                // ============================================================

                return DashboardStats.builder()

                                .totalLoans(
                                                totalLoans)

                                .activeLoans(
                                                activeLoans)

                                .pendingLoans(
                                                pendingLoans)

                                .completedLoans(
                                                completedLoans)

                                .defaultedLoans(
                                                defaultedLoans)

                                .overdueLoans(
                                                overdueLoans)

                                .totalBorrowers(
                                                totalBorrowers)

                                .totalDisbursed(
                                                totalDisbursed)

                                .totalCollected(
                                                totalCollected)

                                .outstandingBalance(
                                                totalOutstanding)

                                .collectedThisMonth(
                                                collectedThisMonth)

                                .latePaymentsCount(
                                                latePaymentsCount)

                                .portfolioAtRiskPct(
                                                portfolioAtRiskPct)

                                .recentLoans(
                                                recentLoans)

                                .build();
        }

        // ================================================================
        // MONEY
        // ================================================================

        private BigDecimal money(BigDecimal value) {

                if (value == null) {
                        return ZERO;
                }

                return value.setScale(
                                2,
                                RoundingMode.HALF_UP);
        }
}