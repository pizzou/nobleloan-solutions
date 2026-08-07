package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.List;
import java.util.Optional;


public interface BankAccountRepository
        extends JpaRepository<BankAccount, Long> {

    List<BankAccount> findByOrganization_IdOrderByNameAsc(
            Long orgId
    );

    Optional<BankAccount> findByIdAndOrganization_Id(
            Long id,
            Long orgId
    );

    long countByOrganization_Id(
            Long orgId
    );
}