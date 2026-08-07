package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.KycCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KycCheckRepository
        extends JpaRepository<KycCheck, Long> {

    // ============================================================
    // BORROWER KYC HISTORY
    // ============================================================

    List<KycCheck> findByBorrower_Id(
            Long borrowerId
    );


    // ============================================================
    // ORGANIZATION KYC HISTORY
    // ============================================================

    List<KycCheck> findByOrganization_Id(
            Long orgId
    );


    // ============================================================
    // LATEST CHECK OF A SPECIFIC TYPE FOR BORROWER
    // ============================================================

    Optional<KycCheck>
    findFirstByBorrower_IdAndCheckTypeOrderByCreatedAtDesc(
            Long borrowerId,
            KycCheck.CheckType checkType
    );


    // ============================================================
    // ORGANIZATION KYC CHECKS BY RESULT
    // ============================================================

    List<KycCheck> findByOrganization_IdAndResult(
            Long orgId,
            KycCheck.CheckResult result
    );
}