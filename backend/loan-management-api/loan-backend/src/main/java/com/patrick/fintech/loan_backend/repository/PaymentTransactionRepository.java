package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.PaymentTransaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository
        extends JpaRepository<PaymentTransaction, Long> {

    // ============================================================
    // TRANSACTION REFERENCE LOOKUP
    // ============================================================

    Optional<PaymentTransaction>
    findByOrganization_IdAndTransactionReference(
            Long organizationId,
            String reference
    );


    // ============================================================
    // TENANT-SAFE ID LOOKUP
    // ============================================================

    Optional<PaymentTransaction>
    findByOrganization_IdAndId(
            Long organizationId,
            Long id
    );


    // ============================================================
    // LOAN TRANSACTION HISTORY
    // ============================================================

    /*
     * Existing method preserved for compatibility.
     */
    List<PaymentTransaction>
    findByLoanIdOrderByCreatedAtDesc(
            Long loanId
    );


    /*
     * Recommended for screens displaying transaction history.
     *
     * Prevents loading the complete transaction history into memory.
     */
    Page<PaymentTransaction>
    findByLoanIdOrderByCreatedAtDesc(
            Long loanId,
            Pageable pageable
    );


    // ============================================================
    // INSTALLMENT TRANSACTION HISTORY
    // ============================================================

    /*
     * Existing method preserved for compatibility.
     */
    List<PaymentTransaction>
    findByInstallmentIdOrderByCreatedAtAsc(
            Long installmentId
    );


    /*
     * Recommended paginated version.
     */
    Page<PaymentTransaction>
    findByInstallmentIdOrderByCreatedAtAsc(
            Long installmentId,
            Pageable pageable
    );
}