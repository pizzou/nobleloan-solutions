package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.BorrowerDocumentReuseChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface BorrowerDocumentReuseChallengeRepository
        extends JpaRepository<BorrowerDocumentReuseChallenge, UUID> {
    long countByBorrowerIdAndCreatedAtAfter(Long borrowerId, LocalDateTime after);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BorrowerDocumentReuseChallenge c where c.id = :id")
    Optional<BorrowerDocumentReuseChallenge> findLocked(@Param("id") UUID id);
}
