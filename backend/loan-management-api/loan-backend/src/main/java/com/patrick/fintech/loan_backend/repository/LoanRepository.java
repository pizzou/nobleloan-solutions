package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;

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
     * Important for payment concurrency so simultaneous payment
     * transactions cannot modify the same loan balance at the
     * same time.
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
     * Find a loan by public reference number.
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

    /**
     * Returns historical/imported loans for accounting reconciliation.
     *
     * The import flow writes both imported=true and importBatchId. The OR
     * condition deliberately keeps reconciliation compatible with legacy rows
     * created by an earlier importer that populated the batch id before the
     * imported flag was introduced/fixed.
     */
    @EntityGraph(attributePaths = {
            "borrower",
            "organization"
    })
    @Query("""
            SELECT l
            FROM Loan l
            WHERE l.organization.id = :organizationId
              AND (
                  l.imported = true
                  OR l.importBatchId IS NOT NULL
                  OR LOWER(COALESCE(l.internalNotes, '')) LIKE '%imported from legacy ledger%'
                  OR LOWER(COALESCE(l.notes, '')) LIKE '%imported from noble loan historical portfolio workbook%'
              )
            ORDER BY l.id ASC
            """)
    List<Loan> findHistoricalImportedLoans(
            @Param("organizationId") Long organizationId);

    List<Loan> findByOrganization_IdAndImportedTrue(
            Long organizationId);

    /**
     * Returns loans actually persisted by one legacy import batch.
     * This is the authoritative post-commit persistence count.
     */
    @EntityGraph(attributePaths = {
            "borrower",
            "organization",
            "branch"
    })
    List<Loan> findByOrganization_IdAndImportBatchId(
            Long organizationId,
            Long importBatchId);

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
     * Single aggregate query for dashboard loan KPIs.
     *
     * Result order:
     *
     * 0 total loan records
     * 1 pending/review loans
     * 2 active loans
     * 3 completed/paid loans
     * 4 defaulted loans
     * 5 gross principal actually recorded as disbursed
     * 6 current outstanding principal
     * 7 outstanding principal at risk
     * 8 overdue loans
     *
     * Imported historical loans are deliberately excluded from
     * current-period gross disbursement totals.
     */
    @Query("""
            SELECT
                COUNT(l),

                SUM(
                    CASE
                        WHEN l.status IN ('PENDING', 'UNDER_REVIEW')
                        THEN 1
                        ELSE 0
                    END
                ),

                SUM(
                    CASE
                        WHEN l.status IN (
                            'ACTIVE',
                            'DISBURSED',
                            'OVERDUE',
                            'RESTRUCTURED'
                        )
                        THEN 1
                        ELSE 0
                    END
                ),

                SUM(
                    CASE
                        WHEN l.status IN ('PAID', 'CLOSED')
                        THEN 1
                        ELSE 0
                    END
                ),

                SUM(
                    CASE
                        WHEN l.status IN ('DEFAULTED', 'WRITTEN_OFF')
                        THEN 1
                        ELSE 0
                    END
                ),

                COALESCE(
                    SUM(
                        CASE
                            WHEN (l.imported = false OR l.imported IS NULL)
                             AND l.disbursedAmount IS NOT NULL
                            THEN l.disbursedAmount
                            ELSE 0
                        END
                    ),
                    0
                ),

                COALESCE(
                    SUM(
                        CASE
                            WHEN l.outstandingBalance > 0
                            THEN l.outstandingBalance
                            ELSE 0
                        END
                    ),
                    0
                ),

                COALESCE(
                    SUM(
                        CASE
                            WHEN l.outstandingBalance > 0
                             AND l.daysOverdue > 0
                            THEN l.outstandingBalance
                            ELSE 0
                        END
                    ),
                    0
                ),

                COALESCE(
                    SUM(
                        CASE
                            WHEN l.outstandingBalance > 0
                             AND l.daysOverdue > 0
                            THEN 1
                            ELSE 0
                        END
                    ),
                    0
                )

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

    /** Cumulative paid-to-date amount stored on migrated historical loans. */
    @Query("""
            SELECT COALESCE(SUM(l.totalPaid), 0)
            FROM Loan l
            WHERE l.organization = :org
              AND l.imported = true
            """)
    BigDecimal sumImportedHistoricalTotalPaid(
            @Param("org") Organization org);

    /**
     * Processing fees collected at disbursement; not represented by Payment rows.
     */
    @Query("""
            SELECT COALESCE(SUM(l.applicationFeePaid), 0)
            FROM Loan l
            WHERE l.organization = :org
              AND l.status IN (
                  'DISBURSED',
                  'ACTIVE',
                  'OVERDUE',
                  'DEFAULTED',
                  'RESTRUCTURED',
                  'PAID',
                  'CLOSED',
                  'WRITTEN_OFF'
              )
              AND COALESCE(l.disbursedAmount, 0) > 0
              AND COALESCE(l.applicationFeePaid, 0) > 0
            """)
    BigDecimal sumApplicationFeesCollected(
            @Param("org") Organization org);

    @Query("""
            SELECT COALESCE(SUM(l.applicationFeePaid), 0)
            FROM Loan l
            WHERE l.organization = :org
              AND l.imported = true
              AND COALESCE(l.applicationFeePaid, 0) > 0
            """)
    BigDecimal sumImportedApplicationFeesCollected(
            @Param("org") Organization org);

    /**
     * Post-migration payment rows belonging to imported loans, used to avoid
     * double-counting.
     */
    @Query("""
            SELECT COALESCE(SUM(p.amountPaid), 0)
            FROM Payment p
            JOIN p.loan l
            WHERE l.organization = :org
              AND l.imported = true
              AND p.paid = true
            """)
    BigDecimal sumImportedPaymentRows(
            @Param("org") Organization org);

    /**
     * Authoritative current portfolio principal outstanding.
     *
     * This deliberately mirrors the population used by regulatory
     * portfolio reporting:
     *
     * - current receivable statuses only;
     * - imported/import-batch loans remain portfolio loans even when the
     * historical source did not contain a disbursement timestamp;
     * - system-originated loans require a real disbursement timestamp.
     *
     * Keeping this definition here makes /loans/dashboard agree with the
     * dashboard service and BNR outstanding principal instead of silently
     * dropping imported DEFAULTED/RESTRUCTURED or timestamp-less rows.
     */
    @Query("""
            SELECT COALESCE(SUM(l.outstandingBalance), 0)
            FROM Loan l
            WHERE l.organization = :org
              AND COALESCE(l.outstandingBalance, 0) > 0
              AND l.status IN (
                  'ACTIVE',
                  'DISBURSED',
                  'OVERDUE',
                  'DEFAULTED',
                  'RESTRUCTURED'
              )
              AND (
                  l.imported = true
                  OR l.importBatchId IS NOT NULL
                  OR LOWER(COALESCE(l.internalNotes, '')) LIKE '%imported from legacy ledger%'
                  OR LOWER(COALESCE(l.notes, '')) LIKE '%imported from noble loan historical portfolio workbook%'
                  OR l.disbursedAt IS NOT NULL
              )
            """)
    BigDecimal sumOutstandingBalance(
            @Param("org") Organization org);

    /**
     * Sum gross amount actually disbursed.
     *
     * Uses persisted disbursedAmount rather than requested amount.
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
     * Compatibility overload for regulatory reporting code.
     *
     * The LocalDateTime boundaries remain authoritative.
     */
    default List<Loan> findLoansDisbursedDuringPeriod(
            Long orgId,
            Long branchId,
            LocalDateTime from,
            LocalDateTime to,
            java.time.LocalDate fromDate,
            java.time.LocalDate toDate) {

        return findLoansDisbursedDuringPeriod(
                orgId,
                branchId,
                from,
                to);
    }

    /**
     * Find portfolio as of a particular date/time.
     *
     * Imported historical loans are included in the point-in-time
     * regulatory portfolio even where their original disbursement
     * timestamp is unavailable.
     *
     * Imported loans are NOT treated as current-period cash
     * disbursements. Current-period disbursements remain controlled
     * by findLoansDisbursedDuringPeriod().
     *
     * IMPORTANT:
     *
     * The query uses DISTINCT because payments is loaded through
     * EntityGraph.
     *
     * Do not use a CASE expression in ORDER BY here.
     *
     * PostgreSQL rejects:
     *
     * SELECT DISTINCT ...
     * ORDER BY CASE ...
     *
     * when the CASE expression is not in the select list.
     *
     * PostgreSQL's normal ASC ordering places NULL values after
     * non-NULL values, which gives us the required ordering:
     *
     * 1. loans with a known disbursement date
     * 2. oldest disbursement first
     * 3. imported loans with NULL disbursement date last
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
              AND (
                  l.imported = true
                  OR l.importBatchId IS NOT NULL
                  OR LOWER(COALESCE(l.internalNotes, '')) LIKE '%imported from legacy ledger%'
                  OR LOWER(COALESCE(l.notes, '')) LIKE '%imported from noble loan historical portfolio workbook%'
                  OR (
                      l.disbursedAt IS NOT NULL
                      AND l.disbursedAt < :asOf
                  )
              )
            ORDER BY l.disbursedAt ASC
            """)
    List<Loan> findPortfolioAsOf(
            @Param("orgId") Long orgId,
            @Param("branchId") Long branchId,
            @Param("asOf") LocalDateTime asOf);

    /**
     * Compatibility overload for regulatory reporting callers.
     */
    default List<Loan> findPortfolioAsOf(
            Long orgId,
            Long branchId,
            LocalDateTime asOf,
            java.time.LocalDate asOfDate) {

        return findPortfolioAsOf(
                orgId,
                branchId,
                asOf);
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