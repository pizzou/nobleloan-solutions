package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.CollectionAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CollectionActionRepository
        extends JpaRepository<CollectionAction, Long> {

    /**
     * Find all collection actions for a collection case,
     * newest action first.
     */
    List<CollectionAction> findByCollectionCase_IdOrderByCreatedAtDesc(
            Long caseId
    );

    /**
     * Tenant-safe lookup.
     *
     * Use this when CollectionCase has an organization relationship
     * that can be traversed as:
     *
     * CollectionAction -> CollectionCase -> Organization
     */
    List<CollectionAction>
    findByCollectionCase_IdAndCollectionCase_Organization_IdOrderByCreatedAtDesc(
            Long caseId,
            Long organizationId
    );
}