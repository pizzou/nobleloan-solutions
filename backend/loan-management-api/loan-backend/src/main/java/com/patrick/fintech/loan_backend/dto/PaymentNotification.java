package com.patrick.fintech.loan_backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class PaymentNotification {

    private Long paymentId;

    private Long loanId;

    private String loanReference;

    private Long borrowerId;

    private Long organizationId;

    private BigDecimal amount;

    private BigDecimal principalPaid;

    private BigDecimal interestPaid;

    private BigDecimal penaltyPaid;

    private BigDecimal outstandingBalance;

    private String currency;

    private String paymentMethod;

    private String channel;

    private String transactionId;

    private String paymentReference;

    private String paymentStatus;

    private String loanStatus;

    private LocalDateTime paymentTimestamp;

    private String title;

    private String message;
}