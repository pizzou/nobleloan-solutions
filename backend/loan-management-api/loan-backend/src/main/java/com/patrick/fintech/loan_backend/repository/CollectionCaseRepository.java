package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.CollectionCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

@Repository
public interface CollectionCaseRepository extends JpaRepository<CollectionCase, Long> {
    Optional<CollectionCase> findByLoan_Id(Long loanId);
    @EntityGraph(attributePaths = {"loan", "loan.borrower", "assignedAgent"})
    @Query("""
        SELECT c FROM CollectionCase c
        WHERE c.organization.id = :orgId
          AND (:bucket IS NULL OR c.bucket = :bucket)
          AND (:status IS NULL OR c.status = :status)
          AND (:agentId IS NULL OR c.assignedAgent.id = :agentId)
        ORDER BY c.daysPastDue DESC, c.id DESC
        """)
    List<CollectionCase> findQueue(
        @Param("orgId") Long orgId,
        @Param("bucket") CollectionCase.CollectionBucket bucket,
        @Param("status") CollectionCase.CollectionStatus status,
        @Param("agentId") Long agentId);
    List<CollectionCase> findByOrganization_IdAndBucket(Long orgId, CollectionCase.CollectionBucket bucket);
    List<CollectionCase> findByOrganization_IdAndStatus(Long orgId, CollectionCase.CollectionStatus status);
    List<CollectionCase> findByAssignedAgent_Id(Long agentId);

    Long countByOrganization_IdAndStatus(Long orgId, CollectionCase.CollectionStatus status);
    @Query("""
        SELECT c FROM CollectionCase c
        WHERE c.organization.id = :orgId
          AND c.status NOT IN :statuses
        """)
    List<CollectionCase> findOpenForOrganization(
        @Param("orgId") Long orgId,
        @Param("statuses") List<CollectionCase.CollectionStatus> statuses);

    @Query("""
        SELECT c.bucket, c.status, COUNT(c), COALESCE(SUM(c.overdueAmount), 0)
        FROM CollectionCase c
        WHERE c.organization.id = :orgId
        GROUP BY c.bucket, c.status
        """)
    List<Object[]> getStatsByBucket(@Param("orgId") Long orgId);
}
