package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.WebhookDelivery;
import com.patrick.fintech.loan_backend.model.WebhookEndpoint;
import com.patrick.fintech.loan_backend.repository.WebhookDeliveryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookDeliveryTransactionService {

    private final WebhookDeliveryRepository webhookDeliveryRepo;

    // ================================================================
    // CREATE PENDING DELIVERY
    // ================================================================

    @Transactional(
            propagation = Propagation.REQUIRES_NEW
    )
    public WebhookDelivery createPendingDelivery(
            WebhookEndpoint endpoint,
            Organization organization,
            String eventType,
            String payload,
            String endpointUrl
    ) {

        if (endpoint == null) {
            throw new IllegalArgumentException(
                    "Webhook endpoint cannot be null"
            );
        }

        if (organization == null) {
            throw new IllegalArgumentException(
                    "Webhook organization cannot be null"
            );
        }

        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException(
                    "Webhook event type cannot be empty"
            );
        }

        WebhookDelivery delivery =
                WebhookDelivery.builder()
                        .webhookEndpoint(endpoint)
                        .organization(organization)
                        .eventType(eventType)
                        .payload(payload)
                        .endpointUrl(endpointUrl)
                        .status("PENDING")
                        .attemptCount(1)
                        .createdAt(LocalDateTime.now())
                        .build();

        WebhookDelivery saved =
                webhookDeliveryRepo.save(
                        delivery
                );

        log.info(
                "[WEBHOOK DELIVERY] PENDING delivery created. " +
                        "deliveryId={}, organization={}, endpoint={}, event={}",
                saved.getId(),
                organization.getId(),
                endpoint.getId(),
                eventType
        );

        return saved;
    }

    // ================================================================
    // MARK SUCCESS
    // ================================================================

    @Transactional(
            propagation = Propagation.REQUIRES_NEW
    )
    public void markSuccess(
            Long deliveryId,
            Integer httpStatus,
            String responseBody
    ) {

        if (deliveryId == null) {
            log.warn(
                    "[WEBHOOK DELIVERY] Cannot mark success because delivery ID is null"
            );
            return;
        }

        WebhookDelivery delivery =
                webhookDeliveryRepo.findById(
                        deliveryId
                ).orElse(null);

        if (delivery == null) {

            log.warn(
                    "[WEBHOOK DELIVERY] Delivery {} not found while marking SUCCESS",
                    deliveryId
            );

            return;
        }

        delivery.setStatus("SUCCESS");

        delivery.setHttpStatus(
                httpStatus
        );

        delivery.setResponseBody(
                shorten(responseBody)
        );

        delivery.setErrorMessage(
                null
        );

        delivery.setDeliveredAt(
                LocalDateTime.now()
        );

        if (delivery.getAttemptCount() == null) {
            delivery.setAttemptCount(1);
        }

        webhookDeliveryRepo.save(
                delivery
        );

        log.info(
                "[WEBHOOK DELIVERY] SUCCESS persisted. " +
                        "deliveryId={}, HTTP={}",
                deliveryId,
                httpStatus
        );
    }

    // ================================================================
    // MARK FAILURE
    // ================================================================

    @Transactional(
            propagation = Propagation.REQUIRES_NEW
    )
    public void markFailure(
            Long deliveryId,
            Integer httpStatus,
            String responseBody,
            String errorMessage
    ) {

        if (deliveryId == null) {

            log.warn(
                    "[WEBHOOK DELIVERY] Cannot mark failure because delivery ID is null"
            );

            return;
        }

        WebhookDelivery delivery =
                webhookDeliveryRepo.findById(
                        deliveryId
                ).orElse(null);

        if (delivery == null) {

            log.warn(
                    "[WEBHOOK DELIVERY] Delivery {} not found while marking FAILED",
                    deliveryId
            );

            return;
        }

        delivery.setStatus(
                "FAILED"
        );

        delivery.setHttpStatus(
                httpStatus
        );

        delivery.setResponseBody(
                shorten(responseBody)
        );

        delivery.setErrorMessage(
                shorten(errorMessage)
        );

        delivery.setDeliveredAt(
                LocalDateTime.now()
        );

        if (delivery.getAttemptCount() == null) {

            delivery.setAttemptCount(
                    1
            );
        }

        webhookDeliveryRepo.save(
                delivery
        );

        log.warn(
                "[WEBHOOK DELIVERY] FAILED persisted. " +
                        "deliveryId={}, HTTP={}, error={}",
                deliveryId,
                httpStatus,
                shorten(errorMessage)
        );
    }

    // ================================================================
    // SHORTEN
    // ================================================================

    private String shorten(
            String value
    ) {

        if (value == null) {
            return null;
        }

        String cleaned =
                value
                        .replace("\n", " ")
                        .replace("\r", " ")
                        .trim();

        if (cleaned.isBlank()) {
            return null;
        }

        /*
         * Prevent very large external responses/errors from
         * unnecessarily filling the database.
         */
        if (cleaned.length() > 5000) {

            return cleaned.substring(
                    0,
                    5000
            );
        }

        return cleaned;
    }
}