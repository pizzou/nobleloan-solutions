package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportBatchRepository
        extends JpaRepository<ImportBatch, Long> {

    // ============================================================
    // IMPORT BATCHES FOR ORGANIZATION
    // ============================================================

    List<ImportBatch> findByOrganization_IdOrderByCreatedAtDesc(
            Long organizationId
    );
}