package com.patrick.fintech.loan_backend.listener;

import com.patrick.fintech.loan_backend.dto.PaymentNotification;
import com.patrick.fintech.loan_backend.event.PaymentReceivedEvent;
import com.patrick.fintech.loan_backend.service.RealtimePaymentNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Consumes payment events after the database transaction has successfully
 * committed.
 *
 * <p>
 * This prevents realtime notifications from being emitted for payments
 * that subsequently roll back.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReceivedEventListener {

    private final RealtimePaymentNotificationService realtimePaymentNotificationService;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handlePaymentReceived(
            PaymentReceivedEvent event
    ) {

        if (event == null) {

            log.warn(
                    "Received null PaymentReceivedEvent."
            );

            return;
        }

        try {

            PaymentNotification notification =
                    PaymentNotification.builder()

                            .paymentId(
                                    event.paymentId()
                            )

                            .loanId(
                                    event.loanId()
                            )

                            .loanReference(
                                    event.loanReference()
                            )

                            .borrowerId(
                                    event.borrowerId()
                            )

                            .organizationId(
                                    event.organizationId()
                            )

                            .amount(
                                    event.amount()
                            )

                            .principalPaid(
                                    event.principalPaid()
                            )

                            .interestPaid(
                                    event.interestPaid()
                            )

                            .penaltyPaid(
                                    event.penaltyPaid()
                            )

                            .outstandingBalance(
                                    event.outstandingBalance()
                            )

                            .currency(
                                    event.currency()
                            )

                            .paymentMethod(
                                    event.paymentMethod()
                            )

                            .channel(
                                    event.channel()
                            )

                            .transactionId(
                                    event.transactionId()
                            )

                            .paymentReference(
                                    event.paymentReference()
                            )

                            .paymentStatus(
                                    event.paymentStatus() != null
                                            ? event.paymentStatus().name()
                                            : null
                            )

                            .loanStatus(
                                    event.loanStatus() != null
                                            ? event.loanStatus().name()
                                            : null
                            )

                            .paymentTimestamp(
                                    event.paymentTimestamp()
                            )

                            .title(
                                    "Payment Received"
                            )

                            .message(
                                    event.currency()
                                            + " "
                                            + event.amount()
                                            + " payment received for loan "
                                            + event.loanReference()
                            )

                            .build();

            realtimePaymentNotificationService
                    .publishPaymentReceived(
                            notification
                    );

            log.info(
                    "PaymentReceivedEvent processed AFTER COMMIT. " +
                            "organizationId={}, loanId={}, paymentId={}, amount={}",
                    event.organizationId(),
                    event.loanId(),
                    event.paymentId(),
                    event.amount()
            );

        } catch (Exception e) {

            /*
             * The payment transaction has already committed.
             *
             * Notification failure must therefore never cause the payment
             * itself to be rolled back.
             */
            log.error(
                    "Failed to process PaymentReceivedEvent AFTER COMMIT. " +
                            "organizationId={}, loanId={}, paymentId={}, " +
                            "transactionId={}",
                    event.organizationId(),
                    event.loanId(),
                    event.paymentId(),
                    event.transactionId(),
                    e
            );
        }
    }
}