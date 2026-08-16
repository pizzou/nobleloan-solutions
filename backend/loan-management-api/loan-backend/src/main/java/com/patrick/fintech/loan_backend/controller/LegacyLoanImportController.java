package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.ImportBatchResponse;
import com.patrick.fintech.loan_backend.dto.ImportRowResult;
import com.patrick.fintech.loan_backend.service.AsyncLegacyImportService;
import com.patrick.fintech.loan_backend.service.ImportFileSecurityService;
import com.patrick.fintech.loan_backend.service.ImportQueryService;
import com.patrick.fintech.loan_backend.service.LegacyLoanImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/import/legacy-loans")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class LegacyLoanImportController {

    private static final long DEFAULT_MAX_FILE_BYTES = 50L * 1024L * 1024L;

    private final LegacyLoanImportService importService;

    private final AsyncLegacyImportService asyncImportService;

    private final ImportQueryService importQueryService;

    private final ImportFileSecurityService importFileSecurityService;

    @Value("${app.import.max-file-bytes:52428800}")
    private long maxFileBytes;

    @Value("${app.import.preview-max-file-bytes:10485760}")
    private long previewMaxFileBytes;

    @GetMapping(value = "/template", produces = "text/csv")
    public ResponseEntity<String> template() {

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"legacy-loan-import-template.csv\"")
                .contentType(
                        MediaType.parseMediaType("text/csv"))
                .body(
                        importService.buildCsvTemplate());
    }

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<List<ImportRowResult>>> preview(
            @RequestParam("file") MultipartFile file) throws IOException {

        validatePreviewFile(file);

        String safeFilename = importFileSecurityService
                .safeFilename(
                        file.getOriginalFilename());

        List<ImportRowResult> results = importService.preview(
                safeFilename,
                file.getInputStream(),
                importQueryService
                        .getCurrentOrganization());

        long successfulRows = results.stream()
                .filter(
                        ImportRowResult::isSuccess)
                .count();

        String message = successfulRows
                + "/"
                + results.size()
                + " rows would import successfully. "
                + "Nothing has been saved yet.";

        return ResponseEntity.ok(
                ApiResponse.ok(
                        message,
                        results));
    }

    /**
     * Stages the file and queues asynchronous processing.
     *
     * The HTTP request never parses the workbook.
     */
    @PostMapping("/commit")
    public ResponseEntity<ApiResponse<ImportBatchResponse>> commit(
            @RequestParam("file") MultipartFile file) throws IOException {

        validateUploadedFile(file);

        importFileSecurityService
                .validate(file);

        if (file.getSize() > maxFileBytes) {

            throw new IllegalArgumentException(
                    "Import file exceeds the maximum allowed size of "
                            + maxFileBytes
                            + " bytes");
        }

        ImportBatchResponse response = asyncImportService.stageAndQueue(
                file,
                importQueryService
                        .getCurrentOrganizationId(),
                importQueryService
                        .getCurrentUserId());

        /*
         * IMPORTANT:
         *
         * process() is invoked from the controller through the
         * Spring bean proxy, so @Async is actually applied.
         */
        asyncImportService.process(
                response.getId());

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(
                        ApiResponse.ok(
                                "Import queued",
                                response));
    }

    @GetMapping("/batches")
    public ResponseEntity<ApiResponse<List<ImportBatchResponse>>> batches() {

        return ResponseEntity.ok(
                ApiResponse.ok(
                        importQueryService
                                .findCurrentOrganizationBatches()));
    }

    @GetMapping("/batches/{id}")
    public ResponseEntity<ApiResponse<ImportBatchResponse>> batch(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                ApiResponse.ok(
                        importQueryService
                                .findCurrentOrganizationBatch(id)));
    }

    @GetMapping("/batches/{id}/errors")
    public ResponseEntity<Resource> errors(
            @PathVariable Long id) {

        Resource resource = importQueryService
                .getErrorReportForCurrentOrganization(id);

        if (resource == null) {
            return ResponseEntity
                    .noContent()
                    .build();
        }

        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(
                                "text/csv"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"import-"
                                + id
                                + "-errors.csv\"")
                .body(resource);
    }

    private void validateUploadedFile(
            MultipartFile file) {

        if (file == null
                || file.isEmpty()) {

            throw new IllegalArgumentException(
                    "No file was uploaded");
        }
    }

    private void validatePreviewFile(
            MultipartFile file) {

        validateUploadedFile(file);

        importFileSecurityService
                .validate(file);

        if (file.getSize() > previewMaxFileBytes) {

            throw new IllegalArgumentException(
                    "Preview file exceeds the maximum allowed size of "
                            + previewMaxFileBytes
                            + " bytes");
        }
    }
}