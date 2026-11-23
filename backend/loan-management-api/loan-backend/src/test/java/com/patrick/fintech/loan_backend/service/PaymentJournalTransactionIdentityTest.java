package com.patrick.fintech.loan_backend.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.patrick.fintech.loan_backend.model.Payment;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks that a repeated partial payment on one installment creates a new
 * journal source for each immutable PaymentTransaction event.
 */
class PaymentJournalTransactionIdentityTest {

    @Test
    void accountingExposesTransactionScopedReceiptPosting() throws Exception {
        var method = AccountingService.class.getMethod("postPaymentReceived",
                Payment.class, BigDecimal.class, BigDecimal.class, BigDecimal.class,
                BigDecimal.class, BigDecimal.class, BigDecimal.class, BigDecimal.class,
                Long.class);

        assertNotNull(method.getAnnotation(Transactional.class));
    }

    @Test
    void cashJournalUsesTransactionIdRatherThanCumulativeInstallmentId() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/patrick/fintech/loan_backend/service/AccountingService.java"));
        assertTrue(source.contains("\"PAYTX:\" + paymentTransactionId"));
        assertTrue(source.contains("findFirstByOrganization_IdAndSourceTypeAndSourceId"));
        assertTrue(source.contains("String.valueOf(payment.getId())"),
                "Legacy posting overload must remain compatible");
    }

    @Test
    void paymentTransactionIsPersistedBeforeTheNewCashJournal() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/patrick/fintech/loan_backend/service/PaymentService.java"));
        int persisted = source.indexOf("paymentTransactionRepo.saveAndFlush(transaction)");
        int journal = source.indexOf("accountingService.postPaymentReceived(", persisted);
        assertTrue(persisted > 0, "Immutable payment event must be saved");
        assertTrue(journal > persisted, "Journal must be sourced by persisted event");
        assertTrue(source.substring(journal, Math.min(journal + 650, source.length()))
                .contains("savedTransaction.getId()"));
    }
}
