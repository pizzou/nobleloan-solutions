package com.patrick.fintech.loan_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStats {

    private long totalLoans;
    private long pendingLoans;
    private long activeLoans;
    private long overdueLoans;
    private long completedLoans;
    private long defaultedLoans;

    private BigDecimal totalDisbursed;
    private BigDecimal totalCollected;
    private BigDecimal outstandingBalance;
    private BigDecimal collectedThisMonth;

    private long totalBorrowers;
    private long latePaymentsCount;

    private BigDecimal portfolioAtRiskPct;

    private List<LoanResponse> recentLoans;

    private List<Map<String, Object>> loanTypeBreakdown;
}