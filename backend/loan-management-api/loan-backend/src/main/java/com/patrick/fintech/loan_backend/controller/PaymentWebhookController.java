package com.patrick.fintech.loan_backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.service.AirtelMobileMoneyService;
import com.patrick.fintech.loan_backend.service.FlutterwaveService;
import com.patrick.fintech.loan_backend.service.MtnMobileMoneyService;
import com.patrick.fintech.loan_backend.service.PaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

@RestController
@RequestMapping("/api/public/webhooks")
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookController {

    private final FlutterwaveService flutterwaveService;

    private final MtnMobileMoneyService mtnMobileMoneyService;

    private final AirtelMobileMoneyService airtelMoneyService;

    private final PaymentService paymentService;

    private final LoanRepository loanRepo;

    private final ObjectMapper objectMapper;

    @Value("${flutterwave.webhook-secret:}")
    private String flutterwaveWebhookSecret;

    @Value("${mtn.mobile-money.webhook-secret:}")
    private String mtnWebhookSecret;

    @Value("${airtel.money.webhook-secret:}")
    private String airtelWebhookSecret;

    // ================================================================
    // FLUTTERWAVE WEBHOOK
    // ================================================================

    @PostMapping("/flutterwave")
    public ResponseEntity<String> flutterwave(
            @RequestHeader(
                    value = "verif-hash",
                    required = false
            )
            String verificationHash,

            @RequestBody String rawBody
    ) {

        log.info(
                "[FLUTTERWAVE WEBHOOK] Webhook received."
        );

        try {

            // ========================================================
            // VERIFY WEBHOOK SECRET
            // ========================================================

            if (!isValidSecret(
                    flutterwaveWebhookSecret,
                    verificationHash
            )) {

                log.warn(
                        "[FLUTTERWAVE WEBHOOK] Invalid webhook signature."
                );

                return ResponseEntity
                        .status(401)
                        .body(
                                "Invalid webhook signature"
                        );
            }

            // ========================================================
            // PARSE PAYLOAD
            // ========================================================

            JsonNode root =
                    objectMapper.readTree(
                            rawBody
                    );

            JsonNode data =
                    root.path("data");

            if (data.isMissingNode()
                    || data.isNull()) {

                log.warn(
                        "[FLUTTERWAVE WEBHOOK] Missing data object."
                );

                return ResponseEntity
                        .badRequest()
                        .body(
                                "Missing webhook data"
                        );
            }

            // ========================================================
            // TRANSACTION ID
            // ========================================================

            String transactionId =
                    firstText(
                            data,
                            "id",
                            "transaction_id",
                            "transactionId"
                    );

            if (isBlank(transactionId)) {

                log.warn(
                        "[FLUTTERWAVE WEBHOOK] Missing transaction ID."
                );

                return ResponseEntity
                        .badRequest()
                        .body(
                                "Missing transaction ID"
                        );
            }

            // ========================================================
            // TRANSACTION REFERENCE
            // ========================================================

            String transactionReference =
                    firstText(
                            data,
                            "tx_ref",
                            "txRef",
                            "transaction_reference",
                            "transactionReference"
                    );

            if (isBlank(transactionReference)) {

                log.warn(
                        "[FLUTTERWAVE WEBHOOK] Missing transaction reference. " +
                                "transactionId={}",
                        transactionId
                );

                return ResponseEntity
                        .badRequest()
                        .body(
                                "Missing transaction reference"
                        );
            }

            // ========================================================
            // LOAN ID
            // ========================================================

            Long loanId =
                    extractLoanIdFromReference(
                            transactionReference
                    );

            if (loanId == null) {

                log.error(
                        "[FLUTTERWAVE WEBHOOK] Could not determine loan ID. " +
                                "transactionId={}, txRef={}",
                        transactionId,
                        transactionReference
                );

                return ResponseEntity
                        .badRequest()
                        .body(
                                "Loan ID could not be determined"
                        );
            }

            // ========================================================
            // LOAD LOAN
            // ========================================================

            Loan loan =
                    loanRepo.findById(
                            loanId
                    ).orElse(null);

            if (loan == null) {

                log.error(
                        "[FLUTTERWAVE WEBHOOK] Loan not found. loanId={}, " +
                                "transactionId={}",
                        loanId,
                        transactionId
                );

                return ResponseEntity
                        .badRequest()
                        .body(
                                "Loan not found"
                        );
            }

            // ========================================================
            // VERIFY TRANSACTION WITH FLUTTERWAVE
            // ========================================================

            boolean verified =
                    flutterwaveService.verifyTransaction(
                            transactionId
                    );

            if (!verified) {

                log.warn(
                        "[FLUTTERWAVE WEBHOOK] Transaction verification failed. " +
                                "loanId={}, transactionId={}",
                        loanId,
                        transactionId
                );

                return ResponseEntity
                        .badRequest()
                        .body(
                                "Transaction verification failed"
                        );
            }

            // ========================================================
            // STATUS FROM WEBHOOK
            // ========================================================

            String status =
                    firstText(
                            data,
                            "status",
                            "transaction_status",
                            "transactionStatus"
                    );

            if (!isSuccessfulStatus(status)) {

                log.info(
                        "[FLUTTERWAVE WEBHOOK] Payment is not successful. " +
                                "loanId={}, transactionId={}, status={}",
                        loanId,
                        transactionId,
                        status
                );

                return ResponseEntity.ok(
                        "Payment not successful"
                );
            }

            // ========================================================
            // AMOUNT
            // ========================================================

            BigDecimal amount =
                    extractAmount(
                            data
                    );

            if (amount == null
                    || amount.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

                log.error(
                        "[FLUTTERWAVE WEBHOOK] Invalid payment amount. " +
                                "loanId={}, transactionId={}, amount={}",
                        loanId,
                        transactionId,
                        amount
                );

                return ResponseEntity
                        .badRequest()
                        .body(
                                "Invalid payment amount"
                        );
            }

            // ========================================================
            // CURRENCY SAFETY
            // ========================================================

            String currency =
                    firstText(
                            data,
                            "currency"
                    );

            if (currency != null) {
                currency =
                        currency
                                .trim()
                                .toUpperCase(
                                        Locale.ROOT
                                );
            }

            if (loan.getCurrency() != null
                    && currency != null
                    && !loan.getCurrency()
                    .equalsIgnoreCase(currency)) {

                log.error(
                        "[FLUTTERWAVE WEBHOOK] Currency mismatch. " +
                                "loanId={}, loanCurrency={}, paymentCurrency={}, " +
                                "transactionId={}",
                        loanId,
                        loan.getCurrency(),
                        currency,
                        transactionId
                );

                return ResponseEntity
                        .badRequest()
                        .body(
                                "Payment currency does not match loan currency"
                        );
            }

            // ========================================================
            // RECORD PAYMENT
            // ========================================================

            /*
             * PaymentService already has transaction-level idempotency:
             *
             * findByOrganization_IdAndTransactionId(...)
             *
             * Therefore duplicate provider webhooks are safely handled
             * there.
             */

            paymentService.recordPayment(
                    loanId,
                    amount,
                    extractPaymentMethod(
                            data
                    ),
                    transactionId,
                    "FLUTTERWAVE_WEBHOOK",
                    "Payment automatically confirmed by Flutterwave",
                    null
            );

            log.info(
                    "[FLUTTERWAVE WEBHOOK] PAYMENT RECORDED SUCCESSFULLY. " +
                            "loanId={}, transactionId={}, amount={}, currency={}",
                    loanId,
                    transactionId,
                    amount,
                    currency
            );

            return ResponseEntity.ok(
                    "Payment processed"
            );

        } catch (Exception e) {

            log.error(
                    "[FLUTTERWAVE WEBHOOK] Payment processing failed.",
                    e
            );

            /*
             * Return 500 so the provider can retry the webhook.
             */
            return ResponseEntity
                    .internalServerError()
                    .body(
                            "Webhook processing failed"
                    );
        }
    }

    // ================================================================
    // MTN WEBHOOK
    // ================================================================

    @PostMapping("/mtn-momo")
    public ResponseEntity<String> mtn(
            @RequestHeader(
                    value = "X-Webhook-Secret",
                    required = false
            )
            String webhookSecret,

            @RequestBody String rawBody
    ) {

        log.info(
                "[MTN WEBHOOK] Webhook received."
        );

        try {

            if (!isValidSecret(
                    mtnWebhookSecret,
                    webhookSecret
            )) {

                log.warn(
                        "[MTN WEBHOOK] Invalid webhook secret."
                );

                return ResponseEntity
                        .status(401)
                        .body(
                                "Invalid webhook secret"
                        );
            }

            JsonNode root =
                    objectMapper.readTree(
                            rawBody
                    );

            String transactionId =
                    firstText(
                            root,
                            "transactionId",
                            "transaction_id",
                            "externalId",
                            "external_id",
                            "financialTransactionId",
                            "reference",
                            "id"
                    );

            if (isBlank(transactionId)) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                "Missing transaction ID"
                        );
            }

            String status =
                    firstText(
                            root,
                            "status",
                            "transactionStatus",
                            "transaction_status"
                    );

            if (!isSuccessfulStatus(status)) {

                log.info(
                        "[MTN WEBHOOK] Transaction not successful. " +
                                "transactionId={}, status={}",
                        transactionId,
                        status
                );

                return ResponseEntity.ok(
                        "Transaction not successful"
                );
            }

            Long loanId =
                    extractLoanId(
                            root
                    );

            if (loanId == null) {

                log.error(
                        "[MTN WEBHOOK] Could not determine loan ID. " +
                                "transactionId={}",
                        transactionId
                );

                return ResponseEntity
                        .badRequest()
                        .body(
                                "Loan ID could not be determined"
                        );
            }

            Loan loan =
                    loanRepo.findById(
                            loanId
                    ).orElse(null);

            if (loan == null) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                "Loan not found"
                        );
            }

            BigDecimal amount =
                    extractAmount(
                            root
                    );

            if (amount == null
                    || amount.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                "Invalid payment amount"
                        );
            }

            paymentService.recordPayment(
                    loanId,
                    amount,
                    "MOBILE_MONEY",
                    transactionId,
                    "MTN_WEBHOOK",
                    "Payment automatically confirmed by MTN Mobile Money",
                    null
            );

            log.info(
                    "[MTN WEBHOOK] PAYMENT RECORDED. " +
                            "loanId={}, transactionId={}, amount={}",
                    loanId,
                    transactionId,
                    amount
            );

            return ResponseEntity.ok(
                    "Payment processed"
            );

        } catch (Exception e) {

            log.error(
                    "[MTN WEBHOOK] Processing failed.",
                    e
            );

            return ResponseEntity
                    .internalServerError()
                    .body(
                            "Webhook processing failed"
                    );
        }
    }

    // ================================================================
    // AIRTEL WEBHOOK
    // ================================================================

    @PostMapping("/airtel-momo")
    public ResponseEntity<String> airtel(
            @RequestHeader(
                    value = "X-Webhook-Secret",
                    required = false
            )
            String webhookSecret,

            @RequestBody String rawBody
    ) {

        log.info(
                "[AIRTEL WEBHOOK] Webhook received."
        );

        try {

            if (!isValidSecret(
                    airtelWebhookSecret,
                    webhookSecret
            )) {

                log.warn(
                        "[AIRTEL WEBHOOK] Invalid webhook secret."
                );

                return ResponseEntity
                        .status(401)
                        .body(
                                "Invalid webhook secret"
                        );
            }

            JsonNode root =
                    objectMapper.readTree(
                            rawBody
                    );

            String transactionId =
                    firstText(
                            root,
                            "transactionId",
                            "transaction_id",
                            "externalId",
                            "external_id",
                            "financialTransactionId",
                            "reference",
                            "id"
                    );

            if (isBlank(transactionId)) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                "Missing transaction ID"
                        );
            }

            String status =
                    firstText(
                            root,
                            "status",
                            "transactionStatus",
                            "transaction_status",
                            "resultCode",
                            "result_code"
                    );

            if (!isSuccessfulStatus(status)) {

                log.info(
                        "[AIRTEL WEBHOOK] Transaction not successful. " +
                                "transactionId={}, status={}",
                        transactionId,
                        status
                );

                return ResponseEntity.ok(
                        "Transaction not successful"
                );
            }

            Long loanId =
                    extractLoanId(
                            root
                    );

            if (loanId == null) {

                log.error(
                        "[AIRTEL WEBHOOK] Could not determine loan ID. " +
                                "transactionId={}",
                        transactionId
                );

                return ResponseEntity
                        .badRequest()
                        .body(
                                "Loan ID could not be determined"
                        );
            }

            Loan loan =
                    loanRepo.findById(
                            loanId
                    ).orElse(null);

            if (loan == null) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                "Loan not found"
                        );
            }

            BigDecimal amount =
                    extractAmount(
                            root
                    );

            if (amount == null
                    || amount.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                "Invalid payment amount"
                        );
            }

            paymentService.recordPayment(
                    loanId,
                    amount,
                    "MOBILE_MONEY",
                    transactionId,
                    "AIRTEL_WEBHOOK",
                    "Payment automatically confirmed by Airtel Money",
                    null
            );

            log.info(
                    "[AIRTEL WEBHOOK] PAYMENT RECORDED. " +
                            "loanId={}, transactionId={}, amount={}",
                    loanId,
                    transactionId,
                    amount
            );

            return ResponseEntity.ok(
                    "Payment processed"
            );

        } catch (Exception e) {

            log.error(
                    "[AIRTEL WEBHOOK] Processing failed.",
                    e
            );

            return ResponseEntity
                    .internalServerError()
                    .body(
                            "Webhook processing failed"
                    );
        }
    }

    // ================================================================
    // FLUTTERWAVE LOAN ID
    // ================================================================

    private Long extractLoanIdFromReference(
            String reference
    ) {

        if (isBlank(reference)) {
            return null;
        }

        String normalized =
                reference.trim();

        String upper =
                normalized.toUpperCase(
                        Locale.ROOT
                );

        int index =
                upper.indexOf(
                        "LOAN-"
                );

        if (index < 0) {

            index =
                    upper.indexOf(
                            "LOAN_"
                    );
        }

        if (index < 0) {
            return null;
        }

        int start =
                index + 5;

        StringBuilder digits =
                new StringBuilder();

        while (
                start < normalized.length()
                        && Character.isDigit(
                        normalized.charAt(start)
                )
        ) {

            digits.append(
                    normalized.charAt(start)
            );

            start++;
        }

        if (digits.isEmpty()) {
            return null;
        }

        try {

            return Long.parseLong(
                    digits.toString()
            );

        } catch (NumberFormatException e) {

            return null;
        }
    }

    // ================================================================
    // GENERIC LOAN ID
    // ================================================================

    private Long extractLoanId(
            JsonNode root
    ) {

        String directLoanId =
                firstText(
                        root,
                        "loanId",
                        "loan_id"
                );

        if (!isBlank(directLoanId)) {

            try {

                return Long.parseLong(
                        directLoanId
                );

            } catch (NumberFormatException ignored) {
            }
        }

        String reference =
                firstText(
                        root,
                        "tx_ref",
                        "txRef",
                        "transactionReference",
                        "transaction_reference",
                        "reference",
                        "externalId",
                        "external_id"
                );

        if (isBlank(reference)) {
            return null;
        }

        return extractLoanIdFromReference(
                reference
        );
    }

    // ================================================================
    // AMOUNT
    // ================================================================

    private BigDecimal extractAmount(
            JsonNode node
    ) {

        String value =
                firstText(
                        node,
                        "amount",
                        "Amount",
                        "transactionAmount",
                        "transaction_amount",
                        "value"
                );

        if (isBlank(value)) {
            return null;
        }

        try {

            return new BigDecimal(
                    value.trim()
            ).setScale(
                    2,
                    RoundingMode.HALF_UP
            );

        } catch (NumberFormatException e) {

            log.warn(
                    "[PAYMENT WEBHOOK] Invalid amount: {}",
                    value
            );

            return null;
        }
    }

    // ================================================================
    // STATUS
    // ================================================================

    private boolean isSuccessfulStatus(
            String status
    ) {

        if (isBlank(status)) {
            return false;
        }

        String normalized =
                status
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        return normalized.equals(
                "SUCCESS"
        )
                || normalized.equals(
                "SUCCESSFUL"
        )
                || normalized.equals(
                "COMPLETED"
        )
                || normalized.equals(
                "COMPLETED_SUCCESSFULLY"
        )
                || normalized.equals(
                "SUCCESSFUL_PAYMENT"
        )
                || normalized.equals(
                "SUCCESSFUL_TRANSACTION"
        )
                || normalized.equals(
                "200"
        );
    }

    // ================================================================
    // PAYMENT METHOD
    // ================================================================

    private String extractPaymentMethod(
            JsonNode data
    ) {

        String paymentType =
                firstText(
                        data,
                        "payment_type",
                        "paymentType",
                        "payment_method",
                        "paymentMethod"
                );

        if (isBlank(paymentType)) {

            return "MOBILE_MONEY";
        }

        return paymentType
                .trim()
                .toUpperCase(
                        Locale.ROOT
                );
    }

    // ================================================================
    // FIRST TEXT
    // ================================================================

    private String firstText(
            JsonNode node,
            String... fields
    ) {

        if (node == null
                || fields == null) {

            return null;
        }

        for (String field : fields) {

            String value =
                    text(
                            node,
                            field
                    );

            if (!isBlank(value)) {
                return value;
            }
        }

        return null;
    }

    // ================================================================
    // TEXT
    // ================================================================

    private String text(
            JsonNode node,
            String field
    ) {

        if (node == null
                || field == null) {

            return null;
        }

        JsonNode value =
                node.get(field);

        if (value == null
                || value.isNull()) {

            return null;
        }

        return value.asText();
    }

    // ================================================================
    // WEBHOOK SECRET
    // ================================================================

    private boolean isValidSecret(
            String configuredSecret,
            String receivedSecret
    ) {

        if (isBlank(configuredSecret)) {

            log.error(
                    "[PAYMENT WEBHOOK] Webhook secret is not configured."
            );

            return false;
        }

        if (isBlank(receivedSecret)) {
            return false;
        }

        return configuredSecret
                .trim()
                .equals(
                        receivedSecret.trim()
                );
    }

    // ================================================================
    // BLANK
    // ================================================================

    private boolean isBlank(
            String value
    ) {

        return value == null
                || value.trim().isEmpty();
    }
}