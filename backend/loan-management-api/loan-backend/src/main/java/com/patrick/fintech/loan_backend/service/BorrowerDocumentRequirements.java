package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.DocumentType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared source of truth for public and staff borrower KYC requirements.
 *
 * Every borrower must submit National ID, bank statement and selfie.
 * The marital-status certificate and employment evidence are profile-specific.
 */
public final class BorrowerDocumentRequirements {

    private static final Set<String> EMPLOYEE_TYPES = Set.of(
            "EMPLOYEE", "EMPLOYED", "PERMANENT", "CONTRACT", "SALARIED");

    private static final Set<String> BUSINESS_TYPES = Set.of(
            "SELF_EMPLOYED", "SELFEMPLOYED", "BUSINESS",
            "BUSINESS_OWNER", "COMPANY", "COMPANY_OWNER");

    private BorrowerDocumentRequirements() {
    }

    public static boolean isSupportedMaritalStatus(String maritalStatus) {
        String normalized = normalize(maritalStatus);
        return "MARRIED".equals(normalized) || "SINGLE".equals(normalized);
    }

    public static List<DocumentType> forProfile(String maritalStatus, String employmentType) {
        String marital = normalize(maritalStatus);
        if (!isSupportedMaritalStatus(marital)) {
            throw new IllegalArgumentException(
                    "Marital status must be SINGLE or MARRIED so the correct certificate can be collected.");
        }

        List<DocumentType> required = new ArrayList<>();
        required.add(DocumentType.NATIONAL_ID);
        required.add(DocumentType.BANK_STATEMENT);
        required.add(DocumentType.SELFIE);
        required.add("MARRIED".equals(marital)
                ? DocumentType.MARRIAGE_CERTIFICATE
                : DocumentType.SINGLE_CERTIFICATE);

        String employment = normalize(employmentType).replace('-', '_').replace(' ', '_');
        if (EMPLOYEE_TYPES.contains(employment)) {
            required.add(DocumentType.PAYSLIP);
        } else if (BUSINESS_TYPES.contains(employment)) {
            required.add(DocumentType.BUSINESS_REGISTRATION);
        }

        return List.copyOf(required);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
