package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.ImportBatch;
import com.patrick.fintech.loan_backend.repository.ImportBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
@RequiredArgsConstructor
public class ImportBatchStateService {

    private final ImportBatchRepository repo;

    /**
     * Atomically claims a QUEUED batch for application.
     * Returns false when another worker has already claimed or completed it.
     */
    @Transactional
    public boolean claimForProcessing(Long batchId) {
        ImportBatch batch = repo.findForUpdate(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Import batch not found: " + batchId));

        if (!"QUEUED".equalsIgnoreCase(batch.getStatus())) {
            return false;
        }

        batch.setStatus("PROCESSING");
        batch.setProcessedRows(0);
        batch.setProgressPercent(0);
        batch.setErrorMessage(null);
        repo.save(batch);
        return true;
    }

    @Transactional
    public void setTotals(Long batchId, int totalRows) {
        ImportBatch batch = repo.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Import batch not found: " + batchId));
        batch.setTotalRows(totalRows);
        batch.setProcessedRows(0);
        batch.setProgressPercent(0);
        repo.save(batch);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void progress(Long batchId, int processed, int total, int success, int failure) {
        ImportBatch batch = repo.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Import batch not found: " + batchId));

        int safeTotal = Math.max(total, 0);
        int safeProcessed = Math.max(processed, 0);
        int percent = safeTotal == 0
                ? 0
                : Math.min(100, (int) Math.round((safeProcessed * 100.0d) / safeTotal));

        batch.setTotalRows(safeTotal);
        batch.setProcessedRows(Math.min(safeProcessed, safeTotal));
        batch.setProgressPercent(percent);
        batch.setSuccessCount(Math.max(success, 0));
        batch.setFailureCount(Math.max(failure, 0));
        repo.save(batch);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setErrorReportPath(Long batchId, String path) {
        ImportBatch batch = repo.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Import batch not found: " + batchId));
        batch.setErrorReportPath(path);
        repo.save(batch);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long batchId, String status, int total, int processed, int success, int failure,
            String rowResults) {
        ImportBatch batch = repo.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Import batch not found: " + batchId));

        batch.setStatus(status);
        batch.setTotalRows(Math.max(total, 0));
        batch.setProcessedRows(Math.max(processed, 0));
        batch.setProgressPercent(100);
        batch.setSuccessCount(Math.max(success, 0));
        batch.setFailureCount(Math.max(failure, 0));
        batch.setRowResults(rowResults);
        batch.setErrorMessage(null);
        repo.save(batch);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long batchId, String message, int total, int processed, int success, int failure,
            String rowResults) {
        ImportBatch batch = repo.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Import batch not found: " + batchId));

        batch.setStatus("FAILED");
        batch.setTotalRows(Math.max(total, 0));
        batch.setProcessedRows(Math.max(processed, 0));
        batch.setProgressPercent(100);
        batch.setSuccessCount(Math.max(success, 0));
        batch.setFailureCount(Math.max(failure, 0));
        batch.setRowResults(rowResults);
        batch.setErrorMessage(message);
        repo.save(batch);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setStatus(Long batchId, String status) {
        ImportBatch batch = repo.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Import batch not found: " + batchId));
        batch.setStatus(status);
        repo.save(batch);
    }

}