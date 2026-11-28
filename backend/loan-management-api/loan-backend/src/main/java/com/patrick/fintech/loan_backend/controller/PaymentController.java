package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.PaymentGatewayResponse;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.dto.PaymentResponse;
import com.patrick.fintech.loan_backend.mapper.ResponseDtoMapper;
import com.patrick.fintech.loan_backend.service.PaymentReviewService;
import com.patrick.fintech.loan_backend.service.MtnMobileMoneyService;
import com.patrick.fintech.loan_backend.service.PaymentService;
import com.patrick.fintech.loan_backend.service.LoanService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/loans/{loanId}/payments")
@RequiredArgsConstructor
public class PaymentController {

        private final PaymentService paymentService;
        private final LoanService loanService;
        private final CurrentUserUtil currentUserUtil;
        private final PaymentReviewService paymentReviews;
        private final MtnMobileMoneyService mtnMobileMoneyService;

        // ============================================================
        // RECORD MANUAL PAYMENT
        // ============================================================

        @PostMapping
        @PreAuthorize("hasAnyRole('ADMIN','MANAGER','ACCOUNTANT','TELLER','LOAN_OFFICER')")
        public ResponseEntity<ApiResponse<PaymentReviewService.Review>> recordPayment(
                        @PathVariable Long loanId,
                        @RequestBody PaymentReviewService.Request body,
                        @RequestHeader("Idempotency-Key") String idempotencyKey) {
                var review = paymentReviews.submit(loanId, currentUserUtil.getCurrentUser(), idempotencyKey, body);
                return ResponseEntity.accepted().body(ApiResponse.ok("Payment submitted for Business Owner review", review));
        }

        // ============================================================
        // MTN MOBILE MONEY - SANDBOX CONFIRMATION
        // ============================================================

        /**
         * Confirms a simulated MTN Mobile Money sandbox payment.
         *
         * IMPORTANT:
         *
         * This endpoint does NOT stop after returning a successful
         * MTN response.
         *
         * It performs the complete financial flow:
         *
         * sandbox confirmation
         * ↓
         * PaymentService.recordPayment()
         * ↓
         * interest allocation
         * ↓
         * principal allocation
         * ↓
         * loan outstanding balance updated
         * ↓
         * PAYMENT_MADE webhook dispatched
         *
         * The MTN transaction ID is used as the payment transaction ID.
         *
         * Therefore:
         *
         * same MTN transaction ID
         * +
         * repeated webhook
         * =
         * existing payment returned
         *
         * and the loan is NOT reduced twice.
         *
         * This endpoint is intended for sandbox/local testing only.
         */
        @PostMapping("/mtn/sandbox/confirm")
        @PreAuthorize("hasAnyRole('ADMIN','MANAGER','ACCOUNTANT','TELLER')")
        public ResponseEntity<ApiResponse<PaymentResponse>> confirmMtnSandboxPayment(
                        @PathVariable Long loanId,
                        @RequestBody Map<String, Object> body) {

                if (!mtnMobileMoneyService.isAvailable()) {
                        throw new IllegalStateException("MTN sandbox confirmation is unavailable");
                }

                // ========================================================
                // VALIDATE LOAN ID
                // ========================================================

                if (loanId == null) {

                        throw new IllegalArgumentException(
                                        "Loan ID is required.");
                }

                // ========================================================
                // TRANSACTION ID
                // ========================================================

                Object transactionValue = body != null
                                ? body.get("transactionId")
                                : null;

                if (transactionValue == null) {

                        throw new IllegalArgumentException(
                                        "MTN transactionId is required.");
                }

                String transactionId = transactionValue
                                .toString()
                                .trim();

                if (transactionId.isBlank()) {

                        throw new IllegalArgumentException(
                                        "MTN transactionId is required.");
                }

                // ========================================================
                // AMOUNT
                // ========================================================

                Object amountValue = body.get("amount");

                if (amountValue == null) {

                        throw new IllegalArgumentException(
                                        "MTN payment amount is required.");
                }

                BigDecimal amount;

                try {

                        amount = new BigDecimal(
                                        amountValue
                                                        .toString()
                                                        .trim());

                } catch (NumberFormatException e) {

                        throw new IllegalArgumentException(
                                        "MTN payment amount must be a valid number.");
                }

                if (amount.compareTo(
                                BigDecimal.ZERO) <= 0) {

                        throw new IllegalArgumentException(
                                        "MTN payment amount must be greater than zero.");
                }

                // ========================================================
                // CURRENCY
                // ========================================================

                String currency = body.getOrDefault(
                                "currency",
                                "RWF")
                                .toString()
                                .trim();

                if (currency.isBlank()) {
                        currency = "RWF";
                }

                // ========================================================
                // LOG
                // ========================================================

                org.slf4j.LoggerFactory
                                .getLogger(PaymentController.class)
                                .info(
                                                "[MTN SANDBOX CONFIRM] Starting confirmation. " +
                                                                "loanId={}, transactionId={}, amount={}, currency={}",
                                                loanId,
                                                transactionId,
                                                amount,
                                                currency);

                // ========================================================
                // STEP 1
                // SIMULATE MTN SUCCESS
                // ========================================================

                PaymentGatewayResponse confirmation = mtnMobileMoneyService.simulateConfirmation(
                                loanId,
                                transactionId,
                                amount.doubleValue(),
                                currency);

                if (confirmation == null) {

                        throw new IllegalStateException(
                                        "MTN sandbox confirmation returned no response.");
                }

                // ========================================================
                // VERIFY SUCCESS
                // ========================================================

                /*
                 * Your PaymentGatewayResponse.success(...) should produce
                 * a successful response.
                 *
                 * To avoid depending on a specific getter name from the
                 * DTO, we use the transaction verification as an additional
                 * sandbox confirmation check.
                 */
                boolean verified = mtnMobileMoneyService.verify(
                                transactionId);

                if (!verified) {

                        throw new IllegalStateException(
                                        "MTN sandbox transaction could not be verified: "
                                                        + transactionId);
                }

                // ========================================================
                // STEP 2
                // RECORD FINANCIAL PAYMENT
                // ========================================================

                /*
                 * CRITICAL:
                 *
                 * recordedBy = null
                 *
                 * because this is an automatic MTN confirmation and not a
                 * manually recorded staff payment.
                 *
                 * PaymentService already permits recordedBy == null.
                 *
                 * It will:
                 *
                 * - calculate interest
                 * - charge minimum one day interest on first payment
                 * - allocate payment to interest first
                 * - allocate remaining amount to principal
                 * - update loan.outstandingBalance
                 * - update totalPaid
                 * - update payment status
                 * - send email/SMS
                 * - notify loan officer
                 * - dispatch PAYMENT_MADE webhook
                 */
                Payment payment = paymentService.recordPayment(
                                loanId,
                                amount,
                                "MOBILE_MONEY",
                                transactionId,
                                "MTN_MOMO",
                                "MTN Mobile Money sandbox payment confirmed automatically.",
                                null);

                // ========================================================
                // STEP 3
                // FINAL LOG
                // ========================================================

                org.slf4j.LoggerFactory
                                .getLogger(PaymentController.class)
                                .info(
                                                "[MTN SANDBOX CONFIRM] Payment recorded successfully. " +
                                                                "loanId={}, transactionId={}, paymentId={}, amount={}",
                                                loanId,
                                                transactionId,
                                                payment != null
                                                                ? payment.getId()
                                                                : null,
                                                amount);

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                "MTN Mobile Money sandbox payment confirmed and recorded successfully.",
                                                ResponseDtoMapper.payment(payment)));
        }

        // ============================================================
        // MTN MOBILE MONEY - SANDBOX WEBHOOK ALIAS
        // ============================================================

        /**
         * Sandbox webhook-style endpoint.
         *
         * This endpoint exists so the frontend/test simulator can use
         * a webhook-like URL.
         *
         * It intentionally delegates to the exact same confirmation
         * method so there is only ONE financial recording path.
         *
         * DO NOT call PaymentService.recordPayment() separately here.
         */
        @PostMapping("/mtn/webhook")
        @PreAuthorize("hasAnyRole('ADMIN','MANAGER','ACCOUNTANT','TELLER')")
        public ResponseEntity<ApiResponse<PaymentResponse>> mtnWebhook(
                        @PathVariable Long loanId,
                        @RequestBody Map<String, Object> body) {

                return confirmMtnSandboxPayment(
                                loanId,
                                body);
        }

        // ============================================================
        // GET LOAN SCHEDULE
        // ============================================================

        /**
         * Gets the complete repayment schedule for a loan.
         */
        @GetMapping
        @PreAuthorize("hasAnyRole('ADMIN','MANAGER','ACCOUNTANT','LOAN_OFFICER','TELLER')")
        public ResponseEntity<ApiResponse<List<PaymentResponse>>> getSchedule(
                        @PathVariable Long loanId) {

                Long organizationId = currentUserUtil
                                .getCurrentOrganizationId();

                loanService.getLoanForOrg(
                                loanId,
                                organizationId);

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                ResponseDtoMapper.payments(paymentService.getLoanSchedule(
                                                                loanId,
                                                                organizationId))));
        }
}
