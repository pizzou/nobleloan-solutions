package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Guarantor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GuarantorRepository extends JpaRepository<Guarantor, Long> {

    List<Guarantor> findByLoan_IdAndOrganization_Id(Long loanId, Long organizationId);

    List<Guarantor> findByOrganization_Id(Long orgId);
}