
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.PaymentGatewayResponse;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentTransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class MtnWebhookTransactionService {

    private static final String MTN_PROVIDER = "MTN_MOMO";
    private static final String PAYMENT_METHOD = "MOBILE_MONEY";

    private final PaymentService paymentService;
    private final LoanRepository loanRepo;
    private final PaymentTransactionRepository paymentTransactionRepo;

    @Value("${mtn.momo.sandbox:true}")
    private boolean sandbox;

    @Value("${mtn.momo.currency:RWF}")
    private String configuredCurrency;

    
    @Transactional
    public PaymentGatewayResponse processWebhookConfirmation(
            Long loanId,
            String transactionId,
            Double amount,
            String currency,
            String confirmationSource
    ) {

        log.info(
                "[MTN WEBHOOK TRANSACTION] Processing callback. " +
                "loanId={}, transactionId={}, amount={}, currency={}, source={}",
                loanId,
                transactionId,
                amount,
                currency,
                confirmationSource
        );

        return confirmPayment(
                loanId,
                transactionId,
                amount,
                currency,
                confirmationSource
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

        if (
                transactionId == null
                        || transactionId.isBlank()
        ) {

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

        // ========================================================
        // LOCK LOAN
        // ========================================================

        /*
         * THIS WAS THE LINE FAILING BEFORE:
         *
         * loanRepo.findByIdForUpdate(loanId)
         *
         * It now executes inside this service's Spring transaction.
         */
        var loan =
                loanRepo.findByIdForUpdate(
                        loanId
                )
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Loan not found: " + loanId
                        )
                );

        if (
                loan.getOrganization() == null
                        || loan.getOrganization().getId() == null
        ) {

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

        // ========================================================
        // IDEMPOTENCY
        // ========================================================

        /*
         * Because the loan row is locked and this entire method
         * is transactional, duplicate webhook callbacks are much
         * safer.
         */
        var existingTransaction =
                paymentTransactionRepo
                        .findByOrganization_IdAndTransactionReference(
                                organizationId,
                                normalizedTransactionId
                        )
                        .orElse(null);

        if (existingTransaction != null) {

            if (
                    Boolean.TRUE.equals(
                            existingTransaction.getReversed()
                    )
            ) {

                return PaymentGatewayResponse.failed(
                        "MTN transaction has already been reversed",
                        MTN_PROVIDER
                );
            }

            if (
                    existingTransaction.getLoan() == null
                            || !loanId.equals(
                                    existingTransaction
                                            .getLoan()
                                            .getId()
                            )
            ) {

                log.error(
                        "[MTN WEBHOOK TRANSACTION] Transaction reference " +
                        "belongs to another loan. " +
                        "loanId={}, transactionId={}, existingLoanId={}",
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
                    "[MTN WEBHOOK TRANSACTION] Duplicate webhook ignored. " +
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

        // ========================================================
        // SANDBOX CHECK
        // ========================================================

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

        // ========================================================
        // RECORD PAYMENT
        // ========================================================

        Payment payment;

        try {

            payment =
                    paymentService.recordPayment(
                            loanId,
                            BigDecimal.valueOf(
                                    amount
                            ),
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

            /*
             * Re-throwing here is intentional.
             *
             * Because this method is @Transactional, an unexpected
             * database failure should cause the transaction to roll
             * back rather than partially committing data.
             */
            throw e;
        }

        // ========================================================
        // CURRENT PAYMENT AMOUNT
        // ========================================================

        /*
         * PaymentService stores cumulative installment components.
         *
         * The MTN transaction amount is authoritative for the
         * current gateway transaction.
         */
        BigDecimal currentPaymentAmount =
                BigDecimal.valueOf(
                        amount
                ).setScale(
                        2,
                        RoundingMode.HALF_UP
                );

        // ========================================================
        // OUTSTANDING BALANCE
        // ========================================================

        BigDecimal outstandingBalance =
                payment.getOutstandingAfterDecimal();

        if (outstandingBalance == null) {

            outstandingBalance =
                    loan.getOutstandingBalanceDecimal();
        }

        if (outstandingBalance == null) {

            outstandingBalance =
                    BigDecimal.ZERO;
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

        // ========================================================
        // SUCCESS
        // ========================================================

        return PaymentGatewayResponse.success(
                "MTN Mobile Money sandbox payment confirmed and recorded against the loan",
                normalizedTransactionId,
                currentPaymentAmount.doubleValue(),
                paymentCurrency,
                PAYMENT_METHOD,
                MTN_PROVIDER
        );
    }
}
