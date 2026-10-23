package com.patrick.fintech.loan_backend.util;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyLoanIdentityTest {
    private Map<String, String> loan(String date, String amount) {
        Map<String, String> row = new HashMap<>();
        row.put("national_id", "1198880004126183");
        row.put("start_date", date);
        row.put("amount", amount);
        row.put("principal_paid", "100000.00");
        row.put("interest_outstanding", "25000.00");
        return row;
    }

    @Test void paymentsAndFormattingDoNotCreateANewLoan() {
        Map<String, String> first = loan("2025-10-30", "5,000,000.00");
        Map<String, String> changed = loan("2025-10-30", "5000000");
        changed.put("principal_paid", "500000");
        assertThat(LegacyLoanIdentity.stableIdentity(1L, first))
                .isEqualTo(LegacyLoanIdentity.stableIdentity(1L, changed));
        assertThat(LegacyLoanIdentity.differingFields(first, changed))
                .containsExactly("principal_paid");
    }

    @Test void anotherLoanDateGetsAnotherIdentity() {
        assertThat(LegacyLoanIdentity.stableIdentity(1L, loan("2025-10-30", "5000000")))
                .isNotEqualTo(LegacyLoanIdentity.stableIdentity(1L, loan("2025-11-30", "5000000")));
    }

    @Test void conflictingAmountsSameDateAreCandidatesForReview() {
        Map<String, String> first = loan("2025-10-30", "8000000");
        Map<String, String> second = loan("2025-10-30", "2000000");
        assertThat(LegacyLoanIdentity.candidateKey(first))
                .isEqualTo(LegacyLoanIdentity.candidateKey(second));
        assertThat(LegacyLoanIdentity.differingFields(first, second)).contains("amount");
        assertThat(LegacyLoanIdentity.stableIdentity(1L, first))
                .isNotEqualTo(LegacyLoanIdentity.stableIdentity(1L, second));
    }

    @Test void sheetMetadataDoesNotChangeFullRowFingerprint() {
        Map<String, String> row = loan("2025-10-30", "5000000");
        String original = LegacyLoanIdentity.originalRowFingerprint(1L, row);
        row.put("__source_sheet", "Sheet1");
        row.put("__duplicate_count", "2");
        assertThat(LegacyLoanIdentity.originalRowFingerprint(1L, row)).isEqualTo(original);
    }

    @Test void nameDateProvidesOnlyAReviewLinkAcrossDifferingIds() {
        Map<String, String> first = loan("2025-10-30", "5000000");
        first.put("first_name", "NDIZEYE"); first.put("last_name", "LAMBERT");
        Map<String, String> second = new HashMap<>(first);
        second.put("national_id", "1198880004126184");
        second.put("first_name", "ndizeye ");
        assertThat(LegacyLoanIdentity.candidateKey(first))
                .isNotEqualTo(LegacyLoanIdentity.candidateKey(second));
        assertThat(LegacyLoanIdentity.weakNameDateKey(first))
                .isEqualTo(LegacyLoanIdentity.weakNameDateKey(second));
    }

    @Test void malformedIdentityMustNotBeAutoMatched() {
        Map<String, String> bad = loan("2025-10-30", "5000000");
        bad.put("national_id", "123456789012345");
        assertThat(LegacyLoanIdentity.candidateKey(bad)).isNull();
        assertThat(LegacyLoanIdentity.stableIdentity(1L, bad)).isNull();
    }
}
