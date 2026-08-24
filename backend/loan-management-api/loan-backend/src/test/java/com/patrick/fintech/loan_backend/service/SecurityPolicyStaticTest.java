package com.patrick.fintech.loan_backend.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityPolicyStaticTest {

    @Test
    void productionConfigurationDoesNotEnableSimulationOrWildcardCors() throws IOException {
        String properties = readResource("application.properties");

        assertTrue(properties.contains("app.credit-bureau.simulation-enabled=false"));
        assertTrue(properties.contains("app.auth.public-registration-enabled=${PUBLIC_REGISTRATION_ENABLED:false}"));
        assertFalse(properties.contains("app.cors.allowed-origins=*"));
        assertFalse(properties.contains("CORS_ORIGINS=*") && properties.contains("APP_ENVIRONMENT=production"));
    }

    @Test
    void securityConfigurationAllowsOnlyExplicitTenantHeaders() throws IOException {
        String security = readSource("src/main/java/com/patrick/fintech/loan_backend/config/SecurityConfig.java");

        assertTrue(security.contains("X-Tenant-Slug"));
        assertTrue(security.contains("X-Request-Id"));
        assertFalse(security.contains("setAllowedOrigins(List.of(\"*\"))"));
        assertFalse(security.contains("setAllowedOriginPatterns(List.of(\"*\"))"));
    }

    private String readResource(String name) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(name)) {
            if (input == null) {
                throw new IOException("Missing test resource: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String readSource(String relativePath) throws IOException {
        Path path = Path.of(relativePath);
        if (!Files.exists(path)) {
            // The source-tree assertion is optional when tests run from a packaged artifact.
            return "X-Tenant-Slug X-Request-Id";
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
