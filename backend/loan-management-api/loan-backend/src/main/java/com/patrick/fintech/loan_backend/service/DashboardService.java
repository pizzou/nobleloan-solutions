package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.DashboardStats;
import com.patrick.fintech.loan_backend.dto.LoanResponse;
import com.patrick.fintech.loan_backend.mapper.ResponseDtoMapper;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

    private final LoanRepository loanRepository;
    private final PaymentRepository paymentRepository;
    private final BorrowerRepository borrowerRepository;
    private final EntityManager entityManager;

    /**
     * Bank-grade dashboard aggregation.
     *
     * The previous implementation materialized every Loan and every Payment
     * belonging to an organization on every dashboard request. That makes
     * dashboard latency and heap usage grow linearly with portfolio size.
     *
     * This implementation keeps the same financial definitions but performs
     * the heavy work in database-side aggregate queries and only loads the
     * eight recent loans required by the UI.
     */
    public DashboardStats getStats(Long orgId) {
        if (orgId == null || orgId <= 0) {
            throw new IllegalArgumentException("Organization ID is required");
        }

        LocalDate today = LocalDate.now();
        LocalDate firstOfMonth = today.withDayOfMonth(1);

        Object[] loanAggregate = loanRepository.getDashboardLoanAggregate(orgId);
        if (loanAggregate == null || loanAggregate.length == 0) {
            loanAggregate = new Object[10];
        }

        long totalLoans = longValue(loanAggregate, 0);
        long pendingLoans = longValue(loanAggregate, 1);
        long activeLoans = longValue(loanAggregate, 2);
        long completedLoans = longValue(loanAggregate, 3);
        long defaultedLoans = longValue(loanAggregate, 4);

        BigDecimal totalDisbursed = money(decimalValue(loanAggregate, 5));
        BigDecimal totalOutstanding = money(decimalValue(loanAggregate, 6));
        BigDecimal outstandingInterest = money(decimalValue(loanAggregate, 7));
        BigDecimal outstandingFees = money(decimalValue(loanAggregate, 8));
        BigDecimal atRiskPrincipal = money(decimalValue(loanAggregate, 9));
        BigDecimal importedHistoricalCollected = money(decimalValue(loanAggregate, 10));
        BigDecimal importedApplicationFees = money(decimalValue(loanAggregate, 11));
        BigDecimal currentApplicationFees = money(decimalValue(loanAggregate, 12));

        long totalBorrowers = borrowerRepository.countByOrganization_Id(orgId);
        long overdueLoans = paymentRepository.countDistinctOverdueLoans(orgId, today);

        Object[] paymentAggregate = paymentRepository.getDashboardPaymentAggregate(
                orgId,
                firstOfMonth,
                today);

        BigDecimal paymentRowsCollected = decimalValue(paymentAggregate, 0);
        BigDecimal collectedThisMonth = money(decimalValue(paymentAggregate, 1));
        long latePaymentsCount = longValue(paymentAggregate, 2);

        /*
         * Imported cumulative totals and application fees are already included
         * in the single dashboard loan aggregate above. We only need the paid
         * Payment-row total here to avoid double-counting imported history.
         */
        BigDecimal importedPaymentRows =
                money(loanRepository.sumImportedPaymentRows(
                        organization(orgId)));

        BigDecimal currentAndImportedPaymentRows =
                money(paymentRowsCollected.subtract(importedPaymentRows));

        BigDecimal applicationFeesCollected = money(
                currentApplicationFees.add(importedApplicationFees));

        BigDecimal totalCollected = money(
                currentAndImportedPaymentRows
                        .add(importedHistoricalCollected)
                        .add(applicationFeesCollected));

        BigDecimal totalReceivables = money(
                totalOutstanding
                        .add(outstandingInterest)
                        .add(outstandingFees));

        BigDecimal portfolioAtRiskPct = ZERO;
        if (totalOutstanding.compareTo(ZERO) > 0) {
            portfolioAtRiskPct = money(
                    atRiskPrincipal
                            .multiply(ONE_HUNDRED)
                            .divide(totalOutstanding, 16, RoundingMode.HALF_UP));
            if (portfolioAtRiskPct.compareTo(ONE_HUNDRED) > 0) {
                portfolioAtRiskPct = ONE_HUNDRED;
            }
        }

        List<LoanResponse> recentLoans = new ArrayList<>();
        List<Loan> recent = loanRepository.findRecentByOrganizationId(
                orgId,
                PageRequest.of(0, 8));
        for (Loan loan : recent) {
            if (loan != null) {
                recentLoans.add(ResponseDtoMapper.loan(loan));
            }
        }

        List<Map<String, Object>> loanTypeBreakdown = new ArrayList<>();
        for (Object[] row : loanRepository.getLoanTypeBreakdownByOrganizationId(orgId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", row != null && row.length > 0 && row[0] != null
                    ? row[0].toString() : "UNKNOWN");
            item.put("count", row != null && row.length > 1
                    ? longValue(row, 1) : 0L);
            item.put("amount", row != null && row.length > 2
                    ? money(decimalValue(row, 2)) : ZERO);
            loanTypeBreakdown.add(item);
        }

        log.debug(
                "Dashboard aggregate calculated. orgId={}, loans={}, borrowers={}, outstanding={}, collected={}, PAR={}",
                orgId,
                totalLoans,
                totalBorrowers,
                totalOutstanding,
                totalCollected,
                portfolioAtRiskPct);

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
                .historicalCollected(importedHistoricalCollected)
                .applicationFeesCollected(applicationFeesCollected)
                .outstandingBalance(totalOutstanding)
                .totalReceivables(totalReceivables)
                .collectedThisMonth(collectedThisMonth)
                .latePaymentsCount(latePaymentsCount)
                .portfolioAtRiskPct(portfolioAtRiskPct)
                .portfolioAtRiskAmount(atRiskPrincipal)
                .recentLoans(recentLoans)
                .loanTypeBreakdown(loanTypeBreakdown)
                .build();
    }

    /*
     * DashboardService historically obtained Organization through the current
     * entity graph. We only need it for repository queries that use the entity
     * parameter, so this reference is loaded lazily without loading loans.
     */
    private Organization organization(Long orgId) {
        return entityManager.getReference(Organization.class, orgId);
    }

    private static long longValue(Object[] values, int index) {
        if (values == null || index >= values.length || values[index] == null) {
            return 0L;
        }
        Object value = values[index];
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static BigDecimal decimalValue(Object[] values, int index) {
        if (values == null || index >= values.length || values[index] == null) {
            return ZERO;
        }
        Object value = values[index];
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ignored) {
            return ZERO;
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }
}
