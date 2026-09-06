package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.ESignatureRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;


public interface ESignatureRequestRepository extends JpaRepository<ESignatureRequest, Long> {

    @Query("""
    SELECT e
    FROM ESignatureRequest e
    JOIN FETCH e.borrower
    JOIN FETCH e.loan
    JOIN FETCH e.organization
    WHERE e.signingToken = :token
""")
    Optional<ESignatureRequest> findBySigningToken(String token);

    /**
     * Serializes state-changing public signing operations for one signing token.
     * This prevents two concurrent sign/resend/decline requests from both
     * observing the same OTP_SENT state and committing conflicting results.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT e
            FROM ESignatureRequest e
            JOIN FETCH e.borrower
            JOIN FETCH e.loan
            JOIN FETCH e.organization
            WHERE e.signingToken = :token
            """)
    Optional<ESignatureRequest> findBySigningTokenForUpdate(
            @Param("token") String token);
    List<ESignatureRequest> findByLoan_IdOrderByCreatedAtDesc(Long loanId);
    List<ESignatureRequest> findByOrganization_IdAndStatus(Long orgId, ESignatureRequest.SignatureStatus status);
}
