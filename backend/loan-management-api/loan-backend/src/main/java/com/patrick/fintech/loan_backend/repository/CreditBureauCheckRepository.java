package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.CreditBureauCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CreditBureauCheckRepository
        extends JpaRepository<CreditBureauCheck, Long> {

    // ============================================================
    // BORROWER HISTORY
    // ============================================================

    List<CreditBureauCheck> findByBorrower_IdOrderByCreatedAtDesc(
            Long borrowerId
    );


    // ============================================================
    // LATEST BORROWER CHECK
    // ============================================================

    Optional<CreditBureauCheck> findFirstByBorrower_IdOrderByCreatedAtDesc(
            Long borrowerId
    );


    // ============================================================
    // ORGANIZATION HISTORY
    // ============================================================

    List<CreditBureauCheck> findByOrganization_IdOrderByCreatedAtDesc(
            Long organizationId
    );


    // ============================================================
    // BORROWER + FROM DATE
    // ============================================================

    List<CreditBureauCheck>
    findByBorrower_IdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            Long borrowerId,
            LocalDateTime from
    );


    // ============================================================
    // BORROWER + TO DATE
    // ============================================================

    List<CreditBureauCheck>
    findByBorrower_IdAndCreatedAtLessThanOrderByCreatedAtDesc(
            Long borrowerId,
            LocalDateTime to
    );


    // ============================================================
    // BORROWER + DATE RANGE
    // ============================================================

    List<CreditBureauCheck>
    findByBorrower_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            Long borrowerId,
            LocalDateTime from,
            LocalDateTime to
    );


    // ============================================================
    // ORGANIZATION + FROM DATE
    // ============================================================

    List<CreditBureauCheck>
    findByOrganization_IdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            Long organizationId,
            LocalDateTime from
    );


    // ============================================================
    // ORGANIZATION + TO DATE
    // ============================================================

    List<CreditBureauCheck>
    findByOrganization_IdAndCreatedAtLessThanOrderByCreatedAtDesc(
            Long organizationId,
            LocalDateTime to
    );


    // ============================================================
    // ORGANIZATION + DATE RANGE
    // ============================================================

    List<CreditBureauCheck>
    findByOrganization_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            Long organizationId,
            LocalDateTime from,
            LocalDateTime to
    );


    // ============================================================
    // ORGANIZATION
    // ============================================================

    List<CreditBureauCheck> findByOrganization_Id(
            Long organizationId
    );
}