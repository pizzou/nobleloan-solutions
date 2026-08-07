package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Expense;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    // ============================================================
    // FIND ONE EXPENSE
    // ============================================================

    /**
     * Loads one expense belonging to the organization.
     *
     * EntityGraph prevents lazy-loading problems when the service
     * needs the related organization, branch, payment account and
     * GL account.
     *
     * Because this query returns only one row, the EntityGraph
     * overhead is limited.
     */
    @EntityGraph(attributePaths = {
            "organization",
            "branch",
            "paymentAccount",
            "paymentAccount.glAccount",
            "paymentAccount.branch"
    })
    Optional<Expense> findByIdAndOrganization_Id(
            Long id,
            Long orgId
    );


    // ============================================================
    // PAGINATED EXPENSE LIST
    // ============================================================

    /**
     * Returns expenses for one organization with optional filters.
     *
     * IMPORTANT:
     *
     * Pageable prevents loading the entire expense table into memory.
     *
     * The query remains organization-scoped for tenant isolation.
     *
     * The EntityGraph is retained for compatibility with the existing
     * service/controller code.
     */
    @EntityGraph(attributePaths = {
            "organization",
            "branch",
            "paymentAccount",
            "paymentAccount.glAccount",
            "paymentAccount.branch"
    })
    @Query("""
        SELECT e
        FROM Expense e
        WHERE e.organization.id = :orgId
          AND (:category IS NULL OR e.category = :category)
          AND (:branchId IS NULL OR e.branch.id = :branchId)
          AND (:from IS NULL OR e.expenseDate >= :from)
          AND (:to IS NULL OR e.expenseDate <= :to)
        ORDER BY e.expenseDate DESC, e.id DESC
        """)
    Page<Expense> findByFilters(
            @Param("orgId") Long orgId,
            @Param("category") Expense.ExpenseCategory category,
            @Param("branchId") Long branchId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable
    );


    // ============================================================
    // TOTAL BY CATEGORY
    // ============================================================

    /**
     * Calculates posted expenses grouped by category.
     *
     * Database performs the aggregation instead of loading all
     * expense records into Java.
     */
    @Query("""
        SELECT e.category, COALESCE(SUM(e.amount), 0)
        FROM Expense e
        WHERE e.organization.id = :orgId
          AND e.status = com.patrick.fintech.loan_backend.model.Expense$Status.POSTED
          AND e.expenseDate >= :from
          AND e.expenseDate <= :to
        GROUP BY e.category
        ORDER BY e.category
        """)
    List<Object[]> sumByCategory(
            @Param("orgId") Long orgId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );


    // ============================================================
    // TOTAL EXPENSES
    // ============================================================

    /**
     * Total posted expenses for an organization and period.
     */
    @Query("""
        SELECT COALESCE(SUM(e.amount), 0)
        FROM Expense e
        WHERE e.organization.id = :orgId
          AND e.status = com.patrick.fintech.loan_backend.model.Expense$Status.POSTED
          AND e.expenseDate >= :from
          AND e.expenseDate <= :to
        """)
    Double sumTotal(
            @Param("orgId") Long orgId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );


    // ============================================================
    // TOTAL BY PAYMENT ACCOUNT
    // ============================================================

    /**
     * Total posted expenses paid from one payment account.
     *
     * This returns only the aggregate value, so no Expense entities
     * are loaded.
     */
    @Query("""
        SELECT COALESCE(SUM(e.amount), 0)
        FROM Expense e
        WHERE e.organization.id = :orgId
          AND e.paymentAccount.id = :paymentAccountId
          AND e.status = com.patrick.fintech.loan_backend.model.Expense$Status.POSTED
          AND e.expenseDate >= :from
          AND e.expenseDate <= :to
        """)
    Double sumByPaymentAccount(
            @Param("orgId") Long orgId,
            @Param("paymentAccountId") Long paymentAccountId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );


    // ============================================================
    // POSTED EXPENSES BY PAYMENT ACCOUNT
    // ============================================================

    /**
     * Returns actual posted expense records for a payment account.
     *
     * EntityGraph is retained because callers may need the related
     * accounting objects.
     */
    @EntityGraph(attributePaths = {
            "organization",
            "branch",
            "paymentAccount",
            "paymentAccount.glAccount",
            "paymentAccount.branch"
    })
    @Query("""
        SELECT e
        FROM Expense e
        WHERE e.organization.id = :orgId
          AND e.paymentAccount.id = :paymentAccountId
          AND e.status = com.patrick.fintech.loan_backend.model.Expense$Status.POSTED
          AND e.expenseDate >= :from
          AND e.expenseDate <= :to
        ORDER BY e.expenseDate DESC, e.id DESC
        """)
    List<Expense> findPostedByPaymentAccount(
            @Param("orgId") Long orgId,
            @Param("paymentAccountId") Long paymentAccountId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );


    // ============================================================
    // COUNTS
    // ============================================================

    long countByOrganization_Id(
            Long orgId
    );


    long countByOrganization_IdAndStatus(
            Long orgId,
            Expense.Status status
    );
}