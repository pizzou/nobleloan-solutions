package com.patrick.fintech.loan_backend.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class RegulatoryCashPaymentQueryTest {
    @Test
    void periodQueryIncludesPartialCashReceipts() throws Exception {
        var method = PaymentRepository.class.getMethod("findVisiblePaymentsDuringPeriod",
                Long.class, Long.class, LocalDate.class, LocalDate.class, boolean.class);
        String jpql = method.getAnnotation(Query.class).value();
        assertTrue(jpql.contains("p.amountPaid > 0"));
        assertFalse(jpql.contains("p.paid = true"));
        assertTrue(jpql.contains("p.deletedAt IS NULL"));
        assertTrue(jpql.contains(":includeBusinessOwnerOnly = true"));
    }
}
