package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.ImportRowResult;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.ImportBatchRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.service.AsyncLegacyImportService;
import com.patrick.fintech.loan_backend.service.LegacyLoanImportService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Controlled administrative entry point for historical loan migration.
 *
 * Preview is synchronous and read-only. Commit only stages the workbook and
 * queues a background job; no workbook is parsed on the HTTP request thread.
 */
@RestController
@RequestMapping("/api/import/legacy-loans")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class LegacyLoanImportController {

        private static final long DEFAULT_MAX_FILE_BYTES = 50L * 1024L * 1024L;

        private final LegacyLoanImportService importService;
        private final AsyncLegacyImportService asyncImportService;
        private final ImportBatchRepository importBatchRepo;
        private final OrganizationRepository orgRepo;
        private final CurrentUserUtil currentUserUtil;

        @Value("${app.import.staging-dir:${java.io.tmpdir}/loansaas-imports}")
        private String stagingDir;

        @Value("${app.import.max-file-bytes:52428800}")
        private long maxFileBytes;

        @GetMapping(value = "/template", produces = "text/csv")
        public ResponseEntity<String> template() {
                return ResponseEntity.ok()
                                .header(
                                                HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=\"legacy-loan-import-template.csv\"")
                                .contentType(MediaType.parseMediaType("text/csv"))
                                .body(importService.buildCsvTemplate());
        }

        @PostMapping("/preview")
        public ResponseEntity<ApiResponse<List<ImportRowResult>>> preview(
                        @RequestParam("file") MultipartFile file) throws Exception {

                validateUploadedFile(file, true);

                Organization organization = currentOrganization();
                String filename = safeFilename(file.getOriginalFilename());

                List<ImportRowResult> results = importService.preview(
                                filename,
                                file.getInputStream(),
                                organization);

                long successful = results.stream()
                                .filter(ImportRowResult::isSuccess)
                                .count();

                String message = successful + "/" + results.size()
                                + " rows passed validation. Nothing has been saved.";

                return ResponseEntity.ok(ApiResponse.ok(message, results));
        }

        /**
         * Production import endpoint. The HTTP request only stages the file and
         * creates a QUEUED ImportBatch. Workbook parsing and database writes happen
         * asynchronously so Render/Vercel request timeouts cannot terminate a
         * long-running historical migration.
         */
        @PostMapping("/commit")
        public ResponseEntity<ApiResponse<Object>> commit(
                        @RequestParam("file") MultipartFile file) throws Exception {

                validateUploadedFile(file, false);

                Path root = Path.of(stagingDir).toAbsolutePath().normalize();
                Files.createDirectories(root);

                String safeFilename = safeFilename(file.getOriginalFilename());
                String lower = safeFilename.toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".csv") && !lower.endsWith(".xlsx")) {
                        throw new IllegalArgumentException(
                                        "Production legacy import accepts CSV and XLSX files. Convert XLS files to XLSX first.");
                }

                Path staged = Files.createTempFile(root, "loan-import-", "-" + safeFilename);
                try {
                        file.transferTo(staged);
                } catch (Exception e) {
                        Files.deleteIfExists(staged);
                        throw e;
                }

                Long organizationId = currentUserUtil.getCurrentOrganizationId();
                Long userId = currentUserUtil.getCurrentUserId();

                try {
                        var batch = asyncImportService.stage(
                                        staged,
                                        safeFilename,
                                        organizationId,
                                        userId,
                                        file.getSize());

                        asyncImportService.process(batch.getId());

                        return ResponseEntity.status(HttpStatus.ACCEPTED)
                                        .body(ApiResponse.safe(
                                                        "Legacy loan import queued. Monitor the import batch for progress.",
                                                        batch));
                } catch (Exception e) {
                        Files.deleteIfExists(staged);
                        throw e;
                }
        }

        @GetMapping("/batches")
        public ResponseEntity<ApiResponse<Object>> batches() {
                Long organizationId = currentUserUtil.getCurrentOrganizationId();
                var list = importBatchRepo.findByOrganization_IdOrderByCreatedAtDesc(organizationId);
                return ResponseEntity.ok(ApiResponse.safe(list));
        }

        @GetMapping("/batches/{id}")
        public ResponseEntity<ApiResponse<Object>> batch(@PathVariable Long id) {
                if (id == null || id <= 0) {
                        throw new IllegalArgumentException("Invalid import batch ID.");
                }

                var batch = importBatchRepo.findDetailedById(id)
                                .orElseThrow(() -> new IllegalArgumentException("Import batch not found."));

                assertOrganizationAccess(batch.getOrganization());
                return ResponseEntity.ok(ApiResponse.safe(batch));
        }

        @GetMapping("/batches/{id}/errors")
        public ResponseEntity<Resource> errors(@PathVariable Long id) {
                if (id == null || id <= 0) {
                        throw new IllegalArgumentException("Invalid import batch ID.");
                }

                var batch = importBatchRepo.findDetailedById(id)
                                .orElseThrow(() -> new IllegalArgumentException("Import batch not found."));

                assertOrganizationAccess(batch.getOrganization());

                if (batch.getErrorReportPath() == null || batch.getErrorReportPath().isBlank()) {
                        return ResponseEntity.noContent().build();
                }

                Path stagingRoot = Path.of(stagingDir).toAbsolutePath().normalize();
                Path reportPath = Path.of(batch.getErrorReportPath()).toAbsolutePath().normalize();

                if (!reportPath.startsWith(stagingRoot)) {
                        throw new IllegalStateException("Invalid error-report path.");
                }

                Resource resource = new FileSystemResource(reportPath);
                if (!resource.exists() || !resource.isReadable()) {
                        return ResponseEntity.noContent().build();
                }

                return ResponseEntity.ok()
                                .contentType(MediaType.parseMediaType("text/csv"))
                                .header(
                                                HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=\"legacy-import-" + id + "-errors.csv\"")
                                .body(resource);
        }

        private void validateUploadedFile(MultipartFile file, boolean allowXls) {
                if (file == null || file.isEmpty()) {
                        throw new IllegalArgumentException("No import file was uploaded.");
                }

                long limit = maxFileBytes > 0 ? maxFileBytes : DEFAULT_MAX_FILE_BYTES;
                if (file.getSize() > limit) {
                        throw new IllegalArgumentException(
                                        "Import file exceeds the configured maximum size of "
                                                        + (limit / (1024L * 1024L)) + " MB.");
                }

                String filename = safeFilename(file.getOriginalFilename());
                String lower = filename.toLowerCase(Locale.ROOT);
                boolean supported = lower.endsWith(".csv")
                                || lower.endsWith(".xlsx")
                                || (allowXls && lower.endsWith(".xls"));

                if (!supported) {
                        throw new IllegalArgumentException(
                                        "Unsupported import file type. Please upload CSV, XLSX"
                                                        + (allowXls ? " or XLS" : "") + ".");
                }
        }

        private String safeFilename(String originalFilename) {
                if (originalFilename == null || originalFilename.isBlank()) {
                        return "legacy-loan-import.xlsx";
                }

                String filename = Path.of(originalFilename).getFileName().toString().trim();
                if (filename.isBlank() || ".".equals(filename) || "..".equals(filename)) {
                        throw new IllegalArgumentException("Invalid import filename.");
                }
                if (filename.length() > 255) {
                        filename = filename.substring(0, 255);
                }
                return filename;
        }

        private Organization currentOrganization() {
                Long organizationId = currentUserUtil.getCurrentOrganizationId();
                return orgRepo.findById(organizationId)
                                .orElseThrow(() -> new IllegalArgumentException("Organization not found."));
        }

        private void assertOrganizationAccess(Organization organization) {
                Long currentOrganizationId = currentUserUtil.getCurrentOrganizationId();
                if (organization == null
                                || organization.getId() == null
                                || !organization.getId().equals(currentOrganizationId)) {
                        throw new IllegalArgumentException("Access denied.");
                }
        }
}
