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
    /*
     * ============================================================
     * BNR ACCOUNT-LEVEL LEDGER AGGREGATION
     * ============================================================
     *
     * Returns historical and requested-period totals without hydrating
     * JournalEntry/JournalLine entity graphs into Hibernate.
     */
    @Query(value = """
            SELECT
                l.account_id AS accountId,
                COALESCE(SUM(l.debit), 0) AS historicalDebit,
                COALESCE(SUM(l.credit), 0) AS historicalCredit,
                COALESCE(SUM(
                    CASE
                        WHEN e.entry_date >= :fromDate
                         AND e.entry_date <= :toDate
                        THEN l.debit
                        ELSE 0
                    END
                ), 0) AS periodDebit,
                COALESCE(SUM(
                    CASE
                        WHEN e.entry_date >= :fromDate
                         AND e.entry_date <= :toDate
                        THEN l.credit
                        ELSE 0
                    END
                ), 0) AS periodCredit
            FROM journal_lines l
            INNER JOIN journal_entries e
                ON e.id = l.journal_entry_id
            INNER JOIN chart_of_accounts a
                ON a.id = l.account_id
            WHERE e.organization_id = :organizationId
              AND a.organization_id = :organizationId
              AND e.entry_date >= :epochDate
              AND e.entry_date <= :toDate
              AND COALESCE(e.reversed, FALSE) = FALSE
            GROUP BY l.account_id
            ORDER BY l.account_id
            """, nativeQuery = true)
    List<BnrLedgerAggregateProjection> aggregateBnrLedger(
            @Param("organizationId") Long organizationId,
            @Param("epochDate") java.time.LocalDate epochDate,
            @Param("fromDate") java.time.LocalDate fromDate,
            @Param("toDate") java.time.LocalDate toDate);

    /**
     * Finds the first invalid BNR journal entry without hydrating the full
     * ledger. This preserves the report's accounting-integrity gate.
     */
    @Query(value = """
            SELECT entry_id
            FROM (
                SELECT e.id AS entry_id
                FROM journal_entries e
                WHERE e.organization_id = :organizationId
                  AND e.entry_date >= :epochDate
                  AND e.entry_date <= :toDate
                  AND COALESCE(e.reversed, FALSE) = FALSE
                  AND NOT EXISTS (
                      SELECT 1
                      FROM journal_lines l
                      WHERE l.journal_entry_id = e.id
                  )

                UNION

                SELECT e.id AS entry_id
                FROM journal_entries e
                INNER JOIN journal_lines l
                    ON l.journal_entry_id = e.id
                WHERE e.organization_id = :organizationId
                  AND e.entry_date >= :epochDate
                  AND e.entry_date <= :toDate
                  AND COALESCE(e.reversed, FALSE) = FALSE
                  AND (
                       COALESCE(l.debit, 0) < 0
                    OR COALESCE(l.credit, 0) < 0
                    OR (
                        COALESCE(l.debit, 0) > 0
                        AND COALESCE(l.credit, 0) > 0
                    )
                    OR (
                        COALESCE(l.debit, 0) = 0
                        AND COALESCE(l.credit, 0) = 0
                    )
                    OR l.account_id IS NULL
                  )

                UNION

                SELECT e.id AS entry_id
                FROM journal_entries e
                INNER JOIN journal_lines l
                    ON l.journal_entry_id = e.id
                WHERE e.organization_id = :organizationId
                  AND e.entry_date >= :epochDate
                  AND e.entry_date <= :toDate
                  AND COALESCE(e.reversed, FALSE) = FALSE
                GROUP BY e.id
                HAVING ABS(
                    COALESCE(SUM(l.debit), 0)
                    - COALESCE(SUM(l.credit), 0)
                ) > :tolerance
            ) invalid_entries
            ORDER BY entry_id
            LIMIT 1
            """, nativeQuery = true)
    List<Long> findInvalidBnrJournalEntryIds(
            @Param("organizationId") Long organizationId,
            @Param("epochDate") java.time.LocalDate epochDate,
            @Param("toDate") java.time.LocalDate toDate,
            @Param("tolerance") java.math.BigDecimal tolerance);

}