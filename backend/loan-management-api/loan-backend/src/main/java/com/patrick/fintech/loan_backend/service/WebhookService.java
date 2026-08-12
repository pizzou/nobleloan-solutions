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

    // ================================================================
    // DISPATCH WEBHOOK
    // ================================================================

    @Async("loansaasAsyncExecutor")
    @Transactional
    public void dispatch(
            Organization org,
            String eventType,
            Object payload
    ) {

        if (org == null || org.getId() == null) {
            log.warn(
                    "[WEBHOOK] Dispatch skipped. Organization is null or has no ID. event={}",
                    eventType
            );
            return;
        }

        if (eventType == null || eventType.isBlank()) {
            log.warn(
                    "[WEBHOOK] Dispatch skipped. Event type is empty. organization={}",
                    org.getId()
            );
            return;
        }

        final Long organizationId = org.getId();
        final String normalizedEventType = eventType.trim();

        log.info("[WEBHOOK] ==================================================");
        log.info(
                "[WEBHOOK] DISPATCH STARTED. organization={}, event={}",
                organizationId,
                normalizedEventType
        );

        // ============================================================
        // LOAD ENDPOINTS
        // ============================================================

        List<WebhookEndpoint> endpoints = new ArrayList<>();

        try {
            /*
             * First use the existing organization-based query.
             */
            List<WebhookEndpoint> found =
                    webhookRepo.findByOrganization(org);

            if (found != null) {
                endpoints.addAll(found);
            }

        } catch (Exception e) {

            log.error(
                    "[WEBHOOK] Failed loading webhook endpoints. " +
                            "organization={}, event={}",
                    organizationId,
                    normalizedEventType,
                    e
            );

            return;
        }

        log.info(
                "[WEBHOOK] Endpoint lookup completed. " +
                        "organization={}, totalEndpoints={}",
                organizationId,
                endpoints.size()
        );

        // ============================================================
        // FILTER ACTIVE + ORGANIZATION + EVENT
        // ============================================================

        List<WebhookEndpoint> matchingEndpoints =
                new ArrayList<>();

        for (WebhookEndpoint endpoint : endpoints) {

            if (endpoint == null) {
                continue;
            }

            Long endpointId = endpoint.getId();

            log.info(
                    "[WEBHOOK] Inspecting endpoint. " +
                            "endpoint={}, url={}, active={}, subscriptions={}",
                    endpointId,
                    endpoint.getUrl(),
                    endpoint.isActive(),
                    endpoint.getSubscribedEvents()
            );

            if (!endpoint.isActive()) {

                log.info(
                        "[WEBHOOK] Endpoint inactive. endpoint={}",
                        endpointId
                );

                continue;
            }

            if (
                    endpoint.getOrganization() == null
                            || endpoint.getOrganization().getId() == null
            ) {

                log.warn(
                        "[WEBHOOK] Endpoint has no organization. endpoint={}",
                        endpointId
                );

                continue;
            }

            if (
                    !organizationId.equals(
                            endpoint.getOrganization().getId()
                    )
            ) {

                log.warn(
                        "[WEBHOOK] Endpoint organization mismatch. " +
                                "endpoint={}, expected={}, actual={}",
                        endpointId,
                        organizationId,
                        endpoint.getOrganization().getId()
                );

                continue;
            }

            if (!isSubscribed(endpoint, normalizedEventType)) {

                log.info(
                        "[WEBHOOK] Endpoint is not subscribed. " +
                                "endpoint={}, event={}, subscriptions={}",
                        endpointId,
                        normalizedEventType,
                        endpoint.getSubscribedEvents()
                );

                continue;
            }

            matchingEndpoints.add(endpoint);
        }

        // ============================================================
        // NO MATCHING ENDPOINT
        // ============================================================

        if (matchingEndpoints.isEmpty()) {

            log.warn(
                    "[WEBHOOK] NO ACTIVE ENDPOINT FOUND. " +
                            "organization={}, event={}, totalEndpoints={}.",
                    organizationId,
                    normalizedEventType,
                    endpoints.size()
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
                "[WEBHOOK] Matching webhook endpoints found. " +
                        "organization={}, event={}, count={}",
                organizationId,
                normalizedEventType,
                matchingEndpoints.size()
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
        // SEND TO EACH ENDPOINT
        // ============================================================

        for (WebhookEndpoint endpoint : matchingEndpoints) {

            sendToEndpoint(
                    endpoint,
                    org,
                    normalizedEventType,
                    body
            );
        }

        log.info(
                "[WEBHOOK] DISPATCH FINISHED. " +
                        "organization={}, event={}, deliveries={}",
                organizationId,
                normalizedEventType,
                matchingEndpoints.size()
        );

        log.info("[WEBHOOK] ==================================================");
    }

    // ================================================================
    // SEND TO ENDPOINT
    // ================================================================

    private void sendToEndpoint(
            WebhookEndpoint endpoint,
            Organization organization,
            String eventType,
            String body
    ) {

        Long endpointId = endpoint.getId();
        String url = endpoint.getUrl();

        if (url == null || url.isBlank()) {

            log.error(
                    "[WEBHOOK] Endpoint URL is empty. endpoint={}",
                    endpointId
            );

            saveFailedDelivery(
                    endpoint,
                    organization,
                    eventType,
                    body,
                    null,
                    null,
                    "Webhook URL is empty"
            );

            markFailure(
                    endpoint,
                    "Webhook URL is empty"
            );

            return;
        }

        url = url.trim();

        WebhookDelivery delivery =
                createPendingDelivery(
                        endpoint,
                        organization,
                        eventType,
                        body,
                        url
                );

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
                eventType
        );

        headers.set(
                "X-Webhook-Organization",
                String.valueOf(
                        organization.getId()
                )
        );

        headers.set(
                "X-Webhook-Endpoint",
                endpointId == null
                        ? "unknown"
                        : String.valueOf(endpointId)
        );

        headers.set(
                "X-Webhook-Delivery",
                delivery != null && delivery.getId() != null
                        ? String.valueOf(delivery.getId())
                        : "loansaas"
        );

        // ============================================================
        // SIGNATURE
        // ============================================================

        if (
                endpoint.getSecret() != null
                        && !endpoint.getSecret().isBlank()
        ) {

            try {

                headers.set(
                        "X-Webhook-Signature",
                        sign(
                                body,
                                endpoint.getSecret()
                        )
                );

            } catch (Exception e) {

                log.error(
                        "[WEBHOOK] Signature generation failed. endpoint={}",
                        endpointId,
                        e
                );

                completeFailedDelivery(
                        delivery,
                        null,
                        null,
                        "Signature generation failed"
                );

                markFailure(
                        endpoint,
                        "Signature generation failed"
                );

                return;
            }
        }

        // ============================================================
        // HTTP POST
        // ============================================================

        try {

            log.info(
                    "[WEBHOOK] SENDING EVENT. " +
                            "event={}, organization={}, endpoint={}, url={}",
                    eventType,
                    organization.getId(),
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

            int status =
                    response.getStatusCode().value();

            String responseBody =
                    response.getBody();

            if (
                    response.getStatusCode()
                            .is2xxSuccessful()
            ) {

                completeSuccessfulDelivery(
                        delivery,
                        status,
                        responseBody
                );

                markSuccess(
                        endpoint,
                        eventType,
                        status
                );

                log.info(
                        "[WEBHOOK] DELIVERY SUCCESS. " +
                                "event={}, endpoint={}, HTTP={}",
                        eventType,
                        endpointId,
                        status
                );

            } else {

                String reason =
                        "HTTP " + status;

                completeFailedDelivery(
                        delivery,
                        status,
                        responseBody,
                        reason
                );

                markFailure(
                        endpoint,
                        reason
                );

                log.warn(
                        "[WEBHOOK] DELIVERY FAILED. " +
                                "event={}, endpoint={}, HTTP={}",
                        eventType,
                        endpointId,
                        status
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

            completeFailedDelivery(
                    delivery,
                    null,
                    null,
                    error
            );

            markFailure(
                    endpoint,
                    error
            );

            log.error(
                    "[WEBHOOK] DELIVERY EXCEPTION. " +
                            "event={}, endpoint={}, url={}, error={}",
                    eventType,
                    endpointId,
                    url,
                    error,
                    e
            );
        }
    }

    // ================================================================
    // CREATE PENDING DELIVERY
    // ================================================================

    private WebhookDelivery createPendingDelivery(
            WebhookEndpoint endpoint,
            Organization organization,
            String eventType,
            String payload,
            String endpointUrl
    ) {

        try {

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

            return webhookDeliveryRepo.save(
                    delivery
            );

        } catch (Exception e) {

            log.error(
                    "[WEBHOOK] Could not create delivery history. " +
                            "endpoint={}, event={}",
                    endpoint != null
                            ? endpoint.getId()
                            : null,
                    eventType,
                    e
            );

            return null;
        }
    }

    // ================================================================
    // SAVE FAILED DELIVERY
    // ================================================================

    private void saveFailedDelivery(
            WebhookEndpoint endpoint,
            Organization organization,
            String eventType,
            String payload,
            String endpointUrl,
            Integer httpStatus,
            String error
    ) {

        try {

            WebhookDelivery delivery =
                    WebhookDelivery.builder()
                            .webhookEndpoint(endpoint)
                            .organization(organization)
                            .eventType(eventType)
                            .payload(payload)
                            .endpointUrl(endpointUrl)
                            .status("FAILED")
                            .httpStatus(httpStatus)
                            .errorMessage(
                                    shorten(error)
                            )
                            .attemptCount(1)
                            .createdAt(LocalDateTime.now())
                            .deliveredAt(LocalDateTime.now())
                            .build();

            webhookDeliveryRepo.save(
                    delivery
            );

        } catch (Exception e) {

            log.error(
                    "[WEBHOOK] Failed to save delivery failure. " +
                            "endpoint={}, event={}",
                    endpoint != null
                            ? endpoint.getId()
                            : null,
                    eventType,
                    e
            );
        }
    }

    // ================================================================
    // SUCCESS DELIVERY
    // ================================================================

    private void completeSuccessfulDelivery(
            WebhookDelivery delivery,
            int httpStatus,
            String responseBody
    ) {

        if (delivery == null) {
            return;
        }

        try {

            delivery.setStatus("SUCCESS");

            delivery.setHttpStatus(
                    httpStatus
            );

            delivery.setResponseBody(
                    shortenResponse(
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

        } catch (Exception e) {

            log.error(
                    "[WEBHOOK] Failed to update successful delivery. " +
                            "deliveryId={}",
                    delivery.getId(),
                    e
            );
        }
    }

    // ================================================================
    // FAILED DELIVERY
    // ================================================================

    private void completeFailedDelivery(
            WebhookDelivery delivery,
            Integer httpStatus,
            String responseBody,
            String error
    ) {

        if (delivery == null) {
            return;
        }

        try {

            delivery.setStatus("FAILED");

            delivery.setHttpStatus(
                    httpStatus
            );

            delivery.setResponseBody(
                    shortenResponse(
                            responseBody
                    )
            );

            delivery.setErrorMessage(
                    shorten(
                            error
                    )
            );

            delivery.setDeliveredAt(
                    LocalDateTime.now()
            );

            webhookDeliveryRepo.save(
                    delivery
            );

        } catch (Exception e) {

            log.error(
                    "[WEBHOOK] Failed to update failed delivery. " +
                            "deliveryId={}",
                    delivery.getId(),
                    e
            );
        }
    }

    // ================================================================
    // SUCCESS METADATA
    // ================================================================

    private void markSuccess(
            WebhookEndpoint endpoint,
            String eventType,
            int httpStatus
    ) {

        if (endpoint == null) {
            return;
        }

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
                    "[WEBHOOK] Could not persist endpoint success. " +
                            "endpoint={}, event={}",
                    endpoint.getId(),
                    eventType,
                    e
            );
        }
    }

    // ================================================================
    // SUBSCRIPTION
    // ================================================================

    private boolean isSubscribed(
            WebhookEndpoint endpoint,
            String eventType
    ) {

        if (endpoint == null) {
            return false;
        }

        List<String> subscriptions =
                endpoint.getSubscribedEvents();

        /*
         * Empty subscriptions = all events.
         */
        if (
                subscriptions == null
                        || subscriptions.isEmpty()
        ) {
            return true;
        }

        if (eventType == null || eventType.isBlank()) {
            return false;
        }

        String normalized =
                eventType.trim();

        return subscriptions
                .stream()
                .filter(
                        value ->
                                value != null
                                        && !value.isBlank()
                )
                .map(
                        String::trim
                )
                .anyMatch(
                        value ->
                                value.equalsIgnoreCase(
                                        normalized
                                )
                                        || value.equalsIgnoreCase("*")
                );
    }

    // ================================================================
    // FAILURE
    // ================================================================

    private void markFailure(
            WebhookEndpoint endpoint,
            String reason
    ) {

        if (endpoint == null) {
            return;
        }

        int failures =
                endpoint.getFailureCount() == null
                        ? 1
                        : endpoint.getFailureCount() + 1;

        endpoint.setLastDeliveryAt(
                LocalDateTime.now()
        );

        endpoint.setLastDeliveryStatus(
                "FAILED: " + shorten(reason)
        );

        endpoint.setFailureCount(
                failures
        );

        if (failures >= 10) {

            endpoint.setActive(
                    false
            );

            log.warn(
                    "[WEBHOOK] Endpoint automatically disabled. " +
                            "endpoint={}, failures={}",
                    endpoint.getId(),
                    failures
            );
        }

        try {

            webhookRepo.save(
                    endpoint
            );

        } catch (Exception e) {

            log.error(
                    "[WEBHOOK] Could not persist endpoint failure. " +
                            "endpoint={}",
                    endpoint.getId(),
                    e
            );
        }
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

        Map<String, Object> data =
                new HashMap<>();

        data.put(
                "message",
                "Webhook test successful"
        );

        data.put(
                "webhookId",
                endpoint.getId()
        );

        data.put(
                "organizationId",
                organization.getId()
        );

        data.put(
                "sentAt",
                System.currentTimeMillis()
        );

        dispatch(
                organization,
                "WEBHOOK_TEST",
                data
        );
    }

    // ================================================================
    // HMAC
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
    // SHORTEN
    // ================================================================

    private String shorten(
            String value
    ) {

        if (value == null) {
            return "Unknown error";
        }

        String cleaned =
                value
                        .replace("\n", " ")
                        .replace("\r", " ")
                        .trim();

        if (cleaned.isBlank()) {
            return "Unknown error";
        }

        return cleaned.length() > 250
                ? cleaned.substring(0, 250)
                : cleaned;
    }

    private String shortenResponse(
            String value
    ) {

        if (value == null) {
            return null;
        }

        String cleaned =
                value.replace(
                        "\u0000",
                        ""
                );

        return cleaned.length() > 10000
                ? cleaned.substring(0, 10000)
                : cleaned;
    }
}