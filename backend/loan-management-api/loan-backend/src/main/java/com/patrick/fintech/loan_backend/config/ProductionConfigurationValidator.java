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
        @Value("${app.jwt.expiration-ms:900000}")
        private long jwtExpirationMs;
        @Value("${app.compliance.external-provider-enabled:false}")
        private boolean externalComplianceEnabled;
        @Value("${app.compliance.provider:}")
        private String complianceProvider;
        @Value("${app.compliance.base-url:}")
        private String complianceBaseUrl;
        @Value("${app.compliance.api-key:}")
        private String complianceApiKey;
        @Value("${mtn.momo.sandbox:true}")
        private boolean mtnSandbox;
        @Value("${mtn.momo.enabled:false}")
        private boolean mtnEnabled;
        @Value("${mtn.momo.webhook-secret:}")
        private String mtnWebhookSecret;
        @Value("${app.auth.cookie.same-site:Lax}")
        private String authCookieSameSite;
        @Value("${app.mail.enabled:false}")
        private boolean mailEnabled;
        @Value("${app.mail.brevo-api-key:}")
        private String brevoApiKey;
        @Value("${app.mail.from:}")
        private String mailFrom;
        @Value("${app.sms.enabled:false}")
        private boolean smsEnabled;
        @Value("${app.sms.twilio.account-sid:}")
        private String twilioAccountSid;
        @Value("${app.sms.twilio.auth-token:}")
        private String twilioAuthToken;
        @Value("${app.sms.twilio.from-number:}")
        private String twilioFromNumber;
        @Value("${app.sms.africas-talking.api-key:}")
        private String atApiKey;
        @Value("${app.sms.africas-talking.username:}")
        private String atUsername;
        @Value("${app.sms.africas-talking.sender-id:}")
        private String atSenderId;
        @Value("${app.regulatory.provision.current:0}") private String provisionCurrent;
        @Value("${app.regulatory.provision.watch:0}") private String provisionWatch;
        @Value("${app.regulatory.provision.substandard:0}") private String provisionSubstandard;
        @Value("${app.regulatory.provision.doubtful:0}") private String provisionDoubtful;
        @Value("${app.regulatory.provision.written-off:0}") private String provisionWrittenOff;
        @Value("${app.regulatory.provision.policy-approved:false}") private boolean provisioningPolicyApproved;

        @PostConstruct
        public void validate() {
                if (!isProd())
                        return;
                if (jwtSecret == null || jwtSecret.length() < 32 || isWeak(jwtSecret))
                        throw new IllegalStateException(
                                        "JWT_SECRET must be a strong secret of at least 32 characters in production");

                if (!"Lax".equalsIgnoreCase(authCookieSameSite) && !"Strict".equalsIgnoreCase(authCookieSameSite)) {
                        throw new IllegalStateException("AUTH_COOKIE_SAME_SITE must be Lax or Strict in production. Use the same-origin Next.js /api proxy rather than cross-site browser cookies.");
                }

                if (jwtExpirationMs <= 0 || jwtExpirationMs > 30 * 60 * 1000L) {
                        throw new IllegalStateException("JWT_EXPIRATION_MS must be between 1 second and 30 minutes in production");
                }

                if (mtnEnabled) {
                        if (mtnSandbox) {
                                throw new IllegalStateException("MTN_MOMO_SANDBOX must be false when MTN Mobile Money is enabled in production");
                        }
                        if (mtnWebhookSecret == null || mtnWebhookSecret.isBlank()) {
                                throw new IllegalStateException("MTN_MOMO_WEBHOOK_SECRET is required when MTN Mobile Money is enabled in production");
                        }
                }

                if (!mailEnabled || brevoApiKey == null || brevoApiKey.isBlank()
                                || mailFrom == null || mailFrom.isBlank()) {
                        throw new IllegalStateException("MAIL_ENABLED=true, BREVO_API_KEY and MAIL_FROM are required in production");
                }

                boolean twilioConfigured = twilioAccountSid != null && !twilioAccountSid.isBlank()
                                && twilioAuthToken != null && !twilioAuthToken.isBlank()
                                && twilioFromNumber != null && !twilioFromNumber.isBlank();
                boolean africasTalkingConfigured = atApiKey != null && !atApiKey.isBlank()
                                && atUsername != null && !atUsername.isBlank()
                                && atSenderId != null && !atSenderId.isBlank();
                if (!smsEnabled || (!twilioConfigured && !africasTalkingConfigured)) {
                        throw new IllegalStateException("SMS_ENABLED=true and at least one complete production SMS provider configuration are required because ADMIN/MANAGER login requires SMS OTP");
                }

                if (!provisioningPolicyApproved) {
                        throw new IllegalStateException("PROVISIONING_POLICY_APPROVED must be true in production after finance/compliance approval of the provisioning methodology");
                }
                validateProvisionRate(provisionCurrent, "PROVISION_RATE_CURRENT");
                validateProvisionRate(provisionWatch, "PROVISION_RATE_WATCH");
                validateProvisionRate(provisionSubstandard, "PROVISION_RATE_SUBSTANDARD");
                validateProvisionRate(provisionDoubtful, "PROVISION_RATE_DOUBTFUL");
                validateProvisionRate(provisionWrittenOff, "PROVISION_RATE_WRITTEN_OFF");

                if (!externalComplianceEnabled) {
                        throw new IllegalStateException("COMPLIANCE_EXTERNAL_PROVIDER_ENABLED must be true in production; provider-backed KYC/AML is a mandatory disbursement control");
                }
                if (complianceProvider == null || complianceProvider.isBlank()
                                || complianceBaseUrl == null || complianceBaseUrl.isBlank()
                                || complianceApiKey == null || complianceApiKey.isBlank()) {
                        throw new IllegalStateException("COMPLIANCE_PROVIDER, COMPLIANCE_BASE_URL and COMPLIANCE_API_KEY are required in production");
                }

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

        private void validateProvisionRate(String value, String name) {
                try {
                        java.math.BigDecimal rate = new java.math.BigDecimal(value);
                        if (rate.signum() < 0 || rate.compareTo(new java.math.BigDecimal("100")) > 0) {
                                throw new IllegalStateException(name + " must be between 0 and 100");
                        }
                } catch (NumberFormatException e) {
                        throw new IllegalStateException(name + " must be a decimal percentage");
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
