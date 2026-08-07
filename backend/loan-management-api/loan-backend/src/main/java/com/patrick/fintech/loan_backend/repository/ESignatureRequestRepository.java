package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.ESignatureRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ESignatureRequestRepository
        extends JpaRepository<ESignatureRequest, Long> {

    // ============================================================
    // FIND BY SIGNING TOKEN
    // ============================================================

    @Query("""
            SELECT e
            FROM ESignatureRequest e
            JOIN FETCH e.borrower
            JOIN FETCH e.loan
            JOIN FETCH e.organization
            WHERE e.signingToken = :token
            """)
    Optional<ESignatureRequest> findBySigningToken(
            @Param("token") String token
    );


    // ============================================================
    // LOAN HISTORY
    // ============================================================

    List<ESignatureRequest> findByLoan_IdOrderByCreatedAtDesc(
            Long loanId
    );


    // ============================================================
    // ORGANIZATION + STATUS
    // ============================================================

    List<ESignatureRequest> findByOrganization_IdAndStatus(
            Long orgId,
            ESignatureRequest.SignatureStatus status
    );
}