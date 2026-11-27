package com.patrick.fintech.loan_backend.util;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Identity and comparison rules for historical loans. A source worksheet's "NO"
 * is NOT a globally unique loan reference. Identity is separate from mutable
 * balances, so a re-upload with updated payments cannot create another loan.
 *
 * A borrower can have separate loans on different dates. Two records with the
 * same identity and date but different principal require human verification.
 */
public final class LegacyLoanIdentity {
    private static final List<String> FINANCIAL_FIELDS = List.of(
            "first_name", "last_name", "amount", "start_date", "duration_months", "interest_rate",
            "management_fee_rate", "application_fee", "application_fee_paid",
            "principal_paid", "interest_paid", "interest_outstanding",
            "management_fee_paid", "total_management_fee_balance",
            "penalties_assessed", "penalties_paid", "extension_fee_assessed",
            "extension_fee_paid", "extension_fee_outstanding",
            "outstanding_balance", "total_paid", "total_repayable", "status",
            "next_due_date");

    private LegacyLoanIdentity() { }

    /** Matches possible duplicates even when two sheets disagree on amount. */
    public static String candidateKey(Map<String, String> row) {
        String id = stripped(row.get("national_id"));
        String date = normalizedDate(row.get("start_date"));
        if (!id.matches("[0-9]{16}") || date == null) {
            return null; // Do not guess identity for malformed rows.
        }
        return id + "|" + date;
    }

    /** Weak review-only link for national-ID discrepancies; NEVER auto-merge on name. */
    public static String weakNameDateKey(Map<String, String> row) {
        String first = normalizedName(row.get("first_name"));
        String last = normalizedName(row.get("last_name"));
        String date = normalizedDate(row.get("start_date"));
        if (first.isEmpty() || last.isEmpty() || date == null) return null;
        return first + "|" + last + "|" + date;
    }

    private static String normalizedName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    /** Stable digest stored on the loan; does not include mutable balances. */
    public static String stableIdentity(Long organizationId, Map<String, String> row) {
        String key = candidateKey(row);
        String principal = normalizedNumber(row.get("amount"));
        if (organizationId == null || key == null || principal == null
                || new BigDecimal(principal).signum() <= 0) {
            return null;
        }
        return com.patrick.fintech.loan_backend.security.HmacIndexer.index(
                "NLS-LOAN-V1|" + organizationId + "|" + key + "|" + principal);
    }

    /** Used ONLY for within-upload conflict analysis, not for persisted identity. */
    public static List<String> differingFields(Map<String, String> first, Map<String, String> second) {
        List<String> conflicts = new ArrayList<>();
        for (String field : FINANCIAL_FIELDS) {
            String left = normalizedField(field, first.get(field));
            String right = normalizedField(field, second.get(field));
            if (!left.equals(right)) {
                conflicts.add(field);
            }
        }
        return conflicts;
    }

    /** Fallback fingerprint excludes transient worksheet metadata. */
    public static String originalRowFingerprint(Long organizationId, Map<String, String> row) {
        TreeMap<String, String> stable = new TreeMap<>();
        row.forEach((key, value) -> {
            if (!key.startsWith("__")) {
                stable.put(key, value);
            }
        });
        return sha256(organizationId + "|" + stable);
    }

    private static String normalizedField(String field, String value) {
        String v = value == null ? "" : value.trim();
        if (field.equals("start_date") || field.equals("next_due_date")) {
            String parsed = normalizedDate(v);
            return parsed == null ? v : parsed;
        }
        if (field.equals("first_name") || field.equals("last_name")) {
            return normalizedName(v);
        }
        if (field.equals("status")) {
            return v.toUpperCase(Locale.ROOT);
        }
        if (field.equals("amount") || field.contains("paid") || field.contains("balance")
                || field.contains("fee") || field.contains("penalties")
                || field.contains("outstanding") || field.equals("total_repayable")
                || field.equals("interest_rate")) {
            String normalized = normalizedNumber(v);
            return normalized == null ? v : normalized;
        }
        return v;
    }

    private static String normalizedNumber(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.trim().replace(",", "")).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String normalizedDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim()).toString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String stripped(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim();
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
