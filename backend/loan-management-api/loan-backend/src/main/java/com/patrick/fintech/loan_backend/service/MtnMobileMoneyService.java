package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.dto.PaymentGatewayRequest;
import com.patrick.fintech.loan_backend.dto.PaymentGatewayResponse;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MtnMobileMoneyService {

    private static final String MTN_PROVIDER = "MTN_MOMO";

    /*
     * MTN Collections API paths.
     */
    private static final String TOKEN_PATH = "/collection/token/";

    private static final String REQUEST_TO_PAY_PATH =
            "/collection/v1_0/requesttopay";

    private static final String REQUEST_TO_PAY_STATUS_PATH =
            "/collection/v1_0/requesttopay/{referenceId}";

    
    private static final Duration MTN_REQUEST_TIMEOUT =
            Duration.ofSeconds(30);

    
    private final WebClient.Builder webClientBuilder;

    private final PaymentService paymentService;

    private final LoanRepository loanRepo;

    private final ObjectMapper objectMapper;

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

    @Value("${mtn.momo.currency:RWF}")
    private String configuredCurrency;

    @Value("${app.environment:development}")
    private String applicationEnvironment;

    // ============================================================
    // STARTUP VALIDATION
    // ============================================================

    @PostConstruct
    private void validateConfiguration() {

       
        if (isProductionEnvironment() && sandbox) {

            throw new IllegalStateException(
                    "MTN Mobile Money sandbox mode cannot be enabled " +
                    "when app.environment is production."
            );
        }

       
        if (!enabled) {

            log.info(
                    "[MTN MOMO] Integration is disabled."
            );

            return;
        }

        /*
         * Sandbox configuration.
         */
        if (sandbox) {

            log.info(
                    "[MTN MOMO] Sandbox integration enabled. " +
                    "baseUrl={}, environment={}, currency={}",
                    safeBaseUrl(),
                    environment,
                    configuredCurrency
            );

            if (!isSandboxConfigurationComplete()) {

                log.warn(
                        "[MTN MOMO] Sandbox is enabled but one or more " +
                        "MTN credentials are missing. " +
                        "Token authentication will fail until the " +
                        "sandbox credentials are configured."
                );
            }

            return;
        }

        /*
         * Real MTN configuration.
         */
        if (!isConfigured()) {

            log.warn(
                    "[MTN MOMO] Real MTN integration is enabled but " +
                    "required credentials are incomplete."
            );

            return;
        }

        log.info(
                "[MTN MOMO] Real MTN integration configured. " +
                "baseUrl={}, environment={}, currency={}",
                safeBaseUrl(),
                environment,
                configuredCurrency
        );
    }

    // ============================================================
    // AVAILABILITY
    // ============================================================

   
    public boolean isAvailable() {

        if (!enabled) {

            return false;
        }

        /*
         * Sandbox can be exposed as a testing option.
         */
        if (sandbox) {

            return true;
        }

        /*
         * Real MTN must have complete configuration.
         */
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

        if (amount == null
                || amount <= 0) {

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

        // ========================================================
        // VERIFY LOAN EXISTS
        // ========================================================

        loanRepo.findById(loanId)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Loan not found: " + loanId
                        )
                );

        // ========================================================
        // NORMALIZE PHONE
        // ========================================================

        String phoneNumber =
                normalizeRwandaPhone(
                        request.getPhoneNumber()
                );

        if (!isValidRwandaMsisdn(phoneNumber)) {

            return PaymentGatewayResponse.failed(
                    "Invalid Rwanda MTN Mobile Money phone number",
                    MTN_PROVIDER
            );
        }

        // ========================================================
        // PAYMENT CURRENCY
        // ========================================================

        String paymentCurrency =
                currency != null
                        && !currency.isBlank()
                        ? currency.trim().toUpperCase()
                        : configuredCurrency;

        if (paymentCurrency == null
                || paymentCurrency.isBlank()) {

            return PaymentGatewayResponse.failed(
                    "Payment currency is required",
                    MTN_PROVIDER
            );
        }

        // ========================================================
        // CREATE INTERNAL TRANSACTION ID
        // ========================================================

        String transactionId =
                createSandboxTransactionId(loanId);

        log.info(
                "[MTN MOMO] Initiating payment. " +
                        "loanId={}, amount={}, currency={}, phone={}, " +
                        "transactionId={}, sandbox={}",
                loanId,
                amount,
                paymentCurrency,
                maskPhone(phoneNumber),
                transactionId,
                sandbox
        );

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
        // LOCAL SANDBOX
        // ========================================================

        if (sandbox) {

            log.info(
                    "[MTN SANDBOX] Payment request created. " +
                            "loanId={}, transactionId={}, amount={}, currency={}",
                    loanId,
                    transactionId,
                    amount,
                    paymentCurrency
            );

            /*
             * Do NOT record the payment here.
             *
             * The payment remains pending until the sandbox
             * confirmation endpoint is called.
             */
            return PaymentGatewayResponse.pending(
                    "MTN Mobile Money sandbox payment created. " +
                            "Waiting for simulated customer confirmation.",
                    transactionId,
                    MTN_PROVIDER
            );
        }

        // ========================================================
        // REAL MTN CONFIGURATION
        // ========================================================

        if (!isConfigured()) {

            log.error(
                    "[MTN MOMO] Real MTN integration is enabled " +
                            "but credentials are missing."
            );

            return PaymentGatewayResponse.failed(
                    "MTN Mobile Money credentials are not configured",
                    MTN_PROVIDER
            );
        }

        // ========================================================
        // REAL MTN REQUEST
        // ========================================================

        try {

            /*
             * MTN requires a unique UUID v4 for X-Reference-Id.
             */
            String referenceId =
                    UUID.randomUUID().toString();

            /*
             * Get OAuth access token.
             */
            String accessToken =
                    getAccessToken();

            if (accessToken == null
                    || accessToken.isBlank()) {

                return PaymentGatewayResponse.failed(
                        "Unable to authenticate with MTN Mobile Money",
                        MTN_PROVIDER
                );
            }

            /*
             * External ID sent to MTN.
             */
            String externalId =
                    externalReference(loanId);

            /*
             * MTN Collections RequestToPay payload.
             */
            MtnRequestBody body =
                    new MtnRequestBody(
                            BigDecimal.valueOf(amount)
                                    .stripTrailingZeros()
                                    .toPlainString(),

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
                    "[MTN MOMO] Sending RequestToPay. " +
                            "loanId={}, referenceId={}, amount={}, " +
                            "currency={}, phone={}",
                    loanId,
                    referenceId,
                    body.amount(),
                    body.currency(),
                    maskPhone(phoneNumber)
            );

            var response =
                    webClientBuilder
                            .baseUrl(normalizeBaseUrl())
                            .build()
                            .post()
                            .uri(
                                    REQUEST_TO_PAY_PATH
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
                                    normalizedEnvironment()
                            )
                            .header(
                                    "Ocp-Apim-Subscription-Key",
                                    subscriptionKey.trim()
                            )
                            .bodyValue(body)
                            .retrieve()
                            .toBodilessEntity()
                            .block(MTN_REQUEST_TIMEOUT);

            int status =
                    response != null
                            ? response.getStatusCode().value()
                            : -1;

            log.info(
                    "[MTN MOMO] RequestToPay response. " +
                            "loanId={}, referenceId={}, status={}",
                    loanId,
                    referenceId,
                    status
            );

            /*
             * MTN RequestToPay is asynchronous.
             *
             * A successful submission is normally HTTP 202.
             */
            if (response == null) {

                return PaymentGatewayResponse.failed(
                        "MTN Mobile Money did not return a response",
                        MTN_PROVIDER
                );
            }

            if (!response.getStatusCode().is2xxSuccessful()) {

                log.error(
                        "[MTN MOMO] RequestToPay returned unexpected " +
                                "HTTP status {}. loanId={}, referenceId={}",
                        status,
                        loanId,
                        referenceId
                );

                return PaymentGatewayResponse.failed(
                        "MTN Mobile Money rejected the payment request",
                        MTN_PROVIDER
                );
            }

            /*
             * IMPORTANT:
             *
             * Do NOT record the loan payment here.
             *
             * MTN has only accepted/queued the payment request.
             * The final payment status must be SUCCESSFUL.
             */
            return PaymentGatewayResponse.pending(
                    "Payment request sent to MTN Mobile Money. " +
                            "Please approve the payment on your phone.",
                    referenceId,
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
                || amount <= 0) {

            return PaymentGatewayResponse.failed(
                    "Payment amount must be greater than zero",
                    MTN_PROVIDER
            );
        }

        // ========================================================
        // VERIFY LOAN
        // ========================================================

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

        // ========================================================
        // NORMALIZE TRANSACTION ID
        // ========================================================

        String normalizedTransactionId =
                transactionId.trim();

        // ========================================================
        // CURRENCY
        // ========================================================

        String paymentCurrency =
                currency != null
                        && !currency.isBlank()
                        ? currency.trim().toUpperCase()
                        : configuredCurrency;

        if (paymentCurrency == null
                || paymentCurrency.isBlank()) {

            return PaymentGatewayResponse.failed(
                    "Payment currency is required",
                    MTN_PROVIDER
            );
        }

        // ========================================================
        // VERIFY REAL MTN PAYMENT
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
                        "MTN Mobile Money transaction has not been " +
                                "confirmed as successful",
                        MTN_PROVIDER
                );
            }
        }

        // ========================================================
        // CONFIRMATION LOG
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

        // ========================================================
        // RECORD PAYMENT EXACTLY ONCE
        // ========================================================

        Payment payment;

        try {

            /*
             * PaymentService is responsible for the actual loan
             * accounting.
             *
             * This preserves your existing behavior where:
             *
             * 1. Interest due is paid first.
             * 2. Remaining payment reduces principal.
             * 3. The same monthly interest is not charged twice
             *    within the same payment cycle.
             */
            payment =
                    paymentService.recordPayment(
                            loanId,
                            BigDecimal.valueOf(amount),
                            "MOBILE_MONEY",
                            normalizedTransactionId,
                            "MTN_MOMO",
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
                    "MTN payment was confirmed but could not be " +
                            "recorded against the loan",
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

        // ========================================================
        // SUCCESS RESPONSE
        // ========================================================

        return PaymentGatewayResponse.success(
                "MTN Mobile Money payment confirmed and recorded " +
                        "against the loan",
                normalizedTransactionId,
                amount,
                paymentCurrency,
                "MOBILE_MONEY",
                MTN_PROVIDER
        );
    }

    // ============================================================
    // REAL MTN WEBHOOK / CALLBACK PROCESSING
    // ============================================================

    @Transactional
    public PaymentGatewayResponse processWebhookConfirmation(
            Long loanId,
            String transactionId,
            Double amount,
            String currency
    ) {

        if (sandbox) {

            /*
             * Sandbox callback behaves like simulated confirmation.
             */
            return confirmPayment(
                    loanId,
                    transactionId,
                    amount,
                    currency,
                    "MTN_SANDBOX_WEBHOOK"
            );
        }

        /*
         * Production MTN callback.
         */
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
                    "[MTN SANDBOX] Transaction {} considered " +
                            "verified for testing",
                    normalizedTransactionId
            );

            return true;
        }

        // ========================================================
        // REAL MTN CONFIGURATION
        // ========================================================

        if (!isConfigured()) {

            log.warn(
                    "[MTN MOMO] Cannot verify transaction because " +
                            "credentials are missing"
            );

            return false;
        }

        // ========================================================
        // VERIFY WITH MTN
        // ========================================================

        try {

            String accessToken =
                    getAccessToken();

            if (accessToken == null
                    || accessToken.isBlank()) {

                log.warn(
                        "[MTN MOMO] Transaction verification aborted " +
                                "because an access token could not be obtained."
                );

                return false;
            }

            String responseBody =
                    webClientBuilder
                            .baseUrl(normalizeBaseUrl())
                            .build()
                            .get()
                            .uri(
                                    REQUEST_TO_PAY_STATUS_PATH,
                                    normalizedTransactionId
                            )
                            .accept(
                                    MediaType.APPLICATION_JSON
                            )
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + accessToken
                            )
                            .header(
                                    "X-Target-Environment",
                                    normalizedEnvironment()
                            )
                            .header(
                                    "Ocp-Apim-Subscription-Key",
                                    subscriptionKey.trim()
                            )
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(MTN_REQUEST_TIMEOUT);

            if (responseBody == null
                    || responseBody.isBlank()) {

                log.warn(
                        "[MTN MOMO] Verification returned an empty response. " +
                                "transactionId={}",
                        normalizedTransactionId
                );

                return false;
            }

            JsonNode response =
                    objectMapper.readTree(responseBody);

            JsonNode statusNode =
                    response.get("status");

            String status =
                    statusNode != null
                            ? statusNode.asText()
                            : null;

            boolean successful =
                    status != null
                            && "SUCCESSFUL".equalsIgnoreCase(
                            status.trim()
                    );

            log.info(
                    "[MTN MOMO] Transaction verification. " +
                            "transactionId={}, status={}, successful={}",
                    normalizedTransactionId,
                    status,
                    successful
            );

            return successful;

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

    /**
     * Obtains an OAuth access token from MTN Collections.
     *
     * IMPORTANT:
     *
     * The response is intentionally read as String first rather than
     * directly as Map.class.
     *
     * Your previous error was:
     *
     * 200 OK
     * Content-Type: text/html;charset=utf-8
     * UnsupportedMediaTypeException
     *
     * Reading String first lets us safely inspect and parse the
     * response instead of asking WebClient to deserialize HTML as JSON.
     */
    private String getAccessToken() {

        // ========================================================
        // VALIDATE CREDENTIALS
        // ========================================================

        if (apiUser == null
                || apiUser.isBlank()) {

            log.error(
                    "[MTN MOMO] API user is missing."
            );

            return null;
        }

        if (apiKey == null
                || apiKey.isBlank()) {

            log.error(
                    "[MTN MOMO] API key is missing."
            );

            return null;
        }

        if (subscriptionKey == null
                || subscriptionKey.isBlank()) {

            log.error(
                    "[MTN MOMO] Collections subscription key is missing."
            );

            return null;
        }

        if (baseUrl == null
                || baseUrl.isBlank()) {

            log.error(
                    "[MTN MOMO] Base URL is missing."
            );

            return null;
        }

        // ========================================================
        // CREATE BASIC AUTH
        // ========================================================

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

            log.info(
                    "[MTN MOMO] Requesting access token. " +
                            "baseUrl={}, environment={}, " +
                            "apiUserConfigured={}, subscriptionKeyConfigured={}",
                    safeBaseUrl(),
                    normalizedEnvironment(),
                    !apiUser.isBlank(),
                    !subscriptionKey.isBlank()
            );

            // ====================================================
            // TOKEN REQUEST
            // ====================================================

            String responseBody =
                    webClientBuilder
                            .baseUrl(normalizeBaseUrl())
                            .build()
                            .post()
                            .uri(TOKEN_PATH)
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Basic " + basicAuth
                            )
                            .header(
                                    "Ocp-Apim-Subscription-Key",
                                    subscriptionKey.trim()
                            )
                            .header(
                                    HttpHeaders.ACCEPT,
                                    MediaType.APPLICATION_JSON_VALUE
                            )
                            .contentType(
                                    MediaType.APPLICATION_FORM_URLENCODED
                            )
                            /*
                             * MTN token endpoint accepts the token
                             * request without a JSON payload.
                             *
                             * Supplying an empty body avoids sending
                             * an accidental JSON request body.
                             */
                            .bodyValue("")
                            .exchangeToMono(
                                    response -> {

                                        int status =
                                                response.statusCode()
                                                        .value();

                                        MediaType contentType =
                                                response.headers()
                                                        .contentType()
                                                        .orElse(null);

                                        return response
                                                .bodyToMono(
                                                        String.class
                                                )
                                                .defaultIfEmpty("")
                                                .map(
                                                        body -> {

                                                            log.info(
                                                                    "[MTN MOMO] " +
                                                                            "Token endpoint response. " +
                                                                            "status={}, contentType={}, " +
                                                                            "bodyLength={}",
                                                                    status,
                                                                    contentType,
                                                                    body.length()
                                                            );

                                                            /*
                                                             * Keep the response
                                                             * for diagnostic
                                                             * handling.
                                                             */
                                                            if (status < 200
                                                                    || status >= 300) {

                                                                log.error(
                                                                        "[MTN MOMO] " +
                                                                                "Token request returned " +
                                                                                "HTTP {}. Response={}",
                                                                        status,
                                                                        sanitizeMtnResponse(
                                                                                body
                                                                        )
                                                                );

                                                                throw new MtnAuthenticationException(
                                                                        "MTN token request returned HTTP "
                                                                                + status
                                                                );
                                                            }

                                                            return body;
                                                        }
                                                );
                                    }
                            )
                            .block(MTN_REQUEST_TIMEOUT);

            // ====================================================
            // EMPTY RESPONSE
            // ====================================================

            if (responseBody == null
                    || responseBody.isBlank()) {

                log.error(
                        "[MTN MOMO] Token endpoint returned an empty response."
                );

                return null;
            }

            // ====================================================
            // PARSE JSON
            // ====================================================

            try {

                JsonNode json =
                        objectMapper.readTree(
                                responseBody
                        );

                if (json == null
                        || json.isNull()) {

                    log.error(
                            "[MTN MOMO] Token response could not be parsed."
                    );

                    return null;
                }

                JsonNode accessTokenNode =
                        json.get("access_token");

                if (accessTokenNode == null
                        || accessTokenNode.isNull()
                        || accessTokenNode.asText().isBlank()) {

                    /*
                     * This is extremely useful when MTN/proxy returns
                     * HTML or another unexpected response.
                     */
                    log.error(
                            "[MTN MOMO] Token response did not contain " +
                                    "access_token. Response={}",
                            sanitizeMtnResponse(responseBody)
                    );

                    return null;
                }

                String accessToken =
                        accessTokenNode.asText()
                                .trim();

                log.info(
                        "[MTN MOMO] Access token obtained successfully."
                );

                return accessToken;

            } catch (Exception jsonException) {

                /*
                 * This is the exact class of failure your old code
                 * was hiding behind UnsupportedMediaTypeException.
                 */
                log.error(
                        "[MTN MOMO] MTN returned HTTP 2xx but the " +
                                "response was not valid JSON. " +
                                "Response={}",
                        sanitizeMtnResponse(responseBody),
                        jsonException
                );

                return null;
            }

        } catch (MtnAuthenticationException e) {

            log.error(
                    "[MTN MOMO] Authentication request failed: {}",
                    e.getMessage()
            );

            return null;

        } catch (Exception e) {

            log.error(
                    "[MTN MOMO] Unexpected error while obtaining " +
                            "access token: {}",
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

        return subscriptionKey != null
                && !subscriptionKey.isBlank()

                && apiUser != null
                && !apiUser.isBlank()

                && apiKey != null
                && !apiKey.isBlank()

                && baseUrl != null
                && !baseUrl.isBlank()

                && environment != null
                && !environment.isBlank();
    }

    private boolean isSandboxConfigurationComplete() {

        return subscriptionKey != null
                && !subscriptionKey.isBlank()

                && apiUser != null
                && !apiUser.isBlank()

                && apiKey != null
                && !apiKey.isBlank()

                && baseUrl != null
                && !baseUrl.isBlank();
    }

    // ============================================================
    // BASE URL
    // ============================================================

    private String normalizeBaseUrl() {

        if (baseUrl == null) {

            return "";
        }

        String value =
                baseUrl.trim();

        while (value.endsWith("/")) {

            value =
                    value.substring(
                            0,
                            value.length() - 1
                    );
        }

        return value;
    }

    private String safeBaseUrl() {

        String value =
                normalizeBaseUrl();

        if (value.isBlank()) {

            return "[not configured]";
        }

        return value;
    }

    // ============================================================
    // MTN ENVIRONMENT
    // ============================================================

    private String normalizedEnvironment() {

        if (environment == null
                || environment.isBlank()) {

            return sandbox
                    ? "sandbox"
                    : "mtnrwanda";
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

        /*
         * +2507XXXXXXXX
         * becomes:
         * 2507XXXXXXXX
         */
        if (value.startsWith("+250")) {

            return value.substring(1);
        }

        /*
         * 2507XXXXXXXX
         */
        if (value.startsWith("250")) {

            return value;
        }

        /*
         * 07XXXXXXXX
         * becomes:
         * 2507XXXXXXXX
         */
        if (value.startsWith("07")) {

            return "250"
                    + value.substring(1);
        }

        /*
         * 7XXXXXXXX
         * becomes:
         * 2507XXXXXXXX
         */
        if (value.startsWith("7")) {

            return "250"
                    + value;
        }

        return value;
    }

    // ============================================================
    // RWANDA MSISDN VALIDATION
    // ============================================================

    private boolean isValidRwandaMsisdn(
            String phone
    ) {

        if (phone == null
                || phone.isBlank()) {

            return false;
        }

        /*
         * Rwanda MSISDN:
         *
         * 250
         * followed by
         * 7
         * followed by
         * 8 digits
         *
         * Example:
         *
         * 2507XXXXXXXX
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
    // SANITIZE MTN RESPONSE
    // ============================================================

    /**
     * Prevents credentials/access tokens from accidentally appearing
     * in application logs while still giving us useful diagnostic
     * information when MTN returns an unexpected response.
     */
    private String sanitizeMtnResponse(
            String response
    ) {

        if (response == null) {

            return "";
        }

        String value =
                response.trim();

        /*
         * Prevent extremely large HTML responses from flooding logs.
         */
        if (value.length() > 2000) {

            value =
                    value.substring(
                            0,
                            2000
                    )
                            + "...";
        }

        /*
         * Never log access tokens.
         */
        value =
                value.replaceAll(
                        "(\"access_token\"\\s*:\\s*\")[^\"]+(\")",
                        "$1[REDACTED]$2"
                );

        /*
         * Also protect token_type-like authorization responses.
         */
        value =
                value.replaceAll(
                        "(Authorization\\s*[:=]\\s*Basic\\s+)[A-Za-z0-9+/=]+",
                        "$1[REDACTED]"
                );

        value =
                value.replaceAll(
                        "(Authorization\\s*[:=]\\s*Bearer\\s+)[A-Za-z0-9._-]+",
                        "$1[REDACTED]"
                );

        return value;
    }

    // ============================================================
    // CONFIRMATION SOURCE
    // ============================================================

    private String safeConfirmationSource(
            String confirmationSource
    ) {

        if (confirmationSource == null
                || confirmationSource.isBlank()) {

            return "UNKNOWN";
        }

        String value =
                confirmationSource.trim();

        if (value.length() > 100) {

            return value.substring(
                    0,
                    100
            );
        }

        return value;
    }

    // ============================================================
    // PRODUCTION ENVIRONMENT
    // ============================================================

    private boolean isProductionEnvironment() {

        return "production"
                .equalsIgnoreCase(
                        applicationEnvironment
                )

                || "prod"
                .equalsIgnoreCase(
                        applicationEnvironment
                );
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
    // MTN PAYER
    // ============================================================

    private record MtnPayer(
            String partyId,
            String partyIdType
    ) {
    }

    // ============================================================
    // AUTHENTICATION EXCEPTION
    // ============================================================

    private static class MtnAuthenticationException
            extends RuntimeException {

        private MtnAuthenticationException(
                String message
        ) {

            super(message);
        }
    }
}