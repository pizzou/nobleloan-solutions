package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.BorrowerFile;
import com.patrick.fintech.loan_backend.model.DocumentType;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanComment;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.model.VerificationStatus;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.LoanCommentRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.BorrowerFileService;
import com.patrick.fintech.loan_backend.service.MailService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Staff-side KYC document endpoints.
 *
 * Security rules:
 * - every operation is tenant scoped;
 * - document bytes are never exposed through JSON metadata endpoints;
 * - preview/download require the authenticated staff JWT;
 * - a file ID from another tenant is indistinguishable from a missing file;
 * - verified documents cannot be physically deleted;
 * - rejection/replacement comments are written to the exact loan associated
 * with the document and are visible to the applicant;
 * - preview/download responses are private and non-cacheable.
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class BorrowerFileController {

    private static final long MAX_COMMENT_LENGTH = 2000L;

    private static final Set<String> INLINE_CONTENT_TYPES = Set.of(
            MediaType.APPLICATION_PDF_VALUE,
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            "image/webp");

    private final BorrowerFileService fileService;
    private final BorrowerRepository borrowerRepository;
    private final LoanRepository loanRepository;
    private final LoanCommentRepository loanCommentRepository;
    private final AuditService auditService;
    private final MailService mailService;
    private final CurrentUserUtil currentUserUtil;

    @PostMapping("/upload/{borrowerId}")
    @Transactional
    public ResponseEntity<ApiResponse<Object>> upload(
            @PathVariable Long borrowerId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "documentType", required = false, defaultValue = "OTHER") String documentType)
            throws Exception {

        User user = currentUserUtil.getCurrentUser();

        var borrower = borrowerRepository.findById(borrowerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Borrower not found: " + borrowerId));

        requireSameOrganization(
                borrower.getOrganization() == null ? null : borrower.getOrganization().getId(),
                user.getOrganization() == null ? null : user.getOrganization().getId());

        DocumentType type = parseDocumentType(documentType);

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

        return ResponseEntity.ok(
                ApiResponse.safe("File uploaded", saved));
    }

    /** Staff KYC document list. */
    @GetMapping("/borrower/{borrowerId}")
    public ResponseEntity<ApiResponse<Object>> getFiles(
            @PathVariable Long borrowerId) {

        User user = currentUserUtil.getCurrentUser();

        var borrower = borrowerRepository.findById(borrowerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Borrower not found: " + borrowerId));

        requireSameOrganization(
                borrower.getOrganization() == null ? null : borrower.getOrganization().getId(),
                user.getOrganization() == null ? null : user.getOrganization().getId());

        return ResponseEntity.ok(
                ApiResponse.safe(fileService.getByBorrower(borrowerId)));
    }

    /** Authenticated attachment download. */
    @GetMapping("/download/{fileId}")
    @Transactional
    public ResponseEntity<byte[]> download(@PathVariable Long fileId) {
        return serveFile(
                fileId,
                "attachment",
                "DOCUMENT_DOWNLOADED",
                "Downloaded");
    }

    /** Authenticated inline PDF/image preview. */
    @GetMapping("/preview/{fileId}")
    @Transactional
    public ResponseEntity<byte[]> preview(@PathVariable Long fileId) {
        return serveFile(
                fileId,
                "inline",
                "DOCUMENT_PREVIEWED",
                "Previewed");
    }

    private ResponseEntity<byte[]> serveFile(
            Long fileId,
            String disposition,
            String action,
            String verb) {

        User user = currentUserUtil.getCurrentUser();

        Long organizationId = user.getOrganization() == null
                ? null
                : user.getOrganization().getId();

        if (organizationId == null || organizationId <= 0) {
            throw new AccessDeniedException("The current user has no valid organization.");
        }

        BorrowerFile file = fileService.getByIdForOrg(fileId, organizationId);

        if (!fileService.hasStoredContent(file)) {
            auditService.log(
                    file.getBorrower().getOrganization(),
                    user,
                    "DOCUMENT_CONTENT_UNAVAILABLE",
                    "BORROWER_FILE",
                    String.valueOf(fileId),
                    "Attempted to " + verb.toLowerCase(Locale.ROOT)
                            + " document " + file.getFileName()
                            + " but the stored file content is missing",
                    null,
                    null,
                    "Documents & KYC");

            throw new IllegalStateException(
                    "The requested document has no stored file content. Please request a replacement document from the applicant.");
        }

        String contentType = normalizeContentType(file.getFileType());

        if (!INLINE_CONTENT_TYPES.contains(contentType)) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        String safeFileName = safeFileName(file.getFileName());

        ContentDisposition contentDisposition = "attachment".equalsIgnoreCase(disposition)
                ? ContentDisposition.attachment()
                        .filename(safeFileName, StandardCharsets.UTF_8)
                        .build()
                : ContentDisposition.inline()
                        .filename(safeFileName, StandardCharsets.UTF_8)
                        .build();

        auditService.log(
                file.getBorrower().getOrganization(),
                user,
                action,
                "BORROWER_FILE",
                String.valueOf(fileId),
                verb + " " + file.getDocumentType() + " (" + safeFileName + ")",
                null,
                null,
                "Documents & KYC");

        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(file.getData().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Download-Options", "noopen")
                .body(file.getData());
    }

    /**
     * Staff document verification decision.
     *
     * REJECTED and REPLACEMENT_REQUESTED require a comment and a valid loan ID.
     * The loan/document relationship is verified BEFORE the document status is
     * changed. The whole operation is transactional, so a comment can never be
     * committed for a different loan or tenant.
     */
    @PatchMapping("/{fileId}/verify")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER')")
    @Transactional
    public ResponseEntity<ApiResponse<Object>> verify(
            @PathVariable Long fileId,
            @RequestBody Map<String, String> body) {

        User user = currentUserUtil.getCurrentUser();

        Long organizationId = user.getOrganization() == null
                ? null
                : user.getOrganization().getId();

        if (organizationId == null || organizationId <= 0) {
            throw new AccessDeniedException("The current user has no valid organization.");
        }

        String statusValue = body == null ? null : body.get("status");
        String comment = body == null ? null : body.get("comment");
        String loanIdValue = body == null ? null : body.get("loanId");

        if (statusValue == null || statusValue.isBlank()) {
            throw new IllegalArgumentException("Verification status is required.");
        }

        VerificationStatus status;
        try {
            status = VerificationStatus.valueOf(
                    statusValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid verification status: " + statusValue);
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

        if (normalizedComment.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException(
                    "Document review comment must not exceed 2000 characters.");
        }

        BorrowerFile currentFile = fileService.getByIdForOrg(fileId, organizationId);

        Loan loan = null;

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

            if (loanId <= 0) {
                throw new IllegalArgumentException("Invalid loanId.");
            }

            loan = loanRepository.findById(loanId)
                    .orElseThrow(() -> new IllegalArgumentException("Loan not found."));

            if (loan.getOrganization() == null
                    || loan.getOrganization().getId() == null
                    || !organizationId.equals(loan.getOrganization().getId())
                    || loan.getBorrower() == null
                    || currentFile.getBorrower() == null
                    || !loan.getBorrower().getId().equals(currentFile.getBorrower().getId())) {
                throw new AccessDeniedException(
                        "The selected loan does not belong to this organization and document.");
            }
        }

        BorrowerFile updated = fileService.verify(
                fileId,
                organizationId,
                status,
                normalizedComment.isBlank() ? null : normalizedComment,
                user.getName());

        if (loan != null) {
            String documentLabel = updated.getDocumentType() == null
                    ? "document"
                    : updated.getDocumentType().name().replace('_', ' ');

            String applicantMessage = status == VerificationStatus.REPLACEMENT_REQUESTED
                    ? "Document replacement requested for " + documentLabel
                            + ". Reason: " + normalizedComment
                    : "Document rejected for " + documentLabel
                            + ". Reason: " + normalizedComment;

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
                updated.getDocumentType() + " (" + updated.getFileName() + ") marked "
                        + status.name()
                        + (!normalizedComment.isBlank()
                                ? ": " + normalizedComment
                                : ""),
                null,
                null,
                "Documents & KYC");

        /*
         * Email is a secondary notification channel. The authoritative applicant
         * message is the committed LoanComment above, so an email outage must not
         * roll back the document review transaction.
         */
        if (updated.getBorrower() != null
                && updated.getBorrower().getEmail() != null
                && !updated.getBorrower().getEmail().isBlank()) {
            try {
                switch (status) {
                    case VERIFIED -> mailService.sendDocumentVerified(
                            updated.getBorrower(),
                            updated.getDocumentType().name());

                    case REJECTED -> mailService.sendDocumentRejected(
                            updated.getBorrower(),
                            updated.getDocumentType().name(),
                            normalizedComment);

                    case REPLACEMENT_REQUESTED -> mailService.sendDocumentReplacementRequested(
                            updated.getBorrower(),
                            updated.getDocumentType().name(),
                            normalizedComment);

                    default -> {
                        // No other status is accepted by BorrowerFileService.
                    }
                }
            } catch (Exception ex) {
                // The document decision and applicant-visible LoanComment remain
                // committed. Email failure is intentionally non-fatal.
            }
        }

        return ResponseEntity.ok(
                ApiResponse.safe(
                        "Document " + status.name().toLowerCase(Locale.ROOT).replace('_', ' '),
                        updated));
    }

    @DeleteMapping("/{fileId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER')")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long fileId) {
        User user = currentUserUtil.getCurrentUser();
        Long organizationId = user.getOrganization() == null
                ? null
                : user.getOrganization().getId();

        BorrowerFile file = fileService.getByIdForOrg(fileId, organizationId);

        if (file.getVerificationStatus() == VerificationStatus.VERIFIED) {
            throw new IllegalStateException(
                    "Verified documents cannot be deleted. Request a controlled replacement instead.");
        }

        auditService.log(
                file.getBorrower().getOrganization(),
                user,
                "DOCUMENT_DELETED",
                "BORROWER_FILE",
                String.valueOf(fileId),
                "Deleted " + file.getDocumentType() + " (" + safeFileName(file.getFileName())
                        + ") for borrower #" + file.getBorrower().getId(),
                null,
                null,
                "Documents & KYC");

        fileService.delete(fileId);

        return ResponseEntity.noContent().build();
    }

    private DocumentType parseDocumentType(String value) {
        if (value == null || value.isBlank()) {
            return DocumentType.OTHER;
        }

        try {
            return DocumentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid document type: " + value);
        }
    }

    private void requireSameOrganization(Long resourceOrganizationId, Long userOrganizationId) {
        if (resourceOrganizationId == null
                || userOrganizationId == null
                || !resourceOrganizationId.equals(userOrganizationId)) {
            throw new AccessDeniedException("Access denied.");
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return contentType.trim()
                .toLowerCase(Locale.ROOT)
                .split(";", 2)[0]
                .trim();
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "document";
        }

        String sanitized = fileName
                .replace('\\', '_')
                .replace('/', '_')
                .replace('\r', '_')
                .replace('\n', '_')
                .replace('\u0000', '_')
                .trim();

        if (sanitized.isBlank()) {
            return "document";
        }

        return sanitized.length() > 180
                ? sanitized.substring(0, 180)
                : sanitized;
    }
}