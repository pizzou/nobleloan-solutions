package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.ImportBatchResponse;
import com.patrick.fintech.loan_backend.mapper.ImportBatchResponseMapper;
import com.patrick.fintech.loan_backend.model.ImportBatch;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.ImportBatchRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tenant-aware read service for import batches.
 *
 * Controllers never access ImportBatchRepository directly.
 */
@Service
@RequiredArgsConstructor
public class ImportQueryService {

    private final ImportBatchRepository importBatchRepository;

    private final OrganizationRepository organizationRepository;

    private final CurrentUserUtil currentUserUtil;

    @Value("${app.import.staging-dir:${java.io.tmpdir}/loansaas-imports}")
    private String stagingDir;

    @Transactional(readOnly = true)
    public Organization getCurrentOrganization() {

        Long organizationId = getCurrentOrganizationId();

        return organizationRepository
                .findById(organizationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Current organization not found"));
    }

    public Long getCurrentOrganizationId() {

        return currentUserUtil
                .getCurrentOrganizationId();
    }

    public Long getCurrentUserId() {

        return currentUserUtil
                .getCurrentUserId();
    }

    @Transactional(readOnly = true)
    public List<ImportBatchResponse> findCurrentOrganizationBatches() {

        List<ImportBatch> batches = importBatchRepository
                .findByOrganization_IdOrderByCreatedAtDesc(
                        getCurrentOrganizationId());

        return ImportBatchResponseMapper
                .toResponses(batches);
    }

    @Transactional(readOnly = true)
    public ImportBatchResponse findCurrentOrganizationBatch(Long id) {

        ImportBatch batch = findOwnedBatch(id);

        return ImportBatchResponseMapper
                .toResponse(batch);
    }

    @Transactional(readOnly = true)
    public Resource getErrorReportForCurrentOrganization(Long id) {

        ImportBatch batch = findOwnedBatch(id);

        String reportPath = batch.getErrorReportPath();

        if (reportPath == null
                || reportPath.isBlank()) {

            return null;
        }

        Path root = Path.of(stagingDir)
                .toAbsolutePath()
                .normalize();

        Path report = Path.of(reportPath)
                .toAbsolutePath()
                .normalize();

        if (!report.startsWith(root)) {

            throw new IllegalStateException(
                    "Invalid import error report location");
        }

        if (!Files.isRegularFile(report)
                || !Files.isReadable(report)) {

            return null;
        }

        return new FileSystemResource(report);
    }

    private ImportBatch findOwnedBatch(Long id) {

        if (id == null || id <= 0) {

            throw new IllegalArgumentException(
                    "Invalid import batch ID");
        }

        Long organizationId = getCurrentOrganizationId();

        ImportBatch batch = importBatchRepository
                .findDetailedById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Import batch not found"));

        if (batch.getOrganization() == null
                || batch.getOrganization().getId() == null
                || !organizationId.equals(
                        batch.getOrganization().getId())) {

            /*
             * Deliberately return "not found" rather than "access denied".
             *
             * This prevents leaking whether another tenant owns
             * a particular batch ID.
             */
            throw new IllegalArgumentException(
                    "Import batch not found");
        }

        return batch;
    }
}