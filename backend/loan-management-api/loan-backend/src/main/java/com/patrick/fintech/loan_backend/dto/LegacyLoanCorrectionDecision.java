package com.patrick.fintech.loan_backend.dto;

/** Independent Business Owner approval/rejection of a historical import correction. */
public record LegacyLoanCorrectionDecision(String confirmReference, String reason) {}
