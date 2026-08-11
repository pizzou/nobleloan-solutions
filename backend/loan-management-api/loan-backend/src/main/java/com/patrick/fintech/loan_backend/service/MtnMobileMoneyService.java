package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.PaymentGatewayRequest;
import com.patrick.fintech.loan_backend.dto.PaymentGatewayResponse;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
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

    /*
     * MTN sandbox currently uses EUR for the documented sandbox
     * RequestToPay test cases.
     *
     * Production Rwanda should use RWF.
     */
    private static final String SANDBOX_CURRENCY = "EUR";
    private static final String PRODUCTION_CURRENCY = "RWF";

    private final WebClient.Builder webClientBuilder;
    private final PaymentService paymentService;
    private final LoanRepository loanRepo;

    // ============================================================
    // CONFIGURATION
    // ============================================================

    @Value("${mtn.momo.enabled:false}")
    private boolean enabled;

    @Value("${mtn.momo.sandbox:true}")
    private boolean sandbox;

    @Value("${mtn.momo.base-url:https://sandbox.momodeveloper.mtn.com}")
    private String baseUrl;

    @Value("${mtn.momo.subscription-key:}")
    private String subscriptionKey;

    @Value("${mtn.momo.api-user:}")
    private String apiUser;

    @Value("${mtn.momo.api-key:}")
    private String apiKey;

    @Value("${mtn.momo.environment:sandbox}")
    private String environment;

    /*
     * This is the production/configured currency.
     *
     * IMPORTANT:
     * When sandbox=true, the service deliberately uses EUR regardless
     * of this value because MTN's documented sandbox uses EUR.
     */
    @Value("${mtn.momo.currency:RWF}")
    private String configuredCurrency;

    @Value("${app.environment:development}")
    private String applicationEnvironment;

    /*
     * Optional callback URL.
     *
     * If configured, it is sent to MTN.
     *
     * In sandbox MTN requires HTTPS and the callback host must match
     * the host registered when the sandbox API user was provisioned.
     */
    @Value("${mtn.momo.callback-url:}")
    private String callbackUrl;

    // ============================================================
    // STARTUP VALIDATION
    // ============================================================

    @PostConstruct
    private void validateConfiguration() {

        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);

        if (normalizedBaseUrl == null) {
            throw new IllegalStateException(
                    "MTN Mobile Money base URL is not configured."
            );
        }

        baseUrl = normalizedBaseUrl;

        if (isProductionEnvironment() && sandbox) {

            throw new IllegalStateException(
                    "MTN Mobile Money sandbox mode cannot be enabled " +
                    "when app.environment is production."
            );
        }

        if (sandbox) {

            if (!"sandbox".equalsIgnoreCase(
                    environment != null ? environment.trim() : ""
            )) {

                log.warn(
                        "[MTN MOMO] Sandbox mode is enabled but " +
                        "mtn.momo.environment is '{}'. MTN sandbox " +
                        "requires X-Target-Environment=sandbox.",
                        environment
                );

                environment = "sandbox";
            }

            log.info(
                    "[MTN MOMO] Sandbox configuration loaded. " +
                    "baseUrl={}, targetEnvironment={}, sandboxCurrency={}",
                    baseUrl,
                    environment,
                    SANDBOX_CURRENCY
            );

        } else {

            if ("sandbox".equalsIgnoreCase(
                    environment != null ? environment.trim() : ""
            )) {

                throw new IllegalStateException(
                        "MTN production mode cannot use " +
                        "X-Target-Environment=sandbox."
                );
            }

            log.info(
                    "[MTN MOMO] Production configuration loaded. " +
                    "baseUrl={}, targetEnvironment={}, currency={}",
                    baseUrl,
                    environment,
                    configuredCurrency
            );
        }

        if (enabled && !sandbox && !isConfigured()) {

            log.warn(
                    "[MTN MOMO] MTN integration is enabled in production " +
                    "but required credentials are missing."
            );
        }
    }

    // ============================================================
    // AVAILABILITY
    // ============================================================

    /**
     * Returns whether MTN Mobile Money can be presented as an
     * available payment method.
     */
    public boolean isAvailable() {

        if (!enabled) {
            return false;
        }

        if (sandbox) {
            return true;
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

        // ========================================================
        // BASIC VALIDATION
        // ========================================================

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

        if (!Double.isFinite(amount)) {

            return PaymentGatewayResponse.failed(
                    "Payment amount is invalid",
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

        // ========================================================
        // VERIFY LOAN
        // ========================================================

        Loan loan;

        try {

            loan = loanRepo.findById(loanId)
                    .orElseThrow(
                            () -> new IllegalArgumentException(
                                    "Loan not found: " + loanId
                            )
                    );

        } catch (IllegalArgumentException e) {

            return PaymentGatewayResponse.failed(
                    e.getMessage(),
                    MTN_PROVIDER
            );
        }

        // ========================================================
        // NORMALIZE PHONE
        // ========================================================

        String phoneNumber =
                normalizeRwandaPhone(
                        request.getPhoneNumber()
                );

        if (!isValidRwandaPhone(phoneNumber)) {

            return PaymentGatewayResponse.failed(
                    "Invalid Rwanda MTN Mobile Money phone number",
                    MTN_PROVIDER
            );
        }

        // ========================================================
        // DISABLED
        // ========================================================

        if (!enabled) {

            return PaymentGatewayResponse.failed(
                    "MTN Mobile Money integration is disabled",
                    MTN_PROVIDER
            );
        }

        // ========================================================
        // CURRENCY
        // ========================================================

        String paymentCurrency =
                resolvePaymentCurrency(currency);

        /*
         * IMPORTANT:
         *
         * Sandbox:
         *     EUR
         *
         * Production Rwanda:
         *     RWF
         *
         * We deliberately do NOT send RWF to the MTN sandbox.
         */

        log.info(
                "[MTN MOMO] Payment configuration resolved. " +
                "loanId={}, requestedCurrency={}, actualCurrency={}, " +
                "sandbox={}, targetEnvironment={}",
                loanId,
                currency,
                paymentCurrency,
                sandbox,
                environment
        );

        // ========================================================
        // TRANSACTION / REFERENCE ID
        // ========================================================

        /*
         * MTN requires X-Reference-Id to be a unique UUID v4.
         */
        String referenceId = UUID.randomUUID().toString();

        /*
         * externalId is our own application reference.
         */
        String externalId = externalReference(loanId);

        log.info(
                "[MTN MOMO] Initiating payment. " +
                "loanId={}, amount={}, currency={}, phone={}, " +
                "referenceId={}, externalId={}, sandbox={}",
                loanId,
                amount,
                paymentCurrency,
                maskPhone(phoneNumber),
                referenceId,
                externalId,
                sandbox
        );

        // ========================================================
        // LOCAL SANDBOX MODE
        // ========================================================

        /*
         * This is your application's local sandbox simulation.
         *
         * IMPORTANT:
         * If sandbox=true and you want to test against the actual
         * MTN Developer sandbox, the code below does NOT confirm
         * the payment automatically. It still sends a real
         * RequestToPay to MTN.
         *
         * Therefore we continue to the real MTN sandbox API.
         */

        // ========================================================
        // REAL MTN CONFIGURATION
        // ========================================================

        if (!isConfigured()) {

            log.error(
                    "[MTN MOMO] Integration is enabled but credentials " +
                    "are missing. sandbox={}, baseUrl={}, apiUserConfigured={}, " +
                    "apiKeyConfigured={}, subscriptionKeyConfigured={}",
                    sandbox,
                    baseUrl,
                    hasText(apiUser),
                    hasText(apiKey),
                    hasText(subscriptionKey)
            );

            return PaymentGatewayResponse.failed(
                    "MTN Mobile Money credentials are not configured",
                    MTN_PROVIDER
            );
        }

        // ========================================================
        // ACCESS TOKEN
        // ========================================================

        String accessToken = getAccessToken();

        if (accessToken == null
                || accessToken.isBlank()) {

            return PaymentGatewayResponse.failed(
                    "Unable to authenticate with MTN Mobile Money",
                    MTN_PROVIDER
            );
        }

        // ========================================================
        // REQUEST BODY
        // ========================================================

        String payerMessage =
                description != null
                        && !description.isBlank()
                        ? sanitizeMessage(description)
                        : "Loan repayment";

        String payeeNote =
                "Loan repayment - Loan " + loanId;

        MtnRequestBody body =
                new MtnRequestBody(
                        formatAmount(amount),
                        paymentCurrency,
                        externalId,
                        new MtnPayer(
                                phoneNumber,
                                "MSISDN"
                        ),
                        payerMessage,
                        payeeNote
                );

        // ========================================================
        // SEND REQUEST TO MTN
        // ========================================================

        try {

            WebClient client =
                    webClientBuilder
                            .baseUrl(baseUrl)
                            .build();

            WebClient.RequestBodySpec requestSpec =
                    client.post()
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
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + accessToken
                            )
                            .header(
                                    "X-Reference-Id",
                                    referenceId
                            )
                            .header(
                                    "X-Target-Environment",
                                    resolveTargetEnvironment()
                            )
                            .header(
                                    "Ocp-Apim-Subscription-Key",
                                    subscriptionKey
                            );

            /*
             * MTN callback URL.
             *
             * Only send it when configured.
             */
            if (hasText(callbackUrl)) {

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

            var response =
                    requestSpec
                            .bodyValue(body)
                            .retrieve()
                            .toBodilessEntity()
                            .block();

            int statusCode =
                    response != null
                            ? response.getStatusCode().value()
                            : 0;

            log.info(
                    "[MTN MOMO] RequestToPay response received. " +
                    "httpStatus={}, loanId={}, referenceId={}, " +
                    "amount={}, currency={}",
                    statusCode,
                    loanId,
                    referenceId,
                    amount,
                    paymentCurrency
            );

            /*
             * MTN RequestToPay must normally return HTTP 202.
             *
             * 202 means the request has been accepted for processing.
             * It does NOT mean the payment has completed.
             */
            if (response == null) {

                return PaymentGatewayResponse.failed(
                        "MTN Mobile Money returned no response",
                        MTN_PROVIDER
                );
            }

            if (statusCode != 202) {

                log.error(
                        "[MTN MOMO] Unexpected MTN HTTP status: {}",
                        statusCode
                );

                return PaymentGatewayResponse.failed(
                        "MTN Mobile Money did not accept the payment request",
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

            /*
             * THIS IS THE IMPORTANT FIX.
             *
             * Previously your code only logged:
             *
             * 500 Internal Server Error
             *
             * and discarded MTN's actual response body.
             *
             * Now we preserve the body so errors such as:
             *
             * INVALID_CURRENCY
             * NOT_ALLOWED
             * NOT_ALLOWED_TARGET_ENVIRONMENT
             * INVALID_CALLBACK_URL_HOST
             *
             * become visible.
             */

            String responseBody =
                    e.getResponseBodyAsString();

            HttpStatusCode status =
                    e.getStatusCode();

            log.error(
                    "[MTN MOMO] MTN API rejected RequestToPay. " +
                    "httpStatus={}, loanId={}, referenceId={}, " +
                    "currency={}, targetEnvironment={}, responseBody={}",
                    status.value(),
                    loanId,
                    referenceId,
                    paymentCurrency,
                    resolveTargetEnvironment(),
                    sanitizeLogValue(responseBody)
            );

            String userMessage =
                    buildMtnErrorMessage(
                            status.value(),
                            responseBody
                    );

            return PaymentGatewayResponse.failed(
                    userMessage,
                    MTN_PROVIDER
            );

        } catch (Exception e) {

            log.error(
                    "[MTN MOMO] Payment initiation failed. " +
                    "loanId={}, referenceId={}, amount={}, currency={}, " +
                    "error={}",
                    loanId,
                    referenceId,
                    amount,
                    paymentCurrency,
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

        // ========================================================
        // VALIDATION
        // ========================================================

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

        if (amount == null
                || amount <= 0
                || !Double.isFinite(amount)) {

            return PaymentGatewayResponse.failed(
                    "Payment amount must be greater than zero",
                    MTN_PROVIDER
            );
        }

        // ========================================================
        // VERIFY LOAN
        // ========================================================

        Loan loan;

        try {

            loan =
                    loanRepo.findById(loanId)
                            .orElseThrow(
                                    () -> new IllegalArgumentException(
                                            "Loan not found: " + loanId
                                    )
                            );

        } catch (IllegalArgumentException e) {

            return PaymentGatewayResponse.failed(
                    e.getMessage(),
                    MTN_PROVIDER
            );
        }

        // ========================================================
        // ORGANIZATION VALIDATION
        // ========================================================

        if (loan.getOrganization() == null
                || loan.getOrganization().getId() == null) {

            return PaymentGatewayResponse.failed(
                    "Loan organization is missing",
                    MTN_PROVIDER
            );
        }

        // ========================================================
        // TRANSACTION ID
        // ========================================================

        String normalizedTransactionId =
                transactionId.trim();

        // ========================================================
        // CURRENCY
        // ========================================================

        String paymentCurrency =
                resolvePaymentCurrency(currency);

        // ========================================================
        // VERIFY WITH MTN
        // ========================================================

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

        // ========================================================
        // SANDBOX VERIFICATION
        // ========================================================

        if (sandbox) {

            log.info(
                    "[MTN SANDBOX] Confirmation accepted for testing. " +
                    "loanId={}, transactionId={}, amount={}, currency={}, source={}",
                    loanId,
                    normalizedTransactionId,
                    amount,
                    paymentCurrency,
                    confirmationSource
            );
        }

        // ========================================================
        // RECORD PAYMENT
        // ========================================================

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
                                    + safeText(confirmationSource),
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

        // ========================================================
        // FINANCIAL RESULT
        // ========================================================

        BigDecimal outstandingBalance =
                payment.getOutstandingAfterDecimal() != null
                        ? payment.getOutstandingAfterDecimal()
                        : loan.getOutstandingBalanceDecimal();

        BigDecimal interestPaid =
                payment.getInterestComponentDecimal() != null
                        ? payment.getInterestComponentDecimal()
                        : BigDecimal.ZERO;

        BigDecimal principalPaid =
                payment.getPrincipalComponentDecimal() != null
                        ? payment.getPrincipalComponentDecimal()
                        : BigDecimal.ZERO;

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

        // ========================================================
        // SUCCESS
        // ========================================================

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
    // MTN WEBHOOK / CALLBACK
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
    // VERIFY MTN TRANSACTION
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

        // ========================================================
        // SANDBOX
        // ========================================================

        if (sandbox) {

            log.info(
                    "[MTN SANDBOX] Transaction {} considered verified " +
                    "for application testing.",
                    normalizedTransactionId
            );

            return true;
        }

        // ========================================================
        // PRODUCTION CONFIGURATION
        // ========================================================

        if (!isConfigured()) {

            log.warn(
                    "[MTN MOMO] Cannot verify transaction because " +
                    "credentials are missing."
            );

            return false;
        }

        // ========================================================
        // ACCESS TOKEN
        // ========================================================

        String accessToken =
                getAccessToken();

        if (accessToken == null
                || accessToken.isBlank()) {

            return false;
        }

        // ========================================================
        // STATUS REQUEST
        // ========================================================

        try {

            Map<?, ?> response =
                    webClientBuilder
                            .baseUrl(baseUrl)
                            .build()
                            .get()
                            .uri(
                                    "/collection/v1_0/requesttopay/{referenceId}",
                                    normalizedTransactionId
                            )
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + accessToken
                            )
                            .header(
                                    "X-Target-Environment",
                                    resolveTargetEnvironment()
                            )
                            .header(
                                    "Ocp-Apim-Subscription-Key",
                                    subscriptionKey
                            )
                            .accept(
                                    MediaType.APPLICATION_JSON
                            )
                            .retrieve()
                            .bodyToMono(
                                    Map.class
                            )
                            .block();

            if (response == null) {

                log.warn(
                        "[MTN MOMO] Empty verification response. " +
                        "transactionId={}",
                        normalizedTransactionId
                );

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
                    "transactionId={}, httpStatus={}, responseBody={}",
                    normalizedTransactionId,
                    e.getStatusCode().value(),
                    sanitizeLogValue(
                            e.getResponseBodyAsString()
                    )
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

        if (!isConfigured()) {

            log.warn(
                    "[MTN MOMO] Cannot request access token because " +
                    "MTN credentials are incomplete."
            );

            return null;
        }

        try {

            String credentials =
                    apiUser + ":" + apiKey;

            String basicAuth =
                    Base64.getEncoder()
                            .encodeToString(
                                    credentials.getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            );

            Map<?, ?> response =
                    webClientBuilder
                            .baseUrl(baseUrl)
                            .build()
                            .post()
                            .uri(
                                    "/collection/token/"
                            )
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Basic " + basicAuth
                            )
                            .header(
                                    "Ocp-Apim-Subscription-Key",
                                    subscriptionKey
                            )
                            .contentType(
                                    MediaType.APPLICATION_FORM_URLENCODED
                            )
                            .accept(
                                    MediaType.APPLICATION_JSON
                            )
                            .retrieve()
                            .bodyToMono(
                                    Map.class
                            )
                            .block();

            if (response == null) {

                log.error(
                        "[MTN MOMO] Access token response was empty."
                );

                return null;
            }

            Object token =
                    response.get("access_token");

            if (token == null
                    || token.toString().isBlank()) {

                log.error(
                        "[MTN MOMO] Access token missing from response. " +
                        "responseKeys={}",
                        response.keySet()
                );

                return null;
            }

            return token.toString();

        } catch (WebClientResponseException e) {

            log.error(
                    "[MTN MOMO] Access token request failed. " +
                    "httpStatus={}, responseBody={}",
                    e.getStatusCode().value(),
                    sanitizeLogValue(
                            e.getResponseBodyAsString()
                    )
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
    // CONFIGURATION
    // ============================================================

    private boolean isConfigured() {

        return hasText(subscriptionKey)
                && hasText(apiUser)
                && hasText(apiKey)
                && hasText(baseUrl);
    }

    // ============================================================
    // PAYMENT CURRENCY
    // ============================================================

    private String resolvePaymentCurrency(
            String requestedCurrency
    ) {

        /*
         * MTN Developer Sandbox:
         *
         * Currency = EUR
         *
         * This is the critical fix for your current 500 error.
         */
        if (sandbox) {

            if (hasText(requestedCurrency)
                    && !"EUR".equalsIgnoreCase(
                            requestedCurrency.trim()
                    )) {

                log.warn(
                        "[MTN SANDBOX] Requested currency '{}' is not " +
                        "supported by the documented MTN sandbox test " +
                        "environment. Using EUR instead.",
                        requestedCurrency
                );
            }

            return SANDBOX_CURRENCY;
        }

        /*
         * Production Rwanda:
         *
         * Currency = RWF
         */
        if (hasText(requestedCurrency)) {

            return requestedCurrency
                    .trim()
                    .toUpperCase();
        }

        if (hasText(configuredCurrency)) {

            return configuredCurrency
                    .trim()
                    .toUpperCase();
        }

        return PRODUCTION_CURRENCY;
    }

    // ============================================================
    // TARGET ENVIRONMENT
    // ============================================================

    private String resolveTargetEnvironment() {

        if (sandbox) {

            return "sandbox";
        }

        if (!hasText(environment)) {

            throw new IllegalStateException(
                    "MTN production target environment is not configured."
            );
        }

        return environment.trim();
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

            return value.substring(1);
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
    // RWANDA PHONE VALIDATION
    // ============================================================

    private boolean isValidRwandaPhone(
            String phone
    ) {

        if (phone == null) {

            return false;
        }

        /*
         * Rwanda mobile MSISDN:
         *
         * 2507XXXXXXXX
         *
         * Total length = 12 digits.
         */
        return phone.matches(
                "2507\\d{8}"
        );
    }

    // ============================================================
    // MASK PHONE
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
                .setScale(
                        2,
                        java.math.RoundingMode.HALF_UP
                )
                .toPlainString();
    }

    // ============================================================
    // MESSAGE SANITIZATION
    // ============================================================

    private String sanitizeMessage(
            String value
    ) {

        if (value == null) {

            return "";
        }

        String cleaned =
                value.trim()
                        .replaceAll(
                                "[\\r\\n]+",
                                " "
                        );

        if (cleaned.length() > 160) {

            return cleaned.substring(
                    0,
                    160
            );
        }

        return cleaned;
    }

    // ============================================================
    // MTN ERROR MESSAGE
    // ============================================================

    private String buildMtnErrorMessage(
            int httpStatus,
            String responseBody
    ) {

        String body =
                responseBody != null
                        ? responseBody.toUpperCase()
                        : "";

        if (body.contains("INVALID_CURRENCY")) {

            if (sandbox) {

                return "MTN sandbox rejected the currency. " +
                        "The MTN sandbox uses EUR for the documented " +
                        "RequestToPay test environment.";
            }

            return "MTN rejected the payment currency.";
        }

        if (body.contains("NOT_ALLOWED_TARGET_ENVIRONMENT")) {

            return "MTN rejected the target environment. " +
                    "For the sandbox it must be 'sandbox'; " +
                    "for Rwanda production it must be 'mtnrwanda'.";
        }

        if (body.contains("NOT_ALLOWED")) {

            return "MTN rejected the request because the API user " +
                    "does not have permission for this operation.";
        }

        if (body.contains("INVALID_CALLBACK_URL_HOST")) {

            return "MTN rejected the callback URL because its host " +
                    "does not match the callback host configured for " +
                    "the MTN API user.";
        }

        if (body.contains("RESOURCE_ALREADY_EXIST")) {

            return "MTN rejected the request because the reference ID " +
                    "has already been used.";
        }

        if (body.contains("SERVICE_UNAVAILABLE")) {

            return "MTN Mobile Money is temporarily unavailable. " +
                    "Please try again.";
        }

        if (httpStatus == 400) {

            return "MTN rejected the payment request as invalid.";
        }

        if (httpStatus == 401) {

            return "MTN authentication failed. Check the Collection " +
                    "subscription key and API credentials.";
        }

        if (httpStatus == 404) {

            return "MTN could not find the requested payment resource.";
        }

        if (httpStatus >= 500) {

            return "MTN Mobile Money rejected the payment request " +
                    "with a server-side error. Check the MTN API " +
                    "configuration and the MTN response log.";
        }

        return "MTN Mobile Money payment initiation failed.";
    }

    // ============================================================
    // LOG SANITIZATION
    // ============================================================

    private String sanitizeLogValue(
            String value
    ) {

        if (value == null) {

            return "";
        }

        String sanitized =
                value.replaceAll(
                        "[\\r\\n]+",
                        " "
                );

        if (sanitized.length() > 2000) {

            return sanitized.substring(
                    0,
                    2000
            ) + "...";
        }

        return sanitized;
    }

    // ============================================================
    // TEXT HELPERS
    // ============================================================

    private boolean hasText(
            String value
    ) {

        return value != null
                && !value.trim().isEmpty();
    }

    private String safeText(
            String value
    ) {

        return value != null
                ? value.trim()
                : "";
    }

    // ============================================================
    // BASE URL
    // ============================================================

    private String normalizeBaseUrl(
            String value
    ) {

        if (!hasText(value)) {

            return null;
        }

        String normalized =
                value.trim();

        while (normalized.endsWith("/")) {

            normalized =
                    normalized.substring(
                            0,
                            normalized.length() - 1
                    );
        }

        return normalized;
    }

    // ============================================================
    // ENVIRONMENT
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
    // MTN REQUEST BODY
    // ============================================================

    private record MtnRequestBody(

            String amount,

            String currency,

            String externalId,

            MtnPayer payer,

            String payerMessage,

            String payeeNote

    ) {
    }

    // ============================================================
    // MTN PAYER
    // ============================================================

    private record MtnPayer(

            String partyId,

            String partyIdType

    ) {
    }
}