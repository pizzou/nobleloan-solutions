package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Collateral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CollateralRepository extends JpaRepository<Collateral, Long> {

    List<Collateral> findByLoan_IdAndOrganization_Id(Long loanId, Long organizationId);

    List<Collateral> findByOrganization_Id(Long orgId);
}