package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.ImportBatch;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    @EntityGraph(attributePaths = { "organization", "importedBy" })
    List<ImportBatch> findByOrganization_IdOrderByCreatedAtDesc(Long organizationId);

    @EntityGraph(attributePaths = { "organization", "importedBy" })
    Optional<ImportBatch> findDetailedById(Long id);

    Optional<ImportBatch> findDetailedByIdAndOrganizationId(
            Long batchId,
            Long organizationId);

    Optional<ImportBatch> findByIdAndOrganization_Id(
            Long batchId,
            Long organizationId);
}
