package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByOrganization_IdAndTransactionReference(Long organizationId, String reference);
    Optional<PaymentTransaction> findByOrganization_IdAndId(Long organizationId, Long id);
    @EntityGraph(attributePaths = { "loan", "loan.borrower", "organization", "recordedBy", "installment" })
    List<PaymentTransaction> findByOrganization_IdOrderByCreatedAtDesc(Long organizationId);

    @EntityGraph(attributePaths = { "loan", "loan.borrower", "organization", "recordedBy", "installment" })
    List<PaymentTransaction> findByLoanIdOrderByCreatedAtDesc(Long loanId);
    List<PaymentTransaction> findByInstallmentIdOrderByCreatedAtAsc(Long installmentId);
}
