package com.patrick.fintech.loan_backend.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression: approving only an oldest-unpaid due-date transcription correction
 * must never be mistaken for permission to change financial values.
 */
class LegacyLoanDueDateApprovalTest {
    private Map<String, Object> original() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("amount", 1000000);
        source.put("principalOutstanding", 600000);
        source.put("totalPaid", 400000);
        source.put("totalRepayable", 1000000);
        source.put("penaltiesAssessed", 0);
        source.put("durationMonths", 3);
        source.put("maturityDate", "2026-12-11");
        source.put("oldestUnpaidDueDate", "2026-11-09");
        return source;
    }

    @Test
    void dateOnlyChangeIsRecognized() {
        Map<String, Object> before = original();
        Map<String, Object> after = new LinkedHashMap<>(before);
        after.put("oldestUnpaidDueDate", "2026-09-11");
        assertTrue(LegacyLoanCorrectionService.isDueDateOnly(before, after));
        assertEquals(600000, before.get("principalOutstanding"));
        assertEquals(before.get("principalOutstanding"), after.get("principalOutstanding"));
    }

    @Test
    void unchangedDateIsNotACorrection() {
        Map<String, Object> before = original();
        assertFalse(LegacyLoanCorrectionService.isDueDateOnly(before, new LinkedHashMap<>(before)));
    }

    @Test
    void financialChangeCannotBeAppliedAsDateOnly() {
        Map<String, Object> before = original();
        Map<String, Object> after = new LinkedHashMap<>(before);
        after.put("oldestUnpaidDueDate", "2026-09-11");
        after.put("principalOutstanding", 500000);
        assertFalse(LegacyLoanCorrectionService.isDueDateOnly(before, after));
    }

    @Test
    void durationChangeCannotBeAppliedAsDateOnly() {
        Map<String, Object> before = original();
        Map<String, Object> after = new LinkedHashMap<>(before);
        after.put("oldestUnpaidDueDate", "2026-09-11");
        after.put("durationMonths", 4);
        assertFalse(LegacyLoanCorrectionService.isDueDateOnly(before, after));
    }

    @Test
    void missingFieldsCannotBeTreatedAsDateOnly() {
        Map<String, Object> before = original();
        Map<String, Object> after = new LinkedHashMap<>(before);
        after.put("oldestUnpaidDueDate", "2026-09-11");
        after.remove("totalPaid");
        assertFalse(LegacyLoanCorrectionService.isDueDateOnly(before, after));
    }
}
