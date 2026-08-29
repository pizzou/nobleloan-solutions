package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.dto.FinancialReconciliationJobResponse;
import com.patrick.fintech.loan_backend.model.FinancialReconciliationJob;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.FinancialReconciliationJobRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FinancialReconciliationJobService {

    private final FinancialReconciliationJobRepository repository;
    private final FinancialReconciliationJobStateService stateService;
    private final ObjectMapper objectMapper;

    @Transactional
    public FinancialReconciliationJob create(Organization organization, User requestedBy) {
        if (organization == null || organization.getId() == null) {
            throw new IllegalArgumentException("Organization is required.");
        }

        if (repository.existsByOrganization_IdAndStatusIn(
                organization.getId(),
                List.of(
                        FinancialReconciliationJob.STATUS_QUEUED,
                        FinancialReconciliationJob.STATUS_PROCESSING,
                        FinancialReconciliationJob.STATUS_VERIFYING))) {
            throw new IllegalStateException(
                    "A financial reconciliation is already queued or processing for this organization.");
        }

        FinancialReconciliationJob job = FinancialReconciliationJob.builder()
                .organization(organization)
                .requestedBy(requestedBy)
                .status(FinancialReconciliationJob.STATUS_QUEUED)
                .phase(FinancialReconciliationJob.PHASE_QUEUED)
                .processedLoans(0)
                .journalAdjustmentsCreated(0)
                .heartbeatAt(LocalDateTime.now())
                .build();

        try {
            return repository.saveAndFlush(job);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(
                    "Another financial reconciliation has already been queued for this organization.", e);
        }
    }

    public void markFailed(Long jobId, String message) {
        stateService.fail(jobId, message);
    }

    @Transactional(readOnly = true)
    public FinancialReconciliationJobResponse get(Long organizationId, Long jobId) {
        if (organizationId == null || organizationId <= 0) {
            throw new IllegalArgumentException("Organization ID must be positive.");
        }
        if (jobId == null || jobId <= 0) {
            throw new IllegalArgumentException("Reconciliation job ID must be positive.");
        }

        FinancialReconciliationJob job = repository.findByIdAndOrganizationId(jobId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException("Reconciliation job not found: " + jobId));

        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public List<FinancialReconciliationJobResponse> list(Long organizationId) {
        return repository.findByOrganization_IdOrderByCreatedAtDesc(organizationId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public FinancialReconciliationJobResponse toResponse(FinancialReconciliationJob job) {
        JsonNode result = null;
        if (job.getResultJson() != null && !job.getResultJson().isBlank()) {
            try {
                result = objectMapper.readTree(job.getResultJson());
            } catch (Exception ignored) {
                result = null;
            }
        }

        return new FinancialReconciliationJobResponse(
                job.getId(),
                job.getStatus(),
                job.getPhase(),
                job.getProcessedLoans() == null ? 0 : job.getProcessedLoans(),
                job.getJournalAdjustmentsCreated() == null ? 0 : job.getJournalAdjustmentsCreated(),
                job.getBeforeBalanced(),
                job.getAfterBalanced(),
                job.getBeforeMaximumDifference(),
                job.getAfterMaximumDifference(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getHeartbeatAt(),
                job.getErrorMessage(),
                result);
    }
}
