package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.DashboardStats;
import com.patrick.fintech.loan_backend.dto.LoanResponse;
import com.patrick.fintech.loan_backend.mapper.ResponseDtoMapper;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DashboardService {

        private final LoanRepository loanRepository;
        private final OrganizationRepository organizationRepository;
        private final PaymentRepository paymentRepository;
        private final BorrowerRepository borrowerRepository;

        private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

        /**
         * Builds the dashboard from database aggregates rather than loading
         * every loan, borrower and payment into JVM memory.
         *
         * This is deliberately read-only and tenant-scoped. The dashboard
         * endpoint should stay fast even when an organization has thousands
         * of loans and a large payment history.
         */
        public DashboardStats getStats(Long orgId) {

                if (orgId == null) {
                        throw new IllegalArgumentException(
                                        "Organization ID is required");
                }

                final LocalDate today = LocalDate.now();
                final LocalDate firstOfMonth = today.withDayOfMonth(1);

                /*
                 * ------------------------------------------------------------
                 * LOAN AGGREGATES
                 * ------------------------------------------------------------
                 *
                 * One SQL query replaces multiple COUNT queries plus the old
                 * full-portfolio load.
                 */
                Object[] loanAggregate = loanRepository.getDashboardLoanAggregate(orgId);

                long totalLoans = asLong(valueAt(loanAggregate, 0));
                long pendingLoans = asLong(valueAt(loanAggregate, 1));
                long activeLoans = asLong(valueAt(loanAggregate, 2));
                long completedLoans = asLong(valueAt(loanAggregate, 3));
                long defaultedLoans = asLong(valueAt(loanAggregate, 4));

                /*
                 * The total loan count is a hard control figure. If the
                 * aggregate ever disagrees with the tenant-scoped repository
                 * count, do not present a false zero to operations staff.
                 */
                long repositoryLoanCount = loanRepository.countByOrganization_Id(orgId);
                BigDecimal totalDisbursed = money(valueAt(loanAggregate, 5));
                BigDecimal totalOutstanding = money(valueAt(loanAggregate, 6));
                BigDecimal atRiskPrincipal = money(valueAt(loanAggregate, 7));
                long overdueLoans = asLong(valueAt(loanAggregate, 8));

                if (totalLoans != repositoryLoanCount) {
                        log.warn(
                                        "Dashboard loan aggregate mismatch. orgId={}, aggregateTotal={}, repositoryTotal={}",
                                        orgId,
                                        totalLoans,
                                        repositoryLoanCount);

                        totalLoans = repositoryLoanCount;

                        /*
                         * Rebuild the operational count controls from the
                         * tenant-scoped repository only when the aggregate
                         * disagrees. This keeps the normal dashboard path
                         * fast while making a bad aggregate impossible to
                         * present as a clean zero/empty portfolio.
                         */
                        pendingLoans = loanRepository.countByOrganization_IdAndStatus(orgId, LoanStatus.PENDING)
                                        + loanRepository.countByOrganization_IdAndStatus(orgId,
                                                        LoanStatus.UNDER_REVIEW);

                        activeLoans = loanRepository.countByOrganization_IdAndStatus(orgId, LoanStatus.ACTIVE)
                                        + loanRepository.countByOrganization_IdAndStatus(orgId, LoanStatus.DISBURSED)
                                        + loanRepository.countByOrganization_IdAndStatus(orgId, LoanStatus.OVERDUE)
                                        + loanRepository.countByOrganization_IdAndStatus(orgId,
                                                        LoanStatus.RESTRUCTURED);

                        completedLoans = loanRepository.countByOrganization_IdAndStatus(orgId, LoanStatus.PAID)
                                        + loanRepository.countByOrganization_IdAndStatus(orgId, LoanStatus.CLOSED);

                        defaultedLoans = loanRepository.countByOrganization_IdAndStatus(orgId, LoanStatus.DEFAULTED)
                                        + loanRepository.countByOrganization_IdAndStatus(orgId, LoanStatus.WRITTEN_OFF);

                        overdueLoans = paymentRepository.countDistinctOverdueLoans(orgId, today);

                }

                /*
                 * A live active portfolio cannot legitimately report both zero
                 * disbursed cash and zero outstanding principal. If that
                 * control breaks, use the established organization-scoped sum
                 * queries as the recovery path.
                 */
                if (repositoryLoanCount > 0 && activeLoans > 0
                                && totalDisbursed.compareTo(ZERO) == 0
                                && totalOutstanding.compareTo(ZERO) == 0) {
                        var org = organizationRepository.findById(orgId).orElse(null);
                        if (org != null) {
                                log.warn(
                                                "Dashboard financial aggregate returned zero for an active portfolio. orgId={}",
                                                orgId);
                                totalDisbursed = money(loanRepository.sumGrossDisbursedPrincipal(org));
                                totalOutstanding = money(loanRepository.sumOutstandingBalance(org));
                                // The recovery path restores the core financial
                                // balances; PAR remains the aggregate value unless
                                // the overdue control is explicitly recalculated.
                                atRiskPrincipal = money(valueAt(loanAggregate, 7));
                        }
                }

                /*
                 * ------------------------------------------------------------
                 * PAYMENT AGGREGATES
                 * ------------------------------------------------------------
                 *
                 * One SQL query replaces the old full payment-history load.
                 */
                Object[] paymentAggregate = paymentRepository.getDashboardPaymentAggregate(
                                orgId,
                                firstOfMonth,
                                today);

                BigDecimal paymentRowsCollected = money(valueAt(paymentAggregate, 0));

                BigDecimal collectedThisMonth = money(valueAt(paymentAggregate, 1));

                long latePaymentsCount = asLong(valueAt(paymentAggregate, 2));

                /*
                 * Legacy imports preserve cumulative paid-to-date values on Loan
                 * rather than creating fake Payment rows. If an imported loan
                 * receives a genuine platform payment after migration, that
                 * payment is present in both Loan.totalPaid and the Payment table,
                 * so remove the payment-row portion before adding the historical
                 * brought-forward collection balance.
                 */
                var organization = organizationRepository.findById(orgId).orElse(null);
                BigDecimal historicalLoanPaid = ZERO;
                BigDecimal importedPaymentRows = ZERO;
                BigDecimal processingFeesCollected = ZERO;
                BigDecimal importedProcessingFeesCollected = ZERO;

                if (organization != null) {
                        historicalLoanPaid = money(
                                        loanRepository.sumImportedHistoricalTotalPaid(organization));
                        importedPaymentRows = money(
                                        loanRepository.sumImportedPaymentRows(organization));
                        processingFeesCollected = money(
                                        loanRepository.sumProcessingFeesCollected(organization));
                        importedProcessingFeesCollected = money(
                                        loanRepository.sumImportedProcessingFeesCollected(organization));
                }

                BigDecimal historicalCollected = money(
                                historicalLoanPaid
                                                .subtract(importedPaymentRows)
                                                .max(ZERO)
                                                .add(importedProcessingFeesCollected));

                BigDecimal totalCollected = money(
                                paymentRowsCollected
                                                .add(historicalLoanPaid.subtract(importedPaymentRows).max(ZERO))
                                                .add(processingFeesCollected));

                /*
                 * ------------------------------------------------------------
                 * BORROWER COUNT
                 * ------------------------------------------------------------
                 *
                 * Count directly in SQL. Never load every borrower just to call
                 * List.size().
                 */
                long totalBorrowers = borrowerRepository.countByOrganization_Id(orgId);

                /*
                 * ------------------------------------------------------------
                 * PORTFOLIO AT RISK
                 * ------------------------------------------------------------
                 *
                 * PAR = at-risk outstanding / active outstanding * 100.
                 *
                 * We keep the denominator consistent with the dashboard's
                 * outstanding portfolio definition.
                 */
                BigDecimal portfolioAtRiskPct = ZERO;

                if (totalOutstanding.compareTo(ZERO) > 0) {
                        portfolioAtRiskPct = money(
                                        atRiskPrincipal
                                                        .multiply(ONE_HUNDRED)
                                                        .divide(
                                                                        totalOutstanding,
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
                 * LOAN TYPE BREAKDOWN
                 * ------------------------------------------------------------
                 */
                List<Map<String, Object>> typeBreakdown = new ArrayList<>();

                List<Object[]> typeRows = loanRepository.getLoanTypeBreakdownByOrganizationId(
                                orgId);

                if (typeRows != null) {
                        for (Object[] row : typeRows) {
                                if (row == null || row.length < 3) {
                                        continue;
                                }

                                Map<String, Object> item = new LinkedHashMap<>();

                                item.put("type", row[0]);
                                item.put("count", asLong(row[1]));
                                item.put(
                                                "amount",
                                                money(row[2]));

                                typeBreakdown.add(item);
                        }
                }

                /*
                 * ------------------------------------------------------------
                 * BORROWER GENDER BREAKDOWN
                 * ------------------------------------------------------------
                 */
                Map<String, Long> genderCounts = new LinkedHashMap<>();
                genderCounts.put("MALE", 0L);
                genderCounts.put("FEMALE", 0L);
                genderCounts.put("OTHER", 0L);

                Object[] genderRow = borrowerRepository.getDashboardGenderBreakdown(orgId);

                if (genderRow != null && genderRow.length >= 3) {
                        genderCounts.put("MALE", asLong(genderRow[0]));
                        genderCounts.put("FEMALE", asLong(genderRow[1]));
                        genderCounts.put("OTHER", asLong(genderRow[2]));
                }

                List<Map<String, Object>> borrowerGenderBreakdown = new ArrayList<>();

                for (Map.Entry<String, Long> entry : genderCounts.entrySet()) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("label", entry.getKey());
                        item.put("count", entry.getValue());
                        borrowerGenderBreakdown.add(item);
                }

                /*
                 * ------------------------------------------------------------
                 * RECENT LOANS
                 * ------------------------------------------------------------
                 *
                 * Only eight records are returned and only the relationships
                 * required by the dashboard are eagerly loaded.
                 */
                List<Loan> recentLoanEntities = loanRepository.findRecentByOrganizationId(
                                orgId,
                                PageRequest.of(0, 8));

                List<LoanResponse> recentLoans = recentLoanEntities == null
                                ? List.of()
                                : recentLoanEntities.stream()
                                                .filter(java.util.Objects::nonNull)
                                                .map(ResponseDtoMapper::loan)
                                                .toList();

                log.debug(
                                "Dashboard calculated efficiently. " +
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

                return DashboardStats.builder()
                                .totalLoans(totalLoans)
                                .pendingLoans(pendingLoans)
                                .activeLoans(activeLoans)
                                .overdueLoans(overdueLoans)
                                .completedLoans(completedLoans)
                                .defaultedLoans(defaultedLoans)
                                .totalBorrowers(totalBorrowers)
                                .totalDisbursed(totalDisbursed)
                                .totalCollected(totalCollected)
                                .historicalCollected(historicalCollected)
                                .processingFeesCollected(processingFeesCollected)
                                .outstandingBalance(totalOutstanding)
                                .collectedThisMonth(collectedThisMonth)
                                .latePaymentsCount(latePaymentsCount)
                                .portfolioAtRiskPct(portfolioAtRiskPct)
                                .portfolioAtRiskAmount(atRiskPrincipal)
                                .borrowerGenderBreakdown(borrowerGenderBreakdown)
                                .loanTypeBreakdown(typeBreakdown)
                                .recentLoans(recentLoans)
                                .build();
        }

        private String normalizeGenderLabel(String value) {
                if (value == null) {
                        return "OTHER";
                }

                String normalized = value.trim().toUpperCase();

                return switch (normalized) {
                        case "MALE", "M" -> "MALE";
                        case "FEMALE", "F" -> "FEMALE";
                        default -> "OTHER";
                };
        }

        private Object valueAt(Object[] values, int index) {
                if (values == null || index < 0 || index >= values.length) {
                        return null;
                }

                return values[index];
        }

        private long asLong(Object value) {
                if (value == null) {
                        return 0L;
                }

                if (value instanceof Number number) {
                        return number.longValue();
                }

                try {
                        return Long.parseLong(value.toString());
                } catch (NumberFormatException ignored) {
                        return 0L;
                }
        }

        private BigDecimal money(Object value) {
                if (value == null) {
                        return ZERO;
                }

                if (value instanceof BigDecimal decimal) {
                        return decimal.setScale(
                                        2,
                                        RoundingMode.HALF_UP);
                }

                if (value instanceof BigInteger integer) {
                        return new BigDecimal(integer)
                                        .setScale(
                                                        2,
                                                        RoundingMode.HALF_UP);
                }

                if (value instanceof Number number) {
                        return BigDecimal.valueOf(number.doubleValue())
                                        .setScale(
                                                        2,
                                                        RoundingMode.HALF_UP);
                }

                try {
                        return new BigDecimal(value.toString())
                                        .setScale(
                                                        2,
                                                        RoundingMode.HALF_UP);
                } catch (NumberFormatException ignored) {
                        return ZERO;
                }
        }
}