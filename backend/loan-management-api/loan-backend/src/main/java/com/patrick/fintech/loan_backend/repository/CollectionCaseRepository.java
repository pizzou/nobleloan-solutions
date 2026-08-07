package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.CollectionCase;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CollectionCaseRepository
        extends JpaRepository<CollectionCase, Long> {

    /**
     * One collection case per loan.
     */
    Optional<CollectionCase> findByLoan_Id(
            Long loanId
    );

    /**
     * Organization queue.
     *
     * EntityGraph prevents lazy-loading problems when the frontend
     * needs borrower, loan and assigned agent information.
     */
    @EntityGraph(attributePaths = {
            "loan",
            "loan.borrower",
            "loan.organization",
            "assignedAgent",
            "organization"
    })
    List<CollectionCase> findByOrganization_Id(
            Long orgId
    );

    /**
     * Bucket filtering.
     */
    @EntityGraph(attributePaths = {
            "loan",
            "loan.borrower",
            "loan.organization",
            "assignedAgent",
            "organization"
    })
    List<CollectionCase> findByOrganization_IdAndBucket(
            Long orgId,
            CollectionCase.CollectionBucket bucket
    );

    /**
     * Status filtering.
     */
    @EntityGraph(attributePaths = {
            "loan",
            "loan.borrower",
            "loan.organization",
            "assignedAgent",
            "organization"
    })
    List<CollectionCase> findByOrganization_IdAndStatus(
            Long orgId,
            CollectionCase.CollectionStatus status
    );

    /**
     * Agent filtering.
     */
    @EntityGraph(attributePaths = {
            "loan",
            "loan.borrower",
            "loan.organization",
            "assignedAgent",
            "organization"
    })
    List<CollectionCase> findByOrganization_IdAndAssignedAgent_Id(
            Long orgId,
            Long agentId
    );

    /**
     * Active/non-excluded cases.
     */
    List<CollectionCase>
    findByOrganization_IdAndStatusNotIn(
            Long orgId,
            List<CollectionCase.CollectionStatus> statuses
    );

    /**
     * Optimized queue query.
     *
     * Filtering happens in PostgreSQL instead of loading every
     * organization's collection case and filtering in Java.
     */
    @EntityGraph(attributePaths = {
            "loan",
            "loan.borrower",
            "loan.organization",
            "assignedAgent",
            "organization"
    })
    @Query("""
        SELECT c
        FROM CollectionCase c
        WHERE c.organization.id = :orgId
          AND (:bucket IS NULL OR c.bucket = :bucket)
          AND (:status IS NULL OR c.status = :status)
          AND (
                :agentId IS NULL
                OR c.assignedAgent.id = :agentId
              )
        ORDER BY
            c.daysPastDue DESC,
            c.overdueAmount DESC,
            c.id DESC
        """)
    List<CollectionCase> findQueue(
            @Param("orgId") Long orgId,
            @Param("bucket") CollectionCase.CollectionBucket bucket,
            @Param("status") CollectionCase.CollectionStatus status,
            @Param("agentId") Long agentId
    );
}