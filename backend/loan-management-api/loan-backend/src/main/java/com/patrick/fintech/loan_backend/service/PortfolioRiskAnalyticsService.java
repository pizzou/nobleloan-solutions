package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.PortfolioRiskAnalyticsResponse;
import com.patrick.fintech.loan_backend.repository.LoanRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Portfolio credit-risk analytics.
 *
 * Source of truth:
 *   Loan.daysOverdue + Loan.outstandingBalance
 *
 * PAR is cumulative:
 *   PAR1  = outstanding principal on loans >= 1 day overdue
 *   PAR7  = outstanding principal on loans >= 7 days overdue
 *   PAR30 = outstanding principal on loans >= 30 days overdue
 *   PAR60 = outstanding principal on loans >= 60 days overdue
 *   PAR90 = outstanding principal on loans >= 90 days overdue
 *
 * The denominator is current outstanding principal, not original
 * disbursement. This makes the KPI useful for collections and portfolio-risk
 * management rather than merely measuring historical originations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PortfolioRiskAnalyticsService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100.00");

    private final LoanRepository loanRepository;

    public PortfolioRiskAnalyticsResponse getCurrentPortfolioRisk(Long organizationId) {
        if (organizationId == null) {
            throw new IllegalArgumentException("Organization ID is required");
        }

        Object[] row = loanRepository.calculatePortfolioRiskMetrics(organizationId);

        if (row == null || row.length < 12) {
            throw new IllegalStateException("Portfolio risk aggregate returned an invalid result");
        }

        long currentLoans = longValue(row[0]);
        BigDecimal outstanding = money(row[1]);

        long par1Loans = longValue(row[2]);
        BigDecimal par1 = money(row[3]);
        long par7Loans = longValue(row[4]);
        BigDecimal par7 = money(row[5]);
        long par30Loans = longValue(row[6]);
        BigDecimal par30 = money(row[7]);
        long par60Loans = longValue(row[8]);
        BigDecimal par60 = money(row[9]);
        long par90Loans = longValue(row[10]);
        BigDecimal par90 = money(row[11]);

        return PortfolioRiskAnalyticsResponse.builder()
                .organizationId(organizationId)
                .asOfDate(LocalDate.now())
                .generatedAt(LocalDateTime.now())
                .currentPortfolioLoans(currentLoans)
                .currentOutstandingPrincipal(outstanding)
                .par1LoanCount(par1Loans)
                .par1Amount(par1)
                .par1Pct(parPercentage(par1, outstanding))
                .par7LoanCount(par7Loans)
                .par7Amount(par7)
                .par7Pct(parPercentage(par7, outstanding))
                .par30LoanCount(par30Loans)
                .par30Amount(par30)
                .par30Pct(parPercentage(par30, outstanding))
                .par60LoanCount(par60Loans)
                .par60Amount(par60)
                .par60Pct(parPercentage(par60, outstanding))
                .par90LoanCount(par90Loans)
                .par90Amount(par90)
                .par90Pct(parPercentage(par90, outstanding))
                .ageingBuckets(buildAgeingBuckets(
                        currentLoans,
                        outstanding,
                        par1Loans, par1,
                        par7Loans, par7,
                        par30Loans, par30,
                        par60Loans, par60,
                        par90Loans, par90))
                .build();
    }

    private List<PortfolioRiskAnalyticsResponse.AgeingBucket> buildAgeingBuckets(
            long currentLoans,
            BigDecimal outstanding,
            long par1Loans,
            BigDecimal par1,
            long par7Loans,
            BigDecimal par7,
            long par30Loans,
            BigDecimal par30,
            long par60Loans,
            BigDecimal par60,
            long par90Loans,
            BigDecimal par90) {

        // Cumulative PAR figures are converted into mutually exclusive ageing
        // buckets for operational collections reporting.
        long currentCount = Math.max(0, currentLoans - par1Loans);
        BigDecimal currentAmount = money(outstanding.subtract(par1).max(ZERO));

        long days1To6Count = Math.max(0, par1Loans - par7Loans);
        BigDecimal days1To6Amount = money(par1.subtract(par7).max(ZERO));

        long days7To29Count = Math.max(0, par7Loans - par30Loans);
        BigDecimal days7To29Amount = money(par7.subtract(par30).max(ZERO));

        long days30To59Count = Math.max(0, par30Loans - par60Loans);
        BigDecimal days30To59Amount = money(par30.subtract(par60).max(ZERO));

        long days60To89Count = Math.max(0, par60Loans - par90Loans);
        BigDecimal days60To89Amount = money(par60.subtract(par90).max(ZERO));

        long days90PlusCount = par90Loans;
        BigDecimal days90PlusAmount = par90;

        List<PortfolioRiskAnalyticsResponse.AgeingBucket> result = new ArrayList<>();
        result.add(bucket("CURRENT", "Current / not overdue", 0, 0,
                currentCount, currentAmount, outstanding));
        result.add(bucket("PAR1_6", "1–6 days overdue", 1, 6,
                days1To6Count, days1To6Amount, outstanding));
        result.add(bucket("PAR7_29", "7–29 days overdue", 7, 29,
                days7To29Count, days7To29Amount, outstanding));
        result.add(bucket("PAR30_59", "30–59 days overdue", 30, 59,
                days30To59Count, days30To59Amount, outstanding));
        result.add(bucket("PAR60_89", "60–89 days overdue", 60, 89,
                days60To89Count, days60To89Amount, outstanding));
        result.add(bucket("PAR90_PLUS", "90+ days overdue", 90, null,
                days90PlusCount, days90PlusAmount, outstanding));
        return result;
    }

    private PortfolioRiskAnalyticsResponse.AgeingBucket bucket(
            String code,
            String label,
            int min,
            Integer max,
            long count,
            BigDecimal amount,
            BigDecimal denominator) {

        return PortfolioRiskAnalyticsResponse.AgeingBucket.builder()
                .code(code)
                .label(label)
                .minDaysOverdue(min)
                .maxDaysOverdue(max)
                .loanCount(count)
                .outstandingPrincipal(money(amount))
                .percentageOfPortfolio(parPercentage(amount, denominator))
                .build();
    }

    private BigDecimal parPercentage(BigDecimal amount, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }

        return amount
                .multiply(HUNDRED)
                .divide(denominator, 4, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal money(Object value) {
        if (value == null) {
            return ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private long longValue(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
