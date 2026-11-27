package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.BorrowerFile;
import com.patrick.fintech.loan_backend.model.DocumentType;
import com.patrick.fintech.loan_backend.model.VerificationStatus;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Borrower-owned KYC can be reused on a second loan without another upload.
 * Time-sensitive financial evidence gets a review-by date; identity and civil
 * status documents are not assumed to expire by age because the actual document
 * expiry dates are not captured by the existing borrower_files schema.
 * A reviewer can always mark a document EXPIRED or REPLACEMENT_REQUESTED.
 */
public final class BorrowerDocumentReusePolicy {
    private BorrowerDocumentReusePolicy() {}

    private static final Set<DocumentType> SHARED_KYC = Set.of(
            DocumentType.NATIONAL_ID, DocumentType.PASSPORT,
            DocumentType.DRIVING_LICENSE, DocumentType.RESIDENCE_PERMIT,
            DocumentType.PROOF_OF_ADDRESS, DocumentType.BANK_STATEMENT,
            DocumentType.PAYSLIP, DocumentType.EMPLOYMENT_LETTER,
            DocumentType.BUSINESS_REGISTRATION, DocumentType.TAX_CERTIFICATE,
            DocumentType.MARRIAGE_CERTIFICATE, DocumentType.SINGLE_CERTIFICATE,
            DocumentType.SELFIE);

    private static final Map<DocumentType, Integer> REVIEW_DAYS = new EnumMap<>(DocumentType.class);
    static {
        REVIEW_DAYS.put(DocumentType.BANK_STATEMENT, 90);
        REVIEW_DAYS.put(DocumentType.PAYSLIP, 90);
        REVIEW_DAYS.put(DocumentType.PROOF_OF_ADDRESS, 365);
        REVIEW_DAYS.put(DocumentType.SELFIE, 365);
        REVIEW_DAYS.put(DocumentType.EMPLOYMENT_LETTER, 365);
        REVIEW_DAYS.put(DocumentType.TAX_CERTIFICATE, 365);
    }

    public static LocalDate reviewDueAt(BorrowerFile file) {
        if (file == null || file.getDocumentType() == null || file.getUploadedAt() == null) {
            return null;
        }
        Integer days = REVIEW_DAYS.get(file.getDocumentType());
        return days == null ? null : file.getUploadedAt().toLocalDate().plusDays(days);
    }

    public static boolean needsUpdate(BorrowerFile file, LocalDate today) {
        if (file == null || file.getData() == null || file.getData().length == 0
                || file.getDocumentType() == null || !SHARED_KYC.contains(file.getDocumentType())) {
            return true;
        }
        if (file.getLoan() != null) {
            return true; // Loan-specific collateral, contracts and other attachments never carry over.
        }
        VerificationStatus status = file.getVerificationStatus();
        if (status != VerificationStatus.VERIFIED
                && status != VerificationStatus.PENDING
                && status != VerificationStatus.UNDER_REVIEW) {
            return true;
        }
        if (REVIEW_DAYS.containsKey(file.getDocumentType()) && file.getUploadedAt() == null) {
            return true; // Missing timestamp cannot establish current financial evidence.
        }
        LocalDate reviewDue = reviewDueAt(file);
        // A verified historic national ID with no expiry metadata remains reusable,
        // but staff are responsible for confirming the ID has not expired.
        return reviewDue != null && today.isAfter(reviewDue);
    }

    public static boolean reusable(BorrowerFile file, LocalDate today) {
        return !needsUpdate(file, today);
    }

    /** Reviewer warning (applies to all document types, not just reusable KYC). */
    public static boolean reviewerUpdateRequired(BorrowerFile file, LocalDate today) {
        if (file == null || file.getData() == null || file.getData().length == 0) return true;
        VerificationStatus status = file.getVerificationStatus();
        if (status == VerificationStatus.EXPIRED || status == VerificationStatus.REJECTED
                || status == VerificationStatus.REPLACEMENT_REQUESTED) return true;
        if (REVIEW_DAYS.containsKey(file.getDocumentType()) && file.getUploadedAt() == null) return true;
        LocalDate due = reviewDueAt(file);
        return due != null && today.isAfter(due);
    }
}
