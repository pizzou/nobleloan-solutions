package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.dto.ImportRowResult;
import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.ImportBatch;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.ImportBatchRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import com.patrick.fintech.loan_backend.util.StreamingLedgerFileParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Production asynchronous legacy-loan import coordinator.
 *
 * The HTTP request only stages the file and creates an ImportBatch. Workbook
 * parsing and row transactions happen in a background worker. Every imported
 * loan is marked as historical/imported by LegacyLoanImportRowService and is
 * posted to accounting as an opening balance, so financial reconciliation is
 * not asked to replay historical cash disbursements.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncLegacyImportService {

        private static final long MAX_ROWS = 100_000L;
        private static final int PROGRESS_UPDATE_EVERY = 100;
        private static final int MAX_STORED_ROW_RESULTS = 10_000;

        @Value("${app.import.staging-dir:${java.io.tmpdir}/loansaas-imports}")
        private String stagingDir;

        private final ImportBatchRepository batchRepo;
        private final OrganizationRepository organizationRepository;
        private final UserRepository userRepository;
        private final LegacyLoanImportRowService rowService;
        private final AuditService auditService;
        private final ImportBatchStateService stateService;
        private final ObjectMapper objectMapper;

        @Transactional
        public ImportBatch stage(
                        Path stagedFile,
                        String fileName,
                        Long organizationId,
                        Long userId,
                        long fileSize) {

                if (stagedFile == null || !Files.isRegularFile(stagedFile)) {
                        throw new IllegalArgumentException("The staged import file does not exist.");
                }
                if (organizationId == null) {
                        throw new IllegalArgumentException("Organization ID is required.");
                }
                if (userId == null) {
                        throw new IllegalArgumentException("Importing user ID is required.");
                }
                if (fileSize <= 0) {
                        throw new IllegalArgumentException("Import file is empty.");
                }

                Organization org = organizationRepository.findById(organizationId)
                                .orElseThrow(() -> new IllegalArgumentException("Organization not found."));

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new IllegalArgumentException("User not found."));

                ImportBatch batch = ImportBatch.builder()
                                .organization(org)
                                .importedBy(user)
                                .fileName(fileName)
                                .totalRows(0)
                                .successCount(0)
                                .failureCount(0)
                                .status("QUEUED")
                                .processedRows(0)
                                .progressPercent(0)
                                .fileSize(fileSize)
                                .stagedFilePath(stagedFile.toAbsolutePath().normalize().toString())
                                .build();

                return batchRepo.save(batch);
        }

        @Async("loansaasAsyncExecutor")
        public CompletableFuture<Void> process(Long batchId) {
                try {
                        doProcess(batchId);
                        return CompletableFuture.completedFuture(null);
                } catch (Exception e) {
                        log.error("Asynchronous legacy import failed. batchId={}", batchId, e);
                        try {
                                ImportBatch failedBatch = batchRepo.findById(batchId).orElse(null);
                                int total = failedBatch != null && failedBatch.getTotalRows() != null
                                                ? failedBatch.getTotalRows()
                                                : 0;
                                int processed = failedBatch != null && failedBatch.getProcessedRows() != null
                                                ? failedBatch.getProcessedRows()
                                                : 0;
                                int success = failedBatch != null && failedBatch.getSuccessCount() != null
                                                ? failedBatch.getSuccessCount()
                                                : 0;
                                int failure = failedBatch != null && failedBatch.getFailureCount() != null
                                                ? failedBatch.getFailureCount()
                                                : 0;
                                stateService.fail(
                                                batchId,
                                                safeMessage(e),
                                                total,
                                                processed,
                                                success,
                                                failure,
                                                failedBatch != null ? failedBatch.getRowResults() : null);
                        } catch (Exception stateFailure) {
                                log.error("Unable to persist failed import-batch state. batchId={}", batchId,
                                                stateFailure);
                        }
                        return CompletableFuture.failedFuture(e);
                }
        }

        protected void doProcess(Long batchId) throws Exception {
                if (batchId == null || batchId <= 0) {
                        throw new IllegalArgumentException("Invalid import batch ID.");
                }

                if (!stateService.claimForProcessing(batchId)) {
                        log.info("Legacy import batch {} was already claimed or completed; skipping duplicate worker.",
                                        batchId);
                        return;
                }

                ImportBatch batch = batchRepo.findDetailedById(batchId)
                                .orElseThrow(() -> new IllegalArgumentException("Import batch not found: " + batchId));

                Organization organization = batch.getOrganization();
                if (organization == null || organization.getId() == null) {
                        throw new IllegalStateException("Import batch has no valid organization.");
                }

                if (batch.getStagedFilePath() == null || batch.getStagedFilePath().isBlank()) {
                        throw new IllegalStateException("Import batch has no staged file.");
                }

                Path stagedFile = Paths.get(batch.getStagedFilePath()).toAbsolutePath().normalize();
                Path stagingRoot = Path.of(stagingDir).toAbsolutePath().normalize();
                if (!stagedFile.startsWith(stagingRoot)) {
                        throw new IllegalStateException(
                                        "Import batch references a file outside the staging directory.");
                }
                if (!Files.isRegularFile(stagedFile)) {
                        throw new IllegalStateException("The staged import file no longer exists.");
                }

                Path errorReport = Path.of(stagedFile + ".errors.csv").toAbsolutePath().normalize();
                if (!errorReport.startsWith(stagingRoot)) {
                        throw new IllegalStateException("Invalid import error-report path.");
                }

                AtomicInteger processed = new AtomicInteger(0);
                AtomicInteger success = new AtomicInteger(0);
                AtomicInteger failed = new AtomicInteger(0);
                List<ImportRowResult> storedResults = new ArrayList<>();
                Map<String, Borrower> sessionBorrowers = new HashMap<>();

                // ---------------------------------------------------------------
                // PASS 1 — COUNT UNIQUE SUPPORTED RECORDS
                // ---------------------------------------------------------------
                long totalRows;
                try (InputStream countInput = Files.newInputStream(stagedFile, StandardOpenOption.READ)) {
                        totalRows = StreamingLedgerFileParser.countRows(
                                        batch.getFileName(),
                                        countInput,
                                        MAX_ROWS);
                }

                if (totalRows <= 0) {
                        throw new IllegalArgumentException("The uploaded ledger contains no supported loan records.");
                }
                if (totalRows > MAX_ROWS) {
                        throw new IllegalArgumentException(
                                        "The uploaded ledger contains " + totalRows
                                                        + " supported rows. The maximum allowed per import is "
                                                        + MAX_ROWS + ".");
                }

                stateService.setTotals(batchId, Math.toIntExact(totalRows));
                stateService.progress(batchId, 0, Math.toIntExact(totalRows), 0, 0);

                // ---------------------------------------------------------------
                // ERROR REPORT
                // ---------------------------------------------------------------
                Files.createDirectories(stagingRoot);
                stateService.setErrorReportPath(batchId, errorReport.toString());

                try (BufferedWriter errorWriter = Files.newBufferedWriter(
                                errorReport,
                                StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING,
                                StandardOpenOption.WRITE)) {

                        errorWriter.write("row,error");
                        errorWriter.newLine();

                        // -----------------------------------------------------------
                        // PASS 2 — ACTUAL ROW IMPORT
                        // -----------------------------------------------------------
                        try (InputStream importInput = Files.newInputStream(stagedFile, StandardOpenOption.READ)) {
                                StreamingLedgerFileParser.stream(
                                                batch.getFileName(),
                                                importInput,
                                                MAX_ROWS,
                                                (rowNumber, row) -> {
                                                        ImportRowResult result;

                                                        try {
                                                                result = rowService.importRow(
                                                                                row,
                                                                                Math.toIntExact(rowNumber),
                                                                                organization,
                                                                                batchId,
                                                                                true,
                                                                                sessionBorrowers);
                                                        } catch (Exception e) {
                                                                log.error(
                                                                                "Unexpected legacy import row failure. batchId={}, rowNumber={}",
                                                                                batchId,
                                                                                rowNumber,
                                                                                e);
                                                                result = ImportRowResult.builder()
                                                                                .rowNumber(Math.toIntExact(rowNumber))
                                                                                .success(false)
                                                                                .error("Unexpected error while processing this row. The row was not imported.")
                                                                                .build();
                                                        }

                                                        if (result != null && result.isSuccess()) {
                                                                success.incrementAndGet();
                                                        } else {
                                                                failed.incrementAndGet();
                                                                writeError(
                                                                                errorWriter,
                                                                                rowNumber,
                                                                                result == null ? "Unknown import error."
                                                                                                : result.getError());
                                                        }

                                                        if (result != null && storedResults
                                                                        .size() < MAX_STORED_ROW_RESULTS) {
                                                                storedResults.add(result);
                                                        }

                                                        int currentProcessed = processed.incrementAndGet();
                                                        if (currentProcessed % PROGRESS_UPDATE_EVERY == 0
                                                                        || currentProcessed == totalRows) {
                                                                stateService.progress(
                                                                                batchId,
                                                                                currentProcessed,
                                                                                Math.toIntExact(totalRows),
                                                                                success.get(),
                                                                                failed.get());
                                                                errorWriter.flush();
                                                        }
                                                });
                        }
                } finally {
                        // The original workbook is no longer needed after the second pass.
                        // Keep the CSV error report for audit/download purposes.
                        Files.deleteIfExists(stagedFile);
                }

                String status = failed.get() == 0
                                ? "COMPLETED"
                                : success.get() == 0
                                                ? "FAILED"
                                                : "PARTIAL";

                String rowResults = serializeResults(
                                storedResults,
                                processed.get() > MAX_STORED_ROW_RESULTS);

                stateService.complete(
                                batchId,
                                status,
                                Math.toIntExact(totalRows),
                                processed.get(),
                                success.get(),
                                failed.get(),
                                rowResults);

                // The error-report path is persisted separately after completion so the
                // download endpoint can serve failed rows without exposing the staged file.
                ImportBatch completed = batchRepo.findById(batchId).orElseThrow();
                completed.setErrorReportPath(errorReport.toString());
                batchRepo.save(completed);

                auditService.log(
                                organization,
                                batch.getImportedBy(),
                                "LEGACY_LOANS_IMPORTED",
                                "IMPORT_BATCH",
                                String.valueOf(batchId),
                                "Imported " + success.get() + "/" + processed.get()
                                                + " unique legacy loan rows from \"" + batch.getFileName()
                                                + "\". Status: " + status
                                                + ". Historical loans were posted as opening balances; no historical cash disbursement journal was replayed.");

                log.info(
                                "Legacy import completed. batchId={}, organizationId={}, file={}, total={}, success={}, failed={}, status={}",
                                batchId,
                                organization.getId(),
                                batch.getFileName(),
                                totalRows,
                                success.get(),
                                failed.get(),
                                status);
        }

        private void writeError(BufferedWriter writer, long rowNumber, String error) throws Exception {
                writer.write(Long.toString(rowNumber));
                writer.write(',');
                writer.write('"');
                writer.write(escapeCsv(error == null ? "Import failed." : error));
                writer.write('"');
                writer.newLine();
        }

        private String escapeCsv(String value) {
                return value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ");
        }

        private String serializeResults(List<ImportRowResult> results, boolean truncated) {
                try {
                        Map<String, Object> payload = new java.util.LinkedHashMap<>();
                        payload.put("truncated", truncated);
                        payload.put("storedRows", results.size());
                        payload.put("results", results);
                        return objectMapper.writeValueAsString(payload);
                } catch (Exception e) {
                        log.warn("Unable to serialize legacy import row results.", e);
                        return null;
                }
        }

        private String safeMessage(Exception e) {
                String message = e.getMessage();
                if (message == null || message.isBlank()) {
                        return "Legacy loan import failed.";
                }
                return message.length() > 1000 ? message.substring(0, 1000) : message;
        }

        @Scheduled(cron = "0 30 3 * * *")
        public void cleanupStagingFiles() {
                try {
                        Path root = Path.of(stagingDir).toAbsolutePath().normalize();
                        if (!Files.isDirectory(root)) {
                                return;
                        }

                        long cutoff = System.currentTimeMillis()
                                        - TimeUnit.DAYS.toMillis(7);

                        try (var files = Files.list(root)) {
                                files.filter(Files::isRegularFile)
                                                .filter(path -> {
                                                        try {
                                                                return Files.getLastModifiedTime(path)
                                                                                .toMillis() < cutoff;
                                                        } catch (Exception e) {
                                                                return false;
                                                        }
                                                })
                                                .forEach(path -> {
                                                        try {
                                                                Files.deleteIfExists(path);
                                                        } catch (Exception e) {
                                                                log.warn("Could not delete stale import staging file {}",
                                                                                path, e);
                                                        }
                                                });
                        }
                } catch (Exception e) {
                        log.warn("Import staging cleanup failed.", e);
                }
        }
}
