package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {

        // ============================================================
        // GENERAL
        // ============================================================

        /**
         * Find loan by ID and eagerly load commonly required
         * relationships.
         */
        @EntityGraph(attributePaths = {
                        "borrower",
                        "organization"
        })
        @Override
        Optional<Loan> findById(Long id);

        /**
         * Find a loan by ID using a pessimistic write lock.
         *
         * This is important for payment concurrency so that two
         * simultaneous payment transactions cannot modify the same
         * loan balance at the same time.
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @EntityGraph(attributePaths = {
                        "borrower",
                        "organization",
                        "loanOfficer"
        })
        @Query("""
                        SELECT l
                        FROM Loan l
                        WHERE l.id = :id
                        """)
        Optional<Loan> findByIdForUpdate(
                        @Param("id") Long id);

        /**
         * Find loan by public reference number.
         */
        Optional<Loan> findByReferenceNumber(
                        String referenceNumber);

        /**
         * Find a loan using reference number and hashed borrower phone.
         */
        @EntityGraph(attributePaths = {
                        "borrower",
                        "organization"
        })
        Optional<Loan> findByReferenceNumberAndBorrower_PhoneHash(
                        String referenceNumber,
                        String phoneHash);

        /**
         * Public borrower dashboard lookup.
         */
        @EntityGraph(attributePaths = {
                        "borrower",
                        "organization",
                        "loanOfficer"
        })
        @Query("""
                        SELECT l
                        FROM Loan l
                        WHERE l.referenceNumber = :referenceNumber
                          AND l.borrower.phoneHash = :phoneHash
                        """)
        Optional<Loan> findPublicDashboardLoan(
                        @Param("referenceNumber") String referenceNumber,
                        @Param("phoneHash") String phoneHash);

        /**
         * Find all loans belonging to an organization.
         */
        @EntityGraph(attributePaths = {
                        "borrower",
                        "organization"
        })
        List<Loan> findByOrganization_Id(
                        Long organizationId);

        List<Loan> findByOrganization_IdAndImportedTrue(
                        Long organizationId);

        /**
         * Find all loans belonging to a borrower within an organization.
         */
        @EntityGraph(attributePaths = {
                        "borrower",
                        "organization"
        })
        List<Loan> findByBorrowerIdAndOrganizationId(
                        Long borrowerId,
                        Long organizationId);

        /**
         * Find loans whose status is one of the supplied statuses.
         */
        List<Loan> findByStatusIn(
                        List<LoanStatus> statuses);

        /**
         * Find all loans by borrower's hashed phone number.
         */
        List<Loan> findByBorrower_PhoneHash(
                        String phoneHash);

        // ============================================================
        // COLLECTION / OVERDUE
        // ============================================================

        /**
         * Find loans whose status is one of the supplied statuses OR
         * whose number of overdue days is greater than or equal to
         * the supplied value.
         *
         * This method is required by CollectionService.
         *
         * Example:
         *
         * findByStatusInOrDaysOverdueGreaterThanEqual(
         * List.of(
         * LoanStatus.ACTIVE,
         * LoanStatus.OVERDUE
         * ),
         * 1
         * );
         *
         * IMPORTANT:
         * The Loan entity must contain a field named:
         *
         * daysOverdue
         *
         * with a numeric type compatible with the int parameter.
         */
        @EntityGraph(attributePaths = {
                        "borrower",
                        "organization",
                        "loanOfficer"
        })
        List<Loan> findByStatusInOrDaysOverdueGreaterThanEqual(
                        List<LoanStatus> statuses,
                        int daysOverdue);

        /**
         * Find loans by organization and status.
         */
        @EntityGraph(attributePaths = {
                        "borrower",
                        "organization",
                        "loanOfficer"
        })
        List<Loan> findByOrganization_IdAndStatus(
                        Long organizationId,
                        LoanStatus status);

        /**
         * Find loans by organization and any of the supplied statuses.
         */
        @EntityGraph(attributePaths = {
                        "borrower",
                        "organization",
                        "loanOfficer"
        })
        List<Loan> findByOrganization_IdAndStatusIn(
                        Long organizationId,
                        List<LoanStatus> statuses);

        /**
         * Find organization loans whose status matches or whose overdue
         * days reach the specified threshold.
         *
         * This is the organization-scoped version and is preferable for
         * tenant-aware collection processing.
         */
        @EntityGraph(attributePaths = {
                        "borrower",
                        "organization",
                        "loanOfficer"
        })
        @Query("""
                        SELECT l
                        FROM Loan l
                        WHERE l.organization.id = :organizationId
                          AND (
                              l.status IN :statuses
                              OR l.daysOverdue >= :daysOverdue
                          )
                        """)
        List<Loan> findByOrganizationIdAndStatusInOrDaysOverdueGreaterThanEqual(
                        @Param("organizationId") Long organizationId,
                        @Param("statuses") List<LoanStatus> statuses,
                        @Param("daysOverdue") int daysOverdue);

        // ============================================================
        // COUNT
        // ============================================================

        /**
         * Count all loans belonging to an organization.
         */
        long countByOrganization(
                        Organization organization);

        /**
         * Count loans by organization ID.
         */
        long countByOrganization_Id(
                        Long organizationId);

        /**
         * Count loans by organization and status.
         */
        long countByOrganizationAndStatus(
                        Organization organization,
                        LoanStatus status);

        /**
         * Count loans by organization ID and status.
         */
        long countByOrganization_IdAndStatus(
                        Long organizationId,
                        LoanStatus status);

        /**
         * Single aggregate query for the dashboard loan KPIs.
         *
         * Result order:
         * 0 total loans
         * 1 pending loans
         * 2 active loans
         * 3 completed/paid loans
         * 4 defaulted loans
         * 5 total disbursed amount
         * 6 outstanding balance
         * 7 at-risk outstanding balance
         */
        @Query("""
                        SELECT
                            COUNT(l),
                            SUM(CASE WHEN l.status = 'PENDING' THEN 1 ELSE 0 END),
                            SUM(CASE WHEN l.status = 'ACTIVE' THEN 1 ELSE 0 END),
                            SUM(CASE WHEN l.status = 'PAID' THEN 1 ELSE 0 END),
                            SUM(CASE WHEN l.status = 'DEFAULTED' THEN 1 ELSE 0 END),
                            COALESCE(SUM(
                                CASE
                                    WHEN l.status IN (
                                        'DISBURSED',
                                        'ACTIVE',
                                        'OVERDUE',
                                        'PAID',
                                        'CLOSED',
                                        'DEFAULTED',
                                        'RESTRUCTURED',
                                        'WRITTEN_OFF'
                                    )
                                    THEN l.amount
                                    ELSE 0
                                END
                            ), 0),
                            COALESCE(SUM(
                                CASE
                                    WHEN l.status IN (
                                        'DISBURSED',
                                        'ACTIVE',
                                        'OVERDUE',
                                        'RESTRUCTURED'
                                    )
                                    THEN l.outstandingBalance
                                    ELSE 0
                                END
                            ), 0),
                            COALESCE(SUM(
                                CASE
                                    WHEN l.status IN (
                                        'OVERDUE',
                                        'DEFAULTED',
                                        'RESTRUCTURED'
                                    )
                                    THEN l.outstandingBalance
                                    ELSE 0
                                END
                            ), 0)
                        FROM Loan l
                        WHERE l.organization.id = :organizationId
                        """)
        Object[] getDashboardLoanAggregate(
                        @Param("organizationId") Long organizationId);

        // ============================================================
        // FILTERING
        // ============================================================

        @EntityGraph(attributePaths = {
                        "borrower",
                        "organization"
        })
        @Query("""
                        SELECT l
                        FROM Loan l
                        WHERE l.organization = :org
                          AND (:status IS NULL OR l.status = :status)
                          AND (:type IS NULL OR l.loanType = :type)
                        ORDER BY l.createdAt DESC
                        """)
        Page<Loan> findByFilters(
                        @Param("org") Organization org,
                        @Param("status") LoanStatus status,
                        @Param("type") Loan.LoanType type,
                        Pageable pageable);

        // ============================================================
        // DASHBOARD
        // ============================================================

        @Query("""
                        SELECT COALESCE(SUM(l.amount), 0)
                        FROM Loan l
                        WHERE l.organization = :org
                          AND l.status IN (
                              'ACTIVE',
                              'DISBURSED',
                              'OVERDUE'
                          )
                        """)
        BigDecimal sumActivePrincipal(
                        @Param("org") Organization org);

        @Query("""
                        SELECT COALESCE(SUM(l.totalPaid), 0)
                        FROM Loan l
                        WHERE l.organization = :org
                        """)
        BigDecimal sumTotalCollected(
                        @Param("org") Organization org);

        @Query("""
                        SELECT COALESCE(SUM(l.outstandingBalance), 0)
                        FROM Loan l
                        WHERE l.organization = :org
                          AND l.status IN (
                              'ACTIVE',
                              'DISBURSED',
                              'OVERDUE'
                          )
                        """)
        BigDecimal sumOutstandingBalance(
                        @Param("org") Organization org);

        /**
         * Sum the gross amount actually disbursed for an organization.
         *
         * Uses the persisted disbursedAmount rather than the requested
         * loan amount so reporting reflects the amount that was actually
         * released to borrowers.
         */
        @Query("""
                        SELECT COALESCE(SUM(l.disbursedAmount), 0)
                        FROM Loan l
                        WHERE l.organization = :org
                          AND l.disbursedAmount IS NOT NULL
                        """)
        BigDecimal sumGrossDisbursedPrincipal(
                        @Param("org") Organization org);

        // ============================================================
        // LOAN TYPE BREAKDOWN
        // ============================================================

        @Query("""
                        SELECT l.loanType,
                               COUNT(l),
                               COALESCE(SUM(l.amount), 0)
                        FROM Loan l
                        WHERE l.organization = :org
                        GROUP BY l.loanType
                        """)
        List<Object[]> getLoanTypeBreakdown(
                        @Param("org") Organization org);

        @Query("""
                        SELECT l.loanType,
                               COUNT(l),
                               COALESCE(SUM(l.amount), 0)
                        FROM Loan l
                        WHERE l.organization.id = :organizationId
                        GROUP BY l.loanType
                        ORDER BY COUNT(l) DESC
                        """)
        List<Object[]> getLoanTypeBreakdownByOrganizationId(
                        @Param("organizationId") Long organizationId);

        // ============================================================
        // RECENT
        // ============================================================

        @EntityGraph(attributePaths = {
                        "borrower",
                        "organization",
                        "createdBy",
                        "approvedBy",
                        "loanOfficer"
        })
        @Query("""
                        SELECT l
                        FROM Loan l
                        WHERE l.organization = :org
                        ORDER BY l.createdAt DESC
                        """)
        List<Loan> findRecentByOrg(
                        @Param("org") Organization org,
                        Pageable pageable);

        @EntityGraph(attributePaths = {
                        "borrower",
                        "organization"
        })
        @Query("""
                        SELECT l
                        FROM Loan l
                        WHERE l.organization.id = :organizationId
                        ORDER BY l.createdAt DESC
                        """)
        List<Loan> findRecentByOrganizationId(
                        @Param("organizationId") Long organizationId,
                        Pageable pageable);

        // ============================================================
        // REGULATORY
        // ============================================================

        /**
         * Find loans disbursed during a date/time period.
         *
         * disbursedAt is LocalDateTime, therefore both from and to
         * are LocalDateTime.
         *
         * The upper bound is exclusive.
         */
        @EntityGraph(attributePaths = {
                        "borrower",
                        "organization",
                        "branch"
        })
        @Query("""
                        SELECT l
                        FROM Loan l
                        WHERE l.organization.id = :orgId
                          AND (:branchId IS NULL OR l.branch.id = :branchId)
                          AND l.disbursedAt IS NOT NULL
                          AND l.disbursedAt >= :from
                          AND l.disbursedAt < :to
                        ORDER BY l.disbursedAt ASC
                        """)
        List<Loan> findLoansDisbursedDuringPeriod(
                        @Param("orgId") Long orgId,
                        @Param("branchId") Long branchId,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);

        /**
         * Compatibility overload for regulatory reporting code that also
         * passes the original inclusive LocalDate boundaries. The query
         * is driven by the LocalDateTime boundaries because disbursedAt
         * is a LocalDateTime field.
         */
        default List<Loan> findLoansDisbursedDuringPeriod(
                        Long orgId,
                        Long branchId,
                        LocalDateTime from,
                        LocalDateTime to,
                        java.time.LocalDate fromDate,
                        java.time.LocalDate toDate) {
                return findLoansDisbursedDuringPeriod(orgId, branchId, from, to);
        }

        /**
         * Find portfolio as of a particular date/time.
         *
         * The asOf parameter is exclusive.
         *
         * Example:
         *
         * asOfDate = 2026-08-31
         *
         * asOfDateTime = 2026-09-01T00:00:00
         *
         * This includes every loan disbursed on 2026-08-31,
         * regardless of the exact disbursement time.
         */
        @EntityGraph(attributePaths = {
                        "borrower",
                        "organization",
                        "branch",
                        "payments"
        })
        @Query("""
                        SELECT DISTINCT l
                        FROM Loan l
                        WHERE l.organization.id = :orgId
                          AND (:branchId IS NULL OR l.branch.id = :branchId)
                          AND l.disbursedAt IS NOT NULL
                          AND l.disbursedAt < :asOf
                        ORDER BY l.disbursedAt ASC
                        """)
        List<Loan> findPortfolioAsOf(
                        @Param("orgId") Long orgId,
                        @Param("branchId") Long branchId,
                        @Param("asOf") LocalDateTime asOf);

        /**
         * Compatibility overload for regulatory reporting callers that
         * also provide the reporting LocalDate. The LocalDateTime value
         * remains authoritative because disbursedAt contains the exact
         * timestamp.
         */
        default List<Loan> findPortfolioAsOf(
                        Long orgId,
                        Long branchId,
                        LocalDateTime asOf,
                        java.time.LocalDate asOfDate) {
                return findPortfolioAsOf(orgId, branchId, asOf);
        }

        /**
         * Find loans for regulatory reporting.
         *
         * createdAt is LocalDateTime, therefore from/to are also
         * LocalDateTime.
         */
        @EntityGraph(attributePaths = {
                        "borrower",
                        "organization",
                        "branch",
                        "payments"
        })
        @Query("""
                        SELECT DISTINCT l
                        FROM Loan l
                        WHERE l.organization.id = :orgId
                          AND (:branchId IS NULL OR l.branch.id = :branchId)
                          AND (:from IS NULL OR l.createdAt >= :from)
                          AND (:to IS NULL OR l.createdAt < :to)
                        ORDER BY l.createdAt DESC
                        """)
        List<Loan> findForRegulatoryReport(
                        @Param("orgId") Long orgId,
                        @Param("branchId") Long branchId,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);

        /**
         * Find loans created during a period.
         */
        @Query("""
                        SELECT l
                        FROM Loan l
                        WHERE l.organization.id = :orgId
                          AND (:branchId IS NULL OR l.branch.id = :branchId)
                          AND l.createdAt >= :from
                          AND l.createdAt < :to
                        ORDER BY l.createdAt ASC
                        """)
        List<Loan> findLoansCreatedDuringPeriod(
                        @Param("orgId") Long orgId,
                        @Param("branchId") Long branchId,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);

        // ============================================================
        // EXISTS
        // ============================================================

        boolean existsByOrganization_IdAndReferenceNumber(
                        Long organizationId,
                        String referenceNumber);
}