package com.patrick.fintech.loan_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.patrick.fintech.loan_backend.model.Loan;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanRequest {

    @NotNull(message = "Borrower ID is required")
    private Long borrowerId;

    @NotNull(message = "Loan amount is required")
    @Positive(message = "Loan amount must be greater than zero")
    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("interestRate")
    private BigDecimal interestRate;

    private String interestRateType;


    @NotNull(message = "Loan duration is required")
    @Min(value = 1, message = "Loan duration must be at least 1 month")
    @Max(value = 6, message = "Loan duration cannot exceed 6 months")
    private Integer durationMonths;

    private String currency;

    private String startDate;

    private String purpose;

    private String notes;

    @JsonProperty("collateralValue")
    private BigDecimal collateralValue;

    private String collateralDescription;

    private Loan.LoanType loanType;

    private Loan.RepaymentFrequency repaymentFrequency;

    private String disbursementMethod;

    private String disbursementAccount;
}