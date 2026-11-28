package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.PaymentReview;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentReviewRepository extends JpaRepository<PaymentReview, Long> {
    Optional<PaymentReview> findByOrganizationIdAndRequestKey(Long organizationId, String requestKey);

    boolean existsByOrganizationIdAndReferenceIgnoreCaseAndStatusNot(
            Long organizationId, String reference, PaymentReview.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PaymentReview r join r.loan l where r.organizationId = :org and r.id = :id and l.organization.id = :org and l.deletedAt is null")
    Optional<PaymentReview> findForDecision(@Param("org") Long organizationId, @Param("id") Long id);

    @Query("""
            select r from PaymentReview r join fetch r.loan l
            where r.organizationId = :org and l.organization.id = :org and l.deletedAt is null
              and (:owner = true or (r.submittedBy = :userId and coalesce(l.businessOwnerOnly, false) = false))
              and (:status is null or r.status = :status)
            order by r.submittedAt desc, r.id desc
            """)
    List<PaymentReview> findVisible(@Param("org") Long organizationId, @Param("userId") Long userId,
                                    @Param("owner") boolean owner, @Param("status") PaymentReview.Status status,
                                    Pageable pageable);
}
