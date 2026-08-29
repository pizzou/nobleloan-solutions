package com.patrick.fintech.loan_backend.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FinancialReconciliationJobResponse(
        Long id,
        String status,
        String phase,
        int processedLoans,
        int journalAdjustmentsCreated,
        Boolean beforeBalanced,
        Boolean afterBalanced,
        BigDecimal beforeMaximumDifference,
        BigDecimal afterMaximumDifference,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime heartbeatAt,
        String errorMessage,
        JsonNode result) {
}
