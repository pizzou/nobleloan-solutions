package com.patrick.fintech.loan_backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanReferenceServiceTest {

    @Mock
    JdbcTemplate jdbc;

    @Test
    void numbersUseOneZeroPaddedNlsFormat() {
        LoanReferenceService numbers = new LoanReferenceService(jdbc);
        assertThat(numbers.format(1)).isEqualTo("NLS-000001");
        assertThat(numbers.format(7)).isEqualTo("NLS-000007");
        assertThat(numbers.format(1000000)).isEqualTo("NLS-1000000");
    }

    @Test
    void sequenceAllocationReturnsNextGloballyAllocatedNumber() {
        when(jdbc.queryForObject(
                "SELECT nextval('public.nls_loan_reference_seq')", Long.class))
                .thenReturn(7L, 8L);
        LoanReferenceService numbers = new LoanReferenceService(jdbc);
        assertThat(numbers.allocate()).isEqualTo("NLS-000007");
        assertThat(numbers.allocate()).isEqualTo("NLS-000008");
    }

    @Test
    void previewsDoNotConsumeTheRealSequenceOrSkipFailedRows() {
        when(jdbc.queryForMap("SELECT last_value, is_called FROM public.nls_loan_reference_seq"))
                .thenReturn(Map.of("last_value", 6L, "is_called", true));
        LoanReferenceService numbers = new LoanReferenceService(jdbc);
        LegacyImportReferenceSession preview = new LegacyImportReferenceSession();

        assertThat(preview.previewReference(numbers)).isEqualTo("NLS-000007");
        preview.recordSuccess("loan-a", null, "identity-a");
        // Invalid row never calls previewReference or recordSuccess.
        assertThat(preview.previewReference(numbers)).isEqualTo("NLS-000008");
        assertThat(preview.alreadySeen("loan-a", null)).isTrue();
        assertThat(preview.alreadySeen("loan-b", null)).isFalse();
    }

    @Test
    void uncalledSequenceBeginsWithItsStartValue() {
        when(jdbc.queryForMap("SELECT last_value, is_called FROM public.nls_loan_reference_seq"))
                .thenReturn(Map.of("last_value", 1L, "is_called", false));
        assertThat(new LoanReferenceService(jdbc).firstAvailableForPreview()).isEqualTo(1L);
    }

    @Test
    void repeatedSourceReferencesAreRejectedWithinAWorkbook() {
        LegacyImportReferenceSession session = new LegacyImportReferenceSession();
        session.recordSuccess("one-fingerprint", "NLS-OLD-42", "stable-identity");
        assertThat(session.alreadySeen("another-fingerprint", "nls-old-42")).isTrue();
        assertThat(session.alreadySeen("one-fingerprint", null)).isTrue();
        assertThat(session.alreadySeenIdentity("stable-identity")).isTrue();
    }
}
