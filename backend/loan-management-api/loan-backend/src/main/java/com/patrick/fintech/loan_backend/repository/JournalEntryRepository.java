package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface JournalEntryRepository
        extends JpaRepository<JournalEntry, Long> {

    // ============================================================
    // ALL JOURNAL ENTRIES FOR ORGANIZATION
    // ============================================================

    List<JournalEntry> findByOrganization_IdOrderByEntryDateDesc(
            Long organizationId
    );


    // ============================================================
    // JOURNAL ENTRIES WITHIN DATE RANGE
    // ============================================================

    List<JournalEntry>
    findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(
            Long organizationId,
            LocalDate from,
            LocalDate to
    );


    // ============================================================
    // GET ONE JOURNAL ENTRY FOR ORGANIZATION
    // ============================================================

    Optional<JournalEntry> findByIdAndOrganization_Id(
            Long id,
            Long organizationId
    );


    // ============================================================
    // NON-REVERSED JOURNAL ENTRIES
    // ============================================================

    List<JournalEntry>
    findByOrganization_IdAndReversedFalseOrderByEntryDateAsc(
            Long organizationId
    );


    // ============================================================
    // NON-REVERSED JOURNAL ENTRIES WITHIN DATE RANGE
    // ============================================================

    List<JournalEntry>
    findByOrganization_IdAndEntryDateBetweenAndReversedFalseOrderByEntryDateAsc(
            Long organizationId,
            LocalDate from,
            LocalDate to
    );
}