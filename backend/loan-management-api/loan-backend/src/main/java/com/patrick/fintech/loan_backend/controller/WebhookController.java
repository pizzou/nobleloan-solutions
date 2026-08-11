package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.model.WebhookEndpoint;
import com.patrick.fintech.loan_backend.dto.PaymentGatewayResponse;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import com.patrick.fintech.loan_backend.repository.WebhookRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.MtnMobileMoneyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookRepository webhookRepo;
    private final UserRepository userRepo;
    private final AuditService auditService;
    private final MtnMobileMoneyService mtnMobileMoneyService;

    // ============================================================
    // AUTHENTICATED WEBHOOK MANAGEMENT
    // ============================================================

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> list(Authentication auth) {

        User user =
                currentUser(auth);

        return ResponseEntity.ok(
                webhookRepo.findByOrganization(
                        user.getOrganization()
                )
        );
    }

    // ============================================================
    // CREATE WEBHOOK ENDPOINT
    // ============================================================

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> create(
            @RequestBody WebhookEndpoint body,
            Authentication auth
    ) {

        User user =
                currentUser(auth);

        body.setOrganization(
                user.getOrganization()
        );

        body.setSecret(
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
        );

        WebhookEndpoint saved =
                webhookRepo.save(body);

        auditService.log(
                user.getOrganization(),
                user,
                "WEBHOOK_CREATED",
                "WEBHOOK",
                String.valueOf(saved.getId()),
                "Created webhook endpoint " + saved.getUrl(),
                null,
                null,
                "Webhooks & Integrations"
        );

        return ResponseEntity.ok(saved);
    }

    // ============================================================
    // DELETE WEBHOOK ENDPOINT
    // ============================================================

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> delete(
            @PathVariable Long id,
            Authentication auth
    ) {

        User user =
                currentUser(auth);

        WebhookEndpoint ep =
                webhookRepo.findById(id)
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "Webhook not found"
                                )
                        );

        if (ep.getOrganization() == null
                || user.getOrganization() == null
                || !ep.getOrganization()
                .getId()
                .equals(
                        user.getOrganization().getId()
                )) {

            throw new RuntimeException(
                    "Access denied"
            );
        }

        webhookRepo.delete(ep);

        auditService.log(
                user.getOrganization(),
                user,
                "WEBHOOK_DELETED",
                "WEBHOOK",
                String.valueOf(id),
                "Deleted webhook endpoint " + ep.getUrl(),
                null,
                null,
                "Webhooks & Integrations"
        );

        return ResponseEntity.ok(
                Map.of(
                        "deleted",
                        true
                )
        );
    }

    // ============================================================
    // MTN MOBILE MONEY SANDBOX WEBHOOK
    // ============================================================

    /*
     * IMPORTANT:
     *
     * This endpoint is intentionally NOT protected by JWT.
     *
     * The sandbox HTTP callback must be able to reach it without
     * logging into the application.
     *
     * URL:
     *
     * POST /api/webhooks/mtn/momo
     */

    @PostMapping("/mtn/momo")
    @PreAuthorize("permitAll()")
    public ResponseEntity<PaymentGatewayResponse> receiveMtnWebhook(
            @RequestBody Map<String, Object> payload
    ) {

        log.info(
                "[MTN WEBHOOK] HTTP callback endpoint reached. payload={}",
                payload
        );

        Long loanId =
                toLong(
                        payload.get("loanId")
                );

        String transactionId =
                firstString(
                        payload,
                        "transactionId",
                        "referenceId",
                        "financialTransactionId"
                );

        Double amount =
                toDouble(
                        payload.get("amount")
                );

        String currency =
                firstString(
                        payload,
                        "currency"
                );

        String status =
                firstString(
                        payload,
                        "status"
                );

        log.info(
                "[MTN WEBHOOK] Parsed callback. " +
                        "loanId={}, transactionId={}, amount={}, " +
                        "currency={}, status={}",
                loanId,
                transactionId,
                amount,
                currency,
                status
        );

        // ========================================================
        // VALIDATION
        // ========================================================

        if (loanId == null) {

            log.warn(
                    "[MTN WEBHOOK] Missing loanId."
            );

            return ResponseEntity.badRequest()
                    .body(
                            PaymentGatewayResponse.failed(
                                    "loanId is required",
                                    "MTN_MOMO"
                            )
                    );
        }

        if (transactionId == null
                || transactionId.isBlank()) {

            log.warn(
                    "[MTN WEBHOOK] Missing transactionId."
            );

            return ResponseEntity.badRequest()
                    .body(
                            PaymentGatewayResponse.failed(
                                    "transactionId is required",
                                    "MTN_MOMO"
                            )
                    );
        }

        if (amount == null
                || amount <= 0) {

            log.warn(
                    "[MTN WEBHOOK] Invalid amount. amount={}",
                    amount
            );

            return ResponseEntity.badRequest()
                    .body(
                            PaymentGatewayResponse.failed(
                                    "amount must be greater than zero",
                                    "MTN_MOMO"
                            )
                    );
        }

        /*
         * Sandbox sends SUCCESSFUL.
         *
         * Do not process failed/rejected transactions.
         */

        if (status != null
                && !status.isBlank()
                && !"SUCCESSFUL".equalsIgnoreCase(status)
                && !"SUCCESS".equalsIgnoreCase(status)) {

            log.warn(
                    "[MTN WEBHOOK] Non-successful callback ignored. " +
                            "transactionId={}, status={}",
                    transactionId,
                    status
            );

            return ResponseEntity.ok(
                    PaymentGatewayResponse.failed(
                            "MTN transaction was not successful",
                            "MTN_MOMO"
                    )
            );
        }

        // ========================================================
        // PROCESS PAYMENT
        // ========================================================

        try {

            PaymentGatewayResponse response =
                    mtnMobileMoneyService
                            .processWebhookConfirmation(
                                    loanId,
                                    transactionId,
                                    amount,
                                    currency
                            );

            log.info(
                    "[MTN WEBHOOK] Callback processing completed. " +
                            "loanId={}, transactionId={}, status={}, message={}",
                    loanId,
                    transactionId,
                    response != null
                            ? response.getStatus()
                            : null,
                    response != null
                            ? response.getMessage()
                            : null
            );

            return ResponseEntity.ok(
                    response
            );

        } catch (Exception e) {

            log.error(
                    "[MTN WEBHOOK] Callback processing failed. " +
                            "loanId={}, transactionId={}",
                    loanId,
                    transactionId,
                    e
            );

            return ResponseEntity.internalServerError()
                    .body(
                            PaymentGatewayResponse.failed(
                                    "Webhook processing failed",
                                    "MTN_MOMO"
                            )
                    );
        }
    }

    // ============================================================
    // WEBHOOK HEALTH CHECK
    // ============================================================

    @GetMapping("/mtn/momo")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> mtnWebhookHealth() {

        return ResponseEntity.ok(
                Map.of(
                        "status",
                        "ok",
                        "provider",
                        "MTN_MOMO",
                        "sandbox",
                        true,
                        "message",
                        "MTN sandbox webhook endpoint is available"
                )
        );
    }

    // ============================================================
    // CURRENT USER
    // ============================================================

    private User currentUser(
            Authentication auth
    ) {

        if (auth == null
                || auth.getName() == null) {

            throw new RuntimeException(
                    "Authentication required"
            );
        }

        return userRepo
                .findByEmail(
                        auth.getName()
                )
                .orElseThrow(
                        () -> new RuntimeException(
                                "User not found"
                        )
                );
    }

    // ============================================================
    // LONG CONVERSION
    // ============================================================

    private Long toLong(
            Object value
    ) {

        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {

            return number.longValue();
        }

        try {

            return Long.parseLong(
                    value.toString()
            );

        } catch (Exception e) {

            return null;
        }
    }

    // ============================================================
    // DOUBLE CONVERSION
    // ============================================================

    private Double toDouble(
            Object value
    ) {

        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {

            return number.doubleValue();
        }

        try {

            return Double.parseDouble(
                    value.toString()
            );

        } catch (Exception e) {

            return null;
        }
    }

    // ============================================================
    // STRING EXTRACTION
    // ============================================================

    private String firstString(
            Map<String, Object> payload,
            String... keys
    ) {

        for (String key : keys) {

            Object value =
                    payload.get(key);

            if (value != null
                    && !value.toString()
                    .isBlank()) {

                return value.toString().trim();
            }
        }

        return null;
    }
}