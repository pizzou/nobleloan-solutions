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

        @Value("${mtn.momo.sandbox:true}")
        private boolean mtnSandbox;

        @Value("${mtn.momo.base-url:}")
        private String mtnBaseUrl;

        @Value("${mtn.momo.subscription-key:}")
        private String mtnSubscriptionKey;

        @Value("${mtn.momo.api-user:}")
        private String mtnApiUser;

        @Value("${mtn.momo.api-key:}")
        private String mtnApiKey;

        @Value("${mtn.momo.callback-url:}")
        private String mtnCallbackUrl;

        @Value("${mtn.momo.webhook-secret:}")
        private String mtnWebhookSecret;

        @Value("${airtel.money.enabled:false}")
        private boolean airtelEnabled;

        @Value("${airtel.money.base-url:}")
        private String airtelBaseUrl;

        @Value("${airtel.money.client-id:}")
        private String airtelClientId;

        @Value("${airtel.money.client-secret:}")
        private String airtelClientSecret;

        @Value("${airtel.money.callback-url:}")
        private String airtelCallbackUrl;

        @Value("${airtel.money.webhook-secret:}")
        private String airtelWebhookSecret;

        @Value("${flutterwave.enabled:false}")
        private boolean flutterwaveEnabled;

        @Value("${flutterwave.secret-key:}")
        private String flutterwaveSecretKey;

        @Value("${flutterwave.webhook-secret:}")
        private String flutterwaveWebhookSecret;

        @Value("${app.credit-bureau.enabled:false}")
        private boolean creditBureauEnabled;

        @Value("${app.credit-bureau.base-url:}")
        private String creditBureauBaseUrl;

        @Value("${app.credit-bureau.api-key:}")
        private String creditBureauApiKey;

        @Value("${app.credit-bureau.required-for-disbursement:false}")
        private boolean creditBureauRequiredForDisbursement;

        @Value("${app.compliance.external-provider-enabled:false}")
        private boolean complianceExternalProviderEnabled;

        @Value("${app.compliance.provider:}")
        private String complianceProvider;

        @Value("${app.compliance.base-url:}")
        private String complianceBaseUrl;

        @Value("${app.compliance.api-key:}")
        private String complianceApiKey;

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
                        if (mtnSandbox) {
                                throw new IllegalStateException(
                                                "MTN_MOMO_SANDBOX must be false in production");
                        }
                        require(mtnBaseUrl, "MTN_MOMO_BASE_URL");
                        require(mtnSubscriptionKey, "MTN_MOMO_SUBSCRIPTION_KEY");
                        require(mtnApiUser, "MTN_MOMO_API_USER");
                        require(mtnApiKey, "MTN_MOMO_API_KEY");
                        require(mtnCallbackUrl, "MTN_MOMO_CALLBACK_URL");
                        require(mtnWebhookSecret, "MTN_MOMO_WEBHOOK_SECRET");
                }

                if (airtelEnabled) {
                        require(airtelBaseUrl, "AIRTEL_MONEY_BASE_URL");
                        require(airtelClientId, "AIRTEL_MONEY_CLIENT_ID");
                        require(airtelClientSecret, "AIRTEL_MONEY_CLIENT_SECRET");
                        require(airtelCallbackUrl, "AIRTEL_MONEY_CALLBACK_URL");
                        require(airtelWebhookSecret, "AIRTEL_MONEY_WEBHOOK_SECRET");
                }

                if (flutterwaveEnabled) {
                        require(flutterwaveSecretKey, "FLUTTERWAVE_SECRET_KEY");
                        require(flutterwaveWebhookSecret, "FLUTTERWAVE_WEBHOOK_SECRET");
                }

                if (creditBureauRequiredForDisbursement && !creditBureauEnabled) {
                        throw new IllegalStateException(
                                        "CREDIT_BUREAU_REQUIRED_FOR_DISBURSEMENT=true requires CREDIT_BUREAU_ENABLED=true");
                }

                if (creditBureauEnabled) {
                        require(creditBureauBaseUrl, "CREDIT_BUREAU_BASE_URL");
                        require(creditBureauApiKey, "CREDIT_BUREAU_API_KEY");
                }

                if (complianceExternalProviderEnabled) {
                        require(complianceProvider, "COMPLIANCE_PROVIDER");
                        require(complianceBaseUrl, "COMPLIANCE_BASE_URL");
                        require(complianceApiKey, "COMPLIANCE_API_KEY");
                }

                require(importStagingDir, "IMPORT_STAGING_DIR");
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