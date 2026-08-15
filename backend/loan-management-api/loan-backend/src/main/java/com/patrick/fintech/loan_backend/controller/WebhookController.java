package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.model.WebhookEndpoint;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import com.patrick.fintech.loan_backend.repository.WebhookRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.InetAddress;
import java.net.UnknownHostException;
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

        @Value("${app.webhooks.allow-http:false}")
        private boolean allowHttp;

        private static final int MAX_ENDPOINTS_PER_ORGANIZATION = 20;
        private static final int MAX_SUBSCRIBED_EVENTS = 50;

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

                if (user.getOrganization() == null || user.getOrganization().getId() == null) {
                        return ResponseEntity.badRequest().body(Map.of("error", "User organization is required"));
                }

                List<WebhookEndpoint> existing = webhookRepo.findByOrganization(user.getOrganization());
                if (existing.size() >= MAX_ENDPOINTS_PER_ORGANIZATION) {
                        return ResponseEntity.status(409).body(Map.of(
                                        "error", "Webhook endpoint limit reached",
                                        "maxEndpoints", MAX_ENDPOINTS_PER_ORGANIZATION));
                }

                String url = body.getUrl() == null ? null : body.getUrl().trim();
                validateUrl(url);

                for (WebhookEndpoint ep : existing) {
                        if (ep.getUrl() != null && ep.getUrl().trim().equalsIgnoreCase(url)) {
                                return ResponseEntity.status(409).body(Map.of(
                                                "error", "A webhook endpoint with this URL already exists"));
                        }
                }

                List<String> events = normalizeEvents(body.getSubscribedEvents());

                WebhookEndpoint endpoint = WebhookEndpoint.builder()
                                .organization(user.getOrganization())
                                .url(url)
                                .description(trimToNull(body.getDescription()))
                                .secret(UUID.randomUUID().toString().replace("-", ""))
                                .active(true)
                                .subscribedEvents(events)
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
                        if (scheme == null) {
                                throw new IllegalArgumentException("Webhook URL scheme is required");
                        }
                        if ("http".equalsIgnoreCase(scheme) && !allowHttp) {
                                throw new IllegalArgumentException("Webhook URL must use HTTPS");
                        }
                        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
                                throw new IllegalArgumentException("Webhook URL must use HTTP or HTTPS");
                        }
                        if (uri.getUserInfo() != null) {
                                throw new IllegalArgumentException("Webhook URL must not contain embedded credentials");
                        }
                        String host = uri.getHost();
                        if (host == null || host.isBlank()) {
                                throw new IllegalArgumentException("Webhook URL host is required");
                        }
                        rejectPrivateOrLocalHost(host);
                } catch (URISyntaxException e) {
                        throw new IllegalArgumentException("Invalid webhook URL");
                }
        }

        private void rejectPrivateOrLocalHost(String host) {
                String normalized = host.trim().toLowerCase();
                if ("localhost".equals(normalized) || normalized.endsWith(".local") ||
                                normalized.equals("0.0.0.0") || normalized.equals("::1")) {
                        throw new IllegalArgumentException("Webhook URL must target a public host");
                }
                try {
                        InetAddress address = InetAddress.getByName(normalized);
                        if (address.isAnyLocalAddress() || address.isLoopbackAddress() ||
                                        address.isLinkLocalAddress() || address.isSiteLocalAddress() ||
                                        address.isMulticastAddress()) {
                                throw new IllegalArgumentException("Webhook URL must target a public host");
                        }
                } catch (UnknownHostException e) {
                        throw new IllegalArgumentException("Webhook host could not be resolved");
                }
        }

        private List<String> normalizeEvents(List<String> events) {
                if (events == null || events.isEmpty()) {
                        return new ArrayList<>();
                }
                if (events.size() > MAX_SUBSCRIBED_EVENTS) {
                        throw new IllegalArgumentException("Too many subscribed events");
                }
                List<String> result = new ArrayList<>();
                for (String event : events) {
                        String normalized = trimToNull(event);
                        if (normalized == null) {
                                continue;
                        }
                        if (normalized.length() > 100) {
                                throw new IllegalArgumentException("Subscribed event name is too long");
                        }
                        if (!normalized.matches("[A-Za-z0-9_.:-]+")) {
                                throw new IllegalArgumentException("Invalid subscribed event name: " + normalized);
                        }
                        if (!result.contains(normalized)) {
                                result.add(normalized);
                        }
                }
                return result;
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