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
 *
 * <p>
 * Borrower information is obtained from the persisted loan on the
 * backend. It is never trusted from the frontend notification payload.
 * </p>
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

        /*
         * ============================================================
         * BORROWER
         * ============================================================
         */

        Long borrowerId =
                loan.getBorrower() != null
                        ? loan.getBorrower().getId()
                        : null;

        String borrowerName =
                resolveBorrowerName(loan);

        /*
         * ============================================================
         * LOAN REFERENCE
         * ============================================================
         */

        String loanReference =
                loan.getReferenceNumber() != null
                        && !loan.getReferenceNumber().isBlank()
                        ? loan.getReferenceNumber().trim()
                        : "Loan #" + loan.getId();

        /*
         * ============================================================
         * CURRENCY
         * ============================================================
         */

        String currency =
                loan.getCurrency() != null
                        && !loan.getCurrency().isBlank()
                        ? loan.getCurrency().trim().toUpperCase()
                        : "RWF";

        /*
         * ============================================================
         * PENALTY
         * ============================================================
         */

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

        /*
         * ============================================================
         * PAYMENT EVENT
         * ============================================================
         */

        PaymentReceivedEvent event =
                new PaymentReceivedEvent(

                        payment.getId(),

                        loan.getId(),

                        borrowerId,

                        borrowerName,

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

        /*
         * ============================================================
         * PUBLISH AFTER PAYMENT STATE HAS BEEN PREPARED
         * ============================================================
         */

        applicationEventPublisher.publishEvent(
                event
        );

        log.info(
                "PaymentReceivedEvent published. " +
                        "organizationId={}, loanId={}, paymentId={}, " +
                        "borrowerId={}, borrowerName={}, amount={}, " +
                        "principalPaid={}, interestPaid={}, penaltyPaid={}, " +
                        "outstandingBalance={}, transactionId={}",
                event.organizationId(),
                event.loanId(),
                event.paymentId(),
                event.borrowerId(),
                event.borrowerName(),
                event.amount(),
                event.principalPaid(),
                event.interestPaid(),
                event.penaltyPaid(),
                event.outstandingBalance(),
                event.transactionId()
        );
    }

    /**
     * Resolves the borrower's display name entirely from the persisted
     * borrower associated with the loan.
     */
    private String resolveBorrowerName(
            Loan loan
    ) {

        if (loan.getBorrower() == null) {

            log.warn(
                    "Loan has no borrower. loanId={}",
                    loan.getId()
            );

            return "Unknown Borrower";
        }



        String name =
                loan.getBorrower().getFullName();

        if (name != null
                && !name.isBlank()) {

            return name.trim();
        }

        return "Unknown Borrower";
    }

    private BigDecimal normalize(
            BigDecimal value
    ) {

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