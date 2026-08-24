package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.mapper.ResponseDtoMapper;
import com.patrick.fintech.loan_backend.model.BorrowerFile;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanComment;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.LoanCommentRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.BorrowerFileService;
import com.patrick.fintech.loan_backend.service.MailService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.http.ContentDisposition;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.patrick.fintech.loan_backend.model.VerificationStatus;
import com.patrick.fintech.loan_backend.model.DocumentType;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Staff-side KYC document endpoints. Every read/write here is scoped to the
 * caller's
 * organization (see BorrowerFileService#getByIdForOrg) — a file ID alone is not
 * enough
 * to fetch, preview, verify, or delete a document belonging to a different
 * tenant.
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class BorrowerFileController {

    private final BorrowerFileService fileService;
    private final BorrowerRepository borrowerRepository;
    private final LoanRepository loanRepository;
    private final LoanCommentRepository loanCommentRepository;
    private final AuditService auditService;
    private final MailService mailService;
    private final CurrentUserUtil currentUserUtil;

    @PostMapping("/upload/{borrowerId}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<ApiResponse<Object>> upload(
            @PathVariable Long borrowerId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "documentType", required = false, defaultValue = "OTHER") String documentType)
            throws Exception {

        User user = currentUserUtil.getCurrentUser();

        var borrower = borrowerRepository.findById(borrowerId)
                .orElseThrow(() -> new RuntimeException("Borrower not found: " + borrowerId));

        if (!borrower.getOrganization().getId().equals(user.getOrganization().getId())) {
            throw new RuntimeException("Access denied");
        }

        DocumentType type;

        try {
            type = DocumentType.valueOf(documentType.toUpperCase());
        } catch (IllegalArgumentException ex) {
            type = DocumentType.OTHER;
        }

        BorrowerFile saved = fileService.upload(
                borrowerId,
                file,
                type,
                false);

        auditService.log(
                saved.getBorrower().getOrganization(),
                user,
                "DOCUMENT_UPLOADED",
                "BORROWER_FILE",
                String.valueOf(saved.getId()),
                "Uploaded " + type + " (" + saved.getFileName() + ") for borrower #" + borrowerId,
                null,
                null,
                "Documents & KYC");

        return ResponseEntity.ok(ApiResponse.safe("File uploaded", saved));
    }

    /**
     * All documents for a borrower — staff KYC review list (Loan Officer opening an
     * application).
     */
    @GetMapping("/borrower/{borrowerId}")
    public ResponseEntity<ApiResponse<Object>> getFiles(@PathVariable Long borrowerId) {
        User user = currentUserUtil.getCurrentUser();
        var borrower = borrowerRepository.findById(borrowerId)
                .orElseThrow(() -> new RuntimeException("Borrower not found: " + borrowerId));
        if (!borrower.getOrganization().getId().equals(user.getOrganization().getId()))
            throw new RuntimeException("Access denied");
        return ResponseEntity.ok(ApiResponse.safe(fileService.getByBorrower(borrowerId)));
    }

    /** Attachment download — forces "Save As". */
    @GetMapping("/download/{fileId}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<byte[]> download(@PathVariable Long fileId) {
        return serveFile(fileId, "attachment", "DOCUMENT_DOWNLOADED", "Downloaded");
    }

    /**
     * Inline view — for the "Preview" / "Open in new tab" buttons; browser renders
     * images/PDFs directly.
     */
    @GetMapping("/preview/{fileId}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<byte[]> preview(@PathVariable Long fileId) {
        return serveFile(fileId, "inline", "DOCUMENT_PREVIEWED", "Previewed");
    }

    private ResponseEntity<byte[]> serveFile(Long fileId, String disposition, String action, String verb) {
        User user = currentUserUtil.getCurrentUser();
        BorrowerFile file = fileService.getByIdForOrg(fileId, user.getOrganization().getId());
        auditService.log(file.getBorrower().getOrganization(), user,
                action, "BORROWER_FILE", String.valueOf(fileId),
                verb + " " + file.getDocumentType() + " (" + file.getFileName() + ")",
                null, null, "Documents & KYC");
        String contentType = file.getFileType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : file.getFileType().trim().toLowerCase(java.util.Locale.ROOT);

        if (!Set.of(
                MediaType.APPLICATION_PDF_VALUE,
                MediaType.IMAGE_JPEG_VALUE,
                MediaType.IMAGE_PNG_VALUE,
                "image/webp").contains(contentType)) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        if (file.getData() == null || file.getData().length == 0) {
            throw new IllegalStateException("The requested document has no stored file content.");
        }

        ContentDisposition contentDisposition = "attachment".equalsIgnoreCase(disposition)
                ? ContentDisposition.attachment()
                        .filename(file.getFileName(), java.nio.charset.StandardCharsets.UTF_8)
                        .build()
                : ContentDisposition.inline()
                        .filename(file.getFileName(), java.nio.charset.StandardCharsets.UTF_8)
                        .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(file.getData().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(file.getData());
    }

    /**
     * Staff verification decision on a single document — Verified / Rejected /
     * Replacement Requested.
     */
    @PatchMapping("/{fileId}/verify")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER')")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<ApiResponse<Object>> verify(
            @PathVariable Long fileId,
            @RequestBody Map<String, String> body) {

        User user = currentUserUtil.getCurrentUser();

        String statusValue = body != null ? body.get("status") : null;
        String comment = body != null ? body.get("comment") : null;
        String loanIdValue = body != null ? body.get("loanId") : null;

        if (statusValue == null || statusValue.isBlank()) {
            throw new IllegalArgumentException("Verification status is required.");
        }

        VerificationStatus status;

        try {
            status = VerificationStatus.valueOf(statusValue.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid verification status: " + statusValue);
        }

        String normalizedComment = comment == null ? "" : comment.trim();

        if ((status == VerificationStatus.REJECTED
                || status == VerificationStatus.REPLACEMENT_REQUESTED)
                && normalizedComment.isBlank()) {
            throw new IllegalArgumentException(
                    status == VerificationStatus.REJECTED
                            ? "A rejection reason is required."
                            : "A replacement reason is required.");
        }

        if (normalizedComment.length() > 2000) {
            throw new IllegalArgumentException("Document review comment must not exceed 2000 characters.");
        }

        BorrowerFile updated = fileService.verify(
                fileId,
                user.getOrganization().getId(),
                status,
                normalizedComment.isBlank() ? null : normalizedComment,
                user.getName());

        /*
         * A document review decision must be visible to the borrower through the
         * existing public application/track comments channel. The document record
         * remains the source of truth for its status; the loan comment is the
         * applicant-facing communication/audit trail. Both are written in this
         * transaction so the portal cannot show a replacement request that was
         * never actually committed.
         */
        if (status == VerificationStatus.REJECTED
                || status == VerificationStatus.REPLACEMENT_REQUESTED) {

            if (loanIdValue == null || loanIdValue.isBlank()) {
                throw new IllegalArgumentException(
                        "loanId is required when rejecting a document or requesting a replacement.");
            }

            final Long loanId;
            try {
                loanId = Long.valueOf(loanIdValue.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid loanId.");
            }

            Loan loan = loanRepository.findById(loanId)
                    .orElseThrow(() -> new IllegalArgumentException("Loan not found."));

            if (loan.getOrganization() == null
                    || !user.getOrganization().getId().equals(loan.getOrganization().getId())
                    || loan.getBorrower() == null
                    || updated.getBorrower() == null
                    || !loan.getBorrower().getId().equals(updated.getBorrower().getId())) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "The selected loan does not belong to this organization and document.");
            }

            String documentLabel = updated.getDocumentType() == null
                    ? "document"
                    : updated.getDocumentType().name().replace('_', ' ');

            String applicantMessage = status == VerificationStatus.REPLACEMENT_REQUESTED
                    ? "Document replacement requested for " + documentLabel + ". Reason: " + normalizedComment
                    : "Document rejected for " + documentLabel + ". Reason: " + normalizedComment;

            loanCommentRepository.save(
                    LoanComment.builder()
                            .loan(loan)
                            .author(user)
                            .message(applicantMessage)
                            .visibleToApplicant(true)
                            .build());
        }

        auditService.log(
                updated.getBorrower().getOrganization(),
                user,
                "DOCUMENT_" + status.name(),
                "BORROWER_FILE",
                String.valueOf(fileId),
                updated.getDocumentType().name() + " (" + updated.getFileName() + ") marked "
                        + status.name()
                        + (!normalizedComment.isBlank()
                                ? ": " + normalizedComment
                                : ""),
                null,
                null,
                "Documents & KYC");

        if (updated.getBorrower() != null &&
                updated.getBorrower().getEmail() != null) {

            try {

                switch (status) {

                    case VERIFIED ->
                        mailService.sendDocumentVerified(
                                updated.getBorrower(),
                                updated.getDocumentType().name());

                    case REJECTED ->
                        mailService.sendDocumentRejected(
                                updated.getBorrower(),
                                updated.getDocumentType().name(),
                                normalizedComment);

                    case REPLACEMENT_REQUESTED ->
                        mailService.sendDocumentReplacementRequested(
                                updated.getBorrower(),
                                updated.getDocumentType().name(),
                                normalizedComment);

                    default -> {
                    }
                }

            } catch (Exception ignored) {
            }
        }

        return ResponseEntity.ok(
                ApiResponse.safe(
                        "Document " + status.name().toLowerCase().replace('_', ' '),
                        updated));
    }

    @DeleteMapping("/{fileId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER')")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Void> delete(@PathVariable Long fileId) {
        User user = currentUserUtil.getCurrentUser();
        BorrowerFile file = fileService.getByIdForOrg(fileId, user.getOrganization().getId());
        if (file.getVerificationStatus() == VerificationStatus.VERIFIED) {
            throw new IllegalStateException(
                    "Verified documents cannot be deleted. Request a controlled replacement instead.");
        }

        auditService.log(file.getBorrower().getOrganization(), user,
                "DOCUMENT_DELETED", "BORROWER_FILE", String.valueOf(fileId),
                "Deleted " + file.getDocumentType() + " (" + file.getFileName() + ") for borrower #"
                        + file.getBorrower().getId(),
                null, null, "Documents & KYC");
        fileService.delete(fileId);
        return ResponseEntity.noContent().build();
    }
}