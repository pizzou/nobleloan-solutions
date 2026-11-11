package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.stream.Stream;

/** Optional coarse IP location, not GPS. Requires a configured HTTPS JSON provider. */
@Service
@RequiredArgsConstructor
public class ContactIpLocationService {
    private final ObjectMapper mapper;
    @Value("${app.contact.ip-geolocation-url:${CONTACT_IP_GEOLOCATION_URL:}}")
    private String urlTemplate;
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(700)).build();

    public String lookup(String ip) {
        if (ip == null || !ip.matches("[0-9a-fA-F.:]{3,45}") ||
                urlTemplate == null || !urlTemplate.startsWith("https://") ||
                !urlTemplate.contains("{ip}")) return "Unavailable";
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (address.isAnyLocalAddress() || address.isSiteLocalAddress() ||
                    address.isLoopbackAddress() || address.isLinkLocalAddress() ||
                    address.isMulticastAddress()) return "Unavailable";
            URI uri = URI.create(urlTemplate.replace("{ip}",address.getHostAddress()));
            if (!"https".equalsIgnoreCase(uri.getScheme())) return "Unavailable";
            HttpRequest request = HttpRequest.newBuilder(uri)
                .GET().timeout(Duration.ofMillis(1200)).header("Accept","application/json").build();
            HttpResponse<String> response = client.send(request,HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()!=200 || response.body().length()>8192) return "Unavailable";
            JsonNode json = mapper.readTree(response.body());
            if (json.has("success") && !json.path("success").asBoolean()) return "Unavailable";
            String text=Stream.of("city","region","country")
                .map(k->json.path(k)).filter(JsonNode::isTextual)
                .map(JsonNode::asText).map(v->v.replaceAll("[\\p{Cntrl}]", "").trim())
                .filter(v->!v.isBlank()).distinct()
                .collect(java.util.stream.Collectors.joining(", "));
            return text.isBlank()?"Unavailable":text.substring(0,Math.min(120,text.length()));
        } catch(Exception ignored) { return "Unavailable"; }
    }
}
