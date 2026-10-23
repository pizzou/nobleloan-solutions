package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.BorrowerFile;
import com.patrick.fintech.loan_backend.model.DocumentType;
import com.patrick.fintech.loan_backend.model.VerificationStatus;
import com.patrick.fintech.loan_backend.repository.BorrowerFileRepository;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BorrowerDocumentReusePolicyTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    private BorrowerFile file(DocumentType type, VerificationStatus status, int daysOld) {
        BorrowerFile file = new BorrowerFile();
        file.setDocumentType(type);
        file.setVerificationStatus(status);
        file.setData(new byte[]{1, 2, 3});
        file.setUploadedAt(TODAY.minusDays(daysOld).atStartOfDay());
        return file;
    }

    @Test
    void verifiedIdentityDocumentIsReusedWithoutArbitraryAgeExpiry() {
        assertTrue(BorrowerDocumentReusePolicy.reusable(
                file(DocumentType.NATIONAL_ID, VerificationStatus.VERIFIED, 600), TODAY));
    }

    @Test
    void recentBankStatementReusedAndOldOneRequiresUpdate() {
        assertTrue(BorrowerDocumentReusePolicy.reusable(
                file(DocumentType.BANK_STATEMENT, VerificationStatus.VERIFIED, 70), TODAY));
        assertTrue(BorrowerDocumentReusePolicy.needsUpdate(
                file(DocumentType.BANK_STATEMENT, VerificationStatus.VERIFIED, 95), TODAY));
    }

    @Test
    void rejectedAndReplacementRequestedCannotBeReused() {
        assertTrue(BorrowerDocumentReusePolicy.needsUpdate(
                file(DocumentType.NATIONAL_ID, VerificationStatus.REJECTED, 1), TODAY));
        assertTrue(BorrowerDocumentReusePolicy.needsUpdate(
                file(DocumentType.NATIONAL_ID, VerificationStatus.REPLACEMENT_REQUESTED, 1), TODAY));
    }

    @Test
    void pendingEvidenceMayCarryForwardButStillRequiresOfficerReview() {
        assertTrue(BorrowerDocumentReusePolicy.reusable(
                file(DocumentType.NATIONAL_ID, VerificationStatus.PENDING, 1), TODAY));
    }

    @Test
    void signedContractCannotBeReusedOnAnotherLoan() {
        assertFalse(BorrowerDocumentReusePolicy.reusable(
                file(DocumentType.SIGNED_LOAN_CONTRACT, VerificationStatus.VERIFIED, 1), TODAY));
    }

    @Test
    void missingBinaryRequiresNewUpload() {
        BorrowerFile missing = file(DocumentType.NATIONAL_ID, VerificationStatus.VERIFIED, 1);
        missing.setData(null);
        assertTrue(BorrowerDocumentReusePolicy.needsUpdate(missing, TODAY));
    }

    @Test
    void serviceChecksOnlyLatestCopy() {
        BorrowerFileRepository repository = mock(BorrowerFileRepository.class);
        BorrowerFileService service = new BorrowerFileService(repository,
                mock(BorrowerRepository.class),
                mock(SecureFileUploadValidator.class),
                mock(EntityManager.class));
        BorrowerFile newest = file(DocumentType.NATIONAL_ID,
                VerificationStatus.REPLACEMENT_REQUESTED, 1);
        when(repository.findFirstByBorrowerIdAndDocumentTypeAndLoanIsNullOrderByUploadedAtDescIdDesc(
                42L, DocumentType.NATIONAL_ID)).thenReturn(Optional.of(newest));
        assertFalse(service.hasReusableBorrowerDocument(42L, DocumentType.NATIONAL_ID));
        verify(repository, times(1))
                .findFirstByBorrowerIdAndDocumentTypeAndLoanIsNullOrderByUploadedAtDescIdDesc(
                        42L, DocumentType.NATIONAL_ID);
    }
}
