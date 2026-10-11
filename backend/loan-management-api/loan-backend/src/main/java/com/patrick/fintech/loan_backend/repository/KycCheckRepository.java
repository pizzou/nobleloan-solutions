package com.patrick.fintech.loan_backend.repository;
import com.patrick.fintech.loan_backend.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public interface KycCheckRepository extends JpaRepository<KycCheck,Long> {
    List<KycCheck> findByBorrower_IdAndOrganization_Id(Long borrowerId, Long organizationId);
    List<KycCheck> findByOrganization_Id(Long orgId);
    Optional<KycCheck> findFirstByBorrower_IdAndOrganization_IdAndCheckTypeOrderByCreatedAtDesc(Long borrowerId, Long organizationId, KycCheck.CheckType checkType);
    List<KycCheck> findByOrganization_IdAndResult(Long orgId,KycCheck.CheckResult result);
}
