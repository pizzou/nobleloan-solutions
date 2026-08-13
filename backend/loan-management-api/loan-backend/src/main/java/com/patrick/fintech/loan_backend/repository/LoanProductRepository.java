package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanProduct;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanProductRepository
        extends JpaRepository<LoanProduct, Long> {

   
    List<LoanProduct> findByOrganization_IdOrderByDisplayOrderAsc(
            Long organizationId
    );

    
    List<LoanProduct> findByOrganization_IdAndActiveTrueOrderByDisplayOrderAsc(
            Long organizationId
    );

   
    Optional<LoanProduct> findFirstByOrganization_IdAndLoanTypeAndActiveTrue(
            Long organizationId,
            Loan.LoanType loanType
    );


    Optional<LoanProduct> findByOrganization_IdAndLoanType(
            Long organizationId,
            Loan.LoanType loanType
    );

    boolean existsByOrganization_IdAndLoanTypeAndActiveTrue(
            Long organizationId,
            Loan.LoanType loanType
    );
}