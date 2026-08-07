package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.CollectionAction;
import com.patrick.fintech.loan_backend.model.CollectionCase;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CollectionActionRepository
        extends JpaRepository<CollectionAction, Long> {

    /**
     * Action history for one collection case.
     */
    List<CollectionAction> findByCollectionCase_IdOrderByCreatedAtDesc(
            Long caseId
    );

    /**
     * Tenant-safe action history.
     */
    List<CollectionAction>
    findByCollectionCase_IdAndCollectionCase_Organization_IdOrderByCreatedAtDesc(
            Long caseId,
            Long organizationId
    );

    /**
     * Count actions by type for one organization.
     *
     * This replaces the old N+1 approach in CollectionsService.
     */
    @Query("""
        SELECT a.actionType, COUNT(a)
        FROM CollectionAction a
        WHERE a.collectionCase.organization.id = :orgId
        GROUP BY a.actionType
        """)
    List<Object[]> countActionsByType(
            @Param("orgId") Long organizationId
    );

    /**
     * Total actions for organization.
     */
    long countByCollectionCase_Organization_Id(
            Long organizationId
    );

    /**
     * Count one action type for organization.
     */
    long countByCollectionCase_Organization_IdAndActionType(
            Long organizationId,
            CollectionAction.ActionType actionType
    );
}