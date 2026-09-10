package com.patrick.fintech.loan_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Data
public class LendingActionRequest {
    @NotBlank private String featureType;
    private Long loanId;
    private Long borrowerId;
    private String status;
    private String priority;
    private BigDecimal amount;
    private LocalDate dueDate;
    private Long assignedUserId;
    private Map<String, Object> payload;
}
