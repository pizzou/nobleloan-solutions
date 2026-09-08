
package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.AccountingPeriod;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, Long> {

    List<AccountingPeriod> findByOrganization_IdOrderByYearDescMonthDesc(Long organizationId);

    Optional<AccountingPeriod> findByOrganization_IdAndYearAndMonth(
            Long organizationId,
            int year,
            int month
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ap
            from AccountingPeriod ap
            where ap.organization.id = :organizationId
              and ap.year = :year
              and ap.month = :month
            """)
    Optional<AccountingPeriod> findByOrganization_IdAndYearAndMonthForUpdate(
            @Param("organizationId") Long organizationId,
            @Param("year") int year,
            @Param("month") int month
    );
}

