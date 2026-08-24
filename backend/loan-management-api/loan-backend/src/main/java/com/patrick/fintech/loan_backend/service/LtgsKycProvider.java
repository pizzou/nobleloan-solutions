package com.patrick.fintech.loan_backend.service;

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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rwanda KYC adapter for LTGS Rwanda's documented KYC API.
 *
 * Production use still requires a commercial/API onboarding relationship and
 * credentials from the provider. The API contract must be re-checked against
 * the provider's production documentation before certification.
 */
@Service
@Slf4j
public class LtgsKycProvider {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.compliance.kyc-enabled:false}")
    private boolean enabled;

    @Value("${app.compliance.kyc-provider:LTGS}")
    private String provider;

    @Value("${app.compliance.kyc-base-url:}")
    private String baseUrl;

    @Value("${app.compliance.kyc-api-key:}")
    private String apiKey;

    @Value("${app.compliance.kyc-path:/api/}")
    private String path;

    public LtgsKycProvider(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return enabled
                && "LTGS".equalsIgnoreCase(provider)
                && baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank();
    }

    public Result verify(Borrower borrower) {
        if (!isEnabled()) {
            throw new IllegalStateException(
                    "Rwanda KYC provider is not configured. LTGS KYC credentials are required for production identity verification.");
        }
        if (borrower == null) {
            throw new IllegalArgumentException("Borrower is required.");
        }
        if (borrower.getNationalId() == null || borrower.getNationalId().isBlank()) {
            throw new IllegalArgumentException("Borrower national ID is required for Rwanda KYC verification.");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id_number", borrower.getNationalId().trim());
        payload.put("id_type", "NID");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(apiKey);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    endpoint(),
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    Map.class);

            if (response == null || !response.getStatusCode().is2xxSuccessful()) {
                int status = response == null ? -1 : response.getStatusCode().value();
                throw new IllegalStateException("Rwanda KYC provider returned HTTP " + status + ".");
            }

            Map<?, ?> body = response.getBody();
            if (body == null) {
                throw new IllegalStateException("Rwanda KYC provider returned an empty response.");
            }

            Object verifiedValue = body.get("verified");
            boolean verified = Boolean.TRUE.equals(verifiedValue)
                    || "true".equalsIgnoreCase(String.valueOf(verifiedValue));

            Object verifiedName = body.get("name");
            Object verifiedDob = body.get("dob");

            return new Result(
                    verified,
                    verifiedName == null ? "" : String.valueOf(verifiedName),
                    verifiedDob == null ? "" : String.valueOf(verifiedDob),
                    objectMapper.writeValueAsString(body));
        } catch (RestClientException ex) {
            log.error("Rwanda KYC provider request failed for borrower {}.", borrower.getId(), ex);
            throw new IllegalStateException(
                    "Rwanda KYC provider is unavailable. Identity cannot be cleared.", ex);
        } catch (Exception ex) {
            log.error("Rwanda KYC provider response could not be processed for borrower {}.", borrower.getId(), ex);
            throw new IllegalStateException(
                    "Rwanda KYC provider response could not be processed.", ex);
        }
    }

    private String endpoint() {
        String normalizedBase = baseUrl.replaceAll("/+$", "");
        String normalizedPath = path == null ? "/api/" : path.trim();
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        return normalizedBase + normalizedPath;
    }

    public record Result(
            boolean verified,
            String verifiedName,
            String verifiedDateOfBirth,
            String rawResponse) {
    }
}
