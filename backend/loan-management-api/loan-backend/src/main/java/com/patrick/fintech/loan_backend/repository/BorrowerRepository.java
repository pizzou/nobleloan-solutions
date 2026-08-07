package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Organization;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.List;
import java.util.Optional;


public interface BorrowerRepository
        extends JpaRepository<Borrower, Long> {

    // ============================================================
    // FIND BY EMAIL + ORGANIZATION
    // ============================================================

    Optional<Borrower> findByEmailAndOrganization(
            String email,
            Organization organization
    );


    // ============================================================
    // CHECK EMAIL + ORGANIZATION
    // ============================================================

    boolean existsByEmailAndOrganization(
            String email,
            Organization organization
    );


    // ============================================================
    // COUNT BORROWERS
    // ============================================================

    long countByOrganization(
            Organization organization
    );


    // ============================================================
    // PAGINATED BORROWER LIST
    // ============================================================
    //
    // This should be the preferred method for API endpoints.
    //
    // Do not return every borrower when the organization may have
    // thousands of borrowers.
    //
    Page<Borrower> findByOrganization(
            Organization organization,
            Pageable pageable
    );


    // ============================================================
    // SEARCH BORROWERS
    // ============================================================
    //
    // Searches:
    //   - first name
    //   - last name
    //   - email
    //
    // National ID and phone are intentionally NOT searched here
    // because those values are encrypted.
    //
    // They are searched using their HMAC/hash fields below.
    //
    // IMPORTANT:
    // The "%query%" pattern can become expensive on large tables.
    // PostgreSQL trigram indexes are recommended for high-volume
    // borrower search.
    //
    // ============================================================

    @Query("""
            SELECT b
            FROM Borrower b
            WHERE b.organization = :org
              AND (
                    LOWER(b.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
                 OR LOWER(b.lastName) LIKE LOWER(CONCAT('%', :q, '%'))
                 OR LOWER(b.email) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """)
    Page<Borrower> search(
            @Param("org") Organization organization,
            @Param("q") String query,
            Pageable pageable
    );


    // ============================================================
    // ALL BORROWERS FOR ORGANIZATION
    // ============================================================
    //
    // Kept for compatibility with existing services.
    //
    // For large organizations, prefer the paginated method above.
    //
    List<Borrower> findByOrganization_Id(
            Long orgId
    );


    // ============================================================
    // PAGINATED BORROWERS BY ORGANIZATION ID
    // ============================================================
    //
    // Prefer this when the service already has organizationId
    // rather than a fully loaded Organization entity.
    //
    Page<Borrower> findByOrganization_Id(
            Long orgId,
            Pageable pageable
    );


    // ============================================================
    // NATIONAL ID HASH
    // ============================================================
    //
    // National ID is encrypted at rest.
    //
    // The application should calculate the HMAC/blind index and
    // search using that value.
    //
    Optional<Borrower> findByNationalIdHashAndOrganization_Id(
            String nationalIdHash,
            Long orgId
    );


    // ============================================================
    // PHONE HASH
    // ============================================================

    Optional<Borrower> findByPhoneHashAndOrganization_Id(
            String phoneHash,
            Long orgId
    );


    // ============================================================
    // EMAIL + ORGANIZATION ID
    // ============================================================

    Optional<Borrower> findByEmailAndOrganization_Id(
            String email,
            Long orgId
    );


    // ============================================================
    // EXISTENCE CHECK — EMAIL + ORGANIZATION ID
    // ============================================================
    //
    // Useful when the service only needs to know whether the
    // borrower exists and does not need the complete entity.
    //
    boolean existsByEmailAndOrganization_Id(
            String email,
            Long orgId
    );


    // ============================================================
    // COUNT BY ORGANIZATION ID
    // ============================================================

    long countByOrganization_Id(
            Long orgId
    );
}