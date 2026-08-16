package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.ImportBatchResponse;
import com.patrick.fintech.loan_backend.mapper.ImportBatchResponseMapper;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncLegacyImportService {

    private static final long MAX_ROWS = 100_000L;

    private static final int PROGRESS_UPDATE_INTERVAL = 100;

    private static final long STALE_FILE_AGE_DAYS = 7L;

    @Value("${app.import.staging-dir:${java.io.tmpdir}/loansaas-imports}")
    private String stagingDir;

    private final ImportBatchRepository batchRepo;

    private final OrganizationRepository organizationRepository;

    private final UserRepository userRepository;

    private final LegacyLoanImportRowService rowService;

    private final AuditService auditService;

    private final ImportBatchStateService stateService;

    private final ImportFileSecurityService fileSecurityService;

    /**
     * Stage an uploaded file and create the durable QUEUED batch record.
     *
     * The controller invokes process() separately.
     * This is intentional because process() is annotated with @Async
     * and must be invoked through the Spring proxy.
     */
    @Transactional
    public ImportBatchResponse stageAndQueue(
            MultipartFile file,
            Long organizationId,
            Long userId) throws IOException {

        if (file == null || file.isEmpty()) {

            throw new IllegalArgumentException(
                    "No import file was uploaded");
        }

        if (organizationId == null
                || organizationId <= 0) {

            throw new IllegalArgumentException(
                    "Invalid organization context");
        }

        if (userId == null
                || userId <= 0) {

            throw new IllegalArgumentException(
                    "Invalid authenticated user");
        }

        fileSecurityService
                .validate(file);

        String safeName = fileSecurityService
                .safeFilename(
                        file.getOriginalFilename());

        Path root = Path.of(stagingDir)
                .toAbsolutePath()
                .normalize();

        Files.createDirectories(root);

        Path staged = Files.createTempFile(
                root,
                "loan-import-",
                "-" + safeName);

        try {

            file.transferTo(staged);

            ImportBatch batch = stage(
                    staged,
                    safeName,
                    organizationId,
                    userId,
                    file.getSize());

            return ImportBatchResponseMapper
                    .toResponse(batch);

        } catch (Exception ex) {

            Files.deleteIfExists(staged);

            throw ex;
        }
    }

    @Transactional
    public ImportBatch stage(
            Path stagedFile,
            String fileName,
            Long organizationId,
            Long userId,
            long fileSize) {

        if (stagedFile == null
                || !Files.isRegularFile(
                        stagedFile)) {

            throw new IllegalArgumentException(
                    "Staged import file does not exist");
        }

        Path root = Path.of(stagingDir)
                .toAbsolutePath()
                .normalize();

        Path normalized = stagedFile
                .toAbsolutePath()
                .normalize();

        if (!normalized.startsWith(root)) {

            throw new IllegalArgumentException(
                    "Invalid staging file location");
        }

        Organization organization = organizationRepository
                .findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Organization not found"));

        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found"));

        if (user.getOrganization() == null
                || user.getOrganization().getId() == null
                || !organizationId.equals(
                        user.getOrganization().getId())) {

            throw new IllegalArgumentException(
                    "Authenticated user is not assigned to this organization");
        }

        String safeName = fileSecurityService
                .safeFilename(fileName);

        ImportBatch batch = ImportBatch.builder()
                .organization(organization)
                .importedBy(user)
                .fileName(safeName)
                .totalRows(0)
                .successCount(0)
                .failureCount(0)
                .status("QUEUED")
                .processedRows(0)
                .progressPercent(0)
                .fileSize(fileSize)
                .stagedFilePath(
                        normalized.toString())
                .build();

        return batchRepo.save(batch);
    }

    /**
     * Runs outside the HTTP request.
     */
    @Async("loansaasAsyncExecutor")
    public CompletableFuture<Void> process(
            Long batchId) {

        try {

            doProcess(batchId);

            return CompletableFuture.completedFuture(
                    null);

        } catch (Exception ex) {

            log.error(
                    "Async legacy import failed. batchId={}",
                    batchId,
                    ex);

            stateService.failed(
                    batchId,
                    ex.getMessage() == null
                            ? "Import failed"
                            : ex.getMessage());

            return CompletableFuture.failedFuture(
                    ex);
        }
    }

    protected void doProcess(
            Long batchId) throws Exception {

        ImportBatch batch = batchRepo
                .findDetailedById(batchId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Import batch not found"));

        if (!"QUEUED".equals(
                batch.getStatus())) {

            log.info(
                    "Skipping import batch {} because status is {}",
                    batchId,
                    batch.getStatus());

            return;
        }

        Organization organization = batch.getOrganization();

        Path root = Path.of(stagingDir)
                .toAbsolutePath()
                .normalize();

        Path file = Path.of(
                batch.getStagedFilePath())
                .toAbsolutePath()
                .normalize();

        if (!file.startsWith(root)
                || !Files.isRegularFile(file)) {

            throw new IllegalStateException(
                    "Import staging file is missing or outside the configured staging directory");
        }

        Path errors = root.resolve(
                file.getFileName()
                        + ".errors.csv")
                .normalize();

        if (!errors.startsWith(root)) {

            throw new IllegalStateException(
                    "Invalid import error report path");
        }

        AtomicInteger processed = new AtomicInteger();

        AtomicInteger success = new AtomicInteger();

        AtomicInteger failed = new AtomicInteger();

        Map<String, Borrower> sessionBorrowers = new HashMap<>();

        stateService.update(
                batchId,
                "PROCESSING",
                0,
                0,
                0,
                0);

        Files.writeString(
                errors,
                "row,error\n",
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);

        stateService.errorReport(
                batchId,
                errors.toString());

        try (
                InputStream input = Files.newInputStream(
                        file,
                        StandardOpenOption.READ)) {

            StreamingLedgerFileParser.stream(
                    batch.getFileName(),
                    input,
                    MAX_ROWS,
                    (rowNumber, row) -> {

                        var result = rowService.importRow(
                                row,
                                Math.toIntExact(
                                        rowNumber),
                                organization,
                                batchId,
                                true,
                                sessionBorrowers);

                        if (result != null
                                && result.isSuccess()) {

                            success.incrementAndGet();

                        } else {

                            failed.incrementAndGet();

                            String error = result == null
                                    ? "Unknown import error"
                                    : String.valueOf(
                                            result.getError());

                            appendError(
                                    errors,
                                    rowNumber,
                                    error);
                        }

                        int current = processed.incrementAndGet();

                        if (current
                                % PROGRESS_UPDATE_INTERVAL == 0) {

                            int percent = Math.min(
                                    99,
                                    (int) ((current
                                            * 100L)
                                            / Math.max(
                                                    1L,
                                                    MAX_ROWS)));

                            stateService.update(
                                    batchId,
                                    "PROCESSING",
                                    current,
                                    success.get(),
                                    failed.get(),
                                    percent);
                        }
                    });

        } finally {

            Files.deleteIfExists(file);
        }

        String status;

        if (failed.get() == 0) {

            status = "COMPLETED";

        } else if (success.get() == 0) {

            status = "FAILED";

        } else {

            status = "PARTIAL";
        }

        stateService.update(
                batchId,
                status,
                processed.get(),
                success.get(),
                failed.get(),
                100);

        auditService.log(
                organization,
                batch.getImportedBy(),
                "LEGACY_LOANS_IMPORTED",
                "IMPORT_BATCH",
                String.valueOf(batchId),
                "Imported "
                        + success.get()
                        + "/"
                        + processed.get()
                        + " rows. Status: "
                        + status);
    }

    private void appendError(
            Path errors,
            long rowNumber,
            String error) throws IOException {

        String safeError = error == null
                ? "Unknown import error"
                : error;

        safeError = safeError
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("\"", "\"\"");

        Files.writeString(
                errors,
                rowNumber
                        + ",\""
                        + safeError
                        + "\"\n",
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
                StandardOpenOption.WRITE);
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void cleanupStagingFiles() {

        try {

            Path root = Path.of(stagingDir)
                    .toAbsolutePath()
                    .normalize();

            if (!Files.isDirectory(root)) {
                return;
            }

            long cutoff = System.currentTimeMillis()
                    - TimeUnit.DAYS.toMillis(
                            STALE_FILE_AGE_DAYS);

            try (var files = Files.list(root)) {

                files
                        .filter(Files::isRegularFile)
                        .filter(path -> {

                            try {

                                return Files
                                        .getLastModifiedTime(path)
                                        .toMillis() < cutoff;

                            } catch (Exception ex) {

                                return false;
                            }
                        })
                        .forEach(path -> {

                            try {

                                Files.deleteIfExists(
                                        path);

                            } catch (Exception ex) {

                                log.warn(
                                        "Could not delete stale import staging file {}",
                                        path,
                                        ex);
                            }
                        });
            }

        } catch (Exception ex) {

            log.warn(
                    "Import staging cleanup failed",
                    ex);
        }
    }
}