package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.AuditLog;
import com.patrick.fintech.loan_backend.model.Organization;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.List;
import java.util.Optional;


public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // ============================================================
    // ORGANIZATION AUDIT LOGS
    // ============================================================
    //
    // IMPORTANT:
    // Do NOT use JOIN FETCH here with Page/ Pageable.
    //
    // Pagination should first retrieve only the required audit rows.
    // The user relationship can remain lazy.
    //
    // Recommended database index:
    //
    // (organization_id, timestamp DESC)
    //
    // ============================================================

    Page<AuditLog> findByOrganizationOrderByTimestampDesc(
            Organization organization,
            Pageable pageable
    );


    // ============================================================
    // CONTROLLER COMPATIBILITY ALIAS
    // ============================================================

    default Page<AuditLog> findByInstitutionOrderByTimestampDesc(
            Organization org,
            Pageable pageable
    ) {

        return findByOrganizationOrderByTimestampDesc(
                org,
                pageable
        );
    }


    // ============================================================
    // MOST RECENT AUDIT LOG
    // ============================================================

    Optional<AuditLog> findTopByOrderByIdDesc();


    // ============================================================
    // WARNING:
    //
    // This loads the ENTIRE audit table into memory.
    //
    // It should only be used where the table is guaranteed to
    // remain very small.
    //
    // For production reporting/exporting, use a paginated query.
    // ============================================================

    List<AuditLog> findAllByOrderByIdAsc();


    // ============================================================
    // PAGINATED GLOBAL AUDIT LOGS
    // ============================================================
    //
    // Use this instead of findAllByOrderByIdAsc() whenever possible.
    //
    // Example:
    //
    // repository.findAllByOrderByIdDesc(pageable);
    //
    // ============================================================

    Page<AuditLog> findAllByOrderByIdDesc(
            Pageable pageable
    );


    // ============================================================
    // PAGINATED ORGANIZATION AUDIT LOGS BY ID
    // ============================================================

    Page<AuditLog> findByOrganizationOrderByIdDesc(
            Organization organization,
            Pageable pageable
    );


    // ============================================================
    // COUNT ORGANIZATION AUDIT LOGS
    // ============================================================

    long countByOrganization(
            Organization organization
    );


    // ============================================================
    // COUNT BY ORGANIZATION AND TIME PERIOD
    // ============================================================

    @Query("""
            SELECT COUNT(a)
            FROM AuditLog a
            WHERE a.organization = :org
              AND a.timestamp >= :from
              AND a.timestamp < :to
            """)
    long countByOrganizationAndTimestampBetween(
            @Param("org") Organization organization,
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to
    );
}