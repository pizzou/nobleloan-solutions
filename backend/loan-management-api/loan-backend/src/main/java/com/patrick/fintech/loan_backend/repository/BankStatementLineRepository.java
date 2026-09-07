package com.patrick.fintech.loan_backend.repository;
import com.patrick.fintech.loan_backend.model.BankStatementLine; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.time.*; import java.util.*;
public interface BankStatementLineRepository extends JpaRepository<BankStatementLine,Long>{
 List<BankStatementLine> findByOrganization_IdAndBankAccount_IdAndTransactionDateBetweenOrderByTransactionDateAscIdAsc(Long orgId,Long bankAccountId,LocalDate from,LocalDate to);
 List<BankStatementLine> findByOrganization_IdAndBankAccount_IdAndReconciliationStatusOrderByTransactionDateAscIdAsc(Long orgId,Long bankAccountId,String status);
 Optional<BankStatementLine> findByOrganization_IdAndBankAccount_IdAndExternalId(Long orgId,Long bankAccountId,String externalId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select b from BankStatementLine b where b.id=:id and b.organization.id=:orgId") Optional<BankStatementLine> findForUpdate(@Param("id")Long id,@Param("orgId")Long orgId);
}
