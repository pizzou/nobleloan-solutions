package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.User;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Bank-grade bulk disbursement facade.
 *
 * There is deliberately NO second implementation of the disbursement rules here.
 * Every loan is sent through LoanService.disburseLoan(), which is the authoritative
 * financial workflow for approval/document/KYC/pricing/locking/schedule/accounting
 * controls. Each loan is executed in its own transaction so one bad loan cannot
 * poison the whole batch.
 */
@Service
@Slf4j
public class BulkDisbursementService {

    private final LoanService loanService;
    private final TransactionTemplate transactionTemplate;

    public BulkDisbursementService(LoanService loanService, PlatformTransactionManager transactionManager) {
        this.loanService = loanService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setReadOnly(false);
    }

    public BulkDisbursementResult disburseAll(
            List<Long> loanIds,
            Long orgId,
            User officer,
            String method) {

        if (loanIds == null || loanIds.isEmpty()) {
            throw new IllegalArgumentException("At least one loan ID is required");
        }
        if (orgId == null) {
            throw new IllegalArgumentException("Organization ID is required");
        }
        if (officer == null || officer.getOrganization() == null
                || !orgId.equals(officer.getOrganization().getId())) {
            throw new IllegalStateException("Officer does not belong to the selected organization");
        }

        String normalizedMethod = method == null || method.isBlank()
                ? "BANK_TRANSFER"
                : method.trim().toUpperCase();

        List<DisbursementLine> lines = new ArrayList<>();
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalFees = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        int success = 0;
        int failure = 0;
        LocalDateTime processedAt = LocalDateTime.now();

        // De-duplicate request IDs before processing. A batch containing the same
        // loan twice must never result in two disbursement attempts.
        List<Long> uniqueLoanIds = loanIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        for (Long loanId : uniqueLoanIds) {
            try {
                Loan saved = transactionTemplate.execute(status ->
                        loanService.disburseLoan(loanId, officer, normalizedMethod));

                if (saved == null) {
                    throw new IllegalStateException("Disbursement returned no loan");
                }

                BigDecimal gross = money(saved.getAmountDecimal());
                BigDecimal fee = money(saved.getApplicationFeeDecimal());
                BigDecimal net = money(saved.getNetDisbursedAmountDecimal());

                totalGross = totalGross.add(gross);
                totalFees = totalFees.add(fee);
                totalNet = totalNet.add(net);
                success++;

                lines.add(DisbursementLine.success(
                        loanId,
                        saved.getReferenceNumber(),
                        gross,
                        fee,
                        net,
                        saved.getCurrency()));

            } catch (Exception ex) {
                failure++;
                String message = ex.getMessage() == null || ex.getMessage().isBlank()
                        ? "Disbursement failed"
                        : ex.getMessage();

                log.error("Bulk disbursement failed for loan {}: {}", loanId, message, ex);
                lines.add(DisbursementLine.failed(loanId, null, message));
            }
        }

        return new BulkDisbursementResult(
                success,
                failure,
                totalGross.doubleValue(),
                totalFees.doubleValue(),
                totalNet.doubleValue(),
                normalizedMethod,
                processedAt,
                lines);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public record DisbursementLine(
            Long loanId,
            String referenceNumber,
            boolean success,
            Double grossAmount,
            Double applicationFee,
            Double netDisbursedAmount,
            String currency,
            String errorMessage) {

        static DisbursementLine success(
                Long id,
                String referenceNumber,
                BigDecimal grossAmount,
                BigDecimal applicationFee,
                BigDecimal netDisbursedAmount,
                String currency) {
            return new DisbursementLine(
                    id,
                    referenceNumber,
                    true,
                    grossAmount.doubleValue(),
                    applicationFee.doubleValue(),
                    netDisbursedAmount.doubleValue(),
                    currency,
                    null);
        }

        static DisbursementLine failed(Long id, String referenceNumber, String error) {
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

    public record BulkDisbursementResult(
            int successCount,
            int failureCount,
            double totalGrossAmountDisbursed,
            double totalApplicationFees,
            double totalNetAmountDisbursed,
            String disbursementMethod,
            LocalDateTime processedAt,
            List<DisbursementLine> lines) {
    }
}
