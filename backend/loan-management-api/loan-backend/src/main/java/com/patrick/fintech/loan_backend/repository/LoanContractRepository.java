package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.LoanContract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoanContractRepository extends JpaRepository<LoanContract, Long> {
    Optional<LoanContract> findByLoan_IdAndOrganization_Id(Long loanId, Long organizationId);
}
