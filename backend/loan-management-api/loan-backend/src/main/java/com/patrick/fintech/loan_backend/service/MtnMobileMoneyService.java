package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.PaymentGatewayRequest;
import com.patrick.fintech.loan_backend.dto.PaymentGatewayResponse;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentTransactionRepository;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MtnMobileMoneyService {

    private static final String MTN_PROVIDER = "MTN_MOMO";
    private static final String PAYMENT_METHOD = "MOBILE_MONEY";

    private final PaymentService paymentService;
    private final LoanRepository loanRepo;
    private final PaymentTransactionRepository paymentTransactionRepo;

    /**
     * IMPORTANT:
     *
     * The automatic MTN sandbox webhook runs on a CompletableFuture
     * thread. That thread does not inherit the HTTP request transaction.
     *
     * TransactionTemplate creates a real Spring transaction around
     * the asynchronous webhook processing.
     */
    private final PlatformTransactionManager transactionManager;

    // ============================================================
    // CONFIGURATION
    // ============================================================

    @Value("${mtn.momo.enabled:false}")
    private boolean enabled;

    @Value("${mtn.momo.sandbox:true}")
    private boolean sandbox;

    @Value("${mtn.momo.currency:RWF}")
    private String configuredCurrency;

    @Value("${mtn.momo.simulation-delay-ms:1500}")
    private long simulationDelayMs;

    @Value("${app.environment:development}")
    private String applicationEnvironment;

    // ============================================================
    // STARTUP
    // ============================================================

    @PostConstruct
    private void validateConfiguration() {

        log.info(
                "[MTN MOMO] Configuration: enabled={}, sandbox={}, currency={}, environment={}, simulationDelayMs={}",
                enabled,
                sandbox,
                configuredCurrency,
                applicationEnvironment,
                simulationDelayMs
        );

        if (sandbox) {

            log.info(
                    "[MTN MOMO] SANDBOX SIMULATION MODE ENABLED. " +
                    "No real MTN API calls will be made."
            );
        }

        if (isProductionEnvironment() && sandbox) {

            log.warn(
                    "[MTN MOMO] APPLICATION IS RUNNING IN PRODUCTION " +
                    "WHILE MTN SANDBOX MODE IS ENABLED."
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

        return sandbox;
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

        loanRepo.findById(loanId)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Loan not found: " + loanId
                        )
                );

        String phoneNumber =
                normalizeRwandaPhone(
                        request.getPhoneNumber()
                );

        String paymentCurrency =
                currency != null
                        && !currency.isBlank()
                                ? currency.trim().toUpperCase()
                                : configuredCurrency;

        if (!enabled) {

            log.warn(
                    "[MTN MOMO] Payment attempted while integration is disabled. loanId={}",
                    loanId
            );

            return PaymentGatewayResponse.failed(
                    "MTN Mobile Money integration is disabled",
                    MTN_PROVIDER
            );
        }

        if (!sandbox) {

            log.error(
                    "[MTN MOMO] Real MTN integration was requested, " +
                    "but this application is configured for sandbox-only testing. " +
                    "No real MTN API is available."
            );

            return PaymentGatewayResponse.failed(
                    "MTN sandbox simulation must be enabled for testing",
                    MTN_PROVIDER
            );
        }

        String transactionId =
                createSandboxTransactionId(loanId);

        log.info(
                "[MTN SANDBOX] Public payment received. " +
                "loanId={}, amount={}, currency={}, phone={}, transactionId={}",
                loanId,
                amount,
                paymentCurrency,
                maskPhone(phoneNumber),
                transactionId
        );

        scheduleAutomaticSandboxWebhook(
                loanId,
                transactionId,
                amount,
                paymentCurrency
        );

        return PaymentGatewayResponse.pending(
                "MTN Mobile Money sandbox payment created. " +
                "Simulated webhook confirmation has been scheduled.",
                transactionId,
                MTN_PROVIDER
        );
    }

    // ============================================================
    // MANUAL SANDBOX CONFIRMATION
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
                    "MTN sandbox simulation is disabled",
                    MTN_PROVIDER
            );
        }

        return processWebhookConfirmation(
                loanId,
                transactionId,
                amount,
                currency
        );
    }

    // ============================================================
    // AUTOMATIC SANDBOX WEBHOOK
    // ============================================================

    private void scheduleAutomaticSandboxWebhook(
            Long loanId,
            String transactionId,
            Double amount,
            String currency
    ) {

        long delay =
                Math.max(
                        0L,
                        simulationDelayMs
                );

        CompletableFuture.runAsync(
                () -> {

                    try {

                        log.info(
                                "[MTN SANDBOX WEBHOOK] " +
                                "Simulating successful MTN callback. " +
                                "loanId={}, transactionId={}, amount={}, currency={}",
                                loanId,
                                transactionId,
                                amount,
                                currency
                        );

                        /*
                         * ====================================================
                         * CRITICAL FIX
                         * ====================================================
                         *
                         * CompletableFuture executes on another thread.
                         *
                         * @Transactional on processWebhookConfirmation()
                         * cannot be relied upon when the method is invoked
                         * internally from this same Spring bean.
                         *
                         * TransactionTemplate explicitly starts a database
                         * transaction on the asynchronous thread.
                         */
                        TransactionTemplate transactionTemplate =
                                new TransactionTemplate(
                                        transactionManager
                                );

                        transactionTemplate.setName(
                                "mtn-sandbox-webhook-confirmation"
                        );

                        PaymentGatewayResponse response =
                                transactionTemplate.execute(
                                        status ->
                                                processWebhookConfirmation(
                                                        loanId,
                                                        transactionId,
                                                        amount,
                                                        currency
                                                )
                                );

                        if (response == null) {

                            log.error(
                                    "[MTN SANDBOX WEBHOOK] " +
                                    "Webhook returned null response. " +
                                    "loanId={}, transactionId={}",
                                    loanId,
                                    transactionId
                            );

                            return;
                        }

                        if (!"success".equalsIgnoreCase(
                                response.getStatus()
                        )) {

                            log.error(
                                    "[MTN SANDBOX WEBHOOK] " +
                                    "Webhook processing failed. " +
                                    "loanId={}, transactionId={}, status={}, message={}",
                                    loanId,
                                    transactionId,
                                    response.getStatus(),
                                    response.getMessage()
                            );

                            return;
                        }

                        log.info(
                                "[MTN SANDBOX WEBHOOK] " +
                                "Webhook processed successfully. " +
                                "loanId={}, transactionId={}, amount={}",
                                loanId,
                                transactionId,
                                amount
                        );

                    } catch (Exception e) {

                        log.error(
                                "[MTN SANDBOX WEBHOOK] " +
                                "Webhook processing threw an exception. " +
                                "loanId={}, transactionId={}",
                                loanId,
                                transactionId,
                                e
                        );
                    }
                },
                CompletableFuture.delayedExecutor(
                        delay,
                        TimeUnit.MILLISECONDS
                )
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

        /*
         * IMPORTANT:
         *
         * This query requires an active transaction because the
         * repository method uses SELECT ... FOR UPDATE / PESSIMISTIC_WRITE.
         *
         * The automatic sandbox path now guarantees that transaction
         * through TransactionTemplate.
         */
        var loan =
                loanRepo.findByIdForUpdate(loanId)
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

        Long organizationId =
                loan.getOrganization().getId();

        String normalizedTransactionId =
                transactionId.trim();

        String paymentCurrency =
                currency != null
                        && !currency.isBlank()
                                ? currency.trim().toUpperCase()
                                : configuredCurrency;

        // ============================================================
        // IDEMPOTENCY
        // ============================================================

        var existingTransaction =
                paymentTransactionRepo
                        .findByOrganization_IdAndTransactionReference(
                                organizationId,
                                normalizedTransactionId
                        )
                        .orElse(null);

        if (existingTransaction != null) {

            if (Boolean.TRUE.equals(
                    existingTransaction.getReversed()
            )) {

                return PaymentGatewayResponse.failed(
                        "MTN transaction has already been reversed",
                        MTN_PROVIDER
                );
            }

            if (existingTransaction.getLoan() == null
                    || !loanId.equals(
                            existingTransaction
                                    .getLoan()
                                    .getId()
                    )) {

                log.error(
                        "[MTN SANDBOX] Transaction reference belongs " +
                        "to another loan. loanId={}, transactionId={}, existingLoanId={}",
                        loanId,
                        normalizedTransactionId,
                        existingTransaction.getLoan() != null
                                ? existingTransaction
                                        .getLoan()
                                        .getId()
                                : null
                );

                return PaymentGatewayResponse.failed(
                        "MTN transaction reference is already associated with another loan",
                        MTN_PROVIDER
                );
            }

            BigDecimal existingAmount =
                    existingTransaction.getAmount() != null
                            ? existingTransaction.getAmount()
                            : BigDecimal.ZERO;

            log.info(
                    "[MTN SANDBOX] Duplicate webhook ignored. " +
                    "Payment already exists. " +
                    "loanId={}, transactionId={}, paymentTransactionId={}, amount={}",
                    loanId,
                    normalizedTransactionId,
                    existingTransaction.getId(),
                    existingAmount
            );

            return PaymentGatewayResponse.success(
                    "MTN Mobile Money payment was already confirmed and recorded",
                    normalizedTransactionId,
                    existingAmount.doubleValue(),
                    paymentCurrency,
                    PAYMENT_METHOD,
                    MTN_PROVIDER
            );
        }

        // ============================================================
        // SANDBOX
        // ============================================================

        if (!sandbox) {

            return PaymentGatewayResponse.failed(
                    "Real MTN API verification is not configured. " +
                    "Enable MTN sandbox simulation for testing.",
                    MTN_PROVIDER
            );
        }

        log.info(
                "[MTN SANDBOX] Processing simulated webhook. " +
                "loanId={}, transactionId={}, amount={}, currency={}, source={}",
                loanId,
                normalizedTransactionId,
                amount,
                paymentCurrency,
                confirmationSource
        );

        // ============================================================
        // RECORD PAYMENT
        // ============================================================

        Payment payment;

        try {

            payment =
                    paymentService.recordPayment(
                            loanId,
                            BigDecimal.valueOf(amount),
                            PAYMENT_METHOD,
                            normalizedTransactionId,
                            MTN_PROVIDER,
                            "MTN sandbox webhook confirmation. Source="
                                    + confirmationSource,
                            null
                    );

        } catch (Exception e) {

            log.error(
                    "[MTN SANDBOX] Failed to record payment. " +
                    "loanId={}, transactionId={}, amount={}",
                    loanId,
                    normalizedTransactionId,
                    amount,
                    e
            );

            return PaymentGatewayResponse.failed(
                    "MTN sandbox payment could not be recorded against the loan",
                    MTN_PROVIDER
            );
        }

        // ============================================================
        // PAYMENT RESULT
        // ============================================================

        BigDecimal currentPaymentAmount =
                BigDecimal.valueOf(amount)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        BigDecimal outstandingBalance =
                payment.getOutstandingAfterDecimal() != null
                        ? payment.getOutstandingAfterDecimal()
                        : loan.getOutstandingBalanceDecimal();

        if (outstandingBalance == null) {
            outstandingBalance = BigDecimal.ZERO;
        }

        outstandingBalance =
                outstandingBalance.setScale(
                        2,
                        RoundingMode.HALF_UP
                );

        log.info(
                "[MTN SANDBOX] Payment successfully recorded. " +
                "loanId={}, paymentId={}, transactionId={}, " +
                "amount={}, outstandingBalance={}, paymentStatus={}",
                loanId,
                payment.getId(),
                normalizedTransactionId,
                currentPaymentAmount,
                outstandingBalance,
                payment.getStatus()
        );

        return PaymentGatewayResponse.success(
                "MTN Mobile Money sandbox payment confirmed and recorded against the loan",
                normalizedTransactionId,
                currentPaymentAmount.doubleValue(),
                paymentCurrency,
                PAYMENT_METHOD,
                MTN_PROVIDER
        );
    }

    // ============================================================
    // WEBHOOK ENTRY POINT
    // ============================================================

    @Transactional
    public PaymentGatewayResponse processWebhookConfirmation(
            Long loanId,
            String transactionId,
            Double amount,
            String currency
    ) {

        log.info(
                "[MTN WEBHOOK] Callback received. " +
                "loanId={}, transactionId={}, amount={}, currency={}, sandbox={}",
                loanId,
                transactionId,
                amount,
                currency,
                sandbox
        );

        return confirmPayment(
                loanId,
                transactionId,
                amount,
                currency,
                sandbox
                        ? "MTN_SANDBOX_WEBHOOK"
                        : "MTN_WEBHOOK"
        );
    }

    // ============================================================
    // VERIFY
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

        if (sandbox) {

            log.info(
                    "[MTN SANDBOX] Transaction {} accepted as verified for sandbox testing.",
                    normalizedTransactionId
            );

            return true;
        }

        log.warn(
                "[MTN MOMO] Real MTN verification is not configured. transactionId={}",
                normalizedTransactionId
        );

        return false;
    }

    // ============================================================
    // PHONE
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
                        .replace("-", "");

        if (value.startsWith("+250")) {
            return value.substring(1);
        }

        if (value.startsWith("250")) {
            return value;
        }

        if (value.startsWith("07")) {
            return "250" + value.substring(1);
        }

        if (value.startsWith("7")) {
            return "250" + value;
        }

        return value;
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
    // TRANSACTION ID
    // ============================================================

    private String createSandboxTransactionId(
            Long loanId
    ) {

        return "MTN-"
                + loanId
                + "-"
                + UUID.randomUUID()
                        .toString()
                        .substring(
                                0,
                                8
                        )
                        .toUpperCase();
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
}
