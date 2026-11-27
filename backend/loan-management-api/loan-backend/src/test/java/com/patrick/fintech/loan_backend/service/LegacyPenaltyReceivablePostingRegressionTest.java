package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.repository.JournalLineRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the loan-level GL lookup used to allocate payments against accrued receivables. */
class LegacyPenaltyReceivablePostingRegressionTest {

    @Test
    void paymentAllocationIncludesHistoricalPenaltyOpeningAndPriorAmendments() throws Exception {
        var method = JournalLineRepository.class.getMethod("findReceivableLinesForLoanIdentity",
                Long.class, Long.class, Long.class, String.class);
        Query query = method.getAnnotation(Query.class);
        assertNotNull(query);
        assertTrue(query.value().contains("'LEGACY_PENALTY_OPENING'"),
                "Imported historical penalty receivables must be cleared before recognizing income");
        assertTrue(query.value().contains("'PAYMENT_RECEIVED'"));
        assertTrue(query.value().contains("'LOAN_AMENDMENT_RECONCILIATION'"));
    }
}
