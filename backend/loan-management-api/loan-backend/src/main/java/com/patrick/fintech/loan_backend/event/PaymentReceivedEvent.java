package com.patrick.fintech.loan_backend.event;

import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;


public record PaymentReceivedEvent(

        Long paymentId,

        Long loanId,

        Long borrowerId,

        Long organizationId,

        BigDecimal amount,

        BigDecimal principalPaid,

        BigDecimal interestPaid,

        BigDecimal penaltyPaid,

        BigDecimal outstandingBalance,

        BigDecimal totalInterestPaid,

        BigDecimal totalInterestDue,

        BigDecimal remainingInterest,

        BigDecimal totalPrincipalPaid,

        BigDecimal totalPenalty,

        BigDecimal totalPenaltyPaid,

        BigDecimal remainingPenalty,

        String loanReference,

        String currency,

        String paymentMethod,

        String channel,

        String transactionId,

        String paymentReference,

        LocalDate paymentDate,

        LocalDateTime paymentTimestamp,

        LoanStatus loanStatus,

        Payment.PaymentStatus paymentStatus

) {

    public PaymentReceivedEvent {

        amount = normalize(amount);
        principalPaid = normalize(principalPaid);
        interestPaid = normalize(interestPaid);
        penaltyPaid = normalize(penaltyPaid);
        outstandingBalance = normalize(outstandingBalance);
        totalInterestPaid = normalize(totalInterestPaid);
        totalInterestDue = normalize(totalInterestDue);
        remainingInterest = normalize(remainingInterest);
        totalPrincipalPaid = normalize(totalPrincipalPaid);
        totalPenalty = normalize(totalPenalty);
        totalPenaltyPaid = normalize(totalPenaltyPaid);
        remainingPenalty = normalize(remainingPenalty);

        if (loanReference != null) {
            loanReference = loanReference.trim();
        }

        if (currency == null || currency.isBlank()) {
            currency = "RWF";
        } else {
            currency = currency.trim();
        }

        if (paymentMethod != null) {
            paymentMethod = paymentMethod.trim();
        }

        if (channel != null) {
            channel = channel.trim();
        }

        if (transactionId != null) {
            transactionId = transactionId.trim();
        }

        if (paymentReference != null) {
            paymentReference = paymentReference.trim();
        }

        if (paymentTimestamp == null) {
            paymentTimestamp = LocalDateTime.now();
        }
    }

    private static BigDecimal normalize(BigDecimal value) {

        if (value == null) {
            return BigDecimal.ZERO.setScale(
                    2,
                    RoundingMode.HALF_UP
            );
        }

        return value.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }
}