package com.patrick.fintech.loan_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
/** All monetary inputs are the complete corrected historical amounts, never increments. */
public record LegacyLoanCorrectionRequest(
        String confirmReference,
        String reason,
        String evidenceReference,
        LocalDate oldestUnpaidDueDate,
        BigDecimal principalPaid,
        BigDecimal interestPaid,
        BigDecimal interestOutstanding,
        BigDecimal managementFeePaid,
        BigDecimal managementFeeOutstanding,
        BigDecimal penaltiesAssessed,
        BigDecimal penaltiesPaid,
        Integer durationMonths) {}
