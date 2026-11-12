package com.patrick.fintech.loan_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * All monetary inputs are the complete corrected historical amounts, never
 * increments.
 */
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
                Integer durationMonths,
                BigDecimal applicationFee,
                BigDecimal applicationFeePaid,
                BigDecimal applicationFeeOutstanding,
                String purpose) {
        /**
         * Backwards-compatible source constructor for existing correction
         * clients/tests.
         */
        public LegacyLoanCorrectionRequest(String confirmReference, String reason,
                        String evidenceReference, LocalDate oldestUnpaidDueDate,
                        BigDecimal principalPaid, BigDecimal interestPaid, BigDecimal interestOutstanding,
                        BigDecimal managementFeePaid, BigDecimal managementFeeOutstanding,
                        BigDecimal penaltiesAssessed, BigDecimal penaltiesPaid, Integer durationMonths) {
                this(confirmReference, reason, evidenceReference, oldestUnpaidDueDate,
                                principalPaid, interestPaid, interestOutstanding, managementFeePaid,
                                managementFeeOutstanding, penaltiesAssessed, penaltiesPaid, durationMonths,
                                null, null, null, null);
        }

        /** Compatibility for callers providing all former monetary fields. */
        public LegacyLoanCorrectionRequest(String confirmReference, String reason,
                        String evidenceReference, LocalDate oldestUnpaidDueDate,
                        BigDecimal principalPaid, BigDecimal interestPaid, BigDecimal interestOutstanding,
                        BigDecimal managementFeePaid, BigDecimal managementFeeOutstanding,
                        BigDecimal penaltiesAssessed, BigDecimal penaltiesPaid, Integer durationMonths,
                        BigDecimal applicationFee, BigDecimal applicationFeePaid,
                        BigDecimal applicationFeeOutstanding) {
                this(confirmReference, reason, evidenceReference, oldestUnpaidDueDate,
                        principalPaid, interestPaid, interestOutstanding, managementFeePaid,
                        managementFeeOutstanding, penaltiesAssessed, penaltiesPaid, durationMonths,
                        applicationFee, applicationFeePaid, applicationFeeOutstanding, null);
        }
}
