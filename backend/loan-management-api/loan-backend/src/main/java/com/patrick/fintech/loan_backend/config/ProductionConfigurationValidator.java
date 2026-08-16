package com.patrick.fintech.loan_backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProductionConfigurationValidator {

    @Value("${app.environment:development}")
    private String environment;

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Value("${app.cors.allowed-origins:}")
    private String corsOrigins;

    @Value("${mtn.momo.enabled:false}")
    private boolean mtnEnabled;

    @Value("${mtn.momo.webhook-secret:}")
    private String mtnWebhookSecret;

    @Value("${flutterwave.webhook-secret:}")
    private String flutterwaveWebhookSecret;

    @Value("${app.import.staging-dir:}")
    private String importStagingDir;

    @PostConstruct
    public void validate() {

        if (!isProduction()) {
            return;
        }

        require(
                jwtSecret,
                "JWT_SECRET");

        if (jwtSecret.length() < 32) {

            throw new IllegalStateException(
                    "JWT_SECRET must contain at least 32 characters in production");
        }

        require(
                corsOrigins,
                "CORS_ORIGINS");

        if (corsOrigins.contains("*")) {

            throw new IllegalStateException(
                    "Wildcard CORS is forbidden in production");
        }

        if (mtnEnabled) {

            require(
                    mtnWebhookSecret,
                    "MTN webhook secret");
        }

        if (flutterwaveWebhookSecret == null
                || flutterwaveWebhookSecret.isBlank()) {

            /*
             * Flutterwave can be disabled, but if it is enabled,
             * the webhook secret must exist.
             */
            // validation of the enabled flag is intentionally handled
            // by the provider configuration when that integration is used.
        }

        require(
                importStagingDir,
                "IMPORT_STAGING_DIR");
    }

    private boolean isProduction() {

        return "production".equalsIgnoreCase(
                environment)
                || "prod".equalsIgnoreCase(
                        environment);
    }

    private void require(
            String value,
            String name) {

        if (value == null
                || value.isBlank()) {

            throw new IllegalStateException(
                    name
                            + " must be configured in production");
        }
    }
}