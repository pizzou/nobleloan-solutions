package com.patrick.fintech.loan_backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanRestructuringRequest {

    @NotNull(message = "New loan duration is required")
    @Min(value = 1, message = "New loan duration must be at least 1 month")
    @Max(value = 6, message = "New loan duration cannot exceed 6 months")
    private Integer newMonths;

    @DecimalMin(value = "5.00", message = "Interest rate is fixed at 5% monthly")
    @DecimalMax(value = "5.00", message = "Interest rate is fixed at 5% monthly")
    private BigDecimal newRate;

    @NotBlank(message = "Restructuring reason is required")
    private String reason;
}