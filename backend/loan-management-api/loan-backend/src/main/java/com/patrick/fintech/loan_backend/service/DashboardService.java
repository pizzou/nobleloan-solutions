package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.DashboardStats;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
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

                BigDecimal totalDisbursed = money(valueAt(loanAggregate, 5));

                BigDecimal totalOutstanding = money(valueAt(loanAggregate, 6));

                BigDecimal atRiskPrincipal = money(valueAt(loanAggregate, 7));

                long overdueLoans = asLong(valueAt(loanAggregate, 8));

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

                BigDecimal totalCollected = money(valueAt(paymentAggregate, 0));

                BigDecimal collectedThisMonth = money(valueAt(paymentAggregate, 1));

                long latePaymentsCount = asLong(valueAt(paymentAggregate, 2));

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
                List<Loan> recentLoans = loanRepository.findRecentByOrganizationId(
                                orgId,
                                PageRequest.of(0, 8));

                if (recentLoans == null) {
                        recentLoans = List.of();
                }

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