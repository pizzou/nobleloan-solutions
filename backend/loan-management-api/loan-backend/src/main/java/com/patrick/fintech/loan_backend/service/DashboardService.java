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

        public DashboardStats getStats(Long orgId) {

                if (orgId == null) {
                        throw new IllegalArgumentException("Organization ID is required");
                }

                LocalDate today = LocalDate.now();
                LocalDate firstOfMonth = today.withDayOfMonth(1);
                boolean includeBusinessOwnerOnly = ReportingScopeService.includeBusinessOwnerOnly();

                // One SQL aggregate replaces loading the entire loan portfolio.
                Object[] loanAgg = loanRepository.getVisibleDashboardLoanAggregate(
                                orgId, includeBusinessOwnerOnly);

                long totalLoans = number(loanAgg, 0).longValue();
                long pendingLoans = number(loanAgg, 1).longValue();
                long activeLoans = number(loanAgg, 2).longValue();
                long completedLoans = number(loanAgg, 3).longValue();
                long defaultedLoans = number(loanAgg, 4).longValue();
                BigDecimal totalDisbursed = money(decimal(loanAgg, 5));
                BigDecimal totalOutstanding = money(decimal(loanAgg, 6));
                BigDecimal atRiskPrincipal = money(decimal(loanAgg, 7));

                // One SQL aggregate replaces loading every overdue Payment entity.
                Object[] paymentAgg = paymentRepository.getDashboardPerformanceAggregate(
                                orgId, firstOfMonth, today, includeBusinessOwnerOnly);

                BigDecimal paymentCollections = money(decimal(paymentAgg, 0));
                BigDecimal collectedThisMonth = money(decimal(paymentAgg, 1));
                long latePaymentsCount = number(paymentAgg, 2).longValue();
                long overdueLoans = number(paymentAgg, 3).longValue();

                // Preserve the existing legacy/imported collection rules, but evaluate
                // them with SQL aggregates instead of iterating every historical loan.
                Object[] legacyAgg = loanRepository.getDashboardLegacyCollectionAggregate(orgId, includeBusinessOwnerOnly);
                BigDecimal legacyHistoricalCollected = money(decimal(legacyAgg, 0))
                                .add(money(decimal(legacyAgg, 1)))
                                .add(money(decimal(legacyAgg, 2)))
                                .add(money(decimal(legacyAgg, 3)))
                                .add(money(decimal(legacyAgg, 4)))
                                .add(money(decimal(legacyAgg, 5)));
                legacyHistoricalCollected = money(legacyHistoricalCollected);

                BigDecimal legacyApplicationFeesCollected = money(decimal(legacyAgg, 5));
                BigDecimal currentApplicationFeesCollected = money(
                                loanRepository.sumCurrentApplicationFeesCollected(orgId, includeBusinessOwnerOnly));

                BigDecimal totalCollected = money(
                                paymentCollections
                                                .add(currentApplicationFeesCollected)
                                                .add(legacyHistoricalCollected));

                BigDecimal totalReceivables = money(totalOutstanding);
                // The dashboard's contractual receivable figure includes the same
                // outstanding charges used by the existing implementation. Those
                // charge fields are already persisted on each loan, so calculate
                // the charge total in SQL rather than loading the portfolio.
                Object[] receivableAgg = loanRepository.getVisibleDashboardReceivableAggregate(
                                orgId, includeBusinessOwnerOnly);
                BigDecimal outstandingInterest = money(decimal(receivableAgg, 0));
                BigDecimal outstandingFees = money(decimal(receivableAgg, 1));
                totalReceivables = money(totalOutstanding.add(outstandingInterest).add(outstandingFees));

                BigDecimal activePortfolioPrincipal = totalOutstanding;
                BigDecimal portfolioAtRiskPct = ZERO;
                if (activePortfolioPrincipal.compareTo(ZERO) > 0) {
                        portfolioAtRiskPct = money(atRiskPrincipal
                                        .multiply(ONE_HUNDRED)
                                        .divide(activePortfolioPrincipal, 16, RoundingMode.HALF_UP));
                        if (portfolioAtRiskPct.compareTo(ONE_HUNDRED) > 0) {
                                portfolioAtRiskPct = ONE_HUNDRED.setScale(2, RoundingMode.HALF_UP);
                        }
                }

                // Only fetch the rows actually displayed on the dashboard.
                List<LoanResponse> recentLoans = loanRepository.findVisibleRecentLoans(
                                orgId, includeBusinessOwnerOnly,
                                org.springframework.data.domain.PageRequest.of(0, 8))
                                .stream()
                                .filter(java.util.Objects::nonNull)
                                .map(ResponseDtoMapper::loan)
                                .toList();

                return DashboardStats.builder()
                                .totalLoans(totalLoans)
                                .activeLoans(activeLoans)
                                .pendingLoans(pendingLoans)
                                .completedLoans(completedLoans)
                                .defaultedLoans(defaultedLoans)
                                .overdueLoans(overdueLoans)
                                .totalBorrowers(borrowerRepository.countByOrganization_Id(orgId))
                                .totalDisbursed(totalDisbursed)
                                .totalCollected(totalCollected)
                                .historicalCollected(legacyHistoricalCollected)
                                .applicationFeesCollected(money(currentApplicationFeesCollected.add(legacyApplicationFeesCollected)))
                                .outstandingBalance(totalOutstanding)
                                .totalReceivables(totalReceivables)
                                .collectedThisMonth(collectedThisMonth)
                                .latePaymentsCount(latePaymentsCount)
                                .portfolioAtRiskPct(portfolioAtRiskPct)
                                .portfolioAtRiskAmount(atRiskPrincipal)
                                .recentLoans(recentLoans)
                                .build();
        }

        private static BigDecimal decimal(Object[] values, int index) {
                if (values == null || index < 0 || index >= values.length || values[index] == null) {
                        return ZERO;
                }
                Object value = values[index];
                if (value instanceof BigDecimal bd) return bd;
                if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
                try { return new BigDecimal(value.toString()); } catch (NumberFormatException ex) { return ZERO; }
        }

        private static BigDecimal number(Object[] values, int index) {
                return decimal(values, index);
        }

        /**
         * Current portfolio identity used by dashboard balances.
         *
         * This intentionally mirrors the population rule used by
         * LoanRepository.sumOutstandingBalance() and the BNR portfolio query.
         * Do not require disbursedAt for imported historical loans because
         * legacy ledgers often contain an opening position without a precise
         * timestamp.
         */
        private boolean isCurrentPortfolioLoan(Loan loan) {
                if (loan == null || loan.getStatus() == null) {
                        return false;
                }

                LoanStatus status = loan.getStatus();

                boolean receivableStatus = status == LoanStatus.ACTIVE
                                || status == LoanStatus.DISBURSED
                                || status == LoanStatus.OVERDUE
                                || status == LoanStatus.DEFAULTED
                                || status == LoanStatus.RESTRUCTURED;

                if (!receivableStatus) {
                        return false;
                }

                if (Boolean.TRUE.equals(loan.getImported())
                                || loan.getImportBatchId() != null) {
                        return true;
                }

                String internalNotes = loan.getInternalNotes();
                if (internalNotes != null
                                && internalNotes.toLowerCase(java.util.Locale.ROOT)
                                                .contains("imported from legacy ledger")) {
                        return true;
                }

                String notes = loan.getNotes();
                if (notes != null
                                && notes.toLowerCase(java.util.Locale.ROOT)
                                                .contains("imported from noble loan historical portfolio workbook")) {
                        return true;
                }

                return loan.getDisbursedAt() != null;
        }

        // ================================================================
        // MONEY
        // ================================================================

        /**
         * Single legacy-portfolio identity rule used by dashboard historical
         * collection aggregation. This also recognizes older imported rows
         * where imported/importBatchId were not persisted but the importer
         * provenance note was preserved.
         */
        private boolean isLegacyImportedLoan(Loan loan) {
                if (loan == null) {
                        return false;
                }

                if (Boolean.TRUE.equals(loan.getImported())
                                || loan.getImportBatchId() != null) {
                        return true;
                }

                String internalNotes = loan.getInternalNotes();
                if (internalNotes != null
                                && internalNotes.toLowerCase(java.util.Locale.ROOT)
                                                .contains("imported from legacy ledger")) {
                        return true;
                }

                String notes = loan.getNotes();
                return notes != null
                                && notes.toLowerCase(java.util.Locale.ROOT)
                                                .contains("imported from noble loan historical portfolio workbook");
        }

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