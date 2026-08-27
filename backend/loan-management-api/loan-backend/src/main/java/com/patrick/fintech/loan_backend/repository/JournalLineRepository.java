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
              l.description LIKE CONCAT('%', :loanReference, '%')
              OR e.reference = :loanReference
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
              l.description LIKE CONCAT('%', :loanReference, '%')
              OR e.reference = :loanReference
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
              'LOAN_DISBURSEMENT',
              'SCHEDULED_INTEREST_ACCRUAL',
              'SCHEDULED_MANAGEMENT_FEE_ACCRUAL',
              'CONTRACTUAL_MONTHLY_INTEREST_ACCRUAL',
              'CONTRACTUAL_MONTHLY_MANAGEMENT_FEE_ACCRUAL',
              'HISTORICAL_LOAN_OPENING',
              'LEGACY_LOAN_OPENING',
              'LEGACY_LOAN_RECONCILIATION',
              'LEGACY_LOAN_OPENING_DATE_REPAIR',
              'PAYMENT_RECEIVED',
              'LOAN_PAYMENT'
        )
        AND (
              e.sourceId = CONCAT('LOAN:', :loanId)
              OR e.reference = :loanReference
              OR l.description LIKE CONCAT('%', :loanReference, '%')
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
}