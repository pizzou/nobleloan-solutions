package com.patrick.fintech.loan_backend.repository;
import com.patrick.fintech.loan_backend.model.FinancialApproval;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface FinancialApprovalRepository extends JpaRepository<FinancialApproval,Long>{
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select a from FinancialApproval a where a.id=:id and a.organization.id=:orgId")
 Optional<FinancialApproval> findForUpdate(@Param("id") Long id,@Param("orgId") Long orgId);
 List<FinancialApproval> findByOrganization_IdAndStatusOrderByCreatedAtAsc(Long orgId,String status);
 Optional<FinancialApproval> findFirstByOrganization_IdAndOperationTypeAndOperationIdAndStatus(Long orgId,String operationType,String operationId,String status);
}
