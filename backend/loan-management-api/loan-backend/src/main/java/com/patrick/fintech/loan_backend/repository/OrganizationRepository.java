package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Organization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrganizationRepository
        extends JpaRepository<Organization, Long> {

    // ============================================================
    // FIND ORGANIZATION BY REGISTRATION NUMBER
    // ============================================================

    Optional<Organization> findByRegistrationNumber(
            String regNumber
    );


    // ============================================================
    // CHECK REGISTRATION NUMBER
    // ============================================================

    boolean existsByRegistrationNumber(
            String regNumber
    );
}