package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.BankStatementLine;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BankStatementLineRepository
        extends JpaRepository<BankStatementLine, Long> {

    List<BankStatementLine>
    findByOrganization_IdAndBankAccount_IdAndTransactionDateBetweenOrderByTransactionDateAscIdAsc(
            Long organizationId,
            Long bankAccountId,
            LocalDate from,
            LocalDate to
    );

    List<BankStatementLine>
    findByOrganization_IdAndBankAccount_IdAndReconciliationStatusOrderByTransactionDateAscIdAsc(
            Long organizationId,
            Long bankAccountId,
            String reconciliationStatus
    );

    Optional<BankStatementLine>
    findByOrganization_IdAndBankAccount_IdAndExternalId(
            Long organizationId,
            Long bankAccountId,
            String externalId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b
            FROM BankStatementLine b
            WHERE b.id = :id
              AND b.organization.id = :orgId
            """)
    Optional<BankStatementLine> findForUpdate(
            @Param("id") Long id,
            @Param("orgId") Long organizationId
    );
}
