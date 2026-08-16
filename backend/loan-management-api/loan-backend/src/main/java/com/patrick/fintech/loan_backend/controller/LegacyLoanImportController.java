package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.ImportRowResult;
import com.patrick.fintech.loan_backend.mapper.ResponseDtoMapper;
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
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.util.List;

@RestController
@RequestMapping("/api/import/legacy-loans")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class LegacyLoanImportController {
    private final LegacyLoanImportService importService;
    private final AsyncLegacyImportService asyncImportService;
    private final ImportBatchRepository importBatchRepo;
    private final OrganizationRepository orgRepo;
    private final CurrentUserUtil currentUserUtil;
    @Value("${app.import.staging-dir:${java.io.tmpdir}/loansaas-imports}")
    private String stagingDir;
    private static final long MAX_FILE_BYTES = 50L * 1024 * 1024;

    @GetMapping(value = "/template", produces = "text/csv")
    public ResponseEntity<String> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"legacy-loan-import-template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv")).body(importService.buildCsvTemplate());
    }

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<List<ImportRowResult>>> preview(@RequestParam("file") MultipartFile file)
            throws Exception {
        if (file == null || file.isEmpty())
            throw new IllegalArgumentException("No file was uploaded");
        if (file.getSize() > MAX_FILE_BYTES)
            throw new IllegalArgumentException("Import file exceeds 50MB");
        Organization org = currentOrg();
        List<ImportRowResult> results = importService.preview(file.getOriginalFilename(), file.getInputStream(), org);
        long ok = results.stream().filter(ImportRowResult::isSuccess).count();
        return ResponseEntity.ok(ApiResponse.ok(
                ok + "/" + results.size() + " rows would import successfully. Nothing has been saved yet.", results));
    }

    /**
     * Asynchronous production import. The HTTP request never parses the workbook.
     */
    @PostMapping("/commit")
    public ResponseEntity<ApiResponse<Object>> commit(@RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty())
            throw new IllegalArgumentException("No file was uploaded");
        if (file.getSize() > MAX_FILE_BYTES)
            throw new IllegalArgumentException("Import file exceeds 50MB");
        Files.createDirectories(Path.of(stagingDir));
        String safe = Path.of(file.getOriginalFilename() == null ? "import.xlsx" : file.getOriginalFilename())
                .getFileName().toString();
        if (safe.contains(".."))
            throw new IllegalArgumentException("Invalid filename");
        Path staged = Files.createTempFile(Path.of(stagingDir), "loan-import-", "-" + safe);
        try {
            file.transferTo(staged);
        } catch (Exception e) {
            Files.deleteIfExists(staged);
            throw e;
        }
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        Long userId = currentUserUtil.getCurrentUserId();
        var batch = asyncImportService.stage(staged, safe, orgId, userId, file.getSize());
        asyncImportService.process(batch.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.safe("Import queued", batch));
    }

    @GetMapping("/batches")
    public ResponseEntity<ApiResponse<Object>> batches() {
        var list = importBatchRepo
                .findByOrganization_IdOrderByCreatedAtDesc(currentUserUtil.getCurrentOrganizationId());
        return ResponseEntity.ok(ApiResponse.safe(list));
    }

    @GetMapping("/batches/{id}")
    public ResponseEntity<ApiResponse<Object>> batch(@PathVariable Long id) {
        var b = importBatchRepo.findById(id).orElseThrow(() -> new RuntimeException("Import batch not found"));
        if (!b.getOrganization().getId().equals(currentUserUtil.getCurrentOrganizationId()))
            throw new RuntimeException("Access denied");
        return ResponseEntity.ok(ApiResponse.safe(b));
    }

    @GetMapping("/batches/{id}/errors")
    public ResponseEntity<Resource> errors(@PathVariable Long id) {
        var b = importBatchRepo.findById(id).orElseThrow(() -> new RuntimeException("Import batch not found"));
        if (!b.getOrganization().getId().equals(currentUserUtil.getCurrentOrganizationId()))
            throw new RuntimeException("Access denied");
        if (b.getErrorReportPath() == null)
            return ResponseEntity.noContent().build();
        Path path = Path.of(b.getErrorReportPath()).normalize();
        if (!path.startsWith(Path.of(stagingDir).toAbsolutePath().normalize()))
            throw new RuntimeException("Invalid error report path");
        Resource resource = new FileSystemResource(path);
        if (!resource.exists())
            return ResponseEntity.noContent().build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"import-" + id + "-errors.csv\"")
                .body(resource);
    }

    private Organization currentOrg() {
        return orgRepo.findById(currentUserUtil.getCurrentOrganizationId())
                .orElseThrow(() -> new RuntimeException("Organization not found"));
    }
}
