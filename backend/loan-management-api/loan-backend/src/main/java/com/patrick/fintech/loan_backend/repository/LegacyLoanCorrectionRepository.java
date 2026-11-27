package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.LegacyLoanCorrection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface LegacyLoanCorrectionRepository extends JpaRepository<LegacyLoanCorrection, Long> {
    List<LegacyLoanCorrection> findByOrganizationIdAndLoanIdOrderByCreatedAtDesc(Long organizationId, Long loanId);
    List<LegacyLoanCorrection> findByOrganizationIdAndStatusOrderByCreatedAtAsc(Long organizationId, String status);
    boolean existsByOrganizationIdAndLoanIdAndStatus(Long organizationId, Long loanId, String status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LegacyLoanCorrection c where c.id=:id and c.organizationId=:organizationId and c.loanId=:loanId")
    Optional<LegacyLoanCorrection> lock(@Param("organizationId") Long orgId, @Param("loanId") Long loanId, @Param("id") Long id);
}
