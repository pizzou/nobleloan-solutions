package com.patrick.fintech.loan_backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanExtensionRequest {

    @NotNull(message = "Extension duration is required")
    @Min(value = 1, message = "Extension duration must be at least 1 month")
    @Max(value = 5, message = "Extension duration is too large")
    private Integer extensionMonths;

    @NotBlank(message = "Extension reason is required")
    private String reason;
}