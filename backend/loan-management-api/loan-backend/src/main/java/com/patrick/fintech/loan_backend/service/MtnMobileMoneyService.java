package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.PaymentGatewayRequest;
import com.patrick.fintech.loan_backend.dto.PaymentGatewayResponse;
import com.patrick.fintech.loan_backend.repository.LoanRepository;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MtnMobileMoneyService {

        private static final String MTN_PROVIDER = "MTN_MOMO";

        private final LoanRepository loanRepo;
        private final MtnWebhookTransactionService mtnWebhookTransactionService;

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
                                simulationDelayMs);

                if (sandbox) {

                        log.info(
                                        "[MTN MOMO] SANDBOX SIMULATION MODE ENABLED. " +
                                                        "No real MTN API calls will be made.");
                }

                if (isProductionEnvironment() && sandbox) {

                        log.warn(
                                        "[MTN MOMO] APPLICATION IS RUNNING IN PRODUCTION " +
                                                        "WHILE MTN SANDBOX MODE IS ENABLED.");
                }
        }

        // ============================================================
        // AVAILABILITY
        // ============================================================

        public boolean isAvailable() {

                if (!enabled) {
                        return false;
                }

                // Never advertise a sandbox/simulated payment method from a production
                // public portal. A real MTN merchant API integration must be explicitly
                // implemented and configured before this method returns true in prod.
                return sandbox && !isProductionEnvironment();
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

                if (loanId == null) {

                        return PaymentGatewayResponse.failed(
                                        "Loan ID is required",
                                        MTN_PROVIDER);
                }

                if (request == null) {

                        return PaymentGatewayResponse.failed(
                                        "Payment request is required",
                                        MTN_PROVIDER);
                }

                if (amount == null || amount <= 0) {

                        return PaymentGatewayResponse.failed(
                                        "Payment amount must be greater than zero",
                                        MTN_PROVIDER);
                }

                if (request.getPhoneNumber() == null
                                || request.getPhoneNumber().isBlank()) {

                        return PaymentGatewayResponse.failed(
                                        "MTN Mobile Money phone number is required",
                                        MTN_PROVIDER);
                }

                loanRepo.findById(loanId)
                                .orElseThrow(
                                                () -> new IllegalArgumentException(
                                                                "Loan not found: " + loanId));

                String phoneNumber = normalizeRwandaPhone(
                                request.getPhoneNumber());

                String paymentCurrency = currency != null
                                && !currency.isBlank()
                                                ? currency.trim().toUpperCase()
                                                : configuredCurrency;

                if (!enabled) {

                        log.warn(
                                        "[MTN MOMO] Payment attempted while integration is disabled. loanId={}",
                                        loanId);

                        return PaymentGatewayResponse.failed(
                                        "MTN Mobile Money integration is disabled",
                                        MTN_PROVIDER);
                }

                if (!sandbox || isProductionEnvironment()) {

                        log.error(
                                        "[MTN MOMO] A real MTN provider integration is required before accepting production payments. "
                                                        +
                                                        "sandbox={}, environment={}",
                                        sandbox, applicationEnvironment);

                        return PaymentGatewayResponse.failed(
                                        "MTN Mobile Money production integration is not configured",
                                        MTN_PROVIDER);
                }

                String transactionId = createSandboxTransactionId(loanId);

                log.info(
                                "[MTN SANDBOX] Public payment received. " +
                                                "loanId={}, amount={}, currency={}, phone={}, transactionId={}",
                                loanId,
                                amount,
                                paymentCurrency,
                                maskPhone(phoneNumber),
                                transactionId);

                scheduleAutomaticSandboxWebhook(
                                loanId,
                                transactionId,
                                amount,
                                paymentCurrency);

                return PaymentGatewayResponse.pending(
                                "MTN Mobile Money sandbox payment created. " +
                                                "Simulated webhook confirmation has been scheduled.",
                                transactionId,
                                MTN_PROVIDER);
        }

        // ============================================================
        // MANUAL SANDBOX CONFIRMATION
        // ============================================================

        public PaymentGatewayResponse simulateConfirmation(
                        Long loanId,
                        String transactionId,
                        Double amount,
                        String currency) {

                if (!sandbox) {

                        return PaymentGatewayResponse.failed(
                                        "MTN sandbox simulation is disabled",
                                        MTN_PROVIDER);
                }

                return processWebhookConfirmation(
                                loanId,
                                transactionId,
                                amount,
                                currency);
        }

        // ============================================================
        // AUTOMATIC SANDBOX WEBHOOK
        // ============================================================

        private void scheduleAutomaticSandboxWebhook(
                        Long loanId,
                        String transactionId,
                        Double amount,
                        String currency) {

                long delay = Math.max(
                                0L,
                                simulationDelayMs);

                CompletableFuture
                                .delayedExecutor(
                                                delay,
                                                TimeUnit.MILLISECONDS)
                                .execute(
                                                () -> {

                                                        try {

                                                                log.info(
                                                                                "[MTN SANDBOX WEBHOOK] " +
                                                                                                "Simulating successful MTN callback. "
                                                                                                +
                                                                                                "loanId={}, transactionId={}, amount={}, currency={}",
                                                                                loanId,
                                                                                transactionId,
                                                                                amount,
                                                                                currency);

                                                                PaymentGatewayResponse response = mtnWebhookTransactionService
                                                                                .processWebhookConfirmation(
                                                                                                loanId,
                                                                                                transactionId,
                                                                                                amount,
                                                                                                currency,
                                                                                                sandbox
                                                                                                                ? "MTN_SANDBOX_WEBHOOK"
                                                                                                                : "MTN_WEBHOOK");

                                                                if (response == null) {

                                                                        log.error(
                                                                                        "[MTN SANDBOX WEBHOOK] " +
                                                                                                        "Webhook returned null response. "
                                                                                                        +
                                                                                                        "loanId={}, transactionId={}",
                                                                                        loanId,
                                                                                        transactionId);

                                                                        return;
                                                                }

                                                                if (!"success".equalsIgnoreCase(
                                                                                response.getStatus())) {

                                                                        log.error(
                                                                                        "[MTN SANDBOX WEBHOOK] " +
                                                                                                        "Webhook processing failed. "
                                                                                                        +
                                                                                                        "loanId={}, transactionId={}, status={}, message={}",
                                                                                        loanId,
                                                                                        transactionId,
                                                                                        response.getStatus(),
                                                                                        response.getMessage());

                                                                        return;
                                                                }

                                                                log.info(
                                                                                "[MTN SANDBOX WEBHOOK] " +
                                                                                                "Webhook processed successfully. "
                                                                                                +
                                                                                                "loanId={}, transactionId={}, amount={}",
                                                                                loanId,
                                                                                transactionId,
                                                                                amount);

                                                        } catch (Exception e) {

                                                                log.error(
                                                                                "[MTN SANDBOX WEBHOOK] " +
                                                                                                "Webhook processing threw an exception. "
                                                                                                +
                                                                                                "loanId={}, transactionId={}",
                                                                                loanId,
                                                                                transactionId,
                                                                                e);
                                                        }
                                                });
        }

        // ============================================================
        // CONFIRM PAYMENT
        // ============================================================

        /*
         * Kept as a public method because existing controllers/services
         * may already call MtnMobileMoneyService.confirmPayment(...).
         *
         * The actual database transaction is delegated to the separate
         * transactional service.
         */
        public PaymentGatewayResponse confirmPayment(
                        Long loanId,
                        String transactionId,
                        Double amount,
                        String currency,
                        String confirmationSource) {

                return mtnWebhookTransactionService.confirmPayment(
                                loanId,
                                transactionId,
                                amount,
                                currency,
                                confirmationSource);
        }

        // ============================================================
        // WEBHOOK ENTRY POINT
        // ============================================================

        /*
         * This method intentionally does NOT have @Transactional.
         *
         * Transaction management belongs to
         * MtnWebhookTransactionService.
         */
        public PaymentGatewayResponse processWebhookConfirmation(
                        Long loanId,
                        String transactionId,
                        Double amount,
                        String currency) {

                log.info(
                                "[MTN WEBHOOK] Callback received. " +
                                                "loanId={}, transactionId={}, amount={}, currency={}, sandbox={}",
                                loanId,
                                transactionId,
                                amount,
                                currency,
                                sandbox);

                return mtnWebhookTransactionService
                                .processWebhookConfirmation(
                                                loanId,
                                                transactionId,
                                                amount,
                                                currency,
                                                sandbox
                                                                ? "MTN_SANDBOX_WEBHOOK"
                                                                : "MTN_WEBHOOK");
        }

        // ============================================================
        // VERIFY
        // ============================================================

        public boolean verify(
                        String transactionId) {

                if (transactionId == null
                                || transactionId.isBlank()) {

                        return false;
                }

                String normalizedTransactionId = transactionId.trim();

                if (sandbox) {

                        log.info(
                                        "[MTN SANDBOX] Transaction {} accepted as verified for sandbox testing.",
                                        normalizedTransactionId);

                        return true;
                }

                log.warn(
                                "[MTN MOMO] Real MTN verification is not configured. transactionId={}",
                                normalizedTransactionId);

                return false;
        }

        // ============================================================
        // PHONE NORMALIZATION
        // ============================================================

        private String normalizeRwandaPhone(
                        String phone) {

                if (phone == null) {
                        return "";
                }

                String value = phone.trim()
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
                        String phone) {

                if (phone == null
                                || phone.length() < 4) {

                        return "***";
                }

                return "***"
                                + phone.substring(
                                                phone.length() - 4);
        }

        // ============================================================
        // TRANSACTION ID
        // ============================================================

        private String createSandboxTransactionId(
                        Long loanId) {

                return "MTN-"
                                + loanId
                                + "-"
                                + UUID.randomUUID()
                                                .toString()
                                                .substring(
                                                                0,
                                                                8)
                                                .toUpperCase();
        }

        // ============================================================
        // ENVIRONMENT
        // ============================================================

        private boolean isProductionEnvironment() {

                return "production".equalsIgnoreCase(
                                applicationEnvironment)
                                || "prod".equalsIgnoreCase(
                                                applicationEnvironment);
        }
}