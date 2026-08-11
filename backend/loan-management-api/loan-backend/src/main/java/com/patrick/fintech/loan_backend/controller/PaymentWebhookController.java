package com.patrick.fintech.loan_backend.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.service.FlutterwaveService;
import com.patrick.fintech.loan_backend.service.IdempotencyService;
import com.patrick.fintech.loan_backend.service.PaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/public/webhooks")
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookController {

    private static final String FLUTTERWAVE_PROVIDER = "FLUTTERWAVE";

    private static final String FLUTTERWAVE_PATH =
            "POST /api/public/webhooks/flutterwave";

    private final FlutterwaveService flutterwaveService;

    private final PaymentService paymentService;

    private final LoanRepository loanRepo;

    private final IdempotencyService idempotencyService;

    private final ObjectMapper objectMapper;

    // ================================================================
    // FLUTTERWAVE WEBHOOK SECRET
    // ================================================================

    @Value("${flutterwave.webhook-secret:}")
    private String flutterwaveWebhookSecret;

    // ================================================================
    // FLUTTERWAVE WEBHOOK
    //
    // IMPORTANT:
    // MTN and Airtel are intentionally NOT handled here.
    //
    // They belong to PublicController.
    //
    // This controller owns only:
    //
    // POST /api/public/webhooks/flutterwave
    // ================================================================

    @PostMapping("/flutterwave")
    public ResponseEntity<String> handleFlutterwaveWebhook(
            @RequestBody(required = false) Map<String, Object> payload,
            @RequestHeader(
                    value = "verif-hash",
                    required = false
            ) String signature
    ) {

        final String provider = FLUTTERWAVE_PROVIDER;

        try {

            // --------------------------------------------------------
            // PAYLOAD VALIDATION
            // --------------------------------------------------------

            if (payload == null || payload.isEmpty()) {

                log.warn(
                        "[PAYMENT WEBHOOK] Empty Flutterwave payload"
                );

                return ResponseEntity
                        .badRequest()
                        .body("Invalid payload");
            }

            // --------------------------------------------------------
            // WEBHOOK AUTHENTICATION
            // --------------------------------------------------------

            if (!isWebhookSecretValid(
                    flutterwaveWebhookSecret,
                    signature
            )) {

                log.warn(
                        "[PAYMENT WEBHOOK] Invalid Flutterwave webhook signature"
                );

                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body("Invalid signature");
            }

            // --------------------------------------------------------
            // FLUTTERWAVE DATA OBJECT
            // --------------------------------------------------------

            Object dataObject = payload.get("data");

            if (!(dataObject instanceof Map<?, ?>)) {

                log.warn(
                        "[PAYMENT WEBHOOK] Flutterwave payload missing data object"
                );

                /*
                 * The webhook was authenticated but does not contain
                 * a payment event that this application understands.
                 *
                 * Return 200 so Flutterwave does not continuously retry
                 * an intentionally ignored event.
                 */
                return ResponseEntity
                        .ok("ignored: no data");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data =
                    (Map<String, Object>) dataObject;

            // --------------------------------------------------------
            // TRANSACTION ID
            // --------------------------------------------------------

            String transactionId =
                    firstNonBlank(
                            data,
                            "id",
                            "transaction_id",
                            "transactionId"
                    );

            if (transactionId == null) {

                log.warn(
                        "[PAYMENT WEBHOOK] Flutterwave transaction ID missing"
                );

                return ResponseEntity
                        .ok("ignored: missing transaction id");
            }

            // --------------------------------------------------------
            // PAYMENT STATUS
            // --------------------------------------------------------

            String status =
                    firstNonBlank(
                            data,
                            "status"
                    );

            if (!isSuccessfulStatus(status)) {

                log.info(
                        "[PAYMENT WEBHOOK] Flutterwave payment not successful. " +
                                "transaction={}, status={}",
                        transactionId,
                        status
                );

                return ResponseEntity
                        .ok("payment not successful");
            }

            // --------------------------------------------------------
            // TRANSACTION REFERENCE
            // --------------------------------------------------------

            String transactionReference =
                    firstNonBlank(
                            data,
                            "tx_ref",
                            "txRef",
                            "reference"
                    );

            if (transactionReference == null) {

                log.warn(
                        "[PAYMENT WEBHOOK] Flutterwave transaction reference missing. " +
                                "transaction={}",
                        transactionId
                );

                return ResponseEntity
                        .ok("ignored: missing transaction reference");
            }

            // --------------------------------------------------------
            // LOAN ID
            // --------------------------------------------------------

            Long loanId =
                    FlutterwaveService.loanIdFromTxRef(
                            transactionReference
                    );

            if (loanId == null || loanId <= 0) {

                log.warn(
                        "[PAYMENT WEBHOOK] Unrecognized Flutterwave transaction reference. " +
                                "transaction={}, reference={}",
                        transactionId,
                        transactionReference
                );

                return ResponseEntity
                        .ok("ignored: unrecognized transaction");
            }

            // --------------------------------------------------------
            // FIND LOAN
            // --------------------------------------------------------

            Loan loan =
                    loanRepo.findById(loanId)
                            .orElse(null);

            if (loan == null) {

                log.warn(
                        "[PAYMENT WEBHOOK] Flutterwave loan not found. " +
                                "loan={}, transaction={}",
                        loanId,
                        transactionId
                );

                return ResponseEntity
                        .ok("ignored: unknown loan");
            }

            // --------------------------------------------------------
            // ORGANIZATION VALIDATION
            // --------------------------------------------------------

            if (loan.getOrganization() == null) {

                log.error(
                        "[PAYMENT WEBHOOK] Flutterwave loan has no organization. " +
                                "loan={}, transaction={}",
                        loanId,
                        transactionId
                );

                /*
                 * This is an internal data-integrity problem.
                 * Return 500 so Flutterwave can retry.
                 */
                return ResponseEntity
                        .internalServerError()
                        .body("loan organization unavailable");
            }

            // --------------------------------------------------------
            // VERIFY TRANSACTION WITH FLUTTERWAVE
            // --------------------------------------------------------

            boolean verified =
                    flutterwaveService.verifyTransaction(
                            transactionId
                    );

            if (!verified) {

                log.warn(
                        "[PAYMENT WEBHOOK] Flutterwave transaction verification failed. " +
                                "transaction={}, loan={}",
                        transactionId,
                        loanId
                );

                /*
                 * Never record an unverified payment.
                 *
                 * Returning 200 prevents an endless retry loop when
                 * Flutterwave has sent an event that cannot currently
                 * be verified.
                 */
                return ResponseEntity
                        .ok("not verified");
            }

            // --------------------------------------------------------
            // AMOUNT
            // --------------------------------------------------------

            BigDecimal amount =
                    extractPositiveAmount(
                            data.get("amount")
                    );

            if (amount == null) {

                log.warn(
                        "[PAYMENT WEBHOOK] Invalid Flutterwave amount. " +
                                "transaction={}, loan={}",
                        transactionId,
                        loanId
                );

                return ResponseEntity
                        .ok("invalid amount");
            }

            // --------------------------------------------------------
            // IDEMPOTENCY
            // --------------------------------------------------------

            var idempotency =
                    idempotencyService.checkOrReserve(
                            "flw-webhook-" + transactionId,
                            loan.getOrganization(),
                            FLUTTERWAVE_PATH,
                            toJson(payload)
                    );

            if (idempotency.isReplay()) {

                log.info(
                        "[PAYMENT WEBHOOK] Flutterwave replay ignored. " +
                                "transaction={}, loan={}",
                        transactionId,
                        loanId
                );

                return ResponseEntity
                        .ok("already processed");
            }

            // --------------------------------------------------------
            // PAYMENT METHOD
            // --------------------------------------------------------

            String method =
                    firstNonBlank(
                            data,
                            "payment_type",
                            "paymentType"
                    );

            method =
                    normalizePaymentMethod(
                            method
                    );

            // --------------------------------------------------------
            // RECORD PAYMENT
            // --------------------------------------------------------

            paymentService.recordPayment(
                    loanId,
                    amount,
                    method,
                    transactionId,
                    provider + "_WEBHOOK",
                    "Confirmed via Flutterwave webhook",
                    null
            );

            // --------------------------------------------------------
            // SUCCESS LOG
            // --------------------------------------------------------

            log.info(
                    "[PAYMENT WEBHOOK] Flutterwave payment processed successfully. " +
                            "loan={}, amount={}, transaction={}",
                    loanId,
                    amount,
                    transactionId
            );

            return ResponseEntity
                    .ok("processed");

        } catch (Exception e) {

            log.error(
                    "[PAYMENT WEBHOOK] Flutterwave processing failed",
                    e
            );

            /*
             * Internal processing failure.
             *
             * Return 500 so the provider can retry.
             */
            return ResponseEntity
                    .internalServerError()
                    .body("error processing webhook");
        }
    }

    // ================================================================
    // WEBHOOK SECRET VALIDATION
    // ================================================================

    private boolean isWebhookSecretValid(
            String configuredSecret,
            String receivedSignature
    ) {

        /*
         * Production security requirement:
         *
         * Never accept a webhook when the configured secret is
         * missing or blank.
         */

        if (
                configuredSecret == null
                        || configuredSecret.isBlank()
        ) {

            log.error(
                    "[PAYMENT WEBHOOK] Flutterwave webhook secret is not configured"
            );

            return false;
        }

        if (
                receivedSignature == null
                        || receivedSignature.isBlank()
        ) {

            return false;
        }

        return constantTimeEquals(
                configuredSecret.trim(),
                receivedSignature.trim()
        );
    }

    // ================================================================
    // CONSTANT-TIME SECRET COMPARISON
    // ================================================================

    private boolean constantTimeEquals(
            String expected,
            String actual
    ) {

        if (expected == null || actual == null) {
            return false;
        }

        byte[] expectedBytes =
                expected.getBytes(
                        StandardCharsets.UTF_8
                );

        byte[] actualBytes =
                actual.getBytes(
                        StandardCharsets.UTF_8
                );

        return MessageDigest.isEqual(
                expectedBytes,
                actualBytes
        );
    }

    // ================================================================
    // POSITIVE AMOUNT EXTRACTION
    // ================================================================

    private BigDecimal extractPositiveAmount(
            Object value
    ) {

        if (value == null) {
            return null;
        }

        try {

            BigDecimal amount;

            if (value instanceof BigDecimal decimal) {

                amount = decimal;

            } else if (value instanceof Number number) {

                amount =
                        new BigDecimal(
                                number.toString()
                        );

            } else {

                String text =
                        String.valueOf(value)
                                .trim();

                if (text.isBlank()) {
                    return null;
                }

                amount =
                        new BigDecimal(text);
            }

            /*
             * Reject:
             * - zero
             * - negative values
             */
            if (amount.signum() <= 0) {
                return null;
            }

            /*
             * Normalize the value used by the payment layer.
             *
             * We do not use double here because financial amounts
             * must remain exact decimal values.
             */
            return amount.stripTrailingZeros();

        } catch (NumberFormatException e) {

            return null;
        }
    }

    // ================================================================
    // STRING EXTRACTION
    // ================================================================

    private String firstNonBlank(
            Map<String, Object> payload,
            String... fields
    ) {

        if (payload == null || fields == null) {
            return null;
        }

        for (String field : fields) {

            if (field == null || field.isBlank()) {
                continue;
            }

            Object value =
                    payload.get(field);

            if (value == null) {
                continue;
            }

            String text =
                    String.valueOf(value)
                            .trim();

            if (!text.isBlank()) {
                return text;
            }
        }

        return null;
    }

    // ================================================================
    // PAYMENT STATUS
    // ================================================================

    private boolean isSuccessfulStatus(
            String status
    ) {

        if (status == null || status.isBlank()) {
            return false;
        }

        String normalized =
                status.trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        return normalized.equals("success")
                || normalized.equals("successful")
                || normalized.equals("completed")
                || normalized.equals("complete")
                || normalized.equals("paid")
                || normalized.equals("successful_payment")
                || normalized.equals("successful payment")
                || normalized.equals("successful-payment")
                || normalized.equals("200")
                || normalized.equals("0");
    }

    // ================================================================
    // PAYMENT METHOD NORMALIZATION
    // ================================================================

    private String normalizePaymentMethod(
            String method
    ) {

        if (method == null || method.isBlank()) {
            return "MOBILE_MONEY";
        }

        String normalized =
                method.trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (
                normalized.equals("MOBILEMONEY")
                        || normalized.equals("MOBILE-MONEY")
                        || normalized.equals("MOMO")
        ) {

            return "MOBILE_MONEY";
        }

        return normalized;
    }

    // ================================================================
    // JSON SERIALIZATION
    // ================================================================

    private String toJson(
            Object payload
    ) {

        if (payload == null) {
            return "{}";
        }

        try {

            return objectMapper.writeValueAsString(
                    payload
            );

        } catch (JsonProcessingException e) {

            /*
             * Do not expose the serialization exception to the
             * payment provider.
             */

            log.warn(
                    "[PAYMENT WEBHOOK] Could not serialize Flutterwave payload for idempotency"
            );

            return "{\"serialization\":\"failed\"}";
        }
    }
}
