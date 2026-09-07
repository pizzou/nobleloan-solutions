package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    List<BankAccount> findByOrganization_IdOrderByNameAsc(Long orgId);
    Optional<BankAccount> findByIdAndOrganization_Id(Long id, Long orgId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BankAccount> findByIdAndOrganization_IdForUpdate(Long id, Long orgId);
}
