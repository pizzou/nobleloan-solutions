package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.PaymentGatewayRequest;
import com.patrick.fintech.loan_backend.dto.PaymentGatewayResponse;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MtnMobileMoneyService {

    private static final String MTN_PROVIDER = "MTN_MOMO";

    private final WebClient.Builder webClientBuilder;
    private final PaymentService paymentService;
    private final LoanRepository loanRepo;

    // ============================================================
    // CONFIGURATION
    // ============================================================

    @Value("${mtn.momo.enabled:false}")
    private boolean enabled;

    @Value("${mtn.momo.sandbox:false}")
    private boolean sandbox;

    @Value("${mtn.momo.base-url:}")
    private String baseUrl;

    @Value("${mtn.momo.subscription-key:}")
    private String subscriptionKey;

    @Value("${mtn.momo.api-user:}")
    private String apiUser;

    @Value("${mtn.momo.api-key:}")
    private String apiKey;

    @Value("${mtn.momo.environment:mtnrwanda}")
    private String environment;

    @Value("${mtn.momo.currency:RWF}")
    private String configuredCurrency;

    @Value("${mtn.momo.callback-url:}")
    private String callbackUrl;

    @Value("${app.environment:development}")
    private String applicationEnvironment;

    // ============================================================
    // STARTUP VALIDATION
    // ============================================================

    @PostConstruct
    private void validateConfiguration() {

        String normalizedEnvironment =
                environment == null
                        ? ""
                        : environment.trim().toLowerCase();

        String normalizedBaseUrl =
                baseUrl == null
                        ? ""
                        : baseUrl.trim();

        String normalizedCurrency =
                configuredCurrency == null
                        ? ""
                        : configuredCurrency.trim().toUpperCase();

        log.info(
                "[MTN MOMO] Configuration loaded. " +
                        "enabled={}, sandbox={}, environment={}, " +
                        "baseUrl={}, currency={}, callbackConfigured={}, " +
                        "applicationEnvironment={}",
                enabled,
                sandbox,
                normalizedEnvironment,
                sanitizeBaseUrl(normalizedBaseUrl),
                normalizedCurrency,
                callbackUrl != null && !callbackUrl.isBlank(),
                applicationEnvironment
        );

        // --------------------------------------------------------
        // Disabled integration
        // --------------------------------------------------------

        if (!enabled) {

            log.info(
                    "[MTN MOMO] Integration is disabled. " +
                            "No MTN Mobile Money calls will be made."
            );

            return;
        }

        // --------------------------------------------------------
        // Sandbox configuration
        // --------------------------------------------------------

        if (sandbox) {

            if (isProductionEnvironment()) {

                throw new IllegalStateException(
                        "MTN Mobile Money sandbox mode cannot be enabled in production."
                );
            }

            if (!"sandbox".equalsIgnoreCase(normalizedEnvironment)) {

                throw new IllegalStateException(
                        "MTN sandbox mode requires " +
                                "mtn.momo.environment=sandbox."
                );
            }

            if (normalizedBaseUrl.isBlank()) {

                throw new IllegalStateException(
                        "MTN sandbox mode requires mtn.momo.base-url."
                );
            }

            log.warn(
                    "[MTN MOMO] SANDBOX MODE ENABLED. " +
                            "This configuration must not be used for real production payments."
            );

            return;
        }

        // --------------------------------------------------------
        // Production configuration
        // --------------------------------------------------------

        if (isProductionEnvironment()) {

            if (!"mtnrwanda".equalsIgnoreCase(normalizedEnvironment)) {

                throw new IllegalStateException(
                        "MTN production mode for Rwanda requires " +
                                "X-Target-Environment=mtnrwanda. " +
                                "Current value=" + environment
                );
            }

            if (normalizedBaseUrl.isBlank()) {

                throw new IllegalStateException(
                        "MTN production mode requires MTN_MOMO_BASE_URL."
                );
            }

            if (isSandboxBaseUrl(normalizedBaseUrl)) {

                throw new IllegalStateException(
                        "MTN production mode cannot use the sandbox MTN base URL."
                );
            }

            if (!isConfigured()) {

                throw new IllegalStateException(
                        "MTN Mobile Money is enabled in production " +
                                "but required credentials are missing."
                );
            }

            if (callbackUrl == null || callbackUrl.isBlank()) {

                throw new IllegalStateException(
                        "MTN production mode requires MTN_MOMO_CALLBACK_URL."
                );
            }

            if (!callbackUrl.startsWith("https://")) {

                throw new IllegalStateException(
                        "MTN production callback URL must use HTTPS."
                );
            }

            log.info(
                    "[MTN MOMO] Production configuration validated successfully."
            );

            return;
        }

        // --------------------------------------------------------
        // Non-production real API configuration
        // --------------------------------------------------------

        if (!"sandbox".equalsIgnoreCase(normalizedEnvironment)
                && !"mtnrwanda".equalsIgnoreCase(normalizedEnvironment)) {

            throw new IllegalStateException(
                    "Unsupported MTN target environment: " + environment
            );
        }

        if (normalizedBaseUrl.isBlank()) {

            throw new IllegalStateException(
                    "MTN Mobile Money base URL is required."
            );
        }

        if (!isConfigured()) {

            log.warn(
                    "[MTN MOMO] Integration is enabled but " +
                            "credentials are incomplete."
            );
        }
    }

    // ============================================================
    // AVAILABILITY
    // ============================================================

    public boolean isAvailable() {

        if (!enabled) {
            return false;
        }

        if (sandbox) {
            return !isProductionEnvironment();
        }

        return isConfigured();
    }

    // ============================================================
    // INITIATE PAYMENT
    // ============================================================

    public PaymentGatewayResponse initiate(
            Long loanId,
            PaymentGatewayRequest request,
            Double amount,
            String currency,
            String description
    ) {

        // --------------------------------------------------------
        // BASIC VALIDATION
        // --------------------------------------------------------

        if (loanId == null) {

            return PaymentGatewayResponse.failed(
                    "Loan ID is required",
                    MTN_PROVIDER
            );
        }

        if (request == null) {

            return PaymentGatewayResponse.failed(
                    "Payment request is required",
                    MTN_PROVIDER
            );
        }

        if (amount == null || amount <= 0) {

            return PaymentGatewayResponse.failed(
                    "Payment amount must be greater than zero",
                    MTN_PROVIDER
            );
        }

        if (request.getPhoneNumber() == null
                || request.getPhoneNumber().isBlank()) {

            return PaymentGatewayResponse.failed(
                    "MTN Mobile Money phone number is required",
                    MTN_PROVIDER
            );
        }

        // --------------------------------------------------------
        // VERIFY LOAN
        // --------------------------------------------------------

        loanRepo.findById(loanId)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Loan not found: " + loanId
                        )
                );

        // --------------------------------------------------------
        // NORMALIZE PHONE
        // --------------------------------------------------------

        String phoneNumber =
                normalizeRwandaPhone(
                        request.getPhoneNumber()
                );

        if (phoneNumber.isBlank()) {

            return PaymentGatewayResponse.failed(
                    "Invalid Rwanda mobile money phone number",
                    MTN_PROVIDER
            );
        }

        // --------------------------------------------------------
        // CURRENCY
        // --------------------------------------------------------

        String paymentCurrency =
                normalizeCurrency(currency);

        // --------------------------------------------------------
        // VALIDATE CURRENCY
        // --------------------------------------------------------

        if (paymentCurrency.isBlank()) {

            return PaymentGatewayResponse.failed(
                    "Payment currency is required",
                    MTN_PROVIDER
            );
        }

        // --------------------------------------------------------
        // TRANSACTION ID
        // --------------------------------------------------------

        String transactionId =
                createSandboxTransactionId(loanId);

        log.info(
                "[MTN MOMO] Initiating payment. " +
                        "loanId={}, amount={}, currency={}, phone={}, " +
                        "transactionId={}, sandbox={}, environment={}",
                loanId,
                amount,
                paymentCurrency,
                maskPhone(phoneNumber),
                transactionId,
                sandbox,
                environment
        );

        // --------------------------------------------------------
        // DISABLED
        // --------------------------------------------------------

        if (!enabled) {

            return PaymentGatewayResponse.failed(
                    "MTN Mobile Money integration is disabled",
                    MTN_PROVIDER
            );
        }

        // --------------------------------------------------------
        // SANDBOX
        // --------------------------------------------------------

        if (sandbox) {

            log.info(
                    "[MTN SANDBOX] Payment request created. " +
                            "loanId={}, transactionId={}, amount={}, currency={}",
                    loanId,
                    transactionId,
                    amount,
                    paymentCurrency
            );

            return PaymentGatewayResponse.pending(
                    "MTN Mobile Money sandbox payment created. " +
                            "Waiting for simulated customer confirmation.",
                    transactionId,
                    MTN_PROVIDER
            );
        }

        // --------------------------------------------------------
        // PRODUCTION / REAL API
        // --------------------------------------------------------

        if (!isConfigured()) {

            log.error(
                    "[MTN MOMO] Real MTN integration requested " +
                            "but credentials/configuration are missing."
            );

            return PaymentGatewayResponse.failed(
                    "MTN Mobile Money credentials are not configured",
                    MTN_PROVIDER
            );
        }

        try {

            // ----------------------------------------------------
            // MTN REQUEST REFERENCE
            // ----------------------------------------------------

            String referenceId =
                    UUID.randomUUID().toString();

            // ----------------------------------------------------
            // ACCESS TOKEN
            // ----------------------------------------------------

            String accessToken =
                    getAccessToken();

            if (accessToken == null
                    || accessToken.isBlank()) {

                return PaymentGatewayResponse.failed(
                        "Unable to authenticate with MTN Mobile Money",
                        MTN_PROVIDER
                );
            }

            // ----------------------------------------------------
            // EXTERNAL REFERENCE
            // ----------------------------------------------------

            String externalId =
                    externalReference(loanId);

            // ----------------------------------------------------
            // REQUEST BODY
            // ----------------------------------------------------

            MtnRequestBody body =
                    new MtnRequestBody(
                            formatAmount(amount),
                            paymentCurrency,
                            externalId,
                            new MtnPayer(
                                    phoneNumber,
                                    "MSISDN"
                            ),
                            description != null
                                    && !description.isBlank()
                                    ? description.trim()
                                    : "Loan repayment"
                    );

            log.info(
                    "[MTN MOMO] Sending requestToPay. " +
                            "loanId={}, referenceId={}, amount={}, " +
                            "currency={}, payer={}",
                    loanId,
                    referenceId,
                    body.amount(),
                    body.currency(),
                    maskPhone(phoneNumber)
            );

            // ----------------------------------------------------
            // REQUEST TO PAY
            // ----------------------------------------------------

            var requestSpec =
                    webClientBuilder
                            .baseUrl(normalizedBaseUrl())
                            .build()
                            .post()
                            .uri(
                                    "/collection/v1_0/requesttopay"
                            )
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .accept(
                                    MediaType.APPLICATION_JSON
                            )
                            .header(
                                    "Authorization",
                                    "Bearer " + accessToken
                            )
                            .header(
                                    "X-Reference-Id",
                                    referenceId
                            )
                            .header(
                                    "X-Target-Environment",
                                    environment.trim()
                            )
                            .header(
                                    "Ocp-Apim-Subscription-Key",
                                    subscriptionKey.trim()
                            );

            // ----------------------------------------------------
            // CALLBACK URL
            // ----------------------------------------------------

            if (callbackUrl != null
                    && !callbackUrl.isBlank()) {

                requestSpec =
                        requestSpec.header(
                                "X-Callback-Url",
                                callbackUrl.trim()
                        );

                log.info(
                        "[MTN MOMO] Callback URL supplied: {}",
                        callbackUrl.trim()
                );
            }

            // ----------------------------------------------------
            // EXECUTE REQUEST
            // ----------------------------------------------------

            var response =
                    requestSpec
                            .bodyValue(body)
                            .retrieve()
                            .toBodilessEntity()
                            .block();

            int status =
                    response != null
                            ? response.getStatusCode().value()
                            : -1;

            log.info(
                    "[MTN MOMO] Request submitted. " +
                            "HTTP status={}, loanId={}, referenceId={}",
                    status,
                    loanId,
                    referenceId
            );

            // ----------------------------------------------------
            // MTN EXPECTS 202
            // ----------------------------------------------------

            if (status < 200 || status >= 300) {

                return PaymentGatewayResponse.failed(
                        "MTN Mobile Money rejected the payment request",
                        MTN_PROVIDER
                );
            }

            return PaymentGatewayResponse.pending(
                    "Payment request sent to MTN Mobile Money. " +
                            "Please approve the payment on your phone.",
                    referenceId,
                    MTN_PROVIDER
            );

        } catch (WebClientResponseException e) {

            String responseBody =
                    e.getResponseBodyAsString();

            log.error(
                    "[MTN MOMO] Payment initiation rejected by MTN. " +
                            "loanId={}, httpStatus={}, environment={}, " +
                            "response={}",
                    loanId,
                    e.getStatusCode().value(),
                    environment,
                    sanitizeMtnError(responseBody),
                    e
            );

            return PaymentGatewayResponse.failed(
                    buildMtnInitiationErrorMessage(
                            e.getStatusCode().value(),
                            responseBody
                    ),
                    MTN_PROVIDER
            );

        } catch (Exception e) {

            log.error(
                    "[MTN MOMO] Payment initiation failed. " +
                            "loanId={}, error={}",
                    loanId,
                    e.getMessage(),
                    e
            );

            return PaymentGatewayResponse.failed(
                    "MTN Mobile Money payment initiation failed",
                    MTN_PROVIDER
            );
        }
    }

    // ============================================================
    // SANDBOX CONFIRMATION
    // ============================================================

    @Transactional
    public PaymentGatewayResponse simulateConfirmation(
            Long loanId,
            String transactionId,
            Double amount,
            String currency
    ) {

        if (!sandbox) {

            return PaymentGatewayResponse.failed(
                    "Sandbox simulation is disabled",
                    MTN_PROVIDER
            );
        }

        return confirmPayment(
                loanId,
                transactionId,
                amount,
                currency,
                "SANDBOX"
        );
    }

    // ============================================================
    // CONFIRM PAYMENT
    // ============================================================

    @Transactional
    public PaymentGatewayResponse confirmPayment(
            Long loanId,
            String transactionId,
            Double amount,
            String currency,
            String confirmationSource
    ) {

        // --------------------------------------------------------
        // VALIDATION
        // --------------------------------------------------------

        if (loanId == null) {

            return PaymentGatewayResponse.failed(
                    "Loan ID is required",
                    MTN_PROVIDER
            );
        }

        if (transactionId == null
                || transactionId.isBlank()) {

            return PaymentGatewayResponse.failed(
                    "Transaction ID is required",
                    MTN_PROVIDER
            );
        }

        if (amount == null || amount <= 0) {

            return PaymentGatewayResponse.failed(
                    "Payment amount must be greater than zero",
                    MTN_PROVIDER
            );
        }

        // --------------------------------------------------------
        // VERIFY LOAN
        // --------------------------------------------------------

        var loan =
                loanRepo.findById(loanId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Loan not found: " + loanId
                                )
                        );

        if (loan.getOrganization() == null
                || loan.getOrganization().getId() == null) {

            return PaymentGatewayResponse.failed(
                    "Loan organization is missing",
                    MTN_PROVIDER
            );
        }

        // --------------------------------------------------------
        // TRANSACTION ID
        // --------------------------------------------------------

        String normalizedTransactionId =
                transactionId.trim();

        // --------------------------------------------------------
        // CURRENCY
        // --------------------------------------------------------

        String paymentCurrency =
                normalizeCurrency(currency);

        if (paymentCurrency.isBlank()) {

            return PaymentGatewayResponse.failed(
                    "Payment currency is required",
                    MTN_PROVIDER
            );
        }

        // --------------------------------------------------------
        // VERIFY REAL MTN PAYMENT
        // --------------------------------------------------------

        if (!sandbox) {

            boolean verified =
                    verify(
                            normalizedTransactionId
                    );

            if (!verified) {

                log.warn(
                        "[MTN MOMO] Payment confirmation rejected. " +
                                "loanId={}, transactionId={}, source={}",
                        loanId,
                        normalizedTransactionId,
                        confirmationSource
                );

                return PaymentGatewayResponse.failed(
                        "MTN Mobile Money transaction has not been confirmed as successful",
                        MTN_PROVIDER
                );
            }
        }

        // --------------------------------------------------------
        // LOG
        // --------------------------------------------------------

        log.info(
                "[MTN MOMO] Confirming payment. " +
                        "loanId={}, transactionId={}, amount={}, " +
                        "currency={}, source={}",
                loanId,
                normalizedTransactionId,
                amount,
                paymentCurrency,
                confirmationSource
        );

        // --------------------------------------------------------
        // RECORD PAYMENT
        // --------------------------------------------------------

        Payment payment;

        try {

            payment =
                    paymentService.recordPayment(
                            loanId,
                            BigDecimal.valueOf(amount),
                            "MOBILE_MONEY",
                            normalizedTransactionId,
                            MTN_PROVIDER,
                            "Confirmed by MTN Mobile Money. Source="
                                    + safeConfirmationSource(
                                    confirmationSource
                            ),
                            (User) null
                    );

        } catch (Exception e) {

            log.error(
                    "[MTN MOMO] Failed to record confirmed payment. " +
                            "loanId={}, transactionId={}, amount={}",
                    loanId,
                    normalizedTransactionId,
                    amount,
                    e
            );

            return PaymentGatewayResponse.failed(
                    "MTN payment was confirmed but could not be recorded against the loan",
                    MTN_PROVIDER
            );
        }

        // --------------------------------------------------------
        // FINANCIAL RESULT
        // --------------------------------------------------------

        BigDecimal outstandingBalance =
                payment.getOutstandingAfterDecimal() != null
                        ? payment.getOutstandingAfterDecimal()
                        : loan.getOutstandingBalanceDecimal();

        BigDecimal interestPaid =
                payment.getInterestComponentDecimal();

        BigDecimal principalPaid =
                payment.getPrincipalComponentDecimal();

        log.info(
                "[MTN MOMO] Payment successfully recorded. " +
                        "loanId={}, paymentId={}, transactionId={}, " +
                        "amount={}, interestPaid={}, principalPaid={}, " +
                        "outstandingBalance={}, paymentStatus={}",
                loanId,
                payment.getId(),
                normalizedTransactionId,
                amount,
                interestPaid,
                principalPaid,
                outstandingBalance,
                payment.getStatus()
        );

        // --------------------------------------------------------
        // SUCCESS
        // --------------------------------------------------------

        return PaymentGatewayResponse.success(
                "MTN Mobile Money payment confirmed and recorded against the loan",
                normalizedTransactionId,
                amount,
                paymentCurrency,
                "MOBILE_MONEY",
                MTN_PROVIDER
        );
    }

    // ============================================================
    // WEBHOOK / CALLBACK
    // ============================================================

    @Transactional
    public PaymentGatewayResponse processWebhookConfirmation(
            Long loanId,
            String transactionId,
            Double amount,
            String currency
    ) {

        if (sandbox) {

            return confirmPayment(
                    loanId,
                    transactionId,
                    amount,
                    currency,
                    "MTN_SANDBOX_WEBHOOK"
            );
        }

        return confirmPayment(
                loanId,
                transactionId,
                amount,
                currency,
                "MTN_WEBHOOK"
        );
    }

    // ============================================================
    // VERIFY TRANSACTION
    // ============================================================

    public boolean verify(
            String transactionId
    ) {

        if (transactionId == null
                || transactionId.isBlank()) {

            return false;
        }

        String normalizedTransactionId =
                transactionId.trim();

        // --------------------------------------------------------
        // SANDBOX
        // --------------------------------------------------------

        if (sandbox) {

            log.info(
                    "[MTN SANDBOX] Transaction {} considered verified for testing",
                    normalizedTransactionId
            );

            return true;
        }

        // --------------------------------------------------------
        // CONFIGURATION
        // --------------------------------------------------------

        if (!isConfigured()) {

            log.warn(
                    "[MTN MOMO] Cannot verify transaction because " +
                            "credentials are missing."
            );

            return false;
        }

        // --------------------------------------------------------
        // MTN STATUS API
        // --------------------------------------------------------

        try {

            String accessToken =
                    getAccessToken();

            if (accessToken == null
                    || accessToken.isBlank()) {

                return false;
            }

            Map<?, ?> response =
                    webClientBuilder
                            .baseUrl(normalizedBaseUrl())
                            .build()
                            .get()
                            .uri(
                                    "/collection/v1_0/requesttopay/{referenceId}",
                                    normalizedTransactionId
                            )
                            .header(
                                    "Authorization",
                                    "Bearer " + accessToken
                            )
                            .header(
                                    "X-Target-Environment",
                                    environment.trim()
                            )
                            .header(
                                    "Ocp-Apim-Subscription-Key",
                                    subscriptionKey.trim()
                            )
                            .accept(
                                    MediaType.APPLICATION_JSON
                            )
                            .retrieve()
                            .bodyToMono(Map.class)
                            .block();

            if (response == null) {

                return false;
            }

            Object status =
                    response.get("status");

            boolean successful =
                    status != null
                            && "SUCCESSFUL".equalsIgnoreCase(
                            status.toString()
                    );

            log.info(
                    "[MTN MOMO] Transaction verification. " +
                            "transactionId={}, status={}, successful={}",
                    normalizedTransactionId,
                    status,
                    successful
            );

            return successful;

        } catch (WebClientResponseException e) {

            log.error(
                    "[MTN MOMO] Transaction verification rejected. " +
                            "transactionId={}, httpStatus={}, response={}",
                    normalizedTransactionId,
                    e.getStatusCode().value(),
                    sanitizeMtnError(
                            e.getResponseBodyAsString()
                    ),
                    e
            );

            return false;

        } catch (Exception e) {

            log.error(
                    "[MTN MOMO] Verification failed. " +
                            "transactionId={}, error={}",
                    normalizedTransactionId,
                    e.getMessage(),
                    e
            );

            return false;
        }
    }

    // ============================================================
    // ACCESS TOKEN
    // ============================================================

    private String getAccessToken() {

        try {

            String credentials =
                    apiUser.trim()
                            + ":"
                            + apiKey.trim();

            String basicAuth =
                    Base64.getEncoder()
                            .encodeToString(
                                    credentials.getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            );

            Map<?, ?> response =
                    webClientBuilder
                            .baseUrl(normalizedBaseUrl())
                            .build()
                            .post()
                            .uri(
                                    "/collection/token/"
                            )
                            .header(
                                    "Authorization",
                                    "Basic " + basicAuth
                            )
                            .header(
                                    "Ocp-Apim-Subscription-Key",
                                    subscriptionKey.trim()
                            )
                            .contentType(
                                    MediaType.APPLICATION_FORM_URLENCODED
                            )
                            .accept(
                                    MediaType.APPLICATION_JSON
                            )
                            .retrieve()
                            .bodyToMono(Map.class)
                            .block();

            if (response == null) {

                log.warn(
                        "[MTN MOMO] Access token response was empty."
                );

                return null;
            }

            Object token =
                    response.get("access_token");

            if (token == null) {

                log.warn(
                        "[MTN MOMO] Access token missing from MTN response."
                );

                return null;
            }

            return token.toString();

        } catch (WebClientResponseException e) {

            log.error(
                    "[MTN MOMO] Access token request rejected. " +
                            "httpStatus={}, response={}",
                    e.getStatusCode().value(),
                    sanitizeMtnError(
                            e.getResponseBodyAsString()
                    ),
                    e
            );

            return null;

        } catch (Exception e) {

            log.error(
                    "[MTN MOMO] Failed to obtain access token: {}",
                    e.getMessage(),
                    e
            );

            return null;
        }
    }

    // ============================================================
    // CONFIGURATION CHECK
    // ============================================================

    private boolean isConfigured() {

        return !isBlank(subscriptionKey)
                && !isBlank(apiUser)
                && !isBlank(apiKey)
                && !isBlank(baseUrl)
                && !isBlank(environment);
    }

    // ============================================================
    // RWANDA PHONE NORMALIZATION
    // ============================================================

    private String normalizeRwandaPhone(
            String phone
    ) {

        if (phone == null) {

            return "";
        }

        String value =
                phone.trim()
                        .replace(" ", "")
                        .replace("-", "")
                        .replace("(", "")
                        .replace(")", "");

        if (value.startsWith("+250")) {

            value = value.substring(1);
        }

        if (value.startsWith("250")) {

            return value;
        }

        if (value.startsWith("07")
                && value.length() == 10) {

            return "250" + value.substring(1);
        }

        if (value.startsWith("7")
                && value.length() == 9) {

            return "250" + value;
        }

        return value;
    }

    // ============================================================
    // PHONE MASKING
    // ============================================================

    private String maskPhone(
            String phone
    ) {

        if (phone == null
                || phone.length() < 4) {

            return "***";
        }

        return "***"
                + phone.substring(
                phone.length() - 4
        );
    }

    // ============================================================
    // SANDBOX TRANSACTION ID
    // ============================================================

    private String createSandboxTransactionId(
            Long loanId
    ) {

        return "MTN-"
                + loanId
                + "-"
                + UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase();
    }

    // ============================================================
    // EXTERNAL REFERENCE
    // ============================================================

    private String externalReference(
            Long loanId
    ) {

        return "LOAN-"
                + loanId
                + "-"
                + UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase();
    }

    // ============================================================
    // AMOUNT FORMATTING
    // ============================================================

    private String formatAmount(
            Double amount
    ) {

        return BigDecimal.valueOf(amount)
                .stripTrailingZeros()
                .toPlainString();
    }

    // ============================================================
    // CURRENCY
    // ============================================================

    private String normalizeCurrency(
            String currency
    ) {

        if (currency != null
                && !currency.isBlank()) {

            return currency.trim()
                    .toUpperCase();
        }

        if (configuredCurrency != null
                && !configuredCurrency.isBlank()) {

            return configuredCurrency.trim()
                    .toUpperCase();
        }

        return "";
    }

    // ============================================================
    // BASE URL
    // ============================================================

    private String normalizedBaseUrl() {

        if (baseUrl == null) {

            return "";
        }

        return baseUrl.trim()
                .replaceAll("/+$", "");
    }

    // ============================================================
    // SANDBOX BASE URL DETECTION
    // ============================================================

    private boolean isSandboxBaseUrl(
            String url
    ) {

        if (url == null) {

            return false;
        }

        return url.toLowerCase()
                .contains("sandbox.momodeveloper.mtn.com");
    }

    // ============================================================
    // PRODUCTION ENVIRONMENT
    // ============================================================

    private boolean isProductionEnvironment() {

        return "production".equalsIgnoreCase(
                applicationEnvironment
        )
                || "prod".equalsIgnoreCase(
                applicationEnvironment
        );
    }

    // ============================================================
    // SAFE STRING
    // ============================================================

    private boolean isBlank(
            String value
    ) {

        return value == null
                || value.isBlank();
    }

    // ============================================================
    // SAFE CONFIRMATION SOURCE
    // ============================================================

    private String safeConfirmationSource(
            String source
    ) {

        if (source == null
                || source.isBlank()) {

            return "UNKNOWN";
        }

        return source.trim();
    }

    // ============================================================
    // SANITIZE MTN ERROR
    // ============================================================

    private String sanitizeMtnError(
            String responseBody
    ) {

        if (responseBody == null
                || responseBody.isBlank()) {

            return "<empty response>";
        }

        String value =
                responseBody
                        .replace("\n", " ")
                        .replace("\r", " ")
                        .trim();

        if (value.length() > 1000) {

            return value.substring(0, 1000)
                    + "...";
        }

        return value;
    }

    // ============================================================
    // MTN ERROR MESSAGE
    // ============================================================

    private String buildMtnInitiationErrorMessage(
            int status,
            String responseBody
    ) {

        String body =
                responseBody == null
                        ? ""
                        : responseBody.toLowerCase();

        if (body.contains("not_allowed_target_environment")) {

            return "MTN rejected the target environment. " +
                    "For Rwanda production, X-Target-Environment must be mtnrwanda.";
        }

        if (body.contains("invalid_callback_url_host")) {

            return "MTN rejected the callback URL because its host " +
                    "does not match the host configured for the MTN API user.";
        }

        if (body.contains("invalid_currency")) {

            return "MTN rejected the payment currency for this account.";
        }

        if (body.contains("not_allowed")) {

            return "MTN rejected the request because the API user or account " +
                    "does not have permission for this operation.";
        }

        if (status == 500) {

            return "MTN Mobile Money rejected the payment request. " +
                    "Check the target environment, callback URL, currency, " +
                    "API user permissions, subscription key, and MTN account configuration.";
        }

        if (status == 401
                || status == 403) {

            return "MTN Mobile Money authentication or authorization failed.";
        }

        return "MTN Mobile Money payment initiation failed with HTTP status "
                + status
                + ".";
    }

    // ============================================================
    // MTN REQUEST DTO
    // ============================================================

    private record MtnRequestBody(
            String amount,
            String currency,
            String externalId,
            MtnPayer payer,
            String payerMessage
    ) {
    }

    // ============================================================
    // MTN PAYER DTO
    // ============================================================

    private record MtnPayer(
            String partyId,
            String partyIdType
    ) {
    }

    // ============================================================
    // BASE URL SANITIZATION FOR LOGGING
    // ============================================================

    private String sanitizeBaseUrl(
            String url
    ) {

        if (url == null
                || url.isBlank()) {

            return "<not-configured>";
        }

        return url;
    }
}