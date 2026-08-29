package com.patrick.fintech.loan_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.util.Base64;

@Component
public class ProductionConfigurationValidator {
        @Value("${app.environment:development}")
        private String environment;
        @Value("${app.jwt.secret:}")
        private String jwtSecret;
        @Value("${APP_ENCRYPTION_KEY:}")
        private String encryptionKey;
        @Value("${APP_INDEX_KEY:}")
        private String indexKey;
        @Value("${app.credit-bureau.enabled:false}")
        private boolean creditBureauEnabled;
        @Value("${app.credit-bureau.base-url:}")
        private String creditBureauBaseUrl;
        @Value("${app.credit-bureau.api-key:}")
        private String creditBureauApiKey;
        @Value("${app.credit-bureau.simulation-enabled:false}")
        private boolean creditBureauSimulation;
        @Value("${app.cors.allowed-origins:}")
        private String corsOrigins;
        @Value("${app.auth.public-registration-enabled:false}")
        private boolean publicRegistration;
        @Value("${app.public.default-tenant-slug:}")
        private String publicTenant;
        @Value("${spring.jpa.open-in-view:false}")
        private boolean openInView;
        @Value("${app.security.expose-h2:false}")
        private boolean exposeH2;
        @Value("${app.security.expose-api-docs:false}")
        private boolean exposeApiDocs;
        @Value("${app.websocket.allowed-origins:${app.cors.allowed-origins:}}")
        private String websocketOrigins;

        @PostConstruct
        public void validate() {
                if (!isProd())
                        return;
                if (jwtSecret == null || jwtSecret.length() < 32 || isWeak(jwtSecret))
                        throw new IllegalStateException(
                                        "JWT_SECRET must be a strong secret of at least 32 characters in production");

                requireAes256Base64(encryptionKey, "APP_ENCRYPTION_KEY");
                requireBase64AtLeast32Bytes(indexKey, "APP_INDEX_KEY");

                if (creditBureauEnabled) {
                        if (creditBureauBaseUrl == null || creditBureauBaseUrl.isBlank()) {
                                throw new IllegalStateException(
                                                "CREDIT_BUREAU_BASE_URL is required when credit-bureau integration is enabled");
                        }
                        if (creditBureauApiKey == null || creditBureauApiKey.isBlank()) {
                                throw new IllegalStateException(
                                                "CREDIT_BUREAU_API_KEY is required when credit-bureau integration is enabled");
                        }
                        if (creditBureauSimulation) {
                                throw new IllegalStateException(
                                                "Credit-bureau simulation must remain disabled in production");
                        }
                }
                if (corsOrigins == null || corsOrigins.isBlank() || corsOrigins.contains("*"))
                        throw new IllegalStateException("CORS_ORIGINS must contain explicit production origins");
                if (publicRegistration && (publicTenant == null || publicTenant.isBlank()))
                        throw new IllegalStateException(
                                        "PUBLIC_TENANT_SLUG is required when public registration is enabled");
                if (openInView)
                        throw new IllegalStateException("spring.jpa.open-in-view must be false in production");
                if (exposeH2)
                        throw new IllegalStateException("H2 console must remain disabled in production");
                if (exposeApiDocs)
                        throw new IllegalStateException("API documentation must remain disabled in production");
                if (websocketOrigins == null || websocketOrigins.isBlank() || websocketOrigins.contains("*"))
                        throw new IllegalStateException(
                                        "WEBSOCKET_ALLOWED_ORIGINS must contain explicit production origins");
        }

        private void requireAes256Base64(String value, String variable) {
                if (value == null || value.isBlank()) {
                        throw new IllegalStateException(variable + " is required in production");
                }
                try {
                        byte[] bytes = Base64.getDecoder().decode(value);
                        if (bytes.length != 32) {
                                throw new IllegalStateException(
                                                variable + " must decode to exactly 32 bytes (AES-256) in production");
                        }
                } catch (IllegalArgumentException e) {
                        throw new IllegalStateException(variable + " must be valid Base64 in production", e);
                }
        }

        private void requireBase64AtLeast32Bytes(String value, String variable) {
                if (value == null || value.isBlank()) {
                        throw new IllegalStateException(variable + " is required in production");
                }
                try {
                        byte[] bytes = Base64.getDecoder().decode(value);
                        if (bytes.length < 32) {
                                throw new IllegalStateException(
                                                variable + " must decode to at least 32 bytes in production");
                        }
                } catch (IllegalArgumentException e) {
                        throw new IllegalStateException(variable + " must be valid Base64 in production", e);
                }
        }

        private boolean isProd() {
                return "production".equalsIgnoreCase(environment) || "prod".equalsIgnoreCase(environment);
        }

        private boolean isWeak(String s) {
                String x = s.toLowerCase();
                return x.contains("change-me") || x.contains("secret") || x.contains("password")
                                || x.matches("(.)\\1{15,}");
        }
}
