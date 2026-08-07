package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {

    // ============================================================
    // ALL BRANCHES FOR ORGANIZATION
    // ============================================================

    List<Branch> findByOrganization_Id(
            Long organizationId
    );


    // ============================================================
    // ACTIVE BRANCHES FOR ORGANIZATION
    // ============================================================

    List<Branch> findByOrganization_IdAndActiveTrue(
            Long organizationId
    );


    // ============================================================
    // SINGLE BRANCH WITH TENANT ISOLATION
    // ============================================================

    Optional<Branch> findByIdAndOrganization_Id(
            Long branchId,
            Long organizationId
    );
}