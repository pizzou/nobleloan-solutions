package com.patrick.fintech.loan_backend.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.patrick.fintech.loan_backend.model.Organization;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

/**
 * Guard cash-reporting semantics: a receipt may be partially allocated without
 * the installment being fully marked paid.
 *
 * These JPQL contract tests complement, but do not replace, a database-backed
 * integration test using actual payment transactions.
 */
class CashReceiptAggregateQueryTest {

    @Test
    void collectedSinceUsesActualDatedReceiptsRatherThanInstallmentCompletion() throws Exception {
        String jpql = query("sumCollectedSince", Organization.class, LocalDate.class);
        assertCashReceiptCriteria(jpql);
    }

    @Test
    void dashboardAggregatesIncludePositivePartialReceipts() throws Exception {
        String jpql = query("getDashboardPaymentAggregate",
                Long.class, LocalDate.class, LocalDate.class);
        assertCashReceiptCriteria(jpql);
        assertTrue(jpql.contains("p.paidDate <= :to"));
    }

    @Test
    void scopedDashboardAggregatePreservesOwnerOnlyRestriction() throws Exception {
        String jpql = query("getVisibleDashboardPaymentAggregate",
                Long.class, LocalDate.class, LocalDate.class, boolean.class);
        assertCashReceiptCriteria(jpql);
        assertTrue(jpql.contains(":includeBusinessOwnerOnly = true"));
        assertTrue(jpql.contains("l.businessOwnerOnly"));
    }

    @Test
    void importedHistoricalAggregatePreservesUndatedOpeningRows() throws Exception {
        String jpql = query("getImportedPaymentComponentAggregateAsOf",
                Long.class, Long.class, LocalDate.class);
        assertTrue(jpql.contains("l.imported = true"));
        assertTrue(jpql.contains("p.paid = true"),
                "Legacy installment history is not necessarily dated or an individual cash event");
        assertTrue(jpql.contains("p.paidDate IS NULL OR p.paidDate <= :asOf"));
        assertTrue(jpql.contains(":branchId IS NULL"));
    }

    @Test
    void installmentOverdueDetectionRemainsCompletionBased() throws Exception {
        String jpql = query("countDistinctOverdueLoans", Long.class, LocalDate.class);
        assertTrue(jpql.contains("p.paid = false"));
        assertTrue(jpql.contains("p.dueDate < :today"));
    }

    private static String query(String method, Class<?>... parameterTypes) throws Exception {
        return PaymentRepository.class.getMethod(method, parameterTypes)
                .getAnnotation(Query.class).value();
    }

    private static void assertCashReceiptCriteria(String jpql) {
        assertTrue(jpql.contains("p.deletedAt IS NULL"));
        assertTrue(jpql.contains("p.amountPaid > 0"));
        assertTrue(jpql.contains("p.paidDate"));
        assertFalse(jpql.contains("p.paid = true"));
    }
}
