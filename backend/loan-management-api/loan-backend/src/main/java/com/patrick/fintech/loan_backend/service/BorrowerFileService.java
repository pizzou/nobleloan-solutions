package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.BorrowerFile;
import com.patrick.fintech.loan_backend.model.DocumentType;
import com.patrick.fintech.loan_backend.model.VerificationStatus;
import com.patrick.fintech.loan_backend.repository.BorrowerFileRepository;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BorrowerFileService {

    private final BorrowerFileRepository fileRepository;
    private final BorrowerRepository borrowerRepository;
    private final SecureFileUploadValidator secureFileUploadValidator;
    private final EntityManager entityManager;

    public BorrowerFileService(BorrowerFileRepository fileRepository,
            BorrowerRepository borrowerRepository,
            SecureFileUploadValidator secureFileUploadValidator,
            EntityManager entityManager) {
        this.fileRepository = fileRepository;
        this.borrowerRepository = borrowerRepository;
        this.secureFileUploadValidator = secureFileUploadValidator;
        this.entityManager = entityManager;
    }

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp");

    private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;

    /**
     * Validate uploaded file.
     */
    private void validate(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new RuntimeException("No file was received.");
        }

        if (file.getSize() > MAX_FILE_BYTES) {
            throw new RuntimeException("Maximum file size is 8MB.");
        }

        String contentType = file.getContentType();

        if (contentType == null ||
                !ALLOWED_TYPES.contains(contentType.toLowerCase())) {

            throw new RuntimeException(
                    "Unsupported file type. Allowed: PDF, JPG, PNG, WEBP.");
        }
    }

    /**
     * Upload borrower document.
     */
    public BorrowerFile upload(Long borrowerId,
            MultipartFile file,
            DocumentType documentType,
            boolean uploadedByApplicant)
            throws IOException {

        secureFileUploadValidator.validateDocument(file, MAX_FILE_BYTES);

        Borrower borrower = borrowerRepository.findById(borrowerId)
                .orElseThrow(() -> new RuntimeException("Borrower not found: " + borrowerId));

        BorrowerFile borrowerFile = new BorrowerFile();

        borrowerFile.setBorrower(borrower);

        borrowerFile.setFileName(file.getOriginalFilename());
        borrowerFile.setFileType(file.getContentType());
        borrowerFile.setFileSize(file.getSize());
        borrowerFile.setData(file.getBytes());

        borrowerFile.setDocumentType(
                documentType != null
                        ? documentType
                        : DocumentType.OTHER);

        borrowerFile.setUploadedByApplicant(uploadedByApplicant);

        borrowerFile.setVerificationStatus(VerificationStatus.PENDING);

        return fileRepository.save(borrowerFile);
    }

    /**
     * Default upload.
     */
    public BorrowerFile upload(Long borrowerId,
            MultipartFile file)
            throws IOException {

        return upload(
                borrowerId,
                file,
                DocumentType.OTHER,
                false);
    }

    /**
     * Get all files for borrower.
     */
    public List<BorrowerFile> getByBorrower(Long borrowerId) {
        return fileRepository.findByBorrowerId(borrowerId);
    }

    /**
     * Metadata only.
     */
    public List<BorrowerFile> getByBorrowerMetadataOnly(Long borrowerId) {

        List<BorrowerFile> files = fileRepository.findByBorrowerId(borrowerId);

        /*
         * borrower_files.data is the authoritative binary content. This method
         * exists only to build metadata responses, so never mutate a managed
         * BorrowerFile entity to hide the bytes. Doing f.setData(null) while
         * Hibernate is tracking the entity can dirty the row and persist NULL
         * back to PostgreSQL. That was capable of turning a valid uploaded
         * document into a metadata-only record after a normal list request.
         *
         * Detach first, then remove the bytes only from the response object.
         */
        for (BorrowerFile file : files) {
            entityManager.detach(file);
            file.setData(null);
        }

        return files;
    }

    /**
     * Get file.
     */
    public BorrowerFile getById(Long fileId) {

        return fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found: " + fileId));
    }

    /**
     * Get file ensuring organization ownership.
     */
    public BorrowerFile getByIdForOrg(Long fileId, Long orgId) {

        BorrowerFile file = getById(fileId);

        if (file.getBorrower() == null
                || file.getBorrower().getOrganization() == null
                || !file.getBorrower()
                        .getOrganization()
                        .getId()
                        .equals(orgId)) {

            throw new RuntimeException("File not found.");
        }

        return file;
    }

    /**
     * Verify document.
     */
    public BorrowerFile verify(Long fileId,
            Long orgId,
            VerificationStatus status,
            String comment,
            String officerName) {

        BorrowerFile file = getByIdForOrg(fileId, orgId);

        file.setVerificationStatus(status);
        file.setOfficerComment(comment);
        file.setVerifiedByName(officerName);
        file.setVerifiedAt(LocalDateTime.now());

        return fileRepository.save(file);
    }

    /**
     * Replace an applicant-owned document without destroying its audit history.
     *
     * The previous row is retained and moved to REPLACEMENT_REQUESTED. A new
     * row receives the new binary and starts at PENDING so staff must make a
     * fresh verification decision.
     */
    public BorrowerFile replaceApplicantDocument(Long borrowerId,
            Long fileId,
            MultipartFile replacement,
            DocumentType documentType) throws IOException {

        secureFileUploadValidator.validateDocument(replacement, MAX_FILE_BYTES);

        Borrower borrower = borrowerRepository.findById(borrowerId)
                .orElseThrow(() -> new RuntimeException("Borrower not found: " + borrowerId));

        BorrowerFile existing = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("Document not found."));

        if (existing.getBorrower() == null
                || existing.getBorrower().getId() == null
                || !existing.getBorrower().getId().equals(borrowerId)) {
            throw new RuntimeException("Document not found.");
        }

        if (!existing.isUploadedByApplicant()) {
            throw new RuntimeException(
                    "This document was added by our staff and cannot be replaced by the applicant.");
        }

        if (existing.getVerificationStatus() == VerificationStatus.VERIFIED) {
            throw new RuntimeException(
                    "A verified document cannot be replaced unless staff explicitly requests a new document.");
        }

        DocumentType effectiveType = documentType != null
                ? documentType
                : existing.getDocumentType();

        if (effectiveType == null) {
            effectiveType = DocumentType.OTHER;
        }

        existing.setVerificationStatus(VerificationStatus.REPLACEMENT_REQUESTED);
        existing.setOfficerComment(
                "Applicant submitted a replacement document. Previous document retained for audit history.");
        fileRepository.save(existing);

        BorrowerFile replacementFile = new BorrowerFile();
        replacementFile.setBorrower(borrower);
        replacementFile.setFileName(replacement.getOriginalFilename());
        replacementFile.setFileType(replacement.getContentType());
        replacementFile.setFileSize(replacement.getSize());
        replacementFile.setData(replacement.getBytes());
        replacementFile.setDocumentType(effectiveType);
        replacementFile.setUploadedByApplicant(true);
        replacementFile.setVerificationStatus(VerificationStatus.PENDING);

        return fileRepository.save(replacementFile);
    }

    /**
     * Delete file.
     */
    public void delete(Long fileId) {
        fileRepository.deleteById(fileId);
    }

    /**
     * Missing required document types.
     */
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

    /**
     * Required documents not yet verified.
     */
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
}
