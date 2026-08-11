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

    /**
     * Dispatch an application event to all active webhook endpoints
     * belonging to the organization and subscribed to the event.
     *
     * IMPORTANT:
     *
     * This method runs asynchronously so a slow external webhook
     * endpoint does not block payment processing.
     */
    @Async("loansaasAsyncExecutor")
    @Transactional
    public void dispatch(
            Organization org,
            String eventType,
            Object payload
    ) {

        if (org == null) {

            log.warn(
                    "[WEBHOOK] Dispatch skipped because organization is null. event={}",
                    eventType
            );

            return;
        }

        if (org.getId() == null) {

            log.warn(
                    "[WEBHOOK] Dispatch skipped because organization ID is null. event={}",
                    eventType
            );

            return;
        }

        if (eventType == null || eventType.isBlank()) {

            log.warn(
                    "[WEBHOOK] Dispatch skipped because event type is empty. organization={}",
                    org.getId()
            );

            return;
        }

        final String normalizedEventType =
                eventType.trim();

        final Long organizationId =
                org.getId();

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
                            .findByOrganizationAndActiveTrue(org);

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
        // NO ACTIVE ENDPOINTS
        // ============================================================

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


        // ============================================================
        // BUILD WEBHOOK BODY
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
                    "[WEBHOOK] Could not serialize webhook payload. " +
                            "organization={}, event={}",
                    organizationId,
                    normalizedEventType,
                    e
            );

            return;
        }


        // ============================================================
        // PROCESS EVERY ENDPOINT
        // ============================================================

        for (WebhookEndpoint endpoint : endpoints) {

            if (endpoint == null) {

                log.warn(
                        "[WEBHOOK] Null endpoint returned from repository. " +
                                "organization={}, event={}",
                        organizationId,
                        normalizedEventType
                );

                continue;
            }


            final Long endpointId =
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
            // ORGANIZATION SAFETY CHECK
            // ========================================================

            if (
                    endpoint.getOrganization() == null
                            || endpoint.getOrganization().getId() == null
            ) {

                log.error(
                        "[WEBHOOK] Endpoint has no organization. " +
                                "endpoint={}, event={}",
                        endpointId,
                        normalizedEventType
                );

                saveFailedDelivery(
                        endpoint,
                        org,
                        normalizedEventType,
                        body,
                        endpoint.getUrl(),
                        null,
                        "Webhook endpoint organization is missing"
                );

                markFailure(
                        endpoint,
                        "Webhook endpoint organization is missing"
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
                                "endpoint={}, expectedOrganization={}, " +
                                "endpointOrganization={}, event={}",
                        endpointId,
                        organizationId,
                        endpoint.getOrganization().getId(),
                        normalizedEventType
                );

                saveFailedDelivery(
                        endpoint,
                        org,
                        normalizedEventType,
                        body,
                        endpoint.getUrl(),
                        null,
                        "Webhook endpoint organization mismatch"
                );

                markFailure(
                        endpoint,
                        "Webhook endpoint organization mismatch"
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
                        "[WEBHOOK] Endpoint not subscribed to event. " +
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
                        "[WEBHOOK] Endpoint has empty URL. " +
                                "endpoint={}, event={}",
                        endpointId,
                        normalizedEventType
                );

                saveFailedDelivery(
                        endpoint,
                        org,
                        normalizedEventType,
                        body,
                        null,
                        null,
                        "Empty webhook URL"
                );

                markFailure(
                        endpoint,
                        "Empty webhook URL"
                );

                continue;
            }

            url = url.trim();


            // ========================================================
            // CREATE DELIVERY RECORD
            // ========================================================

            WebhookDelivery delivery =
                    createPendingDelivery(
                            endpoint,
                            org,
                            normalizedEventType,
                            body,
                            url
                    );


            // ========================================================
            // HTTP HEADERS
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
            // HMAC SHA-256 SIGNATURE
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
                            "[WEBHOOK] Signature generation failed. " +
                                    "endpoint={}, event={}",
                            endpointId,
                            normalizedEventType,
                            e
                    );

                    completeFailedDelivery(
                            delivery,
                            null,
                            null,
                            "Signature generation error"
                    );

                    markFailure(
                            endpoint,
                            "Signature generation error"
                    );

                    continue;
                }
            }


            // ========================================================
            // SEND WEBHOOK
            // ========================================================

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
                        response.getStatusCode()
                                .value();


                String responseBody =
                        response.getBody();


                // ====================================================
                // SUCCESS
                // ====================================================

                if (
                        response.getStatusCode()
                                .is2xxSuccessful()
                ) {

                    completeSuccessfulDelivery(
                            delivery,
                            httpStatus,
                            responseBody
                    );

                    markSuccess(
                            endpoint,
                            normalizedEventType,
                            httpStatus
                    );

                } else {

                    String reason =
                            "HTTP " + httpStatus;

                    completeFailedDelivery(
                            delivery,
                            httpStatus,
                            responseBody,
                            reason
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
                                "event={}, organization={}, endpoint={}, " +
                                "url={}, error={}",
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


            WebhookDelivery saved =
                    webhookDeliveryRepo.save(
                            delivery
                    );


            log.debug(
                    "[WEBHOOK] Delivery record created. " +
                            "deliveryId={}, endpoint={}, event={}",
                    saved.getId(),
                    endpoint.getId(),
                    eventType
            );


            return saved;

        } catch (Exception e) {

            /*
             * Failure to create a delivery history record must
             * not prevent the actual webhook attempt.
             */
            log.error(
                    "[WEBHOOK] Could not create delivery record. " +
                            "endpoint={}, event={}",
                    endpoint.getId(),
                    eventType,
                    e
            );

            return WebhookDelivery.builder()
                    .webhookEndpoint(endpoint)
                    .organization(organization)
                    .eventType(eventType)
                    .payload(payload)
                    .endpointUrl(endpointUrl)
                    .status("PENDING")
                    .attemptCount(1)
                    .createdAt(LocalDateTime.now())
                    .build();
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
                            .createdAt(
                                    LocalDateTime.now()
                            )
                            .deliveredAt(
                                    LocalDateTime.now()
                            )
                            .build();


            webhookDeliveryRepo.save(
                    delivery
            );

        } catch (Exception e) {

            log.error(
                    "[WEBHOOK] Failed to save failed delivery history. " +
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
    // SUCCESSFUL DELIVERY RECORD
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

            delivery.setStatus(
                    "SUCCESS"
            );

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
                    "[WEBHOOK] Could not persist successful delivery. " +
                            "deliveryId={}",
                    delivery.getId(),
                    e
            );
        }
    }


    // ================================================================
    // FAILED DELIVERY RECORD
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

            delivery.setStatus(
                    "FAILED"
            );

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
                    "[WEBHOOK] Could not persist failed delivery. " +
                            "deliveryId={}",
                    delivery.getId(),
                    e
            );
        }
    }


    // ================================================================
    // SUCCESS
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


            log.info(
                    "[WEBHOOK] DELIVERY SUCCESS. " +
                            "event={}, endpoint={}, HTTP={}, " +
                            "lastDeliveryStatus=SUCCESS",
                    eventType,
                    endpoint.getId(),
                    httpStatus
            );


        } catch (Exception e) {

            /*
             * The actual HTTP delivery already succeeded.
             *
             * Failure to update endpoint metadata must not
             * make a successful external delivery look like
             * a failed payment.
             */
            log.error(
                    "[WEBHOOK] Delivery succeeded but endpoint status " +
                            "could not be persisted. endpoint={}, event={}",
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

        if (endpoint == null) {
            return false;
        }


        List<String> subscribedEvents =
                endpoint.getSubscribedEvents();


        /*
         * Null or empty subscriptions mean ALL EVENTS.
         */
        if (
                subscribedEvents == null
                        || subscribedEvents.isEmpty()
        ) {

            return true;
        }


        String normalizedEvent =
                eventType == null
                        ? ""
                        : eventType.trim();


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
    // FAILURE HANDLING
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

            /*
             * Never allow webhook metadata persistence failure
             * to break payment processing.
             */
            log.error(
                    "[WEBHOOK] Could not persist endpoint failure status. " +
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


        /*
         * Use the same dispatch pipeline used by real events.
         */
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


        if (cleaned.length() > 250) {

            return cleaned.substring(
                    0,
                    250
            );
        }


        return cleaned;
    }


    // ================================================================
    // SHORTEN RESPONSE
    // ================================================================

    private String shortenResponse(
            String value
    ) {

        if (value == null) {

            return null;
        }


        String cleaned =
                value
                        .replace(
                                "\u0000",
                                ""
                        );


        /*
         * Keep webhook response history reasonably sized.
         */
        if (cleaned.length() > 10000) {

            return cleaned.substring(
                    0,
                    10000
            );
        }


        return cleaned;
    }
}