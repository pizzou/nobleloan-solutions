package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.RegulatoryApiClient;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegulatoryApiClientRepository
        extends JpaRepository<RegulatoryApiClient, Long> {

    // ============================================================
    // API KEY LOOKUP
    // ============================================================

    Optional<RegulatoryApiClient> findByKeyPrefix(
            String keyPrefix
    );


    // ============================================================
    // ORGANIZATION CLIENTS
    // ============================================================

    /*
     * Existing method preserved for compatibility.
     */
    List<RegulatoryApiClient>
    findByOrganization_IdOrderByCreatedAtDesc(
            Long organizationId
    );


    /*
     * Recommended for admin/API-client management screens.
     *
     * Prevents loading every API client belonging to an
     * organization when the table becomes large.
     */
    Page<RegulatoryApiClient>
    findByOrganization_IdOrderByCreatedAtDesc(
            Long organizationId,
            Pageable pageable
    );
}