package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"user", "user.role", "user.organization"})
    Optional<PasswordResetToken> findByTokenAndUsedFalse(String token);
    void deleteByUser_Id(Long userId);
}
