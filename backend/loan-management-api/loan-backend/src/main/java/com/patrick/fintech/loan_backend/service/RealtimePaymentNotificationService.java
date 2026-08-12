package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.PaymentNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes payment notifications through WebSocket/STOMP.
 *
 * <p>
 * This service receives an immutable notification DTO rather than JPA
 * entities. Therefore it does not depend on an active Hibernate session.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RealtimePaymentNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Publishes the payment notification to both:
     *
     * <ul>
     *     <li>Organization-wide payment subscribers</li>
     *     <li>Loan-specific payment subscribers</li>
     * </ul>
     */
    public void publishPaymentReceived(
            PaymentNotification notification
    ) {

        if (notification == null) {

            log.warn(
                    "Cannot publish payment notification: notification is null."
            );

            return;
        }

        Long organizationId =
                notification.getOrganizationId();

        Long loanId =
                notification.getLoanId();

        Long paymentId =
                notification.getPaymentId();

        if (organizationId == null) {

            log.warn(
                    "Cannot publish payment notification: organizationId is null. " +
                            "loanId={}, paymentId={}",
                    loanId,
                    paymentId
            );

            return;
        }

        if (loanId == null) {

            log.warn(
                    "Cannot publish payment notification: loanId is null. " +
                            "organizationId={}, paymentId={}",
                    organizationId,
                    paymentId
            );

            return;
        }

        String organizationDestination =
                "/topic/organization/"
                        + organizationId
                        + "/payments";

        String loanDestination =
                "/topic/loan/"
                        + loanId
                        + "/payments";

        messagingTemplate.convertAndSend(
                organizationDestination,
                notification
        );

        messagingTemplate.convertAndSend(
                loanDestination,
                notification
        );

        log.info(
                "REALTIME PAYMENT NOTIFICATION PUBLISHED. " +
                        "organizationId={}, loanId={}, paymentId={}, " +
                        "amount={}, transactionId={}",
                organizationId,
                loanId,
                paymentId,
                notification.getAmount(),
                notification.getTransactionId()
        );
    }
}