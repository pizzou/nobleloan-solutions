package com.patrick.fintech.loan_backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.patrick.fintech.loan_backend.model.Loan;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStats {

    // ============================================================
    // LOAN COUNTS
    // ============================================================

    private long totalLoans;
    private long pendingLoans;
    private long activeLoans;
    private long overdueLoans;
    private long completedLoans;
    private long defaultedLoans;

    // ============================================================
    // PORTFOLIO FINANCIALS
    // ============================================================

    @JsonProperty("totalDisbursed")
    private BigDecimal totalDisbursed;

    @JsonProperty("totalCollected")
    private BigDecimal totalCollected;

    @JsonProperty("outstandingBalance")
    private BigDecimal outstandingBalance;

    @JsonProperty("collectedThisMonth")
    private BigDecimal collectedThisMonth;

    // ============================================================
    // BORROWERS / COLLECTIONS
    // ============================================================

    private long totalBorrowers;

    private long latePaymentsCount;

    // ============================================================
    // PORTFOLIO RISK
    // ============================================================

    @JsonProperty("portfolioAtRiskPct")
    private BigDecimal portfolioAtRiskPct;

    @JsonProperty("portfolioAtRiskAmount")
    private BigDecimal portfolioAtRiskAmount;

    // ============================================================
    // BORROWER DEMOGRAPHICS
    // ============================================================

    /**
     * Organization-level borrower demographic breakdown.
     *
     * Each row contains:
     * - label
     * - count
     */
    private List<Map<String, Object>> borrowerGenderBreakdown;

    // ============================================================
    // RECENT LOANS
    // ============================================================
    //
    // IMPORTANT:
    // DashboardService currently supplies List<Loan>.
    // The frontend DashboardStats type also expects Loan[].
    //
    // Therefore this must remain List<Loan> unless the entire
    // dashboard response is deliberately migrated to LoanResponse.
    // ============================================================

    private List<Loan> recentLoans;

    // ============================================================
    // LOAN TYPE BREAKDOWN
    // ============================================================

    private List<Map<String, Object>> loanTypeBreakdown;

    // ============================================================
    // BACKWARD-COMPATIBLE DOUBLE ACCESSORS
    // ============================================================
    //
    // Keep these because other parts of the application may still
    // use the historical Double-based API.
    //
    // Internally, financial values remain BigDecimal.
    // ============================================================

    @Deprecated
    @JsonIgnore
    public Double getTotalDisbursed() {
        return totalDisbursed == null
                ? null
                : totalDisbursed.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalDisbursedDecimal() {
        return totalDisbursed;
    }

    @Deprecated
    public void setTotalDisbursed(Double value) {
        this.totalDisbursed = value == null
                ? null
                : BigDecimal.valueOf(value);
    }

    public void setTotalDisbursed(BigDecimal value) {
        this.totalDisbursed = value;
    }

    // ============================================================

    @Deprecated
    @JsonIgnore
    public Double getTotalCollected() {
        return totalCollected == null
                ? null
                : totalCollected.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalCollectedDecimal() {
        return totalCollected;
    }

    @Deprecated
    public void setTotalCollected(Double value) {
        this.totalCollected = value == null
                ? null
                : BigDecimal.valueOf(value);
    }

    public void setTotalCollected(BigDecimal value) {
        this.totalCollected = value;
    }

    // ============================================================

    @Deprecated
    @JsonIgnore
    public Double getOutstandingBalance() {
        return outstandingBalance == null
                ? null
                : outstandingBalance.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getOutstandingBalanceDecimal() {
        return outstandingBalance;
    }

    @Deprecated
    public void setOutstandingBalance(Double value) {
        this.outstandingBalance = value == null
                ? null
                : BigDecimal.valueOf(value);
    }

    public void setOutstandingBalance(BigDecimal value) {
        this.outstandingBalance = value;
    }

    // ============================================================

    @Deprecated
    @JsonIgnore
    public Double getCollectedThisMonth() {
        return collectedThisMonth == null
                ? null
                : collectedThisMonth.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getCollectedThisMonthDecimal() {
        return collectedThisMonth;
    }

    @Deprecated
    public void setCollectedThisMonth(Double value) {
        this.collectedThisMonth = value == null
                ? null
                : BigDecimal.valueOf(value);
    }

    public void setCollectedThisMonth(BigDecimal value) {
        this.collectedThisMonth = value;
    }

    // ============================================================

    @Deprecated
    @JsonIgnore
    public Double getPortfolioAtRiskPct() {
        return portfolioAtRiskPct == null
                ? null
                : portfolioAtRiskPct.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getPortfolioAtRiskPctDecimal() {
        return portfolioAtRiskPct;
    }

    @Deprecated
    public void setPortfolioAtRiskPct(Double value) {
        this.portfolioAtRiskPct = value == null
                ? null
                : BigDecimal.valueOf(value);
    }

    public void setPortfolioAtRiskPct(BigDecimal value) {
        this.portfolioAtRiskPct = value;
    }

    // ============================================================
    // LOMBOK BUILDER COMPATIBILITY
    // ============================================================
    //
    // Lombok generates the remaining builder methods, including:
    //
    // recentLoans(List<Loan>)
    //
    // The explicit BigDecimal overloads below preserve compatibility
    // with existing code that supplies Double values.
    // ============================================================

    public static class DashboardStatsBuilder {

        private BigDecimal collectedThisMonth;
        private BigDecimal outstandingBalance;
        private BigDecimal portfolioAtRiskPct;
        private BigDecimal totalCollected;
        private BigDecimal totalDisbursed;

        public DashboardStatsBuilder totalDisbursed(
                Double value) {
            this.totalDisbursed = value == null
                    ? null
                    : BigDecimal.valueOf(value);

            return this;
        }

        public DashboardStatsBuilder totalDisbursed(
                BigDecimal value) {
            this.totalDisbursed = value;

            return this;
        }

        public DashboardStatsBuilder totalCollected(
                Double value) {
            this.totalCollected = value == null
                    ? null
                    : BigDecimal.valueOf(value);

            return this;
        }

        public DashboardStatsBuilder totalCollected(
                BigDecimal value) {
            this.totalCollected = value;

            return this;
        }

        public DashboardStatsBuilder outstandingBalance(
                Double value) {
            this.outstandingBalance = value == null
                    ? null
                    : BigDecimal.valueOf(value);

            return this;
        }

        public DashboardStatsBuilder outstandingBalance(
                BigDecimal value) {
            this.outstandingBalance = value;

            return this;
        }

        public DashboardStatsBuilder collectedThisMonth(
                Double value) {
            this.collectedThisMonth = value == null
                    ? null
                    : BigDecimal.valueOf(value);

            return this;
        }

        public DashboardStatsBuilder collectedThisMonth(
                BigDecimal value) {
            this.collectedThisMonth = value;

            return this;
        }

        public DashboardStatsBuilder portfolioAtRiskPct(
                Double value) {
            this.portfolioAtRiskPct = value == null
                    ? null
                    : BigDecimal.valueOf(value);

            return this;
        }

        public DashboardStatsBuilder portfolioAtRiskPct(
                BigDecimal value) {
            this.portfolioAtRiskPct = value;

            return this;
        }
    }
}