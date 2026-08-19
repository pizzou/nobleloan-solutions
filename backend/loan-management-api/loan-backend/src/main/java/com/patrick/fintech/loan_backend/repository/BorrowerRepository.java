package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Organization;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BorrowerRepository extends JpaRepository<Borrower, Long> {

    // ============================================================
    // BASIC LOOKUPS
    // ============================================================

    @EntityGraph(attributePaths = { "organization" })
    @Override
    Optional<Borrower> findById(Long id);

    Optional<Borrower> findByEmailAndOrganization(
            String email,
            Organization organization);

    boolean existsByEmailAndOrganization(
            String email,
            Organization organization);

    // ============================================================
    // ORGANIZATION COUNTS
    // ============================================================

    long countByOrganization(
            Organization organization);

    long countByOrganization_Id(
            Long organizationId);

    /**
     * Dashboard gender totals are returned as ONE aggregate row.
     *
     * Do not use GROUP BY here. Besides being simpler for the dashboard,
     * this prevents Hibernate/PostgreSQL from producing an invalid grouped
     * projection when entity-level joins/filters are added.
     *
     * Result order: MALE, FEMALE, OTHER/UNKNOWN.
     */
    @Query("""
            SELECT
                COALESCE(SUM(CASE WHEN UPPER(COALESCE(b.gender, '')) IN ('MALE', 'M') THEN 1 ELSE 0 END), 0),
                COALESCE(SUM(CASE WHEN UPPER(COALESCE(b.gender, '')) IN ('FEMALE', 'F') THEN 1 ELSE 0 END), 0),
                COALESCE(SUM(CASE WHEN UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'M', 'FEMALE', 'F') THEN 1 ELSE 0 END), 0)
            FROM Borrower b
            WHERE b.organization.id = :organizationId
            """)
    Object[] getDashboardGenderBreakdown(
            @Param("organizationId") Long organizationId);

    // ============================================================
    // PAGINATED BORROWERS
    // ============================================================

    @EntityGraph(attributePaths = { "organization" })
    Page<Borrower> findByOrganization(
            Organization organization,
            Pageable pageable);

    // ============================================================
    // BORROWER SEARCH
    // ============================================================

    @EntityGraph(attributePaths = { "organization" })
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
            @Param("org") Organization org,
            @Param("q") String query,
            Pageable pageable);

    @EntityGraph(attributePaths = { "organization" })
    List<Borrower> findByOrganization_Id(
            Long orgId);

    Optional<Borrower> findByNationalIdHashAndOrganization_Id(
            String nationalIdHash,
            Long orgId);

    Optional<Borrower> findByPhoneHashAndOrganization_Id(
            String phoneHash,
            Long orgId);

    Optional<Borrower> findByEmailAndOrganization_Id(
            String email,
            Long orgId);
}