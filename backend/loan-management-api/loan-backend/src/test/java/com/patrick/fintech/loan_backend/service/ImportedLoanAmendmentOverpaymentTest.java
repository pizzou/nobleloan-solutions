package com.patrick.fintech.loan_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ImportedLoanAmendmentOverpaymentTest {
    @Test
    void releasesHistoricalRefundLiabilityAndOnlyReducesEquityByContractReduction() {
        // Imported loan #130: GL has RWF 50k interest, RWF 50k management,
        // and an RWF 50k refundable payment surplus. Repricing to 25k + 25k
        // makes all receivables and the refund liability zero.
        BigDecimal receivableDelta = new BigDecimal("-100000.00");
        BigDecimal refundableLiabilityDelta = new BigDecimal("-50000.00");
        BigDecimal equityDebit = AccountingService.importedMigrationEquityChange(
                receivableDelta, refundableLiabilityDelta);

        assertEquals(new BigDecimal("50000.00"), equityDebit);
        // Journal: Dr 3010 RWF50k + Dr 2100 RWF50k,
        // Cr 1150 RWF50k + Cr 1160 RWF50k.
        assertEquals(new BigDecimal("100000.00"),
                equityDebit.add(refundableLiabilityDelta.negate()));
    }
}
