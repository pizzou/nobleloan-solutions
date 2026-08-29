package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.FinancialReconciliationJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FinancialReconciliationJobRepository extends JpaRepository<FinancialReconciliationJob, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select j
            from FinancialReconciliationJob j
            join fetch j.organization
            left join fetch j.requestedBy
            where j.id = :jobId
            """)
    Optional<FinancialReconciliationJob> findForUpdate(@Param("jobId") Long jobId);

    @Query("""
            select j
            from FinancialReconciliationJob j
            where j.id = :jobId
              and j.organization.id = :organizationId
            """)
    Optional<FinancialReconciliationJob> findByIdAndOrganizationId(
            @Param("jobId") Long jobId,
            @Param("organizationId") Long organizationId);

    boolean existsByOrganization_IdAndStatusIn(
            Long organizationId,
            Collection<String> statuses);

    List<FinancialReconciliationJob> findByStatusInAndHeartbeatAtBefore(
            Collection<String> statuses,
            LocalDateTime cutoff);

    List<FinancialReconciliationJob> findByOrganization_IdOrderByCreatedAtDesc(
            Long organizationId);
}
