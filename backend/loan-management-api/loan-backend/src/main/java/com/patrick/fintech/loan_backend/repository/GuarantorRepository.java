package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Guarantor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface GuarantorRepository extends JpaRepository<Guarantor, Long> {

    List<Guarantor> findByLoan_IdAndOrganization_Id(Long loanId, Long organizationId);

    List<Guarantor> findByOrganization_Id(Long orgId);

    @Query("""
            SELECT g FROM Guarantor g
            WHERE g.organization.id = :organizationId
              AND g.loan.id IN :loanIds
            ORDER BY g.loan.id ASC, g.id ASC
            """)
    List<Guarantor> findByLoanIdsAndOrganizationId(
            @Param("loanIds") List<Long> loanIds,
            @Param("organizationId") Long organizationId);
}