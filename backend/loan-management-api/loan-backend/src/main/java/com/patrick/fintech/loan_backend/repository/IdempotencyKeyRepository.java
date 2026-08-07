package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.IdempotencyKey;
import com.patrick.fintech.loan_backend.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdempotencyKeyRepository
        extends JpaRepository<IdempotencyKey, Long> {

    // ============================================================
    // FIND IDEMPOTENCY KEY FOR ORGANIZATION
    // ============================================================

    Optional<IdempotencyKey> findByKeyAndOrganization(
            String key,
            Organization organization
    );


    // ============================================================
    // CHECK WHETHER KEY ALREADY EXISTS
    // ============================================================

    boolean existsByKeyAndOrganization(
            String key,
            Organization organization
    );
}