package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Optional approximate country/region/city lookup of the public IP.
 * Configure a licensed HTTPS JSON IP-geolocation service using an URL template
 * with {ip}, e.g. https://your-provider.example/lookup/{ip}.
 * No precise GPS or actual device hostname can be accessed without permission.
 * Never block contact submission if third-party geolocation is unavailable.
 */
@Service
@RequiredArgsConstructor
public class ContactIpLocationService {
    @Value("${app.contact.ip-geolocation-url:}")
    private String urlTemplate;

    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(700))
            .build();

    public String lookup(String ip) {
        if (ip == null || ip.isBlank() || urlTemplate == null
                || !urlTemplate.startsWith("https://") || !urlTemplate.contains("{ip}")) {
            return "Unavailable";
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isAnyLocalAddress() || address.isLinkLocalAddress()
                    || address.isMulticastAddress())
                return "Unavailable";
            URI uri = URI.create(urlTemplate.replace("{ip}", address.getHostAddress()));
            if (!"https".equalsIgnoreCase(uri.getScheme())) return "Unavailable";
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(1200))
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body().length() > 8192) return "Unavailable";
            JsonNode body = objectMapper.readTree(response.body());
            if (body.has("success") && !body.path("success").asBoolean()) return "Unavailable";
            String city = field(body, "city");
            String region = field(body, "region");
            String country = field(body, "country");
            String result = java.util.stream.Stream.of(city, region, country)
                    .filter(x -> x != null && !x.isBlank())
                    .distinct()
                    .collect(java.util.stream.Collectors.joining(", "));
            return result.isBlank() ? "Unavailable" : result.substring(0, Math.min(120, result.length()));
        } catch (Exception ignored) {
            return "Unavailable";
        }
    }

    private String field(JsonNode body, String key) {
        JsonNode value = body.path(key);
        if (!value.isTextual()) return "";
        return value.asText().replaceAll("[\\p{Cntrl}]", "").trim();
    }
}
