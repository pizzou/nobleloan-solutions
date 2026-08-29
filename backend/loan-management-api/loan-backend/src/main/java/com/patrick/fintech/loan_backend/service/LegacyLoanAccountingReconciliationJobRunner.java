package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.model.FinancialReconciliationJob;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.FinancialReconciliationJobRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Background worker for the long-running legacy-loan accounting repair.
 * No HTTP request is kept open while the financial control runs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LegacyLoanAccountingReconciliationJobRunner {

    private final FinancialReconciliationJobRepository jobRepository;
    private final FinancialReconciliationJobStateService stateService;
    private final LoanRepository loanRepository;
    private final AccountingService accountingService;
    private final FinancialReconciliationService reconciliationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Async("loansaasAsyncExecutor")
    public void runAsync(Long jobId) {
        try {
            if (!stateService.claim(jobId)) {
                log.info("Financial reconciliation job {} was already claimed or completed.", jobId);
                return;
            }

            FinancialReconciliationJob job = load(jobId);
            Organization organization = job.getOrganization();
            User requestedBy = job.getRequestedBy();
            Long organizationId = organization.getId();

            List<Loan> importedLoans = loanRepository.findHistoricalImportedLoans(organizationId);
            if (importedLoans == null) {
                importedLoans = List.of();
            }

            stateService.beforeResult(
                    jobId,
                    false,
                    java.math.BigDecimal.ZERO,
                    importedLoans.size());

            stateService.phase(
                    jobId,
                    FinancialReconciliationJob.STATUS_PROCESSING,
                    FinancialReconciliationJob.PHASE_BEFORE_RECONCILIATION);

            FinancialReconciliationService.ReconciliationReport before =
                    reconciliationService.reconcile(organizationId, null, LocalDate.now());

            stateService.beforeResult(
                    jobId,
                    before.balanced(),
                    before.maximumDifference(),
                    importedLoans.size());

            stateService.phase(
                    jobId,
                    FinancialReconciliationJob.STATUS_PROCESSING,
                    FinancialReconciliationJob.PHASE_OPENING_JOURNALS);

            int repaired = accountingService.reconcileLegacyLoanOpeningBalances(importedLoans);
            stateService.adjustmentsCreated(jobId, repaired);
            stateService.heartbeat(jobId);

            stateService.phase(
                    jobId,
                    FinancialReconciliationJob.STATUS_VERIFYING,
                    FinancialReconciliationJob.PHASE_FINAL_RECONCILIATION);

            FinancialReconciliationService.ReconciliationReport after =
                    reconciliationService.reconcile(organizationId, null, LocalDate.now());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("processed", importedLoans.size());
            result.put("created", repaired);
            result.put("beforeReconciliation", before);
            result.put("afterReconciliation", after);
            result.put("balanced", after.balanced());
            result.put("maximumDifference", after.maximumDifference());

            String resultJson = objectMapper.writeValueAsString(result);

            stateService.complete(
                    jobId,
                    after.balanced(),
                    after.maximumDifference(),
                    resultJson);

            auditService.log(
                    organization,
                    requestedBy,
                    "LEGACY_LOAN_ACCOUNTING_RECONCILIATION_COMPLETED",
                    "ACCOUNTING",
                    String.valueOf(jobId),
                    "Completed imported-loan accounting reconciliation. processed="
                            + importedLoans.size()
                            + ", adjustments=" + repaired
                            + ", balanced=" + after.balanced()
                            + ", maximumDifference=" + after.maximumDifference(),
                    null,
                    null,
                    "Accounting");

            log.info(
                    "Financial reconciliation job completed. jobId={}, organizationId={}, processed={}, adjustments={}, balanced={}, maximumDifference={}",
                    jobId,
                    organizationId,
                    importedLoans.size(),
                    repaired,
                    after.balanced(),
                    after.maximumDifference());

        } catch (Exception e) {
            log.error("Financial reconciliation job failed. jobId={}", jobId, e);
            try {
                stateService.fail(jobId, e.getMessage());
            } catch (Exception stateFailure) {
                log.error("Unable to persist failed reconciliation job state. jobId={}", jobId, stateFailure);
            }
        }
    }

    private FinancialReconciliationJob load(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException(
                        "Financial reconciliation job not found: " + jobId));
    }
}
