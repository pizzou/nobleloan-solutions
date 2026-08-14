package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.PaymentGatewayResponse;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.model.WebhookEndpoint;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import com.patrick.fintech.loan_backend.repository.WebhookRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.MtnMobileMoneyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

        private final WebhookRepository webhookRepo;
        private final UserRepository userRepo;
        private final AuditService auditService;
        private final MtnMobileMoneyService mtnMobileMoneyService;

        @Value("${mtn.mobile-money.webhook-secret:}")
        private String mtnWebhookSecret;

        @GetMapping
        @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
        public ResponseEntity<?> list(Authentication auth) {
                User user = currentUser(auth);

                List<Map<String, Object>> result = new ArrayList<>();
                for (WebhookEndpoint ep : webhookRepo.findByOrganization(user.getOrganization())) {
                        result.add(sanitize(ep));
                }
                return ResponseEntity.ok(result);
        }

        @PostMapping
        @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
        public ResponseEntity<?> create(
                        @RequestBody WebhookEndpoint body,
                        Authentication auth) {
                User user = currentUser(auth);

                if (body == null) {
                        return ResponseEntity.badRequest().body(Map.of("error", "Webhook body is required"));
                }

                String url = body.getUrl() == null ? null : body.getUrl().trim();
                validateUrl(url);

                WebhookEndpoint endpoint = WebhookEndpoint.builder()
                                .organization(user.getOrganization())
                                .url(url)
                                .description(trimToNull(body.getDescription()))
                                .secret(UUID.randomUUID().toString().replace("-", ""))
                                .active(true)
                                .subscribedEvents(body.getSubscribedEvents() == null
                                                ? new ArrayList<>()
                                                : new ArrayList<>(body.getSubscribedEvents()))
                                .failureCount(0)
                                .build();

                WebhookEndpoint saved = webhookRepo.save(endpoint);

                auditService.log(
                                user.getOrganization(),
                                user,
                                "WEBHOOK_CREATED",
                                "WEBHOOK",
                                String.valueOf(saved.getId()),
                                "Created webhook endpoint " + saved.getUrl(),
                                null,
                                null,
                                "Webhooks & Integrations");

                Map<String, Object> response = sanitize(saved);
                response.put("secret", saved.getSecret());
                response.put("secretDisplayedOnce", true);
                return ResponseEntity.ok(response);
        }

        @DeleteMapping("/{id}")
        @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
        public ResponseEntity<?> delete(
                        @PathVariable Long id,
                        Authentication auth) {
                User user = currentUser(auth);

                if (id == null || id <= 0) {
                        return ResponseEntity.badRequest().body(Map.of("error", "Invalid webhook ID"));
                }

                WebhookEndpoint ep = webhookRepo.findById(id)
                                .orElseThrow(() -> new RuntimeException("Webhook not found"));

                if (ep.getOrganization() == null || user.getOrganization() == null ||
                                !ep.getOrganization().getId().equals(user.getOrganization().getId())) {
                        throw new RuntimeException("Access denied");
                }

                webhookRepo.delete(ep);

                auditService.log(
                                user.getOrganization(),
                                user,
                                "WEBHOOK_DELETED",
                                "WEBHOOK",
                                String.valueOf(id),
                                "Deleted webhook endpoint " + ep.getUrl(),
                                null,
                                null,
                                "Webhooks & Integrations");

                return ResponseEntity.ok(Map.of("deleted", true));
        }

        /**
         * Legacy MTN sandbox callback kept for compatibility.
         * Real MTN callbacks should use PaymentWebhookController.
         */
        @PostMapping("/mtn/momo")
        @PreAuthorize("permitAll()")
        public ResponseEntity<PaymentGatewayResponse> receiveMtnWebhook(
                        @RequestHeader(value = "X-Webhook-Secret", required = false) String secret,
                        @RequestBody Map<String, Object> payload) {
                if (payload == null) {
                        return ResponseEntity.badRequest()
                                        .body(PaymentGatewayResponse.failed("Payload is required", "MTN_MOMO"));
                }

                String configured = mtnWebhookSecret;
                if (configured == null || configured.isBlank()) {
                        log.error("[MTN WEBHOOK] MTN_WEBHOOK_SECRET is not configured; rejecting callback");
                        return ResponseEntity.status(503)
                                        .body(PaymentGatewayResponse.failed("Webhook is not configured", "MTN_MOMO"));
                }

                if (!java.security.MessageDigest.isEqual(
                                configured.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                (secret == null ? "" : secret.trim())
                                                .getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                        return ResponseEntity.status(401)
                                        .body(PaymentGatewayResponse.failed("Invalid webhook secret", "MTN_MOMO"));
                }

                Long loanId = toLong(payload.get("loanId"));
                String transactionId = firstString(payload, "transactionId", "referenceId", "financialTransactionId");
                Double amount = toDouble(payload.get("amount"));
                String currency = firstString(payload, "currency");
                String status = firstString(payload, "status");

                if (loanId == null || transactionId == null || transactionId.isBlank()) {
                        return ResponseEntity.badRequest()
                                        .body(PaymentGatewayResponse.failed("loanId and transactionId are required",
                                                        "MTN_MOMO"));
                }
                if (amount == null || amount <= 0) {
                        return ResponseEntity.badRequest()
                                        .body(PaymentGatewayResponse.failed("amount must be greater than zero",
                                                        "MTN_MOMO"));
                }
                if (status != null && !status.isBlank() &&
                                !isSuccessfulStatus(status)) {
                        return ResponseEntity.ok(
                                        PaymentGatewayResponse.failed("MTN transaction was not successful",
                                                        "MTN_MOMO"));
                }

                try {
                        PaymentGatewayResponse response = mtnMobileMoneyService.processWebhookConfirmation(
                                        loanId, transactionId.trim(), amount, currency);
                        return ResponseEntity.ok(response);
                } catch (Exception e) {
                        log.error("[MTN WEBHOOK] Callback processing failed. loanId={}, transactionId={}",
                                        loanId, transactionId, e);
                        return ResponseEntity.internalServerError()
                                        .body(PaymentGatewayResponse.failed("Webhook processing failed", "MTN_MOMO"));
                }
        }

        @GetMapping("/mtn/momo")
        @PreAuthorize("permitAll()")
        public ResponseEntity<?> mtnWebhookHealth() {
                return ResponseEntity.ok(Map.of(
                                "status", "ok",
                                "provider", "MTN_MOMO",
                                "message", "MTN webhook endpoint is available"));
        }

        private User currentUser(Authentication auth) {
                if (auth == null || !auth.isAuthenticated() || auth.getName() == null || auth.getName().isBlank()) {
                        throw new RuntimeException("Authentication required");
                }
                return userRepo.findByEmail(auth.getName())
                                .orElseThrow(() -> new RuntimeException("User not found"));
        }

        private Map<String, Object> sanitize(WebhookEndpoint ep) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("id", ep.getId());
                out.put("url", ep.getUrl());
                out.put("description", ep.getDescription());
                out.put("active", ep.isActive());
                out.put("subscribedEvents", ep.getSubscribedEvents());
                out.put("failureCount", ep.getFailureCount());
                out.put("lastDeliveryAt", ep.getLastDeliveryAt());
                out.put("lastDeliveryStatus", ep.getLastDeliveryStatus());
                out.put("createdAt", ep.getCreatedAt());
                return out;
        }

        private void validateUrl(String url) {
                if (url == null || url.isBlank()) {
                        throw new IllegalArgumentException("Webhook URL is required");
                }
                if (url.length() > 2048) {
                        throw new IllegalArgumentException("Webhook URL is too long");
                }
                try {
                        URI uri = new URI(url);
                        String scheme = uri.getScheme();
                        if (scheme == null || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
                                throw new IllegalArgumentException("Webhook URL must use HTTP or HTTPS");
                        }
                        if (uri.getHost() == null || uri.getHost().isBlank()) {
                                throw new IllegalArgumentException("Webhook URL host is required");
                        }
                } catch (URISyntaxException e) {
                        throw new IllegalArgumentException("Invalid webhook URL");
                }
        }

        private String trimToNull(String value) {
                if (value == null)
                        return null;
                String v = value.trim();
                return v.isEmpty() ? null : v;
        }

        private boolean isSuccessfulStatus(String status) {
                String s = status == null ? "" : status.trim().toUpperCase(java.util.Locale.ROOT);
                return s.equals("SUCCESS") || s.equals("SUCCESSFUL") || s.equals("COMPLETED") || s.equals("200");
        }

        private Long toLong(Object value) {
                if (value == null)
                        return null;
                try {
                        return Long.parseLong(String.valueOf(value).trim());
                } catch (Exception e) {
                        return null;
                }
        }

        private Double toDouble(Object value) {
                if (value == null)
                        return null;
                try {
                        return Double.parseDouble(String.valueOf(value).trim());
                } catch (Exception e) {
                        return null;
                }
        }

        private String firstString(Map<String, Object> payload, String... keys) {
                for (String key : keys) {
                        Object value = payload.get(key);
                        if (value != null && !String.valueOf(value).isBlank()) {
                                return String.valueOf(value).trim();
                        }
                }
                return null;
        }
}