package com.patrick.fintech.loan_backend.event;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Converts the persisted payment and loan state into an immutable
 * PaymentReceivedEvent and publishes it through Spring's application
 * event mechanism.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publishPaymentReceived(
            Loan loan,
            Payment payment,
            BigDecimal amount,
            BigDecimal principalPaid,
            BigDecimal interestPaid,
            BigDecimal penaltyPaid,
            BigDecimal outstandingBalance,
            LocalDateTime paymentTimestamp
    ) {

        if (loan == null) {

            log.warn(
                    "Cannot publish PaymentReceivedEvent because loan is null."
            );

            return;
        }

        if (payment == null) {

            log.warn(
                    "Cannot publish PaymentReceivedEvent because payment is null. loanId={}",
                    loan.getId()
            );

            return;
        }

        if (loan.getOrganization() == null
                || loan.getOrganization().getId() == null) {

            log.warn(
                    "Cannot publish PaymentReceivedEvent because organization " +
                            "is missing. loanId={}",
                    loan.getId()
            );

            return;
        }

        Long borrowerId =
                loan.getBorrower() != null
                        ? loan.getBorrower().getId()
                        : null;

        String loanReference =
                loan.getReferenceNumber() != null
                        && !loan.getReferenceNumber().isBlank()
                        ? loan.getReferenceNumber()
                        : "Loan #" + loan.getId();

        String currency =
                loan.getCurrency() != null
                        && !loan.getCurrency().isBlank()
                        ? loan.getCurrency()
                        : "RWF";

        BigDecimal assessedPenalty =
                normalize(
                        payment.getPenaltyDecimal()
                );

        BigDecimal collectedPenalty =
                normalize(
                        payment.getPenaltyPaidDecimal()
                );

        BigDecimal remainingPenalty =
                assessedPenalty
                        .subtract(collectedPenalty)
                        .max(BigDecimal.ZERO);

        PaymentReceivedEvent event =
                new PaymentReceivedEvent(

                        payment.getId(),

                        loan.getId(),

                        borrowerId,

                        loan.getOrganization().getId(),

                        normalize(amount),

                        normalize(principalPaid),

                        normalize(interestPaid),

                        normalize(penaltyPaid),

                        normalize(outstandingBalance),

                        normalize(
                                payment.getInterestComponentDecimal()
                        ),

                        normalize(
                                payment.getCycleInterestDueDecimal()
                        ),

                        normalize(
                                payment.getCycleInterestRemainingDecimal()
                        ),

                        normalize(
                                payment.getPrincipalComponentDecimal()
                        ),

                        assessedPenalty,

                        collectedPenalty,

                        remainingPenalty,

                        loanReference,

                        currency,

                        payment.getPaymentMethod(),

                        payment.getChannel(),

                        payment.getTransactionId(),

                        payment.getPaymentReference(),

                        payment.getPaidDate() != null
                                ? payment.getPaidDate()
                                : LocalDate.now(),

                        paymentTimestamp != null
                                ? paymentTimestamp
                                : LocalDateTime.now(),

                        loan.getStatus(),

                        payment.getStatus()
                );

        applicationEventPublisher.publishEvent(event);

        log.info(
                "PaymentReceivedEvent published. " +
                        "organizationId={}, loanId={}, paymentId={}, " +
                        "amount={}, transactionId={}",
                event.organizationId(),
                event.loanId(),
                event.paymentId(),
                event.amount(),
                event.transactionId()
        );
    }

    private BigDecimal normalize(BigDecimal value) {

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