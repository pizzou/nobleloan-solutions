package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.ImportBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

        @EntityGraph(attributePaths = { "organization", "importedBy" })
        List<ImportBatch> findByOrganization_IdOrderByCreatedAtDesc(Long organizationId);

        @EntityGraph(attributePaths = { "organization", "importedBy" })
        Optional<ImportBatch> findDetailedById(Long id);

        /**
         * Serializes state transitions at database level so two workers cannot
         * process the same queued import batch concurrently.
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                        select b
                        from ImportBatch b
                        join fetch b.organization
                        left join fetch b.importedBy
                        where b.id = :id
                        """)
        Optional<ImportBatch> findForUpdate(@Param("id") Long id);
}
