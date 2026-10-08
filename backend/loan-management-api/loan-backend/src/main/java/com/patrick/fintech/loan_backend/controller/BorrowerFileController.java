package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.mapper.ResponseDtoMapper;
import com.patrick.fintech.loan_backend.model.BorrowerFile;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.BorrowerFileService;
import com.patrick.fintech.loan_backend.service.MailService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.http.MediaTypeFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.patrick.fintech.loan_backend.model.VerificationStatus;
import com.patrick.fintech.loan_backend.model.DocumentType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;

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
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER')")
public class BorrowerFileController {

        private final BorrowerFileService fileService;
        private final BorrowerRepository borrowerRepository;
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
                return ResponseEntity.ok(ApiResponse.safe(fileService.getByBorrowerMetadataOnly(borrowerId)));
        }

        /** Attachment download — forces "Save As". */
        @GetMapping("/download/{fileId}")
        @org.springframework.transaction.annotation.Transactional(readOnly = true)
        public ResponseEntity<byte[]> download(@PathVariable Long fileId) {
                return serveFile(fileId, "attachment", "DOCUMENT_DOWNLOADED", "Downloaded");
        }

        /**
         * Inline view — for the "Preview" / "Open in new tab" buttons; browser renders
         * images/PDFs directly.
         */
        @GetMapping("/preview/{fileId}")
        @org.springframework.transaction.annotation.Transactional(readOnly = true)
        public ResponseEntity<byte[]> preview(@PathVariable Long fileId) {
                return serveFile(fileId, "inline", "DOCUMENT_PREVIEWED", "Previewed");
        }

        private ResponseEntity<byte[]> serveFile(Long fileId, String disposition, String action, String verb) {
                User user = currentUserUtil.getCurrentUser();
                BorrowerFile file = fileService.getByIdForOrg(fileId, user.getOrganization().getId());
                byte[] data = file.getData();
                if (data == null || data.length == 0) {
                        throw new IllegalStateException(
                                        "The requested document has no stored file content. "
                                                        + "Please request a replacement document from the applicant.");
                }

                auditService.log(file.getBorrower().getOrganization(), user,
                                action, "BORROWER_FILE", String.valueOf(fileId),
                                verb + " " + file.getDocumentType() + " (" + file.getFileName() + ")",
                                null, null, "Documents & KYC");
                String fileName = safeFileName(file.getFileName());
                MediaType mediaType = resolveMediaType(file, data);
                ContentDisposition contentDisposition = ContentDisposition
                                .builder(disposition)
                                .filename(fileName, StandardCharsets.UTF_8)
                                .build();

                return ResponseEntity.ok()
                                .contentType(mediaType)
                                .contentLength(data.length)
                                .cacheControl(CacheControl.noStore().mustRevalidate())
                                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                                .header("Content-Security-Policy",
                                                "default-src 'none'; frame-ancestors 'none'")
                                .header("X-Content-Type-Options", "nosniff")
                                .body(data);
        }

        private MediaType resolveMediaType(BorrowerFile file, byte[] data) {
                MediaType detected = detectStoredMediaType(data);
                if (detected != null) {
                        return detected;
                }

                String declared = file.getFileType();
                if (declared != null && !declared.isBlank()) {
                        try {
                                String normalized = declared.split(";", 2)[0].trim().toLowerCase(java.util.Locale.ROOT);
                                MediaType candidate = MediaType.parseMediaType(normalized);
                                if (Set.of(
                                                MediaType.APPLICATION_PDF_VALUE,
                                                MediaType.IMAGE_JPEG_VALUE,
                                                MediaType.IMAGE_PNG_VALUE,
                                                "image/webp").contains(candidate.toString())) {
                                        return candidate;
                                }
                        } catch (IllegalArgumentException ignored) {
                                // Fall back to the filename extension below.
                        }
                }
                return MediaTypeFactory.getMediaType(file.getFileName())
                                .filter(type -> Set.of(
                                                MediaType.APPLICATION_PDF,
                                                MediaType.IMAGE_JPEG,
                                                MediaType.IMAGE_PNG,
                                                MediaType.valueOf("image/webp")).contains(type))
                                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        }

        private MediaType detectStoredMediaType(byte[] data) {
                if (data == null || data.length == 0) {
                        return null;
                }
                if (startsWith(data, new byte[] { 0x25, 0x50, 0x44, 0x46 })) {
                        return MediaType.APPLICATION_PDF;
                }
                if (startsWith(data, new byte[] { (byte) 0xff, (byte) 0xd8, (byte) 0xff })) {
                        return MediaType.IMAGE_JPEG;
                }
                if (startsWith(data, new byte[] { (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a })) {
                        return MediaType.IMAGE_PNG;
                }
                if (data.length >= 12
                                && data[0] == 0x52 && data[1] == 0x49 && data[2] == 0x46 && data[3] == 0x46
                                && data[8] == 0x57 && data[9] == 0x45 && data[10] == 0x42 && data[11] == 0x50) {
                        return MediaType.valueOf("image/webp");
                }
                return null;
        }

        private boolean startsWith(byte[] value, byte[] prefix) {
                if (value.length < prefix.length) {
                        return false;
                }
                for (int i = 0; i < prefix.length; i++) {
                        if (value[i] != prefix[i]) {
                                return false;
                        }
                }
                return true;
        }

        private String safeFileName(String value) {
                String name = value == null || value.isBlank() ? "document" : value.trim();
                String sanitized = name.replaceAll("[\\/\"\r\n]", "_");
                return sanitized.isBlank() ? "document" : sanitized.substring(0, Math.min(255, sanitized.length()));
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

                String statusValue = body.get("status");
                String comment = body.get("comment");

                VerificationStatus status;

                try {
                        status = VerificationStatus.valueOf(statusValue.toUpperCase());
                } catch (Exception ex) {
                        throw new RuntimeException("Invalid verification status: " + statusValue);
                }

                BorrowerFile updated = fileService.verify(
                                fileId,
                                user.getOrganization().getId(),
                                status,
                                comment,
                                user.getName());

                auditService.log(
                                updated.getBorrower().getOrganization(),
                                user,
                                "DOCUMENT_" + status.name(),
                                "BORROWER_FILE",
                                String.valueOf(fileId),
                                updated.getDocumentType().name() + " (" + updated.getFileName() + ") marked "
                                                + status.name()
                                                + (comment != null && !comment.isBlank()
                                                                ? ": " + comment
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
                                                                comment);

                                        case REPLACEMENT_REQUESTED ->
                                                mailService.sendDocumentReplacementRequested(
                                                                updated.getBorrower(),
                                                                updated.getDocumentType().name(),
                                                                comment);

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
        @org.springframework.transaction.annotation.Transactional
        public ResponseEntity<Void> delete(@PathVariable Long fileId) {
                User user = currentUserUtil.getCurrentUser();
                BorrowerFile file = fileService.getByIdForOrg(fileId, user.getOrganization().getId());
                auditService.log(file.getBorrower().getOrganization(), user,
                                "DOCUMENT_DELETED", "BORROWER_FILE", String.valueOf(fileId),
                                "Deleted " + file.getDocumentType() + " (" + file.getFileName() + ") for borrower #"
                                                + file.getBorrower().getId(),
                                null, null, "Documents & KYC");
                fileService.delete(fileId);
                return ResponseEntity.noContent().build();
        }
}