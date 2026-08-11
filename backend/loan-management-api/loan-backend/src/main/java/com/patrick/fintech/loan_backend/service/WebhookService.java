package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.WebhookDelivery;
import com.patrick.fintech.loan_backend.model.WebhookEndpoint;
import com.patrick.fintech.loan_backend.repository.WebhookDeliveryRepository;
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
import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final WebhookRepository webhookRepo;

    private final WebhookDeliveryRepository webhookDeliveryRepo;

    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate;

    // ============================================================
    // DISPATCH WEBHOOK
    // ============================================================

    @Async("loansaasAsyncExecutor")
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

        dispatchByOrganizationId(
                organization.getId(),
                eventType,
                payload
        );
    }

    // ============================================================
    // DISPATCH BY ORGANIZATION ID
    // ============================================================

    @Async("loansaasAsyncExecutor")
    public void dispatchByOrganizationId(
            Long organizationId,
            String eventType,
            Object payload
    ) {

        if (organizationId == null) {

            log.warn(
                    "[WEBHOOK] Dispatch skipped because organization ID is null. event={}",
                    eventType
            );

            return;
        }

        if (eventType == null || eventType.isBlank()) {

            log.warn(
                    "[WEBHOOK] Dispatch skipped because event type is empty. organization={}",
                    organizationId
            );

            return;
        }

        String normalizedEventType =
                eventType.trim();

        log.info(
                "[WEBHOOK] =================================================="
        );

        log.info(
                "[WEBHOOK] DISPATCH STARTED. organization={}, event={}",
                organizationId,
                normalizedEventType
        );

        // ========================================================
        // FIND ACTIVE ENDPOINTS
        // ========================================================

        List<WebhookEndpoint> endpoints;

        try {

            endpoints =
                    webhookRepo
                            .findByOrganization_IdAndActiveTrue(
                                    organizationId
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

        // ========================================================
        // NO ACTIVE ENDPOINT
        // ========================================================

        if (endpoints == null || endpoints.isEmpty()) {

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

        // ========================================================
        // BUILD PAYLOAD
        // ========================================================

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
                    "[WEBHOOK] Could not serialize webhook payload. " +
                            "organization={}, event={}",
                    organizationId,
                    normalizedEventType,
                    e
            );

            return;
        }

        // ========================================================
        // PROCESS ENDPOINTS
        // ========================================================

        for (WebhookEndpoint endpoint : endpoints) {

            if (endpoint == null) {
                continue;
            }

            Long endpointId =
                    endpoint.getId();

            // ====================================================
            // SAFETY
            // ====================================================

            if (!endpoint.isActive()) {

                log.info(
                        "[WEBHOOK] Endpoint inactive. endpoint={}, event={}",
                        endpointId,
                        normalizedEventType
                );

                continue;
            }

            if (
                    endpoint.getOrganization() == null
                            || endpoint.getOrganization().getId() == null
            ) {

                log.error(
                        "[WEBHOOK] Endpoint organization missing. endpoint={}",
                        endpointId
                );

                continue;
            }

            if (
                    !organizationId.equals(
                            endpoint.getOrganization().getId()
                    )
            ) {

                log.error(
                        "[WEBHOOK] Organization mismatch. " +
                                "endpoint={}, expectedOrganization={}, endpointOrganization={}",
                        endpointId,
                        organizationId,
                        endpoint.getOrganization().getId()
                );

                continue;
            }

            // ====================================================
            // EVENT SUBSCRIPTION
            // ====================================================

            if (
                    !isSubscribed(
                            endpoint,
                            normalizedEventType
                    )
            ) {

                log.info(
                        "[WEBHOOK] Endpoint not subscribed to event. " +
                                "endpoint={}, event={}, subscriptions={}",
                        endpointId,
                        normalizedEventType,
                        endpoint.getSubscribedEvents()
                );

                continue;
            }

            // ====================================================
            // URL
            // ====================================================

            String url =
                    endpoint.getUrl();

            if (url == null || url.isBlank()) {

                log.error(
                        "[WEBHOOK] Endpoint has empty URL. " +
                                "endpoint={}, event={}",
                        endpointId,
                        normalizedEventType
                );

                saveFailedDelivery(
                        endpoint,
                        normalizedEventType,
                        body,
                        "Empty webhook URL",
                        null,
                        null
                );

                markFailure(
                        endpoint,
                        "Empty webhook URL"
                );

                continue;
            }

            url =
                    url.trim();

            // ====================================================
            // CREATE DELIVERY RECORD
            // ====================================================

            WebhookDelivery delivery =
                    createPendingDelivery(
                            endpoint,
                            normalizedEventType,
                            body,
                            url
                    );

            // ====================================================
            // HEADERS
            // ====================================================

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

            headers.set(
                    "X-Webhook-Version",
                    "1"
            );

            // ====================================================
            // HMAC
            // ====================================================

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
                            "[WEBHOOK] Signature generation failed. " +
                                    "endpoint={}, event={}",
                            endpointId,
                            normalizedEventType,
                            e
                    );

                    updateDeliveryFailure(
                            delivery,
                            "Signature generation error",
                            null,
                            null
                    );

                    markFailure(
                            endpoint,
                            "Signature generation error"
                    );

                    continue;
                }
            }

            // ====================================================
            // SEND
            // ====================================================

            try {

                log.info(
                        "[WEBHOOK] SENDING EVENT. " +
                                "event={}, organization={}, endpoint={}, url={}",
                        normalizedEventType,
                        organizationId,
                        endpointId,
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
                        response.getStatusCode().value();

                String responseBody =
                        response.getBody();

                // =================================================
                // SUCCESS
                // =================================================

                if (
                        response.getStatusCode()
                                .is2xxSuccessful()
                ) {

                    updateDeliverySuccess(
                            delivery,
                            httpStatus,
                            responseBody
                    );

                    markSuccess(
                            endpoint,
                            normalizedEventType,
                            httpStatus
                    );

                    log.info(
                            "[WEBHOOK] DELIVERY SUCCESS. " +
                                    "event={}, organization={}, endpoint={}, HTTP={}",
                            normalizedEventType,
                            organizationId,
                            endpointId,
                            httpStatus
                    );

                } else {

                    String reason =
                            "HTTP " + httpStatus;

                    updateDeliveryFailure(
                            delivery,
                            reason,
                            httpStatus,
                            responseBody
                    );

                    markFailure(
                            endpoint,
                            reason
                    );

                    log.warn(
                            "[WEBHOOK] DELIVERY FAILED. " +
                                    "event={}, organization={}, endpoint={}, HTTP={}",
                            normalizedEventType,
                            organizationId,
                            endpointId,
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

                updateDeliveryFailure(
                        delivery,
                        error,
                        null,
                        null
                );

                markFailure(
                        endpoint,
                        error
                );

                log.error(
                        "[WEBHOOK] DELIVERY EXCEPTION. " +
                                "event={}, organization={}, endpoint={}, url={}, error={}",
                        normalizedEventType,
                        organizationId,
                        endpointId,
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

    // ============================================================
    // CREATE PENDING DELIVERY
    // ============================================================

    @Transactional
    protected WebhookDelivery createPendingDelivery(
            WebhookEndpoint endpoint,
            String eventType,
            String payload,
            String url
    ) {

        Organization organization =
                endpoint.getOrganization();

        WebhookDelivery delivery =
                WebhookDelivery.builder()
                        .webhookEndpoint(endpoint)
                        .organization(organization)
                        .eventType(eventType)
                        .payload(payload)
                        .endpointUrl(url)
                        .status("PENDING")
                        .attemptCount(1)
                        .createdAt(LocalDateTime.now())
                        .build();

        return webhookDeliveryRepo.save(
                delivery
        );
    }

    // ============================================================
    // UPDATE DELIVERY SUCCESS
    // ============================================================

    @Transactional
    protected void updateDeliverySuccess(
            WebhookDelivery delivery,
            int httpStatus,
            String responseBody
    ) {

        delivery.setStatus(
                "SUCCESS"
        );

        delivery.setHttpStatus(
                httpStatus
        );

        delivery.setResponseBody(
                shortenResponseBody(
                        responseBody
                )
        );

        delivery.setErrorMessage(
                null
        );

        delivery.setDeliveredAt(
                LocalDateTime.now()
        );

        webhookDeliveryRepo.save(
                delivery
        );
    }

    // ============================================================
    // UPDATE DELIVERY FAILURE
    // ============================================================

    @Transactional
    protected void updateDeliveryFailure(
            WebhookDelivery delivery,
            String errorMessage,
            Integer httpStatus,
            String responseBody
    ) {

        delivery.setStatus(
                "FAILED"
        );

        delivery.setHttpStatus(
                httpStatus
        );

        delivery.setResponseBody(
                shortenResponseBody(
                        responseBody
                )
        );

        delivery.setErrorMessage(
                shorten(
                        errorMessage
                )
        );

        delivery.setDeliveredAt(
                LocalDateTime.now()
        );

        webhookDeliveryRepo.save(
                delivery
        );
    }

    // ============================================================
    // SAVE FAILED DELIVERY
    // ============================================================

    @Transactional
    protected WebhookDelivery saveFailedDelivery(
            WebhookEndpoint endpoint,
            String eventType,
            String payload,
            String errorMessage,
            Integer httpStatus,
            String responseBody
    ) {

        WebhookDelivery delivery =
                WebhookDelivery.builder()
                        .webhookEndpoint(endpoint)
                        .organization(endpoint.getOrganization())
                        .eventType(eventType)
                        .payload(payload)
                        .endpointUrl(endpoint.getUrl())
                        .status("FAILED")
                        .httpStatus(httpStatus)
                        .responseBody(
                                shortenResponseBody(
                                        responseBody
                                )
                        )
                        .errorMessage(
                                shorten(
                                        errorMessage
                                )
                        )
                        .attemptCount(1)
                        .createdAt(LocalDateTime.now())
                        .deliveredAt(LocalDateTime.now())
                        .build();

        return webhookDeliveryRepo.save(
                delivery
        );
    }

    // ============================================================
    // SUCCESS
    // ============================================================

    @Transactional
    protected void markSuccess(
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

            log.info(
                    "[WEBHOOK] Endpoint status updated successfully. " +
                            "endpoint={}, event={}, HTTP={}",
                    endpoint.getId(),
                    eventType,
                    httpStatus
            );

        } catch (Exception e) {

            log.error(
                    "[WEBHOOK] Webhook delivered successfully but " +
                            "endpoint status could not be saved. endpoint={}",
                    endpoint.getId(),
                    e
            );
        }
    }

    // ============================================================
    // FAILURE
    // ============================================================

    @Transactional
    protected void markFailure(
            WebhookEndpoint endpoint,
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
                "FAILED: " +
                        shorten(reason)
        );

        endpoint.setFailureCount(
                failures
        );

        /*
         * Automatically disable after 10 consecutive failures.
         */
        if (failures >= 10) {

            endpoint.setActive(
                    false
            );

            log.warn(
                    "[WEBHOOK] Endpoint automatically disabled after " +
                            "{} consecutive failures. endpoint={}",
                    failures,
                    endpoint.getId()
            );
        }

        try {

            webhookRepo.save(
                    endpoint
            );

        } catch (Exception e) {

            log.error(
                    "[WEBHOOK] Could not persist endpoint failure status. " +
                            "endpoint={}",
                    endpoint.getId(),
                    e
            );
        }
    }

    // ============================================================
    // EVENT SUBSCRIPTION
    // ============================================================

    private boolean isSubscribed(
            WebhookEndpoint endpoint,
            String eventType
    ) {

        List<String> subscribedEvents =
                endpoint.getSubscribedEvents();

        /*
         * Empty/null means all events.
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

    // ============================================================
    // TEST WEBHOOK
    // ============================================================

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

        if (endpoint.getOrganization() == null) {

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
                endpoint.getOrganization().getId()
        );

        testData.put(
                "sentAt",
                System.currentTimeMillis()
        );

        dispatchByOrganizationId(
                endpoint.getOrganization().getId(),
                "WEBHOOK_TEST",
                testData
        );
    }

    // ============================================================
    // HMAC SHA-256
    // ============================================================

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

    // ============================================================
    // SHORTEN ERROR
    // ============================================================

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

        if (cleaned.length() > 250) {

            return cleaned.substring(
                    0,
                    250
            );
        }

        return cleaned;
    }

    // ============================================================
    // SHORTEN RESPONSE BODY
    // ============================================================

    private String shortenResponseBody(
            String value
    ) {

        if (value == null) {
            return null;
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

        if (cleaned.length() > 5000) {

            return cleaned.substring(
                    0,
                    5000
            );
        }

        return cleaned;
    }
}