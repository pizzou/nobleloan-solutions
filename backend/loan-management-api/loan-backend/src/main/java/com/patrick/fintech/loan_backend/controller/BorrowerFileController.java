
package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.BorrowerFile;
import com.patrick.fintech.loan_backend.model.DocumentType;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.model.VerificationStatus;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.BorrowerDocumentRequirements;
import com.patrick.fintech.loan_backend.service.BorrowerFileService;
import com.patrick.fintech.loan_backend.service.MailService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.MediaTypeFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER')")
public class BorrowerFileController {

        private static final Logger log = LoggerFactory.getLogger(BorrowerFileController.class);

        private static final Set<VerificationStatus> FINAL_DECISIONS = java.util.EnumSet.of(
                        VerificationStatus.VERIFIED,
                        VerificationStatus.REJECTED,
                        VerificationStatus.REPLACEMENT_REQUESTED);

        private static final Comparator<BorrowerFile> NEWEST_FIRST = Comparator
                        .comparing(
                                        BorrowerFile::getUploadedAt,
                                        Comparator.nullsFirst(
                                                        Comparator.naturalOrder()))
                        .thenComparing(
                                        BorrowerFile::getId,
                                        Comparator.nullsFirst(
                                                        Comparator.naturalOrder()));

        private final JdbcTemplate jdbc;
        private final BorrowerFileService fileService;
        private final BorrowerRepository borrowerRepository;
        private final AuditService auditService;
        private final MailService mailService;
        private final CurrentUserUtil currentUserUtil;
        private final PlatformTransactionManager transactionManager;

        private enum ReviewNotificationResult {
                SENT,
                ALREADY_SENT,
                INCOMPLETE,
                EMAIL_MISSING
        }

        @PostMapping("/upload/{borrowerId}")
        @org.springframework.transaction.annotation.Transactional
        public ResponseEntity<ApiResponse<Object>> upload(
                        @PathVariable Long borrowerId,
                        @RequestParam("file") MultipartFile file,
                        @RequestParam(value = "documentType", required = false, defaultValue = "OTHER") String documentType)
                        throws Exception {

                User user = currentUserUtil.getCurrentUser();

                Borrower borrower = borrowerRepository.findById(borrowerId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Borrower not found."));

                if (borrower.getOrganization() == null
                                || !borrower.getOrganization().getId().equals(
                                                user.getOrganization().getId())) {

                        throw new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "Access denied.");
                }

                DocumentType type;

                try {
                        type = DocumentType.valueOf(
                                        documentType.trim().toUpperCase(
                                                        java.util.Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                        type = DocumentType.OTHER;
                }

                BorrowerFile saved = fileService.upload(
                                borrowerId,
                                file,
                                type,
                                false);

                auditService.log(
                                borrower.getOrganization(),
                                user,
                                "DOCUMENT_UPLOADED",
                                "BORROWER_FILE",
                                String.valueOf(saved.getId()),
                                "Uploaded " + type + " (" + saved.getFileName()
                                                + ") for borrower #" + borrowerId,
                                null,
                                null,
                                "Documents & KYC");

                return ResponseEntity.ok(
                                ApiResponse.safe("File uploaded", saved));
        }

        @GetMapping("/borrower/{borrowerId}")
        @org.springframework.transaction.annotation.Transactional(readOnly = true)
        public ResponseEntity<ApiResponse<Object>> getFiles(
                        @PathVariable Long borrowerId) {

                User user = currentUserUtil.getCurrentUser();

                Borrower borrower = borrowerRepository.findById(borrowerId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Borrower not found."));

                if (borrower.getOrganization() == null
                                || !borrower.getOrganization().getId().equals(
                                                user.getOrganization().getId())) {

                        throw new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "Access denied.");
                }

                return ResponseEntity.ok(
                                ApiResponse.safe(
                                                fileService.getByBorrowerMetadataOnly(
                                                                borrowerId)));
        }

        @GetMapping("/download/{fileId}")
        @org.springframework.transaction.annotation.Transactional(readOnly = true)
        public ResponseEntity<byte[]> download(
                        @PathVariable Long fileId) {

                return serveFile(
                                fileId,
                                "attachment",
                                "DOCUMENT_DOWNLOADED",
                                "Downloaded");
        }

        @GetMapping("/preview/{fileId}")
        @org.springframework.transaction.annotation.Transactional(readOnly = true)
        public ResponseEntity<byte[]> preview(
                        @PathVariable Long fileId) {

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

                BorrowerFile file = fileService.getByIdForOrg(
                                fileId,
                                user.getOrganization().getId());

                byte[] data = file.getData();

                if (data == null || data.length == 0) {
                        throw new IllegalStateException(
                                        "The requested document has no stored file content. "
                                                        + "Please request a replacement document.");
                }

                auditService.log(
                                file.getBorrower().getOrganization(),
                                user,
                                action,
                                "BORROWER_FILE",
                                String.valueOf(fileId),
                                verb + " borrower document content.",
                                null,
                                null,
                                "Documents & KYC");

                String fileName = safeFileName(file.getFileName());
                MediaType mediaType = resolveMediaType(file, data);

                ContentDisposition contentDisposition = ContentDisposition.builder(disposition)
                                .filename(fileName, StandardCharsets.UTF_8)
                                .build();

                return ResponseEntity.ok()
                                .contentType(mediaType)
                                .contentLength(data.length)
                                .cacheControl(
                                                CacheControl.noStore().mustRevalidate())
                                .header(
                                                HttpHeaders.CONTENT_DISPOSITION,
                                                contentDisposition.toString())
                                .header(
                                                "Content-Security-Policy",
                                                "default-src 'none'; frame-ancestors 'none'")
                                .header("X-Content-Type-Options", "nosniff")
                                .body(data);
        }

        private MediaType resolveMediaType(
                        BorrowerFile file,
                        byte[] data) {

                MediaType detected = detectStoredMediaType(data);

                if (detected != null) {
                        return detected;
                }

                String declared = file.getFileType();

                if (declared != null && !declared.isBlank()) {
                        try {
                                String normalized = declared
                                                .split(";", 2)[0]
                                                .trim()
                                                .toLowerCase(java.util.Locale.ROOT);

                                MediaType candidate = MediaType.parseMediaType(normalized);

                                if (Set.of(
                                                MediaType.APPLICATION_PDF_VALUE,
                                                MediaType.IMAGE_JPEG_VALUE,
                                                MediaType.IMAGE_PNG_VALUE,
                                                "image/webp").contains(candidate.toString())) {

                                        return candidate;
                                }
                        } catch (IllegalArgumentException ignored) {
                                // Fall back to the extension.
                        }
                }

                return MediaTypeFactory
                                .getMediaType(file.getFileName())
                                .filter(type -> Set.of(
                                                MediaType.APPLICATION_PDF,
                                                MediaType.IMAGE_JPEG,
                                                MediaType.IMAGE_PNG,
                                                MediaType.valueOf("image/webp"))
                                                .contains(type))
                                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        }

        private MediaType detectStoredMediaType(byte[] data) {
                if (data == null || data.length == 0) {
                        return null;
                }

                if (startsWith(
                                data,
                                new byte[] { 0x25, 0x50, 0x44, 0x46 })) {
                        return MediaType.APPLICATION_PDF;
                }

                if (startsWith(
                                data,
                                new byte[] {
                                                (byte) 0xff,
                                                (byte) 0xd8,
                                                (byte) 0xff })) {
                        return MediaType.IMAGE_JPEG;
                }

                if (startsWith(
                                data,
                                new byte[] {
                                                (byte) 0x89, 0x50, 0x4e, 0x47,
                                                0x0d, 0x0a, 0x1a, 0x0a })) {
                        return MediaType.IMAGE_PNG;
                }

                if (data.length >= 12
                                && data[0] == 0x52
                                && data[1] == 0x49
                                && data[2] == 0x46
                                && data[3] == 0x46
                                && data[8] == 0x57
                                && data[9] == 0x45
                                && data[10] == 0x42
                                && data[11] == 0x50) {
                        return MediaType.valueOf("image/webp");
                }

                return null;
        }

        private boolean startsWith(
                        byte[] value,
                        byte[] prefix) {

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
                String name = value == null || value.isBlank()
                                ? "document"
                                : value.trim();

                String sanitized = name.replaceAll(
                                "[\\/\"\r\n]", "_");

                return sanitized.isBlank()
                                ? "document"
                                : sanitized.substring(
                                                0,
                                                Math.min(255, sanitized.length()));
        }

        /**
         * Save one individual verification decision.
         *
         * This method never sends an individual document email.
         * After the database commit, it checks whether the entire
         * borrower document review is complete.
         */
        @PatchMapping("/{fileId}/verify")
        @org.springframework.transaction.annotation.Transactional
        public ResponseEntity<ApiResponse<Object>> verify(
                        @PathVariable Long fileId,
                        @RequestBody Map<String, String> body) {

                User user = currentUserUtil.getCurrentUser();

                if (body == null) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "A verification decision is required.");
                }

                String statusValue = body.get("status");
                String comment = body.get("comment");

                VerificationStatus status;

                try {
                        status = VerificationStatus.valueOf(
                                        statusValue.toUpperCase(
                                                        java.util.Locale.ROOT));
                } catch (Exception ex) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Invalid verification status.");
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
                                updated.getDocumentType().name()
                                                + " (" + updated.getFileName()
                                                + ") marked " + status.name()
                                                + (comment != null && !comment.isBlank()
                                                                ? ": " + comment
                                                                : ""),
                                null,
                                null,
                                "Documents & KYC");

                if (FINAL_DECISIONS.contains(status)) {
                        scheduleCombinedNotification(
                                        updated.getBorrower().getId(),
                                        user.getOrganization().getId());
                }

                return ResponseEntity.ok(
                                ApiResponse.safe(
                                                "Document "
                                                                + status.name()
                                                                                .toLowerCase(
                                                                                                java.util.Locale.ROOT)
                                                                                .replace('_', ' '),
                                                updated));
        }

        /**
         * The document decision is committed first.
         *
         * Then the combined-notification check runs in a new
         * transaction. Email failure cannot undo an already
         * saved verification decision.
         */
        private void scheduleCombinedNotification(
                        Long borrowerId,
                        Long organizationId) {

                if (!TransactionSynchronizationManager
                                .isSynchronizationActive()) {

                        log.warn(
                                        "Document review notification not scheduled: "
                                                        + "no active transaction for borrower {}",
                                        borrowerId);
                        return;
                }

                TransactionSynchronizationManager
                                .registerSynchronization(
                                                new TransactionSynchronization() {

                                                        @Override
                                                        public void afterCommit() {
                                                                try {
                                                                        TransactionTemplate transaction = new TransactionTemplate(
                                                                                        transactionManager);

                                                                        transaction.setPropagationBehavior(
                                                                                        TransactionDefinition.PROPAGATION_REQUIRES_NEW);

                                                                        transaction.execute(
                                                                                        status -> sendCombinedNotificationIfReady(
                                                                                                        borrowerId,
                                                                                                        organizationId));

                                                                } catch (RuntimeException ex) {
                                                                        log.error(
                                                                                        "Combined document review email "
                                                                                                        + "failed for borrower {}. "
                                                                                                        + "The manual completion "
                                                                                                        + "action can retry it.",
                                                                                        borrowerId,
                                                                                        ex);
                                                                }
                                                        }
                                                });
        }

        /**
         * Manual completion remains available as a retry path.
         * It uses exactly the same readiness and deduplication
         * rules as the automatic notification.
         */
        @PostMapping("/borrower/{borrowerId}/complete-review")
        @org.springframework.transaction.annotation.Transactional
        public ResponseEntity<ApiResponse<Object>> completeReview(
                        @PathVariable Long borrowerId) {

                User user = currentUserUtil.getCurrentUser();

                ReviewNotificationResult outcome = sendCombinedNotificationIfReady(
                                borrowerId,
                                user.getOrganization().getId());

                return switch (outcome) {
                        case SENT ->
                                ResponseEntity.ok(
                                                ApiResponse.safe(
                                                                "One combined document review email sent.",
                                                                null));

                        case ALREADY_SENT ->
                                ResponseEntity.ok(
                                                ApiResponse.safe(
                                                                "These document review decisions "
                                                                                + "were already emailed.",
                                                                null));

                        case EMAIL_MISSING ->
                                throw new ResponseStatusException(
                                                HttpStatus.CONFLICT,
                                                "The borrower needs an email address "
                                                                + "before review notification.");

                        case INCOMPLETE ->
                                throw new ResponseStatusException(
                                                HttpStatus.CONFLICT,
                                                "The document review is incomplete. "
                                                                + "Upload all mandatory documents "
                                                                + "and finish reviewing the current "
                                                                + "documents before notifying "
                                                                + "the borrower.");
                };
        }

        /**
         * One combined email for the current reviewed document set.
         *
         * The latest borrower-level copy of every document type
         * is considered. Old superseded copies and loan-specific
         * contracts are not included.
         */
        private ReviewNotificationResult sendCombinedNotificationIfReady(
                        Long borrowerId,
                        Long organizationId) {

                Borrower borrower = borrowerRepository.findById(borrowerId)
                                .filter(candidate -> candidate.getOrganization() != null
                                                && organizationId.equals(
                                                                candidate.getOrganization()
                                                                                .getId()))
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Borrower not found."));

                List<DocumentType> required = BorrowerDocumentRequirements.required(
                                borrower.getMaritalStatus(),
                                borrower.getEmploymentType());

                Map<DocumentType, BorrowerFile> latestByType = new EnumMap<>(DocumentType.class);

                for (BorrowerFile file : fileService.getByBorrowerMetadataOnly(borrowerId)) {

                        if (file == null
                                        || file.getDocumentType() == null
                                        || file.getLoan() != null) {
                                continue;
                        }

                        BorrowerFile previous = latestByType.get(file.getDocumentType());

                        if (previous == null
                                        || NEWEST_FIRST.compare(file, previous) > 0) {

                                latestByType.put(
                                                file.getDocumentType(),
                                                file);
                        }
                }

                // Never send the summary after reviewing only one
                // uploaded document when others are still missing.
                for (DocumentType type : required) {
                        if (!latestByType.containsKey(type)) {
                                return ReviewNotificationResult.INCOMPLETE;
                        }
                }

                List<BorrowerFile> current = new ArrayList<>();

                for (BorrowerFile file : latestByType.values()) {
                        boolean requiredType = required.contains(file.getDocumentType());

                        // Ignore obsolete optional documents.
                        // Expired mandatory documents still block completion.
                        if (file.getVerificationStatus() == VerificationStatus.EXPIRED
                                        && !requiredType) {
                                continue;
                        }

                        current.add(file);
                }

                if (current.isEmpty()) {
                        return ReviewNotificationResult.INCOMPLETE;
                }

                // Every current required or optional document must
                // have a final review decision.
                for (BorrowerFile file : current) {
                        if (!FINAL_DECISIONS.contains(
                                        file.getVerificationStatus())) {

                                return ReviewNotificationResult.INCOMPLETE;
                        }
                }

                if (borrower.getEmail() == null
                                || borrower.getEmail().isBlank()) {
                        return ReviewNotificationResult.EMAIL_MISSING;
                }

                current.sort(
                                Comparator.comparing(
                                                file -> file.getDocumentType().name()));

                // This fingerprint changes for a new document version,
                // changed final decision or changed review comment.
                // An unchanged review cannot create another notice.
                StringBuilder fingerprintSource = new StringBuilder();

                for (BorrowerFile file : current) {
                        fingerprintSource
                                        .append(file.getDocumentType())
                                        .append('|')
                                        .append(file.getId())
                                        .append('|')
                                        .append(file.getUploadedAt())
                                        .append('|')
                                        .append(file.getVerificationStatus())
                                        .append('|')
                                        .append(file.getOfficerComment())
                                        .append('\n');
                }

                String fingerprint = UUID.nameUUIDFromBytes(
                                fingerprintSource
                                                .toString()
                                                .getBytes(StandardCharsets.UTF_8))
                                .toString();

                // Reuse the existing V132 notification table.
                // ON CONFLICT prevents duplicate sends for the same
                // set of completed decisions.
                int reserved = jdbc.update(
                                """
                                                INSERT INTO borrower_document_review_notices
                                                    (borrower_id, fingerprint)
                                                VALUES (?, ?)
                                                ON CONFLICT DO NOTHING
                                                """,
                                borrowerId,
                                fingerprint);

                if (reserved == 0) {
                        return ReviewNotificationResult.ALREADY_SENT;
                }

                if (!mailService.sendDocumentReviewSummary(
                                borrower,
                                current)) {

                        // The enclosing transaction rolls back the
                        // reservation, allowing staff to retry.
                        throw new ResponseStatusException(
                                        HttpStatus.SERVICE_UNAVAILABLE,
                                        "Combined document review email delivery failed. "
                                                        + "Please retry the review notification.");
                }

                log.info(
                                "Combined document review notification sent "
                                                + "for borrower {} with {} reviewed types",
                                borrowerId,
                                current.size());

                return ReviewNotificationResult.SENT;
        }

        @DeleteMapping("/{fileId}")
        @org.springframework.transaction.annotation.Transactional
        public ResponseEntity<Void> delete(
                        @PathVariable Long fileId) {

                User user = currentUserUtil.getCurrentUser();

                BorrowerFile file = fileService.getByIdForOrg(
                                fileId,
                                user.getOrganization().getId());

                auditService.log(
                                file.getBorrower().getOrganization(),
                                user,
                                "DOCUMENT_DELETED",
                                "BORROWER_FILE",
                                String.valueOf(fileId),
                                "Deleted "
                                                + file.getDocumentType()
                                                + " (" + file.getFileName()
                                                + ") for borrower #"
                                                + file.getBorrower().getId(),
                                null,
                                null,
                                "Documents & KYC");

                fileService.delete(fileId);

                return ResponseEntity.noContent().build();
        }
}
