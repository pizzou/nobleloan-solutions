package com.patrick.fintech.loan_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

/**
 * One globally shared, PostgreSQL-backed sequence for every NEW loan reference.
 * A database sequence, rather than MAX(reference_number) + 1, is safe with
 * simultaneous users, multiple backend instances and asynchronous imports.
 * Existing loan references are never renumbered (they may be on posted ledgers).
 */
@Service
@RequiredArgsConstructor
public class LoanReferenceService {

    private final JdbcTemplate jdbc;

    public String allocate() {
        Long number = jdbc.queryForObject(
                "SELECT nextval('public.nls_loan_reference_seq')", Long.class);
        if (number == null || number < 1) {
            throw new IllegalStateException("Loan reference sequence returned an invalid value");
        }
        return format(number);
    }

    /** Read-only, NON-RESERVING preview; concurrent creation may change the result. */
    public long firstAvailableForPreview() {
        Map<String, Object> state = jdbc.queryForMap(
                "SELECT last_value, is_called FROM public.nls_loan_reference_seq");
        long last = ((Number) state.get("last_value")).longValue();
        return Boolean.TRUE.equals(state.get("is_called")) ? Math.addExact(last, 1L) : last;
    }

    public String format(long number) {
        if (number < 1) {
            throw new IllegalArgumentException("Loan reference sequence must be positive");
        }
        return "NLS-" + String.format(Locale.ROOT, "%06d", number);
    }

    /**
     * Check ALL loan rows, including recycled/owner-only loans that the Loan
     * entity's visibility filter would otherwise hide. This check complements
     * the database unique indexes, which resolve concurrent duplicate imports.
     */
    public boolean previouslyImported(Long organizationId,
                                      String originalSourceReference,
                                      String sourceFingerprint,
                                      String oldDeterministicReference) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM public.loans
                    WHERE organization_id = ?
                      AND (
                        legacy_import_fingerprint = ?
                        OR LOWER(BTRIM(legacy_source_reference)) = LOWER(CAST(? AS TEXT))
                        OR (COALESCE(imported, FALSE) = TRUE
                            AND LOWER(BTRIM(reference_number)) = LOWER(CAST(? AS TEXT)))
                        OR (COALESCE(imported, FALSE) = TRUE
                            AND LOWER(BTRIM(reference_number)) = LOWER(CAST(? AS TEXT)))
                      )
                )
                """, Boolean.class,
                organizationId,
                sourceFingerprint,
                originalSourceReference,
                oldDeterministicReference,
                originalSourceReference);
        return Boolean.TRUE.equals(exists);
    }
}
