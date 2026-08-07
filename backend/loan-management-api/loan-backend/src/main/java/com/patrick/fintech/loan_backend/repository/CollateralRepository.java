package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Collateral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CollateralRepository
        extends JpaRepository<Collateral, Long> {

    /**
     * Find all collateral attached to a specific loan.
     */
    List<Collateral> findByLoan_Id(
            Long loanId
    );

    /**
     * Find all collateral belonging to an organization.
     *
     * Important for multi-tenant isolation.
     */
    List<Collateral> findByOrganization_Id(
            Long orgId
    );
}