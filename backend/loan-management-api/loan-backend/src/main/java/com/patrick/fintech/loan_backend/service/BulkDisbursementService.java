package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.LoanRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkDisbursementService {

        private final LoanRepository loanRepo;
        private final PaymentScheduleService paymentScheduleService;
        private final LoanService loanService;
        private final AccountingService accountingService;
        private final AuditService auditService;
        private final WebhookService webhookService;
        private final SmsService smsService;

        // ================================================================
        // PLATFORM RULES
        // ================================================================

        private static final BigDecimal PROCESSING_FEE_RATE = new BigDecimal("2.00");

        private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

        private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(
                        2,
                        RoundingMode.HALF_UP);

        // ================================================================
        // BULK DISBURSE
        // ================================================================

        @Transactional
        public BulkDisbursementResult disburseAll(
                        List<Long> loanIds,
                        Long orgId,
                        User officer,
                        String method) {

                if (loanIds == null || loanIds.isEmpty()) {

                        throw new IllegalArgumentException(
                                        "At least one loan ID is required");
                }

                if (orgId == null) {

                        throw new IllegalArgumentException(
                                        "Organization ID is required");
                }

                if (officer == null) {

                        throw new IllegalArgumentException(
                                        "Officer is required");
                }

                if (officer.getOrganization() == null
                                || officer.getOrganization().getId() == null
                                || !orgId.equals(
                                                officer.getOrganization().getId())) {

                        throw new IllegalStateException(
                                        "Officer does not belong to the selected organization");
                }

                String normalizedMethod = method == null || method.isBlank()
                                ? "UNSPECIFIED"
                                : method.trim();

                List<DisbursementLine> lines = new ArrayList<>();

                BigDecimal totalGrossDisbursed = ZERO;

                BigDecimal totalProcessingFees = ZERO;

                BigDecimal totalNetDisbursed = ZERO;

                int successCount = 0;
                int failureCount = 0;

                LocalDateTime processedAt = LocalDateTime.now();

                // ============================================================
                // PROCESS EACH LOAN
                // ============================================================

                for (Long loanId : loanIds) {

                        if (loanId == null) {

                                lines.add(
                                                DisbursementLine.failed(
                                                                null,
                                                                null,
                                                                "Loan ID is required"));

                                failureCount++;
                                continue;
                        }

                        try {

                                // ====================================================
                                // LOAD LOAN
                                // ====================================================

                                Loan loan = loanRepo.findById(
                                                loanId)
                                                .orElseThrow(
                                                                () -> new IllegalArgumentException(
                                                                                "Loan not found: "
                                                                                                + loanId));

                                // ====================================================
                                // ORGANIZATION SECURITY
                                // ====================================================

                                if (loan.getOrganization() == null
                                                || loan.getOrganization().getId() == null) {

                                        throw new IllegalStateException(
                                                        "Loan has no valid organization");
                                }

                                if (!orgId.equals(
                                                loan.getOrganization().getId())) {

                                        throw new IllegalStateException(
                                                        "Access denied");
                                }

                                // ====================================================
                                // STATUS VALIDATION
                                // ====================================================

                                if (loan.getStatus() != LoanStatus.APPROVED) {

                                        throw new IllegalStateException(
                                                        "Loan status is "
                                                                        + loan.getStatus()
                                                                        + ". Only APPROVED loans can be disbursed.");
                                }

                                // ====================================================
                                // AMOUNT VALIDATION
                                // ====================================================

                                BigDecimal grossAmount = money(
                                                loan.getAmountDecimal());

                                if (grossAmount.compareTo(ZERO) <= 0) {

                                        throw new IllegalStateException(
                                                        "Loan gross amount must be greater than zero");
                                }

                                // ====================================================
                                // PROCESSING FEE
                                // ====================================================

                                BigDecimal processingFee = money(
                                                grossAmount
                                                                .multiply(
                                                                                PROCESSING_FEE_RATE)
                                                                .divide(
                                                                                ONE_HUNDRED,
                                                                                16,
                                                                                RoundingMode.HALF_UP));

                                /*
                                 * Net cash delivered to borrower.
                                 */
                                BigDecimal netDisbursement = money(
                                                grossAmount
                                                                .subtract(
                                                                                processingFee)
                                                                .max(
                                                                                ZERO));

                                // ====================================================
                                // DISBURSEMENT TIMESTAMP
                                // ====================================================

                                LocalDateTime disbursedAt = LocalDateTime.now();

                                LocalDate disbursementDate = disbursedAt.toLocalDate();

                                // ====================================================
                                // UPDATE LOAN
                                // ============================================================

                                loan.setStatus(
                                                LoanStatus.ACTIVE);

                                loan.setDisbursedAt(
                                                disbursedAt);

                                loan.setDisbursedAtTimestamp(
                                                disbursedAt);

                                loan.setStartDate(
                                                disbursementDate);

                                loan.setDisbursedAmount(
                                                netDisbursement);

                                loan.setProcessingFeeRate(
                                                PROCESSING_FEE_RATE);

                                loan.setProcessingFee(
                                                processingFee);

                                loan.setOutstandingBalance(
                                                grossAmount);

                                if (loan.getDurationMonths() == null
                                                || loan.getDurationMonths() <= 0) {

                                        throw new IllegalStateException(
                                                        "Loan duration must be greater than zero");
                                }

                                LocalDate firstDueDate = disbursementDate.plusMonths(1);

                                loan.setMaturityDate(
                                                disbursementDate.plusMonths(
                                                                loan.getDurationMonths()));

                                loan.setNextDueDate(
                                                firstDueDate);

                                loan.setNextPaymentDate(
                                                firstDueDate);

                                // ====================================================
                                // SAVE LOAN BEFORE SCHEDULE GENERATION
                                // ====================================================

                                Loan saved = loanRepo.save(
                                                loan);

                                paymentScheduleService.generateSchedule(
                                                saved);

                                saved = loanRepo.save(
                                                saved);

                                accountingService.postDisbursement(
                                                saved);

                                totalGrossDisbursed = money(
                                                totalGrossDisbursed
                                                                .add(
                                                                                grossAmount));

                                totalProcessingFees = money(
                                                totalProcessingFees
                                                                .add(
                                                                                processingFee));

                                totalNetDisbursed = money(
                                                totalNetDisbursed
                                                                .add(
                                                                                netDisbursement));

                                successCount++;

                                // ====================================================
                                // SUCCESS RESPONSE LINE
                                // ====================================================

                                lines.add(
                                                DisbursementLine.success(
                                                                loanId,
                                                                saved.getReferenceNumber(),
                                                                grossAmount,
                                                                processingFee,
                                                                netDisbursement,
                                                                saved.getCurrency()));

                                // ====================================================
                                // SMS
                                // ====================================================

                                try {

                                        /*
                                         * IMPORTANT:
                                         *
                                         * Use sendLoanDisbursed(), not
                                         * sendLoanApproved().
                                         *
                                         * The borrower needs to know the actual net
                                         * amount received after the 2% application fee.
                                         */
                                        smsService.sendLoanDisbursed(
                                                        saved,
                                                        normalizedMethod);

                                } catch (Exception e) {

                                        log.warn(
                                                        "SMS notification failed for loan {}",
                                                        loanId,
                                                        e);
                                }

                                // ====================================================
                                // AUDIT
                                // ====================================================

                                try {

                                        auditService.log(
                                                        saved.getOrganization(),
                                                        officer,
                                                        "BULK_DISBURSEMENT",
                                                        "LOAN",
                                                        loanId.toString(),
                                                        "Bulk disbursement via "
                                                                        + normalizedMethod
                                                                        + ". Gross="
                                                                        + grossAmount
                                                                        + ", application fee="
                                                                        + processingFee
                                                                        + ", net disbursement="
                                                                        + netDisbursement);

                                } catch (Exception e) {

                                        log.warn(
                                                        "Audit logging failed for loan {}",
                                                        loanId,
                                                        e);
                                }

                                // ====================================================
                                // WEBHOOK
                                // ====================================================

                                try {

                                        webhookService.dispatch(
                                                        saved.getOrganization(),
                                                        "LOAN_DISBURSED",
                                                        saved);

                                } catch (Exception e) {

                                        log.warn(
                                                        "Webhook dispatch failed for loan {}",
                                                        loanId,
                                                        e);
                                }

                        } catch (Exception e) {

                                log.error(
                                                "Bulk disbursement failed for loan {}: {}",
                                                loanId,
                                                e.getMessage(),
                                                e);

                                lines.add(
                                                DisbursementLine.failed(
                                                                loanId,
                                                                null,
                                                                e.getMessage() != null
                                                                                ? e.getMessage()
                                                                                : "Disbursement failed"));

                                failureCount++;
                        }
                }

                // ============================================================
                // FINAL LOG
                // ============================================================

                log.info(
                                "Bulk disbursement completed. " +
                                                "organizationId={}, totalLoans={}, " +
                                                "success={}, failures={}, grossDisbursed={}, " +
                                                "processingFees={}, netDisbursed={}, method={}",
                                orgId,
                                loanIds.size(),
                                successCount,
                                failureCount,
                                totalGrossDisbursed,
                                totalProcessingFees,
                                totalNetDisbursed,
                                normalizedMethod);

                // ============================================================
                // RESULT
                // ============================================================

                return new BulkDisbursementResult(
                                successCount,
                                failureCount,
                                totalGrossDisbursed.doubleValue(),
                                totalProcessingFees.doubleValue(),
                                totalNetDisbursed.doubleValue(),
                                normalizedMethod,
                                processedAt,
                                lines);
        }

        // ================================================================
        // MONEY
        // ================================================================

        private BigDecimal money(
                        BigDecimal value) {

                if (value == null) {

                        return ZERO;
                }

                return value.setScale(
                                2,
                                RoundingMode.HALF_UP);
        }

        // ================================================================
        // DISBURSEMENT LINE
        // ================================================================

        public record DisbursementLine(
                        Long loanId,
                        String referenceNumber,
                        boolean success,
                        Double grossAmount,
                        Double processingFee,
                        Double netDisbursedAmount,
                        String currency,
                        String errorMessage) {

                static DisbursementLine success(
                                Long id,
                                String referenceNumber,
                                BigDecimal grossAmount,
                                BigDecimal processingFee,
                                BigDecimal netDisbursedAmount,
                                String currency) {

                        return new DisbursementLine(
                                        id,
                                        referenceNumber,
                                        true,
                                        grossAmount != null
                                                        ? grossAmount.doubleValue()
                                                        : 0.0,
                                        processingFee != null
                                                        ? processingFee.doubleValue()
                                                        : 0.0,
                                        netDisbursedAmount != null
                                                        ? netDisbursedAmount.doubleValue()
                                                        : 0.0,
                                        currency,
                                        null);
                }

                static DisbursementLine failed(
                                Long id,
                                String referenceNumber,
                                String error) {

                        return new DisbursementLine(
                                        id,
                                        referenceNumber,
                                        false,
                                        null,
                                        null,
                                        null,
                                        null,
                                        error);
                }
        }

        // ================================================================
        // BULK DISBURSEMENT RESULT
        // ================================================================

        public record BulkDisbursementResult(
                        int successCount,
                        int failureCount,
                        double totalGrossAmountDisbursed,
                        double totalProcessingFees,
                        double totalNetAmountDisbursed,
                        String disbursementMethod,
                        LocalDateTime processedAt,
                        List<DisbursementLine> lines) {
        }
}