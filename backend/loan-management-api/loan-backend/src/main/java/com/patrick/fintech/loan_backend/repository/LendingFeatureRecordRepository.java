package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.LendingFeatureRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LendingFeatureRecordRepository extends JpaRepository<LendingFeatureRecord, Long> {
    List<LendingFeatureRecord> findByOrganization_IdAndFeatureTypeOrderByCreatedAtDesc(Long orgId, String featureType);
    List<LendingFeatureRecord> findByOrganization_IdAndStatusOrderByCreatedAtDesc(Long orgId, String status);
    List<LendingFeatureRecord> findByOrganization_IdAndLoan_IdOrderByCreatedAtDesc(Long orgId, Long loanId);
    List<LendingFeatureRecord> findByOrganization_IdAndBorrower_IdOrderByCreatedAtDesc(Long orgId, Long borrowerId);
    long countByOrganization_IdAndFeatureTypeAndStatus(Long orgId, String featureType, String status);
}
