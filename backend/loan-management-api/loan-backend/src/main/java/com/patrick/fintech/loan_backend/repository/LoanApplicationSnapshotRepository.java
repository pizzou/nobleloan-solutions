package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.LoanApplicationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LoanApplicationSnapshotRepository extends JpaRepository<LoanApplicationSnapshot, Long> {
    /** Tenant is included to avoid cross-organization document data disclosure. */
    Optional<LoanApplicationSnapshot> findByLoan_IdAndLoan_Organization_Id(
            Long loanId, Long organizationId);
}
