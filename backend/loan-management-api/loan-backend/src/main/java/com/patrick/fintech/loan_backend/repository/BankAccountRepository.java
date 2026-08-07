package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.BankAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankAccountRepository
        extends JpaRepository<BankAccount, Long> {

    // ============================================================
    // LIST ACCOUNTS FOR ORGANIZATION
    // ============================================================
    //
    // Existing method retained for compatibility with services
    // that need the complete list.
    //
    List<BankAccount> findByOrganization_IdOrderByNameAsc(
            Long orgId
    );


    // ============================================================
    // PAGINATED LIST
    // ============================================================
    //
    // Prefer this method for API endpoints when the number of
    // bank/cash accounts can become large.
    //
    Page<BankAccount> findByOrganization_IdOrderByNameAsc(
            Long orgId,
            Pageable pageable
    );


    // ============================================================
    // GET ONE ACCOUNT FOR ORGANIZATION
    // ============================================================
    //
    // This is already efficient because ID is the primary key
    // and organization_id can be indexed.
    //
    Optional<BankAccount> findByIdAndOrganization_Id(
            Long id,
            Long orgId
    );


    // ============================================================
    // COUNT ACCOUNTS FOR ORGANIZATION
    // ============================================================

    long countByOrganization_Id(
            Long orgId
    );
}