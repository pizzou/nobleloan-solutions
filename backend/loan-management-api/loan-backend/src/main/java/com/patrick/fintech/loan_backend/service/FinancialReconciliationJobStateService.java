package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.FinancialReconciliationJob;
import com.patrick.fintech.loan_backend.repository.FinancialReconciliationJobRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FinancialReconciliationJobStateService {

    private final FinancialReconciliationJobRepository repository;

    @Transactional
    public boolean claim(Long jobId) {
        FinancialReconciliationJob job = repository.findForUpdate(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Reconciliation job not found: " + jobId));

        if (!FinancialReconciliationJob.STATUS_QUEUED.equals(job.getStatus())) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        job.setStatus(FinancialReconciliationJob.STATUS_PROCESSING);
        job.setPhase(FinancialReconciliationJob.PHASE_BEFORE_RECONCILIATION);
        job.setStartedAt(now);
        job.setHeartbeatAt(now);
        job.setErrorMessage(null);
        repository.save(job);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void phase(Long jobId, String status, String phase) {
        FinancialReconciliationJob job = require(jobId);
        job.setStatus(status);
        job.setPhase(phase);
        job.setHeartbeatAt(LocalDateTime.now());
        repository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void beforeResult(
            Long jobId,
            boolean balanced,
            BigDecimal maximumDifference,
            int processedLoans) {

        FinancialReconciliationJob job = require(jobId);
        job.setBeforeBalanced(balanced);
        job.setBeforeMaximumDifference(normalize(maximumDifference));
        job.setProcessedLoans(Math.max(processedLoans, 0));
        job.setHeartbeatAt(LocalDateTime.now());
        repository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void adjustmentsCreated(Long jobId, int created) {
        FinancialReconciliationJob job = require(jobId);
        job.setJournalAdjustmentsCreated(Math.max(created, 0));
        job.setHeartbeatAt(LocalDateTime.now());
        repository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(
            Long jobId,
            boolean balanced,
            BigDecimal maximumDifference,
            String resultJson) {

        FinancialReconciliationJob job = require(jobId);
        LocalDateTime now = LocalDateTime.now();
        job.setStatus(FinancialReconciliationJob.STATUS_COMPLETED);
        job.setPhase(FinancialReconciliationJob.PHASE_COMPLETED);
        job.setAfterBalanced(balanced);
        job.setAfterMaximumDifference(normalize(maximumDifference));
        job.setResultJson(resultJson);
        job.setCompletedAt(now);
        job.setHeartbeatAt(now);
        job.setErrorMessage(null);
        repository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long jobId, String message) {
        FinancialReconciliationJob job = require(jobId);
        LocalDateTime now = LocalDateTime.now();
        job.setStatus(FinancialReconciliationJob.STATUS_FAILED);
        job.setPhase(FinancialReconciliationJob.PHASE_FAILED);
        job.setErrorMessage(safeMessage(message));
        job.setCompletedAt(now);
        job.setHeartbeatAt(now);
        repository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void heartbeat(Long jobId) {
        FinancialReconciliationJob job = require(jobId);
        job.setHeartbeatAt(LocalDateTime.now());
        repository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverStaleJobs(LocalDateTime cutoff) {
        var statuses = java.util.List.of(
                FinancialReconciliationJob.STATUS_QUEUED,
                FinancialReconciliationJob.STATUS_PROCESSING,
                FinancialReconciliationJob.STATUS_VERIFYING);

        var jobs = repository.findByStatusInAndHeartbeatAtBefore(statuses, cutoff);
        int recovered = 0;
        for (FinancialReconciliationJob job : jobs) {
            job.setStatus(FinancialReconciliationJob.STATUS_FAILED);
            job.setPhase(FinancialReconciliationJob.PHASE_FAILED);
            job.setErrorMessage("Reconciliation job became stale and was closed automatically. Review the accounting state before rerunning.");
            job.setCompletedAt(LocalDateTime.now());
            job.setHeartbeatAt(LocalDateTime.now());
            recovered++;
        }
        repository.saveAll(jobs);
        return recovered;
    }

    private FinancialReconciliationJob require(Long jobId) {
        return repository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Reconciliation job not found: " + jobId));
    }

    private BigDecimal normalize(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(2)
                : value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String safeMessage(String value) {
        if (value == null || value.isBlank()) {
            return "Financial reconciliation failed.";
        }
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }
}
