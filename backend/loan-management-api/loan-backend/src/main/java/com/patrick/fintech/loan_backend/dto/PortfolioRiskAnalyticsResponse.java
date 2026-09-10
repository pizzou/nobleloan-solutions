package com.patrick.fintech.loan_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Current portfolio-at-risk analytics.
 *
 * PAR is measured against outstanding principal. A loan belongs to a PAR
 * bucket when its persisted daysOverdue is at least that bucket's threshold.
 * The buckets are cumulative, as required for PAR1/PAR7/PAR30/PAR60/PAR90.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioRiskAnalyticsResponse {

    private Long organizationId;
    private LocalDate asOfDate;
    private LocalDateTime generatedAt;

    private long currentPortfolioLoans;
    private BigDecimal currentOutstandingPrincipal;

    private long par1LoanCount;
    private BigDecimal par1Amount;
    private BigDecimal par1Pct;

    private long par7LoanCount;
    private BigDecimal par7Amount;
    private BigDecimal par7Pct;

    private long par30LoanCount;
    private BigDecimal par30Amount;
    private BigDecimal par30Pct;

    private long par60LoanCount;
    private BigDecimal par60Amount;
    private BigDecimal par60Pct;

    private long par90LoanCount;
    private BigDecimal par90Amount;
    private BigDecimal par90Pct;

    /**
     * Operational ageing buckets. These are mutually exclusive and therefore
     * must not be added together to calculate PAR.
     */
    private List<AgeingBucket> ageingBuckets;

    /**
     * Whether the portfolio has any outstanding principal. Useful to avoid
     * interpreting a zero-denominator PAR percentage as a meaningful zero-risk
     * observation.
     */
    @JsonProperty("hasOutstandingPortfolio")
    public boolean hasOutstandingPortfolio() {
        return currentOutstandingPrincipal != null
                && currentOutstandingPrincipal.compareTo(BigDecimal.ZERO) > 0;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AgeingBucket {
        private String code;
        private String label;
        private int minDaysOverdue;
        private Integer maxDaysOverdue;
        private long loanCount;
        private BigDecimal outstandingPrincipal;
        private BigDecimal percentageOfPortfolio;
    }
}
