package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.DashboardStats;
import com.patrick.fintech.loan_backend.dto.LoanResponse;
import com.patrick.fintech.loan_backend.mapper.ResponseDtoMapper;
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

        private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(
                        2,
                        RoundingMode.HALF_UP);

        private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

        // ================================================================
        // DASHBOARD STATISTICS
        // ================================================================

        public DashboardStats getStats(
                        Long orgId) {

                if (orgId == null) {

                        throw new IllegalArgumentException(
                                        "Organization ID is required");
                }

                LocalDate today = LocalDate.now();

                LocalDate firstOfMonth = today.withDayOfMonth(1);

                // ============================================================
                // BASIC COUNTS
                // ============================================================

                long totalLoans = loanRepository.countByOrganization_Id(
                                orgId);

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
                //
                // Use the repository method already used by the application
                // instead of introducing another BorrowerRepository method.
                // ============================================================

                long totalBorrowers = borrowerRepository
                                .findByOrganization_Id(
                                                orgId)
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

                long overdueLoans = overduePayments
                                .stream()
                                .filter(
                                                payment -> payment != null
                                                                && payment.getLoan() != null)
                                .map(
                                                payment -> payment.getLoan().getId())
                                .filter(
                                                loanId -> loanId != null)
                                .distinct()
                                .count();

                long latePaymentsCount = overduePayments.size();

                // ============================================================
                // LOAD ORGANIZATION LOANS
                // ============================================================

                List<Loan> loans = loanRepository.findByOrganization_Id(
                                orgId);

                if (loans == null) {
                        loans = List.of();
                }

                // ============================================================
                // PORTFOLIO TOTALS
                // ============================================================

                BigDecimal totalDisbursed = ZERO;

                BigDecimal totalOutstanding = ZERO;

                BigDecimal activePortfolioPrincipal = ZERO;

                BigDecimal atRiskPrincipal = ZERO;

                for (Loan loan : loans) {

                        if (loan == null) {
                                continue;
                        }

                        LoanStatus status = loan.getStatus();

                        BigDecimal amount = money(
                                        loan.getAmountDecimal());

                        BigDecimal disbursedAmount = money(
                                        loan.getDisbursedAmountDecimal());

                        BigDecimal outstanding = money(
                                        loan.getOutstandingBalanceDecimal());

                        /*
                         * A loan enters the financial portfolio only after an
                         * actual disbursement timestamp exists.
                         */
                        boolean disbursed = loan.getDisbursedAt() != null;

                        if (disbursed) {

                                totalDisbursed = money(
                                                totalDisbursed.add(
                                                                disbursedAmount));
                        }

                        /*
                         * Current outstanding portfolio.
                         *
                         * Only genuinely disbursed active/overdue/restructured
                         * loans contribute to outstanding principal.
                         */
                        boolean outstandingLoan = disbursed
                                        && (status == LoanStatus.ACTIVE
                                                        || status == LoanStatus.OVERDUE
                                                        || status == LoanStatus.DEFAULTED
                                                        || status == LoanStatus.RESTRUCTURED);

                        if (outstandingLoan) {

                                totalOutstanding = money(
                                                totalOutstanding.add(
                                                                outstanding));

                                activePortfolioPrincipal = money(
                                                activePortfolioPrincipal.add(
                                                                outstanding));
                        }

                        /*
                         * Portfolio at risk is the outstanding amount of loans
                         * that are overdue, defaulted, or restructured.
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

                List<Payment> organizationPayments = paymentRepository.findByLoan_Organization_Id(
                                orgId);

                if (organizationPayments == null) {
                        organizationPayments = List.of();
                }

                BigDecimal totalCollected = ZERO;

                BigDecimal collectedThisMonth = ZERO;

                /*
                 * LEGACY PORTFOLIO COLLECTIONS
                 *
                 * Imported loans deliberately do not receive fabricated historical
                 * Payment rows. Their historical collections live on the Loan opening
                 * fields. Therefore the institution-wide collection KPI must combine:
                 *
                 * 1) actual Payment rows for system-originated/current activity
                 * 2) historical component totals on imported loans
                 *
                 * We only add the historical component totals for imported loans.
                 * This prevents double-counting current Payment rows.
                 */
                BigDecimal legacyHistoricalCollected = ZERO;

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
                                        && !paidDate.isBefore(
                                                        firstOfMonth)
                                        && !paidDate.isAfter(
                                                        today)) {

                                collectedThisMonth = money(
                                                collectedThisMonth.add(
                                                                paymentAmount));
                        }
                }

                /*
                 * Historical imported collection basis:
                 *
                 * principal paid
                 * + interest paid
                 * + management fee paid
                 * + extension fee paid
                 * + penalties paid
                 * + processing fee paid
                 *
                 * Processing fee is a one-time cash collection at disbursement.
                 * It is kept in the institution-wide collected KPI, but is not
                 * treated as a repayment Payment row.
                 */
                for (Loan loan : loans) {
                        if (loan == null || !Boolean.TRUE.equals(loan.getImported())) {
                                continue;
                        }

                        BigDecimal historical = money(loan.getPrincipalPaidDecimal())
                                        .add(money(loan.getInterestPaidDecimal()))
                                        .add(money(loan.getManagementFeePaidDecimal()))
                                        .add(money(loan.getExtensionFeePaidDecimal()))
                                        .add(money(loan.getPenaltiesPaidDecimal()))
                                        .add(money(loan.getProcessingFeePaidDecimal()));

                        legacyHistoricalCollected = money(legacyHistoricalCollected.add(historical));
                }

                totalCollected = money(totalCollected.add(legacyHistoricalCollected));

                // Historical legacy collections have no reliable current-period
                // payment date. They must NEVER be added to collectedThisMonth.

                // ============================================================
                // PORTFOLIO AT RISK %
                // ============================================================

                BigDecimal portfolioAtRiskPct = ZERO;

                if (activePortfolioPrincipal.compareTo(
                                ZERO) > 0) {

                        portfolioAtRiskPct = money(
                                        atRiskPrincipal
                                                        .multiply(
                                                                        ONE_HUNDRED)
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
                //
                // Avoid requiring another repository signature. We already
                // have the organization's loans loaded above.
                // ============================================================

                List<LoanResponse> recentLoans = loans
                                .stream()
                                .filter(
                                                loan -> loan != null
                                                                && loan.getCreatedAt() != null)
                                .sorted(
                                                Comparator.comparing(
                                                                Loan::getCreatedAt,
                                                                Comparator.nullsLast(
                                                                                Comparator.reverseOrder())))
                                .limit(8)
                                .map(ResponseDtoMapper::loan)
                                .toList();

                // ============================================================
                // LOG
                // ============================================================

                log.debug(
                                "Dashboard calculated. " +
                                                "orgId={}, totalLoans={}, activeLoans={}, " +
                                                "pendingLoans={}, overdueLoans={}, " +
                                                "defaultedLoans={}, totalDisbursed={}, " +
                                                "totalCollected={}, legacyHistoricalCollected={}, outstanding={}, " +
                                                "collectedThisMonth={}, PAR={}",
                                orgId,
                                totalLoans,
                                activeLoans,
                                pendingLoans,
                                overdueLoans,
                                defaultedLoans,
                                totalDisbursed,
                                totalCollected,
                                legacyHistoricalCollected,
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
                                                totalDisbursed.doubleValue())

                                .totalCollected(
                                                totalCollected.doubleValue())

                                .outstandingBalance(
                                                totalOutstanding.doubleValue())

                                .collectedThisMonth(
                                                collectedThisMonth.doubleValue())

                                .latePaymentsCount(
                                                latePaymentsCount)

                                .portfolioAtRiskPct(
                                                portfolioAtRiskPct.doubleValue())

                                .recentLoans(
                                                recentLoans)

                                .build();
        }

        // ================================================================
        // MONEY
        // ================================================================

        private BigDecimal money(
                        BigDecimal value) {

                if (value == null) {

                        return ZERO;
                }

                return value.setScale(
                                2,
                                RoundingMode.HALF_UP);
        }
}