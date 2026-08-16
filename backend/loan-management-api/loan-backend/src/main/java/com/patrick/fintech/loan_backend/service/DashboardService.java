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

        private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

        /**
         * Returns dashboard data as detached DTOs.
         *
         * IMPORTANT:
         * No JPA entity is allowed to escape this service.
         */
        public DashboardStats getStats(Long organizationId) {

                if (organizationId == null) {
                        throw new IllegalArgumentException(
                                        "Organization ID is required");
                }

                LocalDate today = LocalDate.now();
                LocalDate firstOfMonth = today.withDayOfMonth(1);

                /*
                 * ------------------------------------------------------------
                 * COUNTS
                 * ------------------------------------------------------------
                 */

                long totalLoans = loanRepository.countByOrganization_Id(
                                organizationId);

                long activeLoans = loanRepository.countByOrganization_IdAndStatus(
                                organizationId,
                                LoanStatus.ACTIVE);

                long pendingLoans = loanRepository.countByOrganization_IdAndStatus(
                                organizationId,
                                LoanStatus.PENDING);

                long completedLoans = loanRepository.countByOrganization_IdAndStatus(
                                organizationId,
                                LoanStatus.PAID);

                long defaultedLoans = loanRepository.countByOrganization_IdAndStatus(
                                organizationId,
                                LoanStatus.DEFAULTED);

                long totalBorrowers = borrowerRepository
                                .findByOrganization_Id(organizationId)
                                .size();

                /*
                 * ------------------------------------------------------------
                 * OVERDUE PAYMENTS
                 * ------------------------------------------------------------
                 */

                List<Payment> overduePayments = paymentRepository
                                .findByOrganization_IdAndPaidFalseAndDueDateBefore(
                                                organizationId,
                                                today);

                if (overduePayments == null) {
                        overduePayments = List.of();
                }

                long overdueLoans = overduePayments
                                .stream()
                                .filter(payment -> payment != null &&
                                                payment.getLoan() != null)
                                .map(payment -> payment.getLoan().getId())
                                .filter(id -> id != null)
                                .distinct()
                                .count();

                long latePaymentsCount = overduePayments.size();

                /*
                 * ------------------------------------------------------------
                 * ORGANIZATION LOANS
                 * ------------------------------------------------------------
                 *
                 * The entities are used only while the transaction is open.
                 */

                List<Loan> loans = loanRepository.findByOrganization_Id(
                                organizationId);

                if (loans == null) {
                        loans = List.of();
                }

                /*
                 * ------------------------------------------------------------
                 * PORTFOLIO TOTALS
                 * ------------------------------------------------------------
                 */

                BigDecimal totalDisbursed = ZERO;
                BigDecimal totalOutstanding = ZERO;
                BigDecimal activePortfolioPrincipal = ZERO;
                BigDecimal atRiskPrincipal = ZERO;

                for (Loan loan : loans) {

                        if (loan == null) {
                                continue;
                        }

                        LoanStatus status = loan.getStatus();

                        BigDecimal amount = money(loan.getAmountDecimal());

                        BigDecimal outstanding = money(loan.getOutstandingBalanceDecimal());

                        boolean disbursed = status == LoanStatus.ACTIVE
                                        || status == LoanStatus.OVERDUE
                                        || status == LoanStatus.PAID
                                        || status == LoanStatus.CLOSED
                                        || status == LoanStatus.DEFAULTED
                                        || status == LoanStatus.WRITTEN_OFF
                                        || status == LoanStatus.RESTRUCTURED;

                        if (disbursed) {
                                totalDisbursed = money(totalDisbursed.add(amount));
                        }

                        boolean outstandingLoan = status == LoanStatus.ACTIVE
                                        || status == LoanStatus.OVERDUE
                                        || status == LoanStatus.RESTRUCTURED;

                        if (outstandingLoan) {

                                totalOutstanding = money(totalOutstanding.add(outstanding));

                                activePortfolioPrincipal = money(
                                                activePortfolioPrincipal
                                                                .add(outstanding));
                        }

                        boolean atRisk = status == LoanStatus.OVERDUE
                                        || status == LoanStatus.DEFAULTED
                                        || status == LoanStatus.RESTRUCTURED;

                        if (atRisk) {

                                atRiskPrincipal = money(
                                                atRiskPrincipal
                                                                .add(outstanding));
                        }
                }

                /*
                 * ------------------------------------------------------------
                 * PAYMENTS
                 * ------------------------------------------------------------
                 */

                List<Payment> organizationPayments = paymentRepository
                                .findByLoan_Organization_Id(
                                                organizationId);

                if (organizationPayments == null) {
                        organizationPayments = List.of();
                }

                BigDecimal totalCollected = ZERO;
                BigDecimal collectedThisMonth = ZERO;

                for (Payment payment : organizationPayments) {

                        if (payment == null) {
                                continue;
                        }

                        if (!Boolean.TRUE.equals(payment.getPaid())) {
                                continue;
                        }

                        BigDecimal paymentAmount = money(payment.getAmountPaidDecimal());

                        totalCollected = money(
                                        totalCollected
                                                        .add(paymentAmount));

                        LocalDate paidDate = payment.getPaidDate();

                        if (paidDate != null
                                        && !paidDate.isBefore(firstOfMonth)
                                        && !paidDate.isAfter(today)) {

                                collectedThisMonth = money(
                                                collectedThisMonth
                                                                .add(paymentAmount));
                        }
                }

                /*
                 * ------------------------------------------------------------
                 * PORTFOLIO AT RISK
                 * ------------------------------------------------------------
                 */

                BigDecimal portfolioAtRiskPct = ZERO;

                if (activePortfolioPrincipal.compareTo(ZERO) > 0) {

                        portfolioAtRiskPct = money(
                                        atRiskPrincipal
                                                        .multiply(ONE_HUNDRED)
                                                        .divide(
                                                                        activePortfolioPrincipal,
                                                                        16,
                                                                        RoundingMode.HALF_UP));

                        if (portfolioAtRiskPct.compareTo(ONE_HUNDRED) > 0) {
                                portfolioAtRiskPct = ONE_HUNDRED.setScale(
                                                2,
                                                RoundingMode.HALF_UP);
                        }
                }

                /*
                 * ------------------------------------------------------------
                 * RECENT LOANS
                 * ------------------------------------------------------------
                 *
                 * CRITICAL FIX:
                 *
                 * We do NOT return List<Loan>.
                 *
                 * The entities are converted to LoanResponse while the
                 * Hibernate session is still open.
                 */

                List<LoanResponse> recentLoans = loans
                                .stream()
                                .filter(loan -> loan != null &&
                                                loan.getCreatedAt() != null)
                                .sorted(
                                                Comparator.comparing(
                                                                Loan::getCreatedAt,
                                                                Comparator.nullsLast(
                                                                                Comparator.reverseOrder())))
                                .limit(8)
                                .map(ResponseDtoMapper::loan)
                                .toList();

                /*
                 * ------------------------------------------------------------
                 * LOGGING
                 * ------------------------------------------------------------
                 */

                log.debug(
                                "Dashboard calculated. " +
                                                "orgId={}, totalLoans={}, activeLoans={}, " +
                                                "pendingLoans={}, overdueLoans={}, " +
                                                "defaultedLoans={}, totalDisbursed={}, " +
                                                "totalCollected={}, outstanding={}, " +
                                                "collectedThisMonth={}, PAR={}",
                                organizationId,
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

                /*
                 * ------------------------------------------------------------
                 * DTO RESPONSE
                 * ------------------------------------------------------------
                 */

                return DashboardStats.builder()
                                .totalLoans(totalLoans)
                                .activeLoans(activeLoans)
                                .pendingLoans(pendingLoans)
                                .completedLoans(completedLoans)
                                .defaultedLoans(defaultedLoans)
                                .overdueLoans(overdueLoans)
                                .totalBorrowers(totalBorrowers)
                                .totalDisbursed(totalDisbursed)
                                .totalCollected(totalCollected)
                                .outstandingBalance(totalOutstanding)
                                .collectedThisMonth(collectedThisMonth)
                                .latePaymentsCount(latePaymentsCount)
                                .portfolioAtRiskPct(portfolioAtRiskPct)
                                .recentLoans(recentLoans)
                                .build();
        }

        private BigDecimal money(BigDecimal value) {

                if (value == null) {
                        return ZERO;
                }

                return value.setScale(
                                2,
                                RoundingMode.HALF_UP);
        }
}