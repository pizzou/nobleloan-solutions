package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.PaymentGatewayRequest;
import com.patrick.fintech.loan_backend.dto.PaymentGatewayResponse;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.BodyInserters;
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

    /**
     * Supported operating modes:
     *
     * SIMULATION
     *   - No MTN API credentials required.
     *   - Intended for development/testing.
     *
     * SANDBOX
     *   - Uses the real MTN developer sandbox.
     *   - Requires sandbox credentials.
     *
     * LIVE
     *   - Uses the real MTN production API.
     *   - Requires real production credentials.
     */
    private static final String MODE_SIMULATION = "SIMULATION";
    private static final String MODE_SANDBOX = "SANDBOX";
    private static final String MODE_LIVE = "LIVE";

    private final WebClient.Builder webClientBuilder;
    private final PaymentService paymentService;
    private final LoanRepository loanRepo;
    private final ObjectMapper objectMapper;

    // ============================================================
    // CONFIGURATION
    // ============================================================

    @Value("${mtn.momo.enabled:false}")
    private boolean enabled;

    /**
     * New recommended configuration.
     *
     * SIMULATION = no MTN credentials required
     * SANDBOX    = MTN developer sandbox
     * LIVE       = real MTN production API
     */
    @Value("${mtn.momo.mode:SIMULATION}")
    private String mode;

    /*
     * Kept for compatibility with your existing configuration.
     *
     * If sandbox=true and mode has not been explicitly changed,
     * the service treats it as SANDBOX.
     */
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

    @Value("${mtn.momo.environment:sandbox}")
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

        String effectiveMode = effectiveMode();

        log.info(
                "[MTN MOMO] Configuration loaded. " +
                        "enabled={}, mode={}, sandbox={}, environment={}, " +
                        "baseUrl={}, currency={}, callbackConfigured={}, " +
                        "applicationEnvironment={}",
                enabled,
                effectiveMode,
                sandbox,
                safeEnvironment(),
                sanitizeBaseUrl(normalizedBaseUrl()),
                normalizeCurrency(configuredCurrency),
                callbackUrl != null && !callbackUrl.isBlank(),
                applicationEnvironment
        );

        // --------------------------------------------------------
        // DISABLED
        // --------------------------------------------------------

        if (!enabled) {

            log.info(
                    "[MTN MOMO] Integration is disabled. " +
                            "No MTN Mobile Money calls will be made."
            );

            return;
        }

        // --------------------------------------------------------
        // SIMULATION
        // --------------------------------------------------------

        if (MODE_SIMULATION.equals(effectiveMode)) {

            if (isProductionEnvironment()) {

                log.warn(
                        "[MTN MOMO] SIMULATION mode is enabled while " +
                                "applicationEnvironment=production. " +
                                "Real MTN payments will NOT be performed."
                );
            } else {

                log.info(
                        "[MTN MOMO] SIMULATION MODE ENABLED. " +
                                "No MTN credentials or external API calls are required."
                );
            }

            return;
        }

        // --------------------------------------------------------
        // SANDBOX
        // --------------------------------------------------------

        if (MODE_SANDBOX.equals(effectiveMode)) {

            if (isProductionEnvironment()) {

                throw new IllegalStateException(
                        "MTN sandbox mode cannot be enabled in production."
                );
            }

            if (!isSandboxEnvironment()) {

                throw new IllegalStateException(
                        "MTN sandbox mode requires " +
                                "mtn.momo.environment=sandbox."
                );
            }

            if (normalizedBaseUrl().isBlank()) {

                throw new IllegalStateException(
                        "MTN sandbox mode requires mtn.momo.base-url."
                );
            }

            if (!hasCredentials()) {

                log.warn(
                        "[MTN MOMO] SANDBOX mode selected but MTN sandbox " +
                                "credentials are missing. External MTN calls " +
                                "will fail until credentials are supplied."
                );
            } else {

                log.info(
                        "[MTN MOMO] MTN SANDBOX configuration detected."
                );
            }

            return;
        }

        // --------------------------------------------------------
        // LIVE
        // --------------------------------------------------------

        if (MODE_LIVE.equals(effectiveMode)) {

            if (!isProductionEnvironment()) {

                throw new IllegalStateException(
                        "MTN LIVE mode requires app.environment=production."
                );
            }

            if (!"mtnrwanda".equalsIgnoreCase(safeEnvironment())) {

                throw new IllegalStateException(
                        "MTN Rwanda production mode requires " +
                                "X-Target-Environment=mtnrwanda."
                );
            }

            if (normalizedBaseUrl().isBlank()) {

                throw new IllegalStateException(
                        "MTN LIVE mode requires mtn.momo.base-url."
                );
            }

            if (isSandboxBaseUrl(normalizedBaseUrl())) {

                throw new IllegalStateException(
                        "MTN LIVE mode cannot use the MTN sandbox base URL."
                );
            }

            if (!hasCredentials()) {

                throw new IllegalStateException(
                        "MTN LIVE mode is enabled but MTN production " +
                                "credentials are missing."
                );
            }

            if (callbackUrl == null
                    || callbackUrl.isBlank()) {

                throw new IllegalStateException(
                        "MTN LIVE mode requires mtn.momo.callback-url."
                );
            }

            if (!callbackUrl.trim().startsWith("https://")) {

                throw new IllegalStateException(
                        "MTN production callback URL must use HTTPS."
                );
            }

            log.info(
                    "[MTN MOMO] LIVE configuration validated successfully."
            );

            return;
        }

        // --------------------------------------------------------
        // INVALID MODE
        // --------------------------------------------------------

        throw new IllegalStateException(
                "Unsupported mtn.momo.mode: "
                        + mode
                        + ". Supported values are SIMULATION, SANDBOX, LIVE."
        );
    }

    // ============================================================
    // AVAILABILITY
    // ============================================================

    public boolean isAvailable() {

        if (!enabled) {
            return false;
        }

        String effectiveMode = effectiveMode();

        if (MODE_SIMULATION.equals(effectiveMode)) {
            return true;
        }

        return hasCredentials()
                && !normalizedBaseUrl().isBlank();
    }

    // ============================================================
    // INITIATE PAYMENT
    // ============================================================

    public PaymentGatewayResponse initiate(
            Long loanId,
            PaymentGatewayRequest request,
            Double amount,
            String currency,
            String description) {

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
                normalizeRwandaPhone(request.getPhoneNumber());

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
                createTransactionId(loanId);

        String effectiveMode =
                effectiveMode();

        log.info(
                "[MTN MOMO] Initiating payment. " +
                        "loanId={}, amount={}, currency={}, phone={}, " +
                        "transactionId={}, mode={}, environment={}",
                loanId,
                amount,
                paymentCurrency,
                maskPhone(phoneNumber),
                transactionId,
                effectiveMode,
                safeEnvironment()
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
        // SIMULATION
        // --------------------------------------------------------

        if (MODE_SIMULATION.equals(effectiveMode)) {

            log.info(
                    "[MTN SIMULATION] Payment request created. " +
                            "loanId={}, transactionId={}, amount={}, currency={}, phone={}",
                    loanId,
                    transactionId,
                    amount,
                    paymentCurrency,
                    maskPhone(phoneNumber)
            );

            return PaymentGatewayResponse.pending(
                    "MTN Mobile Money simulation payment created. " +
                            "Use the sandbox/simulation confirmation endpoint " +
                            "to complete the payment.",
                    transactionId,
                    MTN_PROVIDER
            );
        }

        // --------------------------------------------------------
        // SANDBOX / LIVE
        // --------------------------------------------------------

        if (!hasCredentials()) {

            log.error(
                    "[MTN MOMO] External MTN mode requested but credentials " +
                            "are missing. mode={}, loanId={}",
                    effectiveMode,
                    loanId
            );

            return PaymentGatewayResponse.failed(
                    "MTN Mobile Money credentials are not configured",
                    MTN_PROVIDER
            );
        }

        if (normalizedBaseUrl().isBlank()) {

            return PaymentGatewayResponse.failed(
                    "MTN Mobile Money base URL is not configured",
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
                            "currency={}, payer={}, mode={}",
                    loanId,
                    referenceId,
                    body.amount(),
                    body.currency(),
                    maskPhone(phoneNumber),
                    effectiveMode()
            );

            // ----------------------------------------------------
            // REQUEST
            // ----------------------------------------------------

            var requestSpec =
                    webClientBuilder
                            .baseUrl(normalizedBaseUrl())
                            .build()
                            .post()
                            .uri("/collection/v1_0/requesttopay")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
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
                                    safeEnvironment()
                            )
                            .header(
                                    "Ocp-Apim-Subscription-Key",
                                    subscriptionKey.trim()
                            );

            // ----------------------------------------------------
            // CALLBACK
            // ----------------------------------------------------

            if (callbackUrl != null
                    && !callbackUrl.isBlank()) {

                requestSpec =
                        requestSpec.header(
                                "X-Callback-Url",
                                callbackUrl.trim()
                        );

                log.debug(
                        "[MTN MOMO] Callback URL supplied."
                );
            }

            // ----------------------------------------------------
            // EXECUTE
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

            // MTN RequestToPay normally returns 202.
            if (status < 200
                    || status >= 300) {

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
                            "loanId={}, httpStatus={}, environment={}, response={}",
                    loanId,
                    e.getStatusCode().value(),
                    safeEnvironment(),
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
    // SIMULATION CONFIRMATION
    // ============================================================

    @Transactional
    public PaymentGatewayResponse simulateConfirmation(
            Long loanId,
            String transactionId,
            Double amount,
            String currency) {

        String effectiveMode =
                effectiveMode();

        if (!MODE_SIMULATION.equals(effectiveMode)
                && !MODE_SANDBOX.equals(effectiveMode)) {

            return PaymentGatewayResponse.failed(
                    "MTN simulation is available only in SIMULATION or SANDBOX mode",
                    MTN_PROVIDER
            );
        }

        return confirmPayment(
                loanId,
                transactionId,
                amount,
                currency,
                "MTN_SIMULATION"
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
            String confirmationSource) {

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
        // EXTERNAL VERIFICATION
        // --------------------------------------------------------

        String effectiveMode =
                effectiveMode();

        if (MODE_LIVE.equals(effectiveMode)
                || MODE_SANDBOX.equals(effectiveMode)) {

            boolean verified =
                    verify(normalizedTransactionId);

            if (!verified) {

                log.warn(
                        "[MTN MOMO] Payment confirmation rejected. " +
                                "loanId={}, transactionId={}, source={}, mode={}",
                        loanId,
                        normalizedTransactionId,
                        confirmationSource,
                        effectiveMode
                );

                return PaymentGatewayResponse.failed(
                        "MTN Mobile Money transaction has not been confirmed as successful",
                        MTN_PROVIDER
                );
            }
        }

        // --------------------------------------------------------
        // SIMULATION
        // --------------------------------------------------------

        if (MODE_SIMULATION.equals(effectiveMode)) {

            log.info(
                    "[MTN SIMULATION] Confirming simulated payment. " +
                            "loanId={}, transactionId={}, amount={}, currency={}",
                    loanId,
                    normalizedTransactionId,
                    amount,
                    paymentCurrency
            );
        }

        // --------------------------------------------------------
        // LOG
        // --------------------------------------------------------

        log.info(
                "[MTN MOMO] Confirming payment. " +
                        "loanId={}, transactionId={}, amount={}, " +
                        "currency={}, source={}, mode={}",
                loanId,
                normalizedTransactionId,
                amount,
                paymentCurrency,
                safeConfirmationSource(confirmationSource),
                effectiveMode
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
            String currency) {

        String effectiveMode =
                effectiveMode();

        if (MODE_SIMULATION.equals(effectiveMode)) {

            return confirmPayment(
                    loanId,
                    transactionId,
                    amount,
                    currency,
                    "MTN_SIMULATION_WEBHOOK"
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
            String transactionId) {

        if (transactionId == null
                || transactionId.isBlank()) {

            return false;
        }

        String normalizedTransactionId =
                transactionId.trim();

        String effectiveMode =
                effectiveMode();

        // --------------------------------------------------------
        // SIMULATION
        // --------------------------------------------------------

        if (MODE_SIMULATION.equals(effectiveMode)) {

            log.info(
                    "[MTN SIMULATION] Transaction {} considered " +
                            "verified for testing.",
                    normalizedTransactionId
            );

            return true;
        }

        // --------------------------------------------------------
        // CONFIGURATION
        // --------------------------------------------------------

        if (!hasCredentials()) {

            log.warn(
                    "[MTN MOMO] Cannot verify transaction because " +
                            "credentials are missing."
            );

            return false;
        }

        // --------------------------------------------------------
        // STATUS API
        // --------------------------------------------------------

        try {

            String accessToken =
                    getAccessToken();

            if (accessToken == null
                    || accessToken.isBlank()) {

                return false;
            }

            String responseBody =
                    webClientBuilder
                            .baseUrl(normalizedBaseUrl())
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
                                    safeEnvironment()
                            )
                            .header(
                                    "Ocp-Apim-Subscription-Key",
                                    subscriptionKey.trim()
                            )
                            .accept(MediaType.APPLICATION_JSON)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();

            if (responseBody == null
                    || responseBody.isBlank()) {

                log.warn(
                        "[MTN MOMO] Transaction verification returned " +
                                "an empty response. transactionId={}",
                        normalizedTransactionId
                );

                return false;
            }

            String trimmedResponse =
                    responseBody.trim();

            if (!trimmedResponse.startsWith("{")) {

                log.error(
                        "[MTN MOMO] Transaction verification returned " +
                                "non-JSON response. transactionId={}, response={}",
                        normalizedTransactionId,
                        sanitizeMtnError(responseBody)
                );

                return false;
            }

            Map<String, Object> response =
                    objectMapper.readValue(
                            trimmedResponse,
                            new TypeReference<Map<String, Object>>() {
                            }
                    );

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

        if (!hasCredentials()) {

            log.error(
                    "[MTN MOMO] Cannot request access token because " +
                            "credentials are incomplete."
            );

            return null;
        }

        if (normalizedBaseUrl().isBlank()) {

            log.error(
                    "[MTN MOMO] Cannot request access token because " +
                            "base URL is empty."
            );

            return null;
        }

        try {

            String cleanApiUser =
                    apiUser == null
                            ? ""
                            : apiUser.trim();

            String cleanApiKey =
                    apiKey == null
                            ? ""
                            : apiKey.trim();

            String cleanSubscriptionKey =
                    subscriptionKey == null
                            ? ""
                            : subscriptionKey.trim();

            // ----------------------------------------------------
            // BASIC AUTH
            // ----------------------------------------------------

            String credentials =
                    cleanApiUser
                            + ":"
                            + cleanApiKey;

            String basicAuth =
                    Base64.getEncoder()
                            .encodeToString(
                                    credentials.getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            );

            log.info(
                    "[MTN MOMO] Requesting access token. " +
                            "baseUrl={}, environment={}, apiUserConfigured={}, " +
                            "subscriptionKeyConfigured={}",
                    sanitizeBaseUrl(normalizedBaseUrl()),
                    safeEnvironment(),
                    !cleanApiUser.isBlank(),
                    !cleanSubscriptionKey.isBlank()
            );

            // ----------------------------------------------------
            // TOKEN REQUEST
            // ----------------------------------------------------

            String responseBody =
                    webClientBuilder
                            .baseUrl(normalizedBaseUrl())
                            .build()
                            .post()
                            .uri("/collection/token/")
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Basic " + basicAuth
                            )
                            .header(
                                    "Ocp-Apim-Subscription-Key",
                                    cleanSubscriptionKey
                            )
                            .contentType(
                                    MediaType.APPLICATION_FORM_URLENCODED
                            )
                            .accept(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(
                                    BodyInserters.fromFormData(
                                            "grant_type",
                                            "client_credentials"
                                    )
                            )
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();

            // ----------------------------------------------------
            // EMPTY RESPONSE
            // ----------------------------------------------------

            if (responseBody == null
                    || responseBody.isBlank()) {

                log.error(
                        "[MTN MOMO] Access token endpoint returned " +
                                "an empty response."
                );

                return null;
            }

            String sanitizedResponse =
                    sanitizeMtnError(responseBody);

            log.debug(
                    "[MTN MOMO] Access token response received: {}",
                    sanitizedResponse
            );

            // ----------------------------------------------------
            // HTML / NON-JSON RESPONSE
            // ----------------------------------------------------

            String trimmedResponse =
                    responseBody.trim();

            if (!trimmedResponse.startsWith("{")) {

                log.error(
                        "[MTN MOMO] Access token endpoint returned " +
                                "non-JSON content. This usually means the " +
                                "configured base URL is incorrect or the " +
                                "MTN developer portal page was reached instead " +
                                "of the API endpoint. response={}",
                        sanitizedResponse
                );

                return null;
            }

            // ----------------------------------------------------
            // JSON
            // ----------------------------------------------------

            Map<String, Object> response =
                    objectMapper.readValue(
                            trimmedResponse,
                            new TypeReference<Map<String, Object>>() {
                            }
                    );

            Object token =
                    response.get("access_token");

            if (token == null
                    || token.toString().isBlank()) {

                log.error(
                        "[MTN MOMO] Access token missing from MTN response. " +
                                "response={}",
                        sanitizedResponse
                );

                return null;
            }

            log.info(
                    "[MTN MOMO] Access token successfully obtained."
            );

            return token.toString().trim();

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
                    "[MTN MOMO] Failed to obtain access token. " +
                            "error={}",
                    e.getMessage(),
                    e
            );

            return null;
        }
    }

    // ============================================================
    // EFFECTIVE MODE
    // ============================================================

    private String effectiveMode() {

        /*
         * Backward compatibility:
         *
         * If the old property sandbox=true is still configured,
         * automatically use SANDBOX unless an explicit mode is supplied.
         */

        if (mode == null
                || mode.isBlank()) {

            return sandbox
                    ? MODE_SANDBOX
                    : MODE_SIMULATION;
        }

        String normalized =
                mode.trim().toUpperCase();

        if (MODE_SIMULATION.equals(normalized)
                || MODE_SANDBOX.equals(normalized)
                || MODE_LIVE.equals(normalized)) {

            return normalized;
        }

        return normalized;
    }

    // ============================================================
    // CREDENTIAL CHECK
    // ============================================================

    private boolean hasCredentials() {

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
            String phone) {

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

            value =
                    value.substring(1);
        }

        if (value.startsWith("250")) {

            if (value.length() == 12) {
                return value;
            }

            return "";
        }

        if (value.startsWith("07")
                && value.length() == 10) {

            return "250"
                    + value.substring(1);
        }

        if (value.startsWith("7")
                && value.length() == 9) {

            return "250"
                    + value;
        }

        return "";
    }

    // ============================================================
    // PHONE MASKING
    // ============================================================

    private String maskPhone(
            String phone) {

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
    // TRANSACTION ID
    // ============================================================

    private String createTransactionId(
            Long loanId) {

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
            Long loanId) {

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
            Double amount) {

        return BigDecimal.valueOf(amount)
                .setScale(
                        2,
                        java.math.RoundingMode.HALF_UP
                )
                .stripTrailingZeros()
                .toPlainString();
    }

    // ============================================================
    // CURRENCY
    // ============================================================

    private String normalizeCurrency(
            String currency) {

        if (currency != null
                && !currency.isBlank()) {

            return currency
                    .trim()
                    .toUpperCase();
        }

        if (configuredCurrency != null
                && !configuredCurrency.isBlank()) {

            return configuredCurrency
                    .trim()
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
    // ENVIRONMENT
    // ============================================================

    private String safeEnvironment() {

        if (environment == null
                || environment.isBlank()) {

            return "";
        }

        return environment.trim();
    }

    private boolean isSandboxEnvironment() {

        return "sandbox".equalsIgnoreCase(
                safeEnvironment()
        );
    }

    // ============================================================
    // SANDBOX BASE URL
    // ============================================================

    private boolean isSandboxBaseUrl(
            String url) {

        if (url == null) {
            return false;
        }

        return url.toLowerCase()
                .contains(
                        "sandbox.momodeveloper.mtn.com"
                );
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
            String value) {

        return value == null
                || value.isBlank();
    }

    // ============================================================
    // SAFE CONFIRMATION SOURCE
    // ============================================================

    private String safeConfirmationSource(
            String source) {

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
            String responseBody) {

        if (responseBody == null
                || responseBody.isBlank()) {

            return "<empty response>";
        }

        String value =
                responseBody
                        .replace("\n", " ")
                        .replace("\r", " ")
                        .replace("\t", " ")
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
            String responseBody) {

        String body =
                responseBody == null
                        ? ""
                        : responseBody.toLowerCase();

        if (body.contains(
                "not_allowed_target_environment")) {

            return "MTN rejected the target environment. " +
                    "For Rwanda production use mtnrwanda; " +
                    "for MTN sandbox use sandbox.";
        }

        if (body.contains(
                "invalid_callback_url_host")) {

            return "MTN rejected the callback URL because its host " +
                    "does not match the host configured for the MTN API user.";
        }

        if (body.contains(
                "invalid_currency")) {

            return "MTN rejected the payment currency for this account.";
        }

        if (body.contains(
                "not_allowed")) {

            return "MTN rejected the request because the API user " +
                    "or account does not have permission for this operation.";
        }

        if (status == 401
                || status == 403) {

            return "MTN Mobile Money authentication or authorization failed.";
        }

        if (status == 500) {

            return "MTN Mobile Money rejected the payment request. " +
                    "Check the target environment, callback URL, currency, " +
                    "API user permissions, subscription key, and MTN account configuration.";
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
            String payerMessage) {
    }

    // ============================================================
    // MTN PAYER DTO
    // ============================================================

    private record MtnPayer(
            String partyId,
            String partyIdType) {
    }

    // ============================================================
    // BASE URL SANITIZATION
    // ============================================================

    private String sanitizeBaseUrl(
            String url) {

        if (url == null
                || url.isBlank()) {

            return "<not-configured>";
        }

        return url;
    }
}