package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.JournalLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {

    /*
     * ============================================================
     * ORGANIZATION-SCOPED JOURNAL LINES
     * ============================================================
     */

    List<JournalLine> findByJournalEntry_Organization_Id(
            Long organizationId);

    /*
     * ============================================================
     * ACCOUNT LINES
     * ============================================================
     */

    List<JournalLine> findByAccount_Id(
            Long accountId);

    /*
     * Production-safe account lookup.
     *
     * This prevents an account from another organization from
     * being accidentally included in accounting calculations.
     */

    @Query("""
            SELECT l
            FROM JournalLine l
            JOIN l.journalEntry e
            WHERE l.account.id = :accountId
              AND e.organization.id = :organizationId
            ORDER BY e.entryDate ASC, e.id ASC, l.id ASC
            """)
    List<JournalLine> findByAccount_IdAndOrganization_Id(
            @Param("accountId") Long accountId,
            @Param("organizationId") Long organizationId);

    /*
     * ============================================================
     * LEDGER
     * ============================================================
     */

    @Query("""
            SELECT l
            FROM JournalLine l
            JOIN FETCH l.journalEntry e
            WHERE l.account.id = :accountId
              AND e.organization.id = :organizationId
            ORDER BY e.entryDate ASC, e.id ASC, l.id ASC
            """)
    List<JournalLine> findLedgerForAccountAndOrganization(
            @Param("accountId") Long accountId,
            @Param("organizationId") Long organizationId);

    /*
     * Backward-compatible ledger method.
     *
     * Kept because older parts of the application may still call
     * findLedgerForAccount(accountId).
     */
    @Query("""
            SELECT l
            FROM JournalLine l
            JOIN FETCH l.journalEntry e
            WHERE l.account.id = :accountId
            ORDER BY e.entryDate ASC, e.id ASC, l.id ASC
            """)
    List<JournalLine> findLedgerForAccount(
            @Param("accountId") Long accountId);

    /*
     * ============================================================
     * INTEREST RECEIVABLE / ACCRUAL LINES
     * ============================================================
     *
     * Used when a payment clears previously accrued interest.
     *
     * The query deliberately scopes by:
     *
     * 1. organization
     * 2. interest receivable account
     * 3. loan/source reference
     *
     * This prevents cross-organization accounting contamination.
     */

    @Query("""
            SELECT l
            FROM JournalLine l
            JOIN FETCH l.journalEntry e
            WHERE l.account.id = :accountId
              AND e.organization.id = :organizationId
              AND e.sourceType IN ('INTEREST_ACCRUAL', 'PAYMENT_RECEIVED')
              AND (
                    e.reference = :loanReference
                    OR l.description LIKE CONCAT('% — ', :loanReference)
                    OR l.description LIKE CONCAT('% - ', :loanReference)
              )
            ORDER BY e.entryDate ASC, e.id ASC, l.id ASC
            """)
    List<JournalLine> findAccrualLinesForLoan(
            @Param("accountId") Long accountId,
            @Param("organizationId") Long organizationId,
            @Param("loanReference") String loanReference);

    /*
     * ============================================================
     * INTEREST RECEIVABLE BY LOAN
     * ============================================================
     *
     * Some AccountingService versions use this method with:
     *
     * accountId
     * organizationId
     * loanId
     *
     * Because JournalLine normally does not need a direct Loan
     * relationship, the loan is resolved through JournalEntry's
     * sourceId.
     *
     * sourceType is restricted to interest-related entries.
     */

    @Query("""
            SELECT l
            FROM JournalLine l
            JOIN FETCH l.journalEntry e
            WHERE l.account.id = :accountId
              AND e.organization.id = :organizationId
              AND e.sourceType IN ('INTEREST_ACCRUAL', 'PAYMENT_RECEIVED')
              AND e.sourceId = :loanId
            ORDER BY e.entryDate ASC, e.id ASC, l.id ASC
            """)
    List<JournalLine> findInterestReceivableLinesForLoan(
            @Param("accountId") Long accountId,
            @Param("organizationId") Long organizationId,
            @Param("loanId") Long loanId);

    /**
     * Receivable/accrual lines scoped to one loan reference. Used to clear
     * interest, management, penalty and extension receivables without ever
     * using another loan's balances.
     */
    @Query("""
            SELECT l
            FROM JournalLine l
            JOIN FETCH l.journalEntry e
            WHERE l.account.id = :accountId
              AND e.organization.id = :organizationId
              AND e.reversed = FALSE
              AND e.sourceType IN (
                    'INTEREST_ACCRUAL',
                    'MANAGEMENT_FEE_ACCRUAL',
                    'PENALTY_ACCRUAL',
                    'LOAN_EXTENSION_FEE',
                    'LOAN_EXTENSION_FEE_COLLECTION',
                    'LOAN_PAYMENT',
                    'LOAN_DISBURSEMENT',
                    'SCHEDULED_INTEREST_ACCRUAL',
                    'SCHEDULED_MANAGEMENT_FEE_ACCRUAL',
                    'CONTRACTUAL_MONTHLY_INTEREST_ACCRUAL',
                    'CONTRACTUAL_MONTHLY_MANAGEMENT_FEE_ACCRUAL',
                    'HISTORICAL_LOAN_OPENING',
                    'LEGACY_LOAN_OPENING',
                    'LEGACY_LOAN_RECONCILIATION',
                    'LEGACY_LOAN_OPENING_DATE_REPAIR',
                    'PAYMENT_RECEIVED'
              )
              AND (
                    e.reference = :loanReference
                    OR l.description LIKE CONCAT('% — ', :loanReference)
                    OR l.description LIKE CONCAT('% - ', :loanReference)
              )
            ORDER BY e.entryDate ASC, e.id ASC, l.id ASC
            """)
    List<JournalLine> findReceivableLinesForLoan(
            @Param("accountId") Long accountId,
            @Param("organizationId") Long organizationId,
            @Param("loanReference") String loanReference);

    /**
     * Production-safe loan receivable lookup. The legacy migration opening
     * journal is keyed by sourceId = LOAN:<loanId>; later accrual/payment
     * journals may be keyed by another event id but carry the loan reference.
     * We therefore use the immutable loan id as the primary identity and keep
     * the reference as a compatibility fallback.
     */
    @Query("""
            SELECT l
            FROM JournalLine l
            JOIN FETCH l.journalEntry e
            WHERE l.account.id = :accountId
              AND e.organization.id = :organizationId
              AND e.reversed = FALSE
              AND e.sourceType IN (
                    'INTEREST_ACCRUAL',
                    'MANAGEMENT_FEE_ACCRUAL',
                    'PENALTY_ACCRUAL',
                    'LOAN_EXTENSION_FEE',
                    'LOAN_EXTENSION_FEE_COLLECTION',
                    'LOAN_PAYMENT',
                    'LOAN_DISBURSEMENT',
                    'SCHEDULED_INTEREST_ACCRUAL',
                    'SCHEDULED_MANAGEMENT_FEE_ACCRUAL',
                    'CONTRACTUAL_MONTHLY_INTEREST_ACCRUAL',
                    'CONTRACTUAL_MONTHLY_MANAGEMENT_FEE_ACCRUAL',
                    'HISTORICAL_LOAN_OPENING',
                    'LEGACY_LOAN_OPENING',
                    'LEGACY_LOAN_RECONCILIATION',
                    'LEGACY_LOAN_OPENING_DATE_REPAIR',
                    'PAYMENT_RECEIVED'
              )
              AND (
                    e.sourceId = CONCAT('LOAN:', :loanId)
                    OR e.reference = :loanReference
                    OR l.description LIKE CONCAT('% — ', :loanReference)
                    OR l.description LIKE CONCAT('% - ', :loanReference)
              )
            ORDER BY e.entryDate ASC, e.id ASC, l.id ASC
            """)
    List<JournalLine> findReceivableLinesForLoanIdentity(
            @Param("accountId") Long accountId,
            @Param("organizationId") Long organizationId,
            @Param("loanId") Long loanId,
            @Param("loanReference") String loanReference);

    /*
     * ============================================================
     * ACCRUAL LINES BY ORGANIZATION
     * ============================================================
     */

    @Query("""
            SELECT l
            FROM JournalLine l
            JOIN FETCH l.journalEntry e
            WHERE l.account.id = :accountId
              AND e.organization.id = :organizationId
              AND e.sourceType = 'INTEREST_ACCRUAL'
            ORDER BY e.entryDate ASC, e.id ASC, l.id ASC
            """)
    List<JournalLine> findInterestAccrualLines(
            @Param("accountId") Long accountId,
            @Param("organizationId") Long organizationId);

    /**
     * Lightweight BNR financial-statement aggregation.
     *
     * PostgreSQL performs the ledger aggregation instead of hydrating every
     * historical JournalEntry and JournalLine into Hibernate.
     */
    interface BnrAccountAggregate {
        Long getAccountId();
        java.math.BigDecimal getHistoricalDebit();
        java.math.BigDecimal getHistoricalCredit();
        java.math.BigDecimal getPeriodDebit();
        java.math.BigDecimal getPeriodCredit();
    }

    @Query(value = """
            SELECT
                jl.account_id AS accountId,
                COALESCE(SUM(jl.debit), 0) AS historicalDebit,
                COALESCE(SUM(jl.credit), 0) AS historicalCredit,
                COALESCE(SUM(CASE
                    WHEN je.entry_date BETWEEN :fromDate AND :toDate THEN jl.debit
                    ELSE 0
                END), 0) AS periodDebit,
                COALESCE(SUM(CASE
                    WHEN je.entry_date BETWEEN :fromDate AND :toDate THEN jl.credit
                    ELSE 0
                END), 0) AS periodCredit
            FROM journal_lines jl
            INNER JOIN journal_entries je
                ON je.id = jl.journal_entry_id
            WHERE je.organization_id = :organizationId
              AND je.reversed = false
              AND je.entry_date BETWEEN :historicalFrom AND :toDate
            GROUP BY jl.account_id
            ORDER BY jl.account_id
            """, nativeQuery = true)
    List<BnrAccountAggregate> findBnrAccountAggregates(
            @Param("organizationId") Long organizationId,
            @Param("historicalFrom") java.time.LocalDate historicalFrom,
            @Param("fromDate") java.time.LocalDate fromDate,
            @Param("toDate") java.time.LocalDate toDate);

    /**
     * Finds the first active journal entry whose lines violate the same
     * accounting invariants enforced by the previous entity-based report:
     * empty lines, negative amounts, both debit and credit on one line,
     * zero-value lines, or an imbalance of at least the materiality tolerance.
     */
    @Query(value = """
            SELECT je.id
            FROM journal_entries je
            LEFT JOIN journal_lines jl
                ON jl.journal_entry_id = je.id
            WHERE je.organization_id = :organizationId
              AND je.reversed = false
              AND je.entry_date BETWEEN :fromDate AND :toDate
            GROUP BY je.id
            HAVING COUNT(jl.id) = 0
                OR SUM(CASE WHEN jl.debit < 0 OR jl.credit < 0 THEN 1 ELSE 0 END) > 0
                OR SUM(CASE WHEN jl.debit > 0 AND jl.credit > 0 THEN 1 ELSE 0 END) > 0
                OR SUM(CASE WHEN COALESCE(jl.debit, 0) = 0 AND COALESCE(jl.credit, 0) = 0 THEN 1 ELSE 0 END) > 0
                OR ABS(COALESCE(SUM(jl.debit), 0) - COALESCE(SUM(jl.credit), 0)) >= :tolerance
            ORDER BY je.id
            LIMIT 1
            """, nativeQuery = true)
    java.util.Optional<Long> findFirstInvalidBnrJournalEntry(
            @Param("organizationId") Long organizationId,
            @Param("fromDate") java.time.LocalDate fromDate,
            @Param("toDate") java.time.LocalDate toDate,
            @Param("tolerance") java.math.BigDecimal tolerance);

}