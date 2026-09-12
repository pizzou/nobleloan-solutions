
package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.time.LocalDate;
import java.util.List;
import java.util.Optional;


public interface JournalEntryRepository
        extends JpaRepository<JournalEntry, Long> {

    /*
     * ============================================================
     * ORGANIZATION ENTRIES
     * ============================================================
     */

    List<JournalEntry> findByOrganization_IdOrderByEntryDateDesc(
            Long organizationId
    );


    @EntityGraph(attributePaths = {"lines", "lines.account"})
    @Query("""
            SELECT j
            FROM JournalEntry j
            WHERE j.organization.id = :organizationId
              AND (
                    :includeBusinessOwnerOnly = true
                    OR COALESCE(j.businessOwnerOnly, false) = false
              )
            ORDER BY j.entryDate DESC, j.id DESC
            """)
    List<JournalEntry> findVisibleByOrganizationIdOrderByEntryDateDesc(
            @Param("organizationId") Long organizationId,
            @Param("includeBusinessOwnerOnly") boolean includeBusinessOwnerOnly);


    /*
     * ============================================================
     * ENTRIES BETWEEN DATES
     * ============================================================
     */

    @EntityGraph(attributePaths = {"lines", "lines.account"})
    List<JournalEntry>
    findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(
            Long organizationId,
            LocalDate from,
            LocalDate to
    );


    @EntityGraph(attributePaths = {"lines", "lines.account"})
    @Query("""
            SELECT j
            FROM JournalEntry j
            WHERE j.organization.id = :organizationId
              AND j.entryDate BETWEEN :from AND :to
              AND (
                    :includeBusinessOwnerOnly = true
                    OR COALESCE(j.businessOwnerOnly, false) = false
              )
            ORDER BY j.entryDate ASC, j.id ASC
            """)
    List<JournalEntry> findVisibleByOrganizationIdAndEntryDateBetween(
            @Param("organizationId") Long organizationId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("includeBusinessOwnerOnly") boolean includeBusinessOwnerOnly);


    /*
     * ============================================================
     * ENTRIES BETWEEN DATES - DETERMINISTIC ORDER
     * ============================================================
     *
     * Entry date alone is not sufficient when several journal
     * entries are posted on the same day.
     *
     * ID provides deterministic ordering.
     */

    List<JournalEntry>
    findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAscIdAsc(
            Long organizationId,
            LocalDate from,
            LocalDate to
    );


    @EntityGraph(attributePaths = {"lines", "lines.account"})
    @Query("""
            SELECT j
            FROM JournalEntry j
            WHERE j.organization.id = :organizationId
              AND j.entryDate BETWEEN :from AND :to
              AND (
                    :includeBusinessOwnerOnly = true
                    OR COALESCE(j.businessOwnerOnly, false) = false
              )
            ORDER BY j.entryDate ASC, j.id ASC
            """)
    List<JournalEntry> findVisibleByOrganizationIdAndEntryDateBetweenOrderById(
            @Param("organizationId") Long organizationId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("includeBusinessOwnerOnly") boolean includeBusinessOwnerOnly);


    /*
     * ============================================================
     * FIND ENTRY BY ID + ORGANIZATION
     * ============================================================
     */

    Optional<JournalEntry> findByIdAndOrganization_Id(
            Long id,
            Long organizationId
    );

    @EntityGraph(attributePaths = {"lines", "lines.account", "branch"})
    @Query("""
            SELECT j
            FROM JournalEntry j
            WHERE j.id = :id
              AND j.organization.id = :organizationId
              AND (
                    :includeBusinessOwnerOnly = true
                    OR COALESCE(j.businessOwnerOnly, false) = false
              )
            """)
    Optional<JournalEntry> findVisibleByIdAndOrganizationId(
            @Param("id") Long id,
            @Param("organizationId") Long organizationId,
            @Param("includeBusinessOwnerOnly") boolean includeBusinessOwnerOnly);


    /*
     * ============================================================
     * ACTIVE / NON-REVERSED ENTRIES
     * ============================================================
     */

    List<JournalEntry>
    findByOrganization_IdAndReversedFalseOrderByEntryDateAsc(
            Long organizationId
    );


    /*
     * ============================================================
     * ACTIVE ENTRIES BETWEEN DATES
     * ============================================================
     */

    List<JournalEntry>
    findByOrganization_IdAndEntryDateBetweenAndReversedFalseOrderByEntryDateAsc(
            Long organizationId,
            LocalDate from,
            LocalDate to
    );


    /*
     * ============================================================
     * SOURCE EVENT LOOKUP
     * ============================================================
     *
     * This is important for production accounting.
     *
     * Examples:
     *
     * LOAN_DISBURSEMENT + loan ID
     * PAYMENT_RECEIVED  + payment ID
     * INTEREST_ACCRUAL  + loan ID
     * EXPENSE           + expense ID
     *
     * The organization is included in the lookup.
     */

    Optional<JournalEntry>
    findFirstByOrganization_IdAndSourceTypeAndSourceId(
            Long organizationId,
            String sourceType,
            String sourceId
    );


    /*
     * ============================================================
     * ACTIVE SOURCE EVENT LOOKUP
     * ============================================================
     *
     * Useful when checking whether a business event already has
     * an active journal entry.
     */

    Optional<JournalEntry>
    findFirstByOrganization_IdAndSourceTypeAndSourceIdAndReversedFalse(
            Long organizationId,
            String sourceType,
            String sourceId
    );


    /*
     * ============================================================
     * ALL ENTRIES FOR SOURCE EVENT
     * ============================================================
     *
     * This can be useful for auditing because a source event may
     * have an original entry and a reversal entry.
     */

    List<JournalEntry>
    findByOrganization_IdAndSourceTypeAndSourceId(
            Long organizationId,
            String sourceType,
            String sourceId
    );


    /*
     * ============================================================
     * SOURCE TYPE
     * ============================================================
     */

    List<JournalEntry>
    findByOrganization_IdAndSourceTypeOrderByEntryDateAscIdAsc(
            Long organizationId,
            String sourceType
    );


    /*
     * ============================================================
     * SOURCE TYPE + DATE RANGE
     * ============================================================
     */

    List<JournalEntry>
    findByOrganization_IdAndSourceTypeAndEntryDateBetweenOrderByEntryDateAscIdAsc(
            Long organizationId,
            String sourceType,
            LocalDate from,
            LocalDate to
    );


    /*
     * ============================================================
     * REVERSED ENTRIES
     * ============================================================
     */

    List<JournalEntry>
    findByOrganization_IdAndReversedTrueOrderByEntryDateDesc(
            Long organizationId
    );
}
