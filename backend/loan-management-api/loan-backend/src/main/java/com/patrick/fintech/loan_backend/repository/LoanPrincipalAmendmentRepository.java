package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.LoanPrincipalAmendmentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface LoanPrincipalAmendmentRepository extends JpaRepository<LoanPrincipalAmendmentRequest, Long> {
    List<LoanPrincipalAmendmentRequest> findByOrganizationIdAndLoanIdOrderByCreatedAtDesc(Long organizationId, Long loanId);
    List<LoanPrincipalAmendmentRequest> findByOrganizationIdAndStatusOrderByCreatedAtAsc(Long organizationId, LoanPrincipalAmendmentRequest.Status status);
    boolean existsByOrganizationIdAndLoanIdAndStatusIn(Long organizationId, Long loanId, List<LoanPrincipalAmendmentRequest.Status> statuses);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LoanPrincipalAmendmentRequest a where a.id=:id and a.organizationId=:org and a.loanId=:loan")
    Optional<LoanPrincipalAmendmentRequest> lock(@Param("org") Long org, @Param("loan") Long loan, @Param("id") Long id);
}
