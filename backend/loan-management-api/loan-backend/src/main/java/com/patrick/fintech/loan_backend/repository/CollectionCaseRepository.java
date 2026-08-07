package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.CollectionCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CollectionCaseRepository
        extends JpaRepository<CollectionCase, Long> {

    /**
     * Find the collection case associated with a loan.
     *
     * This assumes one collection case per loan.
     */
    Optional<CollectionCase> findByLoan_Id(
            Long loanId
    );

    /**
     * Find all collection cases belonging to an organization.
     */
    List<CollectionCase> findByOrganization_Id(
            Long orgId
    );

    /**
     * Find collection cases by organization and collection bucket.
     */
    List<CollectionCase> findByOrganization_IdAndBucket(
            Long orgId,
            CollectionCase.CollectionBucket bucket
    );

    /**
     * Find collection cases by organization and status.
     */
    List<CollectionCase> findByOrganization_IdAndStatus(
            Long orgId,
            CollectionCase.CollectionStatus status
    );

    /**
     * Find cases assigned to a specific agent.
     */
    List<CollectionCase> findByAssignedAgent_Id(
            Long agentId
    );

    /**
     * Find active/non-excluded collection cases.
     */
    List<CollectionCase> findByOrganization_IdAndStatusNotIn(
            Long orgId,
            List<CollectionCase.CollectionStatus> statuses
    );
}