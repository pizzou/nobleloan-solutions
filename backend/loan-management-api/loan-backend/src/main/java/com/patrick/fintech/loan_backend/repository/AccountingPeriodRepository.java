package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.AccountingPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, Long> {
    Optional<AccountingPeriod> findByOrganization_IdAndYearAndMonth(Long organizationId, int year, int month);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountingPeriod> findByOrganization_IdAndYearAndMonthForUpdate(Long organizationId, int year, int month);
}
