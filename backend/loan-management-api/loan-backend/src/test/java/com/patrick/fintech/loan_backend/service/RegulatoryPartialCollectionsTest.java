package com.patrick.fintech.loan_backend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.patrick.fintech.loan_backend.model.Payment;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RegulatoryPartialCollectionsTest {
    @Test
    void partlyPaidInstallmentStillCountsAsCashReceived() {
        Payment payment = new Payment();
        payment.setPaid(false);
        payment.setStatus(Payment.PaymentStatus.PARTIALLY_PAID);
        payment.setAmountPaid(new BigDecimal("23400000.00"));
        assertTrue(RegulatoryReportingService.hasReceivedCash(payment));
    }

    @Test
    void unpaidScheduledInstallmentDoesNotCountAsCollection() {
        Payment payment = new Payment();
        payment.setPaid(false);
        payment.setAmountPaid(BigDecimal.ZERO);
        assertFalse(RegulatoryReportingService.hasReceivedCash(payment));
    }
}
