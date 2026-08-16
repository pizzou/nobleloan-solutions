package com.patrick.fintech.loan_backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Public API representation of a payment/installment. No JPA relationships. */
@Data
public class PaymentResponse {
    private Long id;
    private String paymentReference;
    private Long loanId;
    private String loanReference;
    private Long organizationId;
    private Long recordedById;
    private Integer installmentNumber;
    private BigDecimal amount;
    private BigDecimal principalComponent;
    private BigDecimal interestComponent;
    private BigDecimal managementFeeComponent;
    private BigDecimal extensionFeeComponent;
    private BigDecimal amountPaid;
    private BigDecimal scheduledInterest;
    private BigDecimal scheduledManagementFee;
    private BigDecimal cycleInterestDue;
    private BigDecimal cycleInterestRemaining;
    private BigDecimal cycleManagementFeeDue;
    private BigDecimal cycleManagementFeeRemaining;
    private LocalDateTime interestCalculationDate;
    private BigDecimal penalty;
    private BigDecimal penaltyPaid;
    private BigDecimal waivedAmount;
    private BigDecimal outstandingAfter;
    private Boolean paid;
    private LocalDate dueDate;
    private LocalDate paidDate;
    private String paymentMethod;
    private String transactionId;
    private String externalReference;
    private String channel;
    private String notes;
    private boolean late;
    private Integer daysLate;
    private LocalDateTime createdAt;
    private LocalDateTime verifiedAt;
    private LocalDateTime updatedAt;
}
