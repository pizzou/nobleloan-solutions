package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.WebhookEndpoint;
import com.patrick.fintech.loan_backend.repository.WebhookRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final WebhookRepository webhookRepo;

    private final WebhookDeliveryTransactionService
            webhookDeliveryTransactionService;

    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate;

    // ================================================================
    // DISPATCH WEBHOOK
    // ================================================================

    @Async
    public void dispatch(
            Organization organization,
            String eventType,
            Object payload
    ) {

        if (organization == null) {

            log.warn(
                    "[WEBHOOK] Dispatch skipped because organization is null. event={}",
                    eventType
            );

            return;
        }

        if (organization.getId() == null) {

            log.warn(
                    "[WEBHOOK] Dispatch skipped because organization ID is null. event={}",
                    eventType
            );

            return;
        }

        if (eventType == null || eventType.isBlank()) {

            log.warn(
                    "[WEBHOOK] Dispatch skipped because event type is empty. organization={}",
                    organization.getId()
            );

            return;
        }

        final Long organizationId =
                organization.getId();

        final String normalizedEventType =
                eventType.trim();

        log.info(
                "[WEBHOOK] =================================================="
        );

        log.info(
                "[WEBHOOK] DISPATCH STARTED. organization={}, event={}",
                organizationId,
                normalizedEventType
        );

        // ============================================================
        // FIND ACTIVE ENDPOINTS
        // ============================================================

        List<WebhookEndpoint> endpoints;

        try {

            endpoints =
                    webhookRepo
                            .findByOrganizationAndActiveTrue(
                                    organization
                            );

        } catch (Exception e) {

            log.error(
                    "[WEBHOOK] Failed to load active endpoints. " +
                            "organization={}, event={}",
                    organizationId,
                    normalizedEventType,
                    e
            );

            return;
        }

        // ============================================================
        // NO ACTIVE ENDPOINT
        // ============================================================

        if (
                endpoints == null
                        || endpoints.isEmpty()
        ) {

            log.warn(
                    "[WEBHOOK] NO ACTIVE ENDPOINT FOUND. " +
                            "organization={}, event={}. " +
                            "Event processing will continue normally.",
                    organizationId,
                    normalizedEventType
            );

            log.info(
                    "[WEBHOOK] DISPATCH FINISHED WITHOUT DELIVERY. " +
                            "organization={}, event={}",
                    organizationId,
                    normalizedEventType
            );

            return;
        }

        log.info(
                "[WEBHOOK] Found {} active endpoint(s). " +
                        "organization={}, event={}",
                endpoints.size(),
                organizationId,
                normalizedEventType
        );

        // ============================================================
        // BUILD PAYLOAD
        // ============================================================

        String body;

        try {

            Map<String, Object> webhookPayload =
                    new HashMap<>();

            webhookPayload.put(
                    "event",
                    normalizedEventType
            );

            webhookPayload.put(
                    "timestamp",
                    System.currentTimeMillis()
            );

            webhookPayload.put(
                    "organizationId",
                    organizationId
            );

            webhookPayload.put(
                    "data",
                    payload
            );

            body =
                    objectMapper.writeValueAsString(
                            webhookPayload
                    );

        } catch (Exception e) {

            log.error(
                    "[WEBHOOK] Failed to serialize payload. " +
                            "organization={}, event={}",
                    organizationId,
                    normalizedEventType,
                    e
            );

            return;
        }

        // ============================================================
        // PROCESS EACH ENDPOINT
        // ============================================================

        for (WebhookEndpoint endpoint : endpoints) {

            if (endpoint == null) {

                log.warn(
                        "[WEBHOOK] Repository returned null endpoint. " +
                                "organization={}, event={}",
                        organizationId,
                        normalizedEventType
                );

                continue;
            }

            Long endpointId =
                    endpoint.getId();

            // ========================================================
            // ACTIVE CHECK
            // ========================================================

            if (!endpoint.isActive()) {

                log.info(
                        "[WEBHOOK] Endpoint inactive. " +
                                "endpoint={}, event={}",
                        endpointId,
                        normalizedEventType
                );

                continue;
            }

            // ========================================================
            // ORGANIZATION CHECK
            // ========================================================

            if (
                    endpoint.getOrganization() == null
                            || endpoint.getOrganization().getId() == null
            ) {

                log.error(
                        "[WEBHOOK] Endpoint organization is missing. " +
                                "endpoint={}, event={}",
                        endpointId,
                        normalizedEventType
                );

                continue;
            }

            Long endpointOrganizationId =
                    endpoint
                            .getOrganization()
                            .getId();

            if (
                    !organizationId.equals(
                            endpointOrganizationId
                    )
            ) {

                log.error(
                        "[WEBHOOK] Organization mismatch. " +
                                "endpoint={}, expectedOrganization={}, " +
                                "endpointOrganization={}, event={}",
                        endpointId,
                        organizationId,
                        endpointOrganizationId,
                        normalizedEventType
                );

                continue;
            }

            // ========================================================
            // EVENT SUBSCRIPTION
            // ========================================================

            if (
                    !isSubscribed(
                            endpoint,
                            normalizedEventType
                    )
            ) {

                log.info(
                        "[WEBHOOK] Endpoint not subscribed. " +
                                "endpoint={}, event={}, subscriptions={}",
                        endpointId,
                        normalizedEventType,
                        endpoint.getSubscribedEvents()
                );

                continue;
            }

            // ========================================================
            // URL VALIDATION
            // ========================================================

            String url =
                    endpoint.getUrl();

            if (url == null || url.isBlank()) {

                log.error(
                        "[WEBHOOK] Endpoint URL is empty. " +
                                "endpoint={}, event={}",
                        endpointId,
                        normalizedEventType
                );

                handleFailure(
                        endpoint,
                        normalizedEventType,
                        "Empty webhook URL"
                );

                continue;
            }

            url =
                    url.trim();

            // ========================================================
            // CREATE DELIVERY HISTORY RECORD
            // ========================================================

            Long deliveryId = null;

            try {

                var delivery =
                        webhookDeliveryTransactionService
                                .createPendingDelivery(
                                        endpoint,
                                        organization,
                                        normalizedEventType,
                                        body,
                                        url
                                );

                if (delivery != null) {

                    deliveryId =
                            delivery.getId();
                }

            } catch (Exception e) {

                /*
                 * Do not prevent the actual webhook from being sent
                 * just because delivery-history persistence failed.
                 */

                log.error(
                        "[WEBHOOK] Could not create PENDING delivery record. " +
                                "organization={}, endpoint={}, event={}",
                        organizationId,
                        endpointId,
                        normalizedEventType,
                        e
                );
            }

            // ========================================================
            // HEADERS
            // ========================================================

            HttpHeaders headers =
                    new HttpHeaders();

            headers.setContentType(
                    MediaType.APPLICATION_JSON
            );

            headers.setAccept(
                    List.of(
                            MediaType.APPLICATION_JSON
                    )
            );

            headers.set(
                    "X-Webhook-Event",
                    normalizedEventType
            );

            headers.set(
                    "X-Webhook-Organization",
                    String.valueOf(
                            organizationId
                    )
            );

            headers.set(
                    "X-Webhook-Endpoint",
                    endpointId != null
                            ? String.valueOf(endpointId)
                            : "unknown"
            );

            headers.set(
                    "X-Webhook-Delivery",
                    "loansaas"
            );

            // ========================================================
            // HMAC
            // ========================================================

            if (
                    endpoint.getSecret() != null
                            && !endpoint.getSecret().isBlank()
            ) {

                try {

                    String signature =
                            sign(
                                    body,
                                    endpoint.getSecret()
                            );

                    headers.set(
                            "X-Webhook-Signature",
                            signature
                    );

                } catch (Exception e) {

                    log.error(
                            "[WEBHOOK] HMAC generation failed. " +
                                    "endpoint={}, event={}",
                            endpointId,
                            normalizedEventType,
                            e
                    );

                    if (deliveryId != null) {

                        webhookDeliveryTransactionService
                                .markFailure(
                                        deliveryId,
                                        null,
                                        null,
                                        "HMAC signature generation failed"
                                );
                    }

                    handleFailure(
                            endpoint,
                            normalizedEventType,
                            "HMAC signature generation failed"
                    );

                    continue;
                }
            }

            // ========================================================
            // SEND HTTP REQUEST
            // ========================================================

            try {

                log.info(
                        "[WEBHOOK] SENDING EVENT. " +
                                "event={}, organization={}, endpoint={}, deliveryId={}, url={}",
                        normalizedEventType,
                        organizationId,
                        endpointId,
                        deliveryId,
                        url
                );

                HttpEntity<String> request =
                        new HttpEntity<>(
                                body,
                                headers
                        );

                ResponseEntity<String> response =
                        restTemplate.exchange(
                                url,
                                HttpMethod.POST,
                                request,
                                String.class
                        );

                int httpStatus =
                        response
                                .getStatusCode()
                                .value();

                String responseBody =
                        response.getBody();

                // ====================================================
                // SUCCESS
                // ====================================================

                if (
                        response
                                .getStatusCode()
                                .is2xxSuccessful()
                ) {

                    if (deliveryId != null) {

                        try {

                            webhookDeliveryTransactionService
                                    .markSuccess(
                                            deliveryId,
                                            httpStatus,
                                            responseBody
                                    );

                        } catch (Exception e) {

                            log.error(
                                    "[WEBHOOK] HTTP delivery succeeded, " +
                                            "but delivery history could not be updated. " +
                                            "deliveryId={}",
                                    deliveryId,
                                    e
                            );
                        }
                    }

                    markEndpointSuccess(
                            endpoint,
                            normalizedEventType,
                            httpStatus
                    );

                    log.info(
                            "[WEBHOOK] DELIVERY SUCCESS. " +
                                    "organization={}, endpoint={}, " +
                                    "event={}, deliveryId={}, HTTP={}",
                            organizationId,
                            endpointId,
                            normalizedEventType,
                            deliveryId,
                            httpStatus
                    );

                } else {

                    // =================================================
                    // NON-2XX RESPONSE
                    // =================================================

                    String error =
                            "HTTP " + httpStatus;

                    if (deliveryId != null) {

                        try {

                            webhookDeliveryTransactionService
                                    .markFailure(
                                            deliveryId,
                                            httpStatus,
                                            responseBody,
                                            error
                                    );

                        } catch (Exception e) {

                            log.error(
                                    "[WEBHOOK] Failed to persist failed delivery. " +
                                            "deliveryId={}",
                                    deliveryId,
                                    e
                            );
                        }
                    }

                    handleFailure(
                            endpoint,
                            normalizedEventType,
                            error
                    );

                    log.warn(
                            "[WEBHOOK] DELIVERY FAILED. " +
                                    "organization={}, endpoint={}, " +
                                    "event={}, deliveryId={}, HTTP={}",
                            organizationId,
                            endpointId,
                            normalizedEventType,
                            deliveryId,
                            httpStatus
                    );
                }

            } catch (Exception e) {

                String error =
                        shorten(
                                e.getMessage()
                        );

                if (
                        error == null
                                || error.isBlank()
                ) {

                    error =
                            e.getClass()
                                    .getSimpleName();
                }

                // ====================================================
                // SAVE DELIVERY FAILURE
                // ====================================================

                if (deliveryId != null) {

                    try {

                        webhookDeliveryTransactionService
                                .markFailure(
                                        deliveryId,
                                        null,
                                        null,
                                        error
                                );

                    } catch (Exception persistenceException) {

                        log.error(
                                "[WEBHOOK] Could not persist delivery failure. " +
                                        "deliveryId={}",
                                deliveryId,
                                persistenceException
                        );
                    }
                }

                // ====================================================
                // UPDATE ENDPOINT FAILURE
                // ====================================================

                handleFailure(
                        endpoint,
                        normalizedEventType,
                        error
                );

                log.error(
                        "[WEBHOOK] DELIVERY EXCEPTION. " +
                                "organization={}, endpoint={}, event={}, " +
                                "deliveryId={}, url={}, error={}",
                        organizationId,
                        endpointId,
                        normalizedEventType,
                        deliveryId,
                        url,
                        error,
                        e
                );
            }
        }

        log.info(
                "[WEBHOOK] DISPATCH FINISHED. " +
                        "organization={}, event={}",
                organizationId,
                normalizedEventType
        );

        log.info(
                "[WEBHOOK] =================================================="
        );
    }

    // ================================================================
    // ENDPOINT SUCCESS
    // ================================================================

    private void markEndpointSuccess(
            WebhookEndpoint endpoint,
            String eventType,
            int httpStatus
    ) {

        endpoint.setLastDeliveryAt(
                LocalDateTime.now()
        );

        endpoint.setLastDeliveryStatus(
                "SUCCESS"
        );

        endpoint.setFailureCount(
                0
        );

        try {

            webhookRepo.save(
                    endpoint
            );

        } catch (Exception e) {

            log.error(
                    "[WEBHOOK] Delivery succeeded but endpoint " +
                            "status could not be persisted. " +
                            "endpoint={}, event={}",
                    endpoint.getId(),
                    eventType,
                    e
            );
        }
    }

    // ================================================================
    // ENDPOINT FAILURE
    // ================================================================

    private void handleFailure(
            WebhookEndpoint endpoint,
            String eventType,
            String reason
    ) {

        int failures =
                endpoint.getFailureCount() == null
                        ? 1
                        : endpoint.getFailureCount() + 1;

        endpoint.setLastDeliveryAt(
                LocalDateTime.now()
        );

        endpoint.setLastDeliveryStatus(
                "FAILED: "
                        + shorten(reason)
        );

        endpoint.setFailureCount(
                failures
        );

        /*
         * Disable after 10 consecutive failures.
         */
        if (failures >= 10) {

            endpoint.setActive(
                    false
            );

            log.warn(
                    "[WEBHOOK] Endpoint automatically disabled after " +
                            "{} consecutive failures. endpoint={}, event={}",
                    failures,
                    endpoint.getId(),
                    eventType
            );
        }

        try {

            webhookRepo.save(
                    endpoint
            );

        } catch (Exception e) {

            /*
             * Never allow endpoint status persistence failure
             * to break the main business transaction.
             */

            log.error(
                    "[WEBHOOK] Could not persist endpoint failure state. " +
                            "endpoint={}, event={}",
                    endpoint.getId(),
                    eventType,
                    e
            );
        }
    }

    // ================================================================
    // EVENT SUBSCRIPTION
    // ================================================================

    private boolean isSubscribed(
            WebhookEndpoint endpoint,
            String eventType
    ) {

        List<String> subscribedEvents =
                endpoint.getSubscribedEvents();

        /*
         * Empty/null subscriptions mean ALL EVENTS.
         */
        if (
                subscribedEvents == null
                        || subscribedEvents.isEmpty()
        ) {

            return true;
        }

        String normalizedEvent =
                eventType.trim();

        return subscribedEvents
                .stream()
                .filter(
                        event ->
                                event != null
                                        && !event.isBlank()
                )
                .map(
                        String::trim
                )
                .anyMatch(
                        event ->
                                event.equalsIgnoreCase(
                                        normalizedEvent
                                )
                );
    }

    // ================================================================
    // TEST WEBHOOK
    // ================================================================

    public void sendTest(
            WebhookEndpoint endpoint
    ) {

        if (endpoint == null) {

            throw new IllegalArgumentException(
                    "Webhook endpoint cannot be null"
            );
        }

        if (!endpoint.isActive()) {

            throw new IllegalStateException(
                    "Webhook endpoint is inactive"
            );
        }

        Organization organization =
                endpoint.getOrganization();

        if (organization == null) {

            throw new IllegalStateException(
                    "Webhook endpoint has no organization"
            );
        }

        Map<String, Object> testData =
                new HashMap<>();

        testData.put(
                "message",
                "Webhook test successful"
        );

        testData.put(
                "webhookId",
                endpoint.getId()
        );

        testData.put(
                "organizationId",
                organization.getId()
        );

        testData.put(
                "sentAt",
                System.currentTimeMillis()
        );

        dispatch(
                organization,
                "WEBHOOK_TEST",
                testData
        );
    }

    // ================================================================
    // HMAC SHA-256
    // ================================================================

    private String sign(
            String payload,
            String secret
    ) throws Exception {

        Mac mac =
                Mac.getInstance(
                        "HmacSHA256"
                );

        SecretKeySpec key =
                new SecretKeySpec(
                        secret.getBytes(
                                StandardCharsets.UTF_8
                        ),
                        "HmacSHA256"
                );

        mac.init(
                key
        );

        byte[] hash =
                mac.doFinal(
                        payload.getBytes(
                                StandardCharsets.UTF_8
                        )
                );

        StringBuilder result =
                new StringBuilder(
                        "sha256="
                );

        for (byte b : hash) {

            result.append(
                    String.format(
                            "%02x",
                            b
                    )
            );
        }

        return result.toString();
    }

    // ================================================================
    // SHORTEN ERROR
    // ================================================================

    private String shorten(
            String value
    ) {

        if (value == null) {

            return "Unknown error";
        }

        String cleaned =
                value
                        .replace(
                                "\n",
                                " "
                        )
                        .replace(
                                "\r",
                                " "
                        )
                        .trim();

        if (cleaned.isBlank()) {

            return "Unknown error";
        }

        if (cleaned.length() > 5000) {

            return cleaned.substring(
                    0,
                    5000
            );
        }

        return cleaned;
    }
}