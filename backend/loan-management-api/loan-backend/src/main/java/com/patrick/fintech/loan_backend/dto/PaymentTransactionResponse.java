package com.patrick.fintech.loan_backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bank-safe immutable payment transaction view for organization users.
 * This represents an actual money movement event, not cumulative installment state.
 */
@Data
public class PaymentTransactionResponse {
    private Long id;
    private Long loanId;
    private String loanReference;
    private Long borrowerId;
    private String borrowerName;
    private Long organizationId;
    private Long recordedById;
    private String recordedByName;
    private String transactionReference;
    private BigDecimal amount;
    private BigDecimal principalComponent;
    private BigDecimal interestComponent;
    private BigDecimal managementFeeComponent;
    private BigDecimal extensionFeeComponent;
    private BigDecimal penaltyComponent;
    private BigDecimal unappliedAmount;
    private String provider;
    private String currency;
    private String externalReference;
    private String gatewayStatus;
    private String paymentMethod;
    private String channel;
    private String notes;
    private String status;
    private Boolean reversed;
    private LocalDateTime createdAt;
    private LocalDateTime reversedAt;
    private String reversalReason;
    private String reversalReference;
}
