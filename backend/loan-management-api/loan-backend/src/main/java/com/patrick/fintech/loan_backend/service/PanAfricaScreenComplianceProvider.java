package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.model.Borrower;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Real AML/sanctions/PEP screening adapter for PanAfrica Screen.
 *
 * PanAfrica Screen documents a POST /api/v1/screen endpoint that accepts a
 * customer name, country, DOB and screening categories and returns matches,
 * risk level and match scores. The provider must be contracted and the API
 * key must be supplied through secrets before production use.
 */
@Service
@Slf4j
public class PanAfricaScreenComplianceProvider implements ExternalComplianceProvider {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.compliance.provider:}")
    private String configuredProvider;

    @Value("${app.compliance.base-url:}")
    private String baseUrl;

    @Value("${app.compliance.api-key:}")
    private String apiKey;

    @Value("${app.compliance.threshold:0.70}")
    private double threshold;

    @Value("${app.compliance.include-adverse-media:true}")
    private boolean includeAdverseMedia;

    public PanAfricaScreenComplianceProvider(
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ScreeningResult screen(Borrower borrower) {
        if (borrower == null) {
            throw new IllegalArgumentException("Borrower is required.");
        }
        if (!"PANAFRICASCREEN".equalsIgnoreCase(normalize(configuredProvider))) {
            throw new IllegalStateException(
                    "The configured compliance provider is not PANAFRICASCREEN.");
        }
        if (isBlank(baseUrl) || isBlank(apiKey)) {
            throw new IllegalStateException(
                    "PanAfrica Screen base URL and API key are required for production compliance screening.");
        }

        String name = fullName(borrower);
        if (name.length() < 2) {
            throw new IllegalArgumentException("Borrower full name is required for AML screening.");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("type", "individual");
        payload.put("countryCode", normalizeCountry(borrower.getCountry()));
        payload.put("categories", List.of("sanctions", "pep", "rca"));
        payload.put("threshold", threshold);
        if (borrower.getDateOfBirth() != null) {
            payload.put("dateOfBirth", borrower.getDateOfBirth().toString());
        }
        payload.put("include_adverse_media", includeAdverseMedia);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(apiKey);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    endpoint("/screen"),
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    Map.class);

            if (response == null || !response.getStatusCode().is2xxSuccessful()) {
                int status = response == null ? -1 : response.getStatusCode().value();
                throw new IllegalStateException(
                        "PanAfrica Screen returned HTTP " + status + ".");
            }

            Map<?, ?> body = response.getBody();
            if (body == null) {
                throw new IllegalStateException("PanAfrica Screen returned an empty response.");
            }

            int totalMatches = toInt(body.get("totalMatches"));
            double highestScore = toDouble(body.get("highestScore"));
            String riskLevel = normalize(body.get("riskLevel"));

            boolean sanctionsClear = true;
            boolean pepClear = true;
            boolean adverseMediaClear = true;

            Object entitiesObject = body.get("entities");
            if (entitiesObject instanceof List<?> entities) {
                for (Object raw : entities) {
                    if (!(raw instanceof Map<?, ?> entity)) {
                        continue;
                    }
                    String type = normalize(entity.get("type"));
                    if ("SANCTIONS".equalsIgnoreCase(type)) {
                        sanctionsClear = false;
                    } else if ("PEP".equalsIgnoreCase(type) || "RCA".equalsIgnoreCase(type)) {
                        pepClear = false;
                    } else if ("ADVERSE_MEDIA".equalsIgnoreCase(type)) {
                        adverseMediaClear = false;
                    }
                }
            }

            // A provider response saying totalMatches > 0 is never silently
            // treated as clear, even when the provider's entity typing changes.
            if (totalMatches > 0 || highestScore >= threshold) {
                if (totalMatches > 0) {
                    sanctionsClear = false;
                }
            }

            boolean clear = sanctionsClear && pepClear && adverseMediaClear;
            String reason = clear
                    ? "Real external AML screening returned no qualifying matches."
                    : "Real external AML screening returned a match or risk signal. Manual review is required."
                    + " riskLevel=" + riskLevel;

            return new ScreeningResult(
                    "PANAFRICASCREEN",
                    true,
                    sanctionsClear,
                    pepClear,
                    adverseMediaClear,
                    Math.min(100.0d, Math.max(0.0d, highestScore * 100.0d)),
                    serialize(body),
                    reason);
        } catch (RestClientException ex) {
            log.error("PanAfrica Screen request failed for borrower {}.", borrower.getId(), ex);
            throw new IllegalStateException(
                    "External AML provider is unavailable. KYC/AML cannot be cleared.", ex);
        }
    }

    private String endpoint(String path) {
        return baseUrl.replaceAll("/+$", "") + "/api/v1" + path;
    }

    private String fullName(Borrower borrower) {
        return ((borrower.getFirstName() == null ? "" : borrower.getFirstName().trim()) + " "
                + (borrower.getLastName() == null ? "" : borrower.getLastName().trim()))
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String normalizeCountry(String country) {
        if (country == null || country.isBlank()) {
            return "RW";
        }
        return country.trim().toUpperCase(Locale.ROOT);
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private double toDouble(Object value) {
        if (value == null) return 0.0d;
        if (value instanceof Number number) return number.doubleValue();
        try {
            return new BigDecimal(String.valueOf(value)).doubleValue();
        } catch (NumberFormatException ignored) {
            return 0.0d;
        }
    }

    private String serialize(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            return "{\"serializationError\":true}";
        }
    }
}
