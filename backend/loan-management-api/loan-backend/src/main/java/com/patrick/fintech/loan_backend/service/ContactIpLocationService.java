package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.stream.Stream;

/** Best-effort coarse IP location, not GPS. Requires a configured HTTPS JSON provider. */
@Service
@RequiredArgsConstructor
public class ContactIpLocationService {
    private static final Logger log = LoggerFactory.getLogger(ContactIpLocationService.class);
    private static final String UNAVAILABLE = "Unavailable";
    private final ObjectMapper mapper;

    @Value("${app.contact.ip-geolocation-url:${CONTACT_IP_GEOLOCATION_URL:}}")
    private String urlTemplate;

    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    public String lookup(String ip) {
        if (ip == null || !ip.matches("[0-9a-fA-F.:]{3,45}")) {
            log.warn("Contact IP geolocation skipped: invalid or missing IP");
            return UNAVAILABLE;
        }
        if (urlTemplate == null || !urlTemplate.startsWith("https://") ||
                !urlTemplate.contains("{ip}")) {
            log.warn("Contact IP geolocation skipped: HTTPS provider URL is not configured");
            return UNAVAILABLE;
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (address.isAnyLocalAddress() || address.isSiteLocalAddress() ||
                    address.isLoopbackAddress() || address.isLinkLocalAddress() ||
                    address.isMulticastAddress()) {
                log.warn("Contact IP geolocation skipped: non-public client IP");
                return UNAVAILABLE;
            }

            URI uri = URI.create(urlTemplate.replace("{ip}", address.getHostAddress()));
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                log.warn("Contact IP geolocation skipped: invalid HTTPS provider URI");
                return UNAVAILABLE;
            }
            HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .build();
            HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Contact IP geolocation provider returned HTTP {}", response.statusCode());
                return UNAVAILABLE;
            }
            if (response.body() == null || response.body().length() > 8192) {
                log.warn("Contact IP geolocation provider returned an invalid-sized response");
                return UNAVAILABLE;
            }

            JsonNode json = mapper.readTree(response.body());
            if (json.has("success") && !json.path("success").asBoolean()) {
                log.warn("Contact IP geolocation provider reported an unsuccessful lookup");
                return UNAVAILABLE;
            }

            String location = Stream.of("city", "region", "country")
                .map(json::path)
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText)
                .map(v -> v.replaceAll("[\\p{Cntrl}]", "").trim())
                .filter(v -> !v.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining(", "));
            if (location.isBlank()) {
                log.warn("Contact IP geolocation provider returned no city, region or country");
                return UNAVAILABLE;
            }
            return location.substring(0, Math.min(120, location.length()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Contact IP geolocation interrupted");
            return UNAVAILABLE;
        } catch (Exception ex) {
            // Never log visitor IPs, provider URLs/tokens or the provider response body.
            log.warn("Contact IP geolocation failed: {}", ex.getClass().getSimpleName());
            return UNAVAILABLE;
        }
    }
}
