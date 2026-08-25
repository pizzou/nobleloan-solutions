package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.BorrowerFile;
import com.patrick.fintech.loan_backend.model.DocumentType;
import com.patrick.fintech.loan_backend.model.VerificationStatus;
import com.patrick.fintech.loan_backend.repository.BorrowerFileRepository;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Secure borrower-document service.
 *
 * File metadata and file bytes are deliberately handled separately:
 * - list endpoints return metadata only so an 8MB document is never loaded into
 * a dashboard response unnecessarily;
 * - preview/download endpoints explicitly load and validate the stored bytes;
 * - tenant ownership is checked before any protected document operation;
 * - uploaded content is validated by SecureFileUploadValidator, including
 * content signatures rather than trusting the browser MIME type alone.
 */
@Service
public class BorrowerFileService {

    private final BorrowerFileRepository fileRepository;
    private final BorrowerRepository borrowerRepository;
    private final SecureFileUploadValidator secureFileUploadValidator;

    private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;

    public BorrowerFileService(
            BorrowerFileRepository fileRepository,
            BorrowerRepository borrowerRepository,
            SecureFileUploadValidator secureFileUploadValidator) {
        this.fileRepository = fileRepository;
        this.borrowerRepository = borrowerRepository;
        this.secureFileUploadValidator = secureFileUploadValidator;
    }

    /**
     * Upload a borrower document after validating its declared type and actual
     * file signature.
     */
    public BorrowerFile upload(
            Long borrowerId,
            MultipartFile file,
            DocumentType documentType,
            boolean uploadedByApplicant) throws IOException {

        if (borrowerId == null || borrowerId <= 0) {
            throw new IllegalArgumentException("Invalid borrower ID.");
        }

        secureFileUploadValidator.validateDocument(file, MAX_FILE_BYTES);

        Borrower borrower = borrowerRepository.findById(borrowerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Borrower not found: " + borrowerId));

        byte[] content = file.getBytes();

        if (content.length == 0) {
            throw new IllegalArgumentException("The uploaded document contains no data.");
        }

        String originalFileName = file.getOriginalFilename();
        String safeFileName = originalFileName == null || originalFileName.isBlank()
                ? "document"
                : originalFileName.trim();

        BorrowerFile borrowerFile = new BorrowerFile();
        borrowerFile.setBorrower(borrower);
        borrowerFile.setFileName(safeFileName);
        borrowerFile.setFileType(normalizeContentType(file.getContentType()));
        borrowerFile.setFileSize((long) content.length);
        borrowerFile.setFilePath(null);
        borrowerFile.setData(content);
        borrowerFile.setDocumentType(
                documentType != null ? documentType : DocumentType.OTHER);
        borrowerFile.setUploadedByApplicant(uploadedByApplicant);
        borrowerFile.setVerificationStatus(VerificationStatus.PENDING);
        borrowerFile.setOfficerComment(null);
        borrowerFile.setVerifiedByName(null);
        borrowerFile.setVerifiedAt(null);

        BorrowerFile saved = fileRepository.save(borrowerFile);

        if (saved.getId() == null || !hasStoredContent(saved)) {
            throw new IllegalStateException(
                    "The document was not stored correctly. Please retry the upload.");
        }

        return saved;
    }

    /** Default staff upload. */
    public BorrowerFile upload(Long borrowerId, MultipartFile file) throws IOException {
        return upload(
                borrowerId,
                file,
                DocumentType.OTHER,
                false);
    }

    /**
     * Returns borrower document metadata without retaining raw bytes in the
     * response entity. The entity is detached after the repository call, so
     * clearing the byte[] here does not write a null back to the database.
     */
    public List<BorrowerFile> getByBorrower(Long borrowerId) {
        if (borrowerId == null || borrowerId <= 0) {
            throw new IllegalArgumentException("Invalid borrower ID.");
        }

        List<BorrowerFile> files = fileRepository.findByBorrowerId(borrowerId);
        files.forEach(file -> {
            file.setContentAvailable(hasStoredContent(file));
            // Never expose raw document bytes from metadata/list endpoints.
            file.setData(null);
        });
        return files;
    }

    /**
     * Explicit metadata-only alias used by the public applicant portal.
     */
    public List<BorrowerFile> getByBorrowerMetadataOnly(Long borrowerId) {
        return getByBorrower(borrowerId);
    }

    /** Get a document by ID. */
    public BorrowerFile getById(Long fileId) {
        if (fileId == null || fileId <= 0) {
            throw new IllegalArgumentException("Invalid document ID.");
        }

        return fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "File not found: " + fileId));
    }

    /**
     * Get a document only when it belongs to the requested organization.
     */
    public BorrowerFile getByIdForOrg(Long fileId, Long orgId) {
        if (orgId == null || orgId <= 0) {
            throw new IllegalArgumentException("Invalid organization ID.");
        }

        BorrowerFile file = getById(fileId);

        if (file.getBorrower() == null
                || file.getBorrower().getOrganization() == null
                || file.getBorrower().getOrganization().getId() == null
                || !file.getBorrower().getOrganization().getId().equals(orgId)) {
            // Do not reveal whether a document ID exists in another tenant.
            throw new IllegalArgumentException("File not found.");
        }

        return file;
    }

    /**
     * Verifies/rejects a document. Relationship/loan validation is performed by
     * the controller before this method is called so a review cannot be applied
     * to an unrelated loan.
     */
    public BorrowerFile verify(
            Long fileId,
            Long orgId,
            VerificationStatus status,
            String comment,
            String officerName) {

        if (status == null) {
            throw new IllegalArgumentException("Verification status is required.");
        }

        if (status != VerificationStatus.VERIFIED
                && status != VerificationStatus.REJECTED
                && status != VerificationStatus.REPLACEMENT_REQUESTED) {
            throw new IllegalArgumentException(
                    "Unsupported document verification status: " + status);
        }

        BorrowerFile file = getByIdForOrg(fileId, orgId);
        String normalizedComment = comment == null ? null : comment.trim();

        if ((status == VerificationStatus.REJECTED
                || status == VerificationStatus.REPLACEMENT_REQUESTED)
                && (normalizedComment == null || normalizedComment.isBlank())) {
            throw new IllegalArgumentException(
                    status == VerificationStatus.REJECTED
                            ? "A rejection reason is required."
                            : "A replacement reason is required.");
        }

        if (normalizedComment != null && normalizedComment.length() > 2000) {
            throw new IllegalArgumentException(
                    "Document review comment must not exceed 2000 characters.");
        }

        // Do not silently downgrade a verified document to REJECTED. A verified
        // document can enter the controlled replacement workflow, but a fresh
        // rejection after verification would destroy the original approval state.
        if (file.getVerificationStatus() == VerificationStatus.VERIFIED
                && status == VerificationStatus.REJECTED) {
            throw new IllegalStateException(
                    "A verified document cannot be rejected directly. Request a controlled replacement instead.");
        }

        file.setVerificationStatus(status);
        file.setOfficerComment(normalizedComment);
        file.setVerifiedByName(
                officerName == null || officerName.isBlank()
                        ? null
                        : officerName.trim());
        file.setVerifiedAt(LocalDateTime.now());

        return fileRepository.save(file);
    }

    /**
     * Delete only through a controller that has already performed authorization
     * and verification-state checks.
     */
    public void delete(Long fileId) {
        if (fileId == null || fileId <= 0) {
            throw new IllegalArgumentException("Invalid document ID.");
        }
        fileRepository.deleteById(fileId);
    }

    public boolean hasStoredContent(BorrowerFile file) {
        return file != null
                && file.getData() != null
                && file.getData().length > 0;
    }

    /** Missing required document types. */
    public List<DocumentType> getMissingDocumentTypes(
            Long borrowerId,
            List<DocumentType> required) {

        if (required == null || required.isEmpty()) {
            return List.of();
        }

        Set<DocumentType> uploaded = fileRepository.findByBorrowerId(borrowerId)
                .stream()
                .map(BorrowerFile::getDocumentType)
                .collect(Collectors.toSet());

        return required.stream()
                .filter(doc -> !uploaded.contains(doc))
                .toList();
    }

    /** Required documents not yet verified. */
    public List<DocumentType> getUnverifiedDocumentTypes(
            Long borrowerId,
            List<DocumentType> required) {

        if (required == null || required.isEmpty()) {
            return List.of();
        }

        Set<DocumentType> verified = fileRepository.findByBorrowerId(borrowerId)
                .stream()
                .filter(f -> f.getVerificationStatus() == VerificationStatus.VERIFIED)
                .map(BorrowerFile::getDocumentType)
                .collect(Collectors.toSet());

        return required.stream()
                .filter(doc -> !verified.contains(doc))
                .toList();
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }

        return contentType.trim()
                .toLowerCase(java.util.Locale.ROOT)
                .split(";", 2)[0]
                .trim();
    }
}