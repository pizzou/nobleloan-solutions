package com.patrick.fintech.loan_backend.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class LoanReportingPeriodQueryTest {
    @Test
    void regulatoryDisbursementPeriodIncludesVerifiedLegacyStartDatesWithoutLosingScope() throws Exception {
        var method = LoanRepository.class.getMethod(
                "findVisibleLoansDisbursedDuringPeriodWithLegacyDates",
                Long.class, Long.class, LocalDateTime.class, LocalDateTime.class,
                LocalDate.class, LocalDate.class, boolean.class);
        String jpql = method.getAnnotation(Query.class).value();
        assertTrue(jpql.contains("l.disbursedAt IS NULL"));
        assertTrue(jpql.contains("l.startDate >= :fromDate"));
        assertTrue(jpql.contains("l.startDate < :toDate"));
        assertTrue(jpql.contains("l.imported = true"));
        assertTrue(jpql.contains(":includeBusinessOwnerOnly = true"));
        assertTrue(jpql.contains("l.deletedAt IS NULL"));
    }
}
