package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.DocumentType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared rules for staff registration and loan approval, matching public
 * applications.
 */
public final class BorrowerDocumentRequirements {
    private BorrowerDocumentRequirements() {
    }

    public static List<DocumentType> required(String maritalStatus, String employmentType) {
        List<DocumentType> result = new ArrayList<>(List.of(
                DocumentType.NATIONAL_ID, DocumentType.BANK_STATEMENT, DocumentType.SELFIE));
        result.add("MARRIED".equals(normalize(maritalStatus))
                ? DocumentType.MARRIAGE_CERTIFICATE
                : DocumentType.SINGLE_CERTIFICATE);
        String employment = normalize(employmentType);
        if (Set.of("EMPLOYEE", "EMPLOYED", "PERMANENT", "CONTRACT", "SALARIED").contains(employment)) {
            result.add(DocumentType.PAYSLIP);
        } else if (Set.of("SELF_EMPLOYED", "SELFEMPLOYED", "BUSINESS", "BUSINESS_OWNER",
                "COMPANY", "COMPANY_OWNER").contains(employment)) {
            result.add(DocumentType.BUSINESS_REGISTRATION);
        }
        return List.copyOf(result);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
