package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Collateral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface CollateralRepository extends JpaRepository<Collateral, Long> {

    List<Collateral> findByLoan_IdAndOrganization_Id(Long loanId, Long organizationId);

    List<Collateral> findByOrganization_Id(Long orgId);

    @Query("""
            SELECT c FROM Collateral c
            WHERE c.organization.id = :organizationId
              AND c.loan.id IN :loanIds
            ORDER BY c.loan.id ASC, c.id ASC
            """)
    List<Collateral> findByLoanIdsAndOrganizationId(
            @Param("loanIds") List<Long> loanIds,
            @Param("organizationId") Long organizationId);
}