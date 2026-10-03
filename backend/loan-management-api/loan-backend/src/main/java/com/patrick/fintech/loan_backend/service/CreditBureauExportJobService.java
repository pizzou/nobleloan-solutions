package com.patrick.fintech.loan_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded background execution for large Credit Bureau workbooks.
 *
 * The job stores only metadata in memory. The generated XLSX is written directly
 * to the staging filesystem so an export cannot hold a second complete byte[] on
 * the Render heap or occupy an HTTP request thread.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditBureauExportJobService {

    private static final Duration JOB_TTL = Duration.ofHours(2);

    private final CreditBureauRegulatoryExportService exportService;

    @Value("${app.report.staging-dir:${java.io.tmpdir}/loansaas-reports}")
    private String stagingDir;

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    public Job create(Long organizationId, Long branchId, Long borrowerId,
                      LocalDate from, LocalDate to) {
        if (organizationId == null || organizationId <= 0) {
            throw new IllegalArgumentException("Organization is required for a Credit Bureau export job.");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Credit Bureau report start date cannot be after the end date.");
        }

        Job job = new Job(
                UUID.randomUUID().toString(),
                organizationId,
                branchId,
                borrowerId,
                from,
                to);
        jobs.put(job.id, job);
        return job;
    }

    public Job get(String jobId) {
        return jobId == null ? null : jobs.get(jobId);
    }

    @Async("reportAsyncExecutor")
    public void process(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            return;
        }

        synchronized (job) {
            if (job.status != Status.QUEUED) {
                return;
            }
            job.status = Status.RUNNING;
            job.startedAt = Instant.now();
        }

        Path root = null;
        Path output = null;
        Path temporary = null;
        try {
            root = Path.of(stagingDir).toAbsolutePath().normalize();
            Files.createDirectories(root);

            output = root.resolve("credit-bureau-export-" + job.id + ".xlsx").normalize();
            temporary = root.resolve(".credit-bureau-export-" + job.id + ".tmp").normalize();
            if (!output.startsWith(root) || !temporary.startsWith(root)) {
                throw new IllegalStateException("Invalid Credit Bureau export path.");
            }

            exportService.exportToFile(
                    temporary,
                    job.organizationId,
                    job.branchId,
                    job.borrowerId,
                    job.from,
                    job.to);

            if (!Files.isRegularFile(temporary) || Files.size(temporary) == 0) {
                throw new IllegalStateException("Credit Bureau export produced an empty workbook.");
            }

            try {
                Files.move(temporary, output,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }

            job.path = output.toString();
            job.size = Files.size(output);
            job.status = Status.COMPLETED;
            job.completedAt = Instant.now();

            log.info("Credit Bureau export completed. jobId={}, organizationId={}, bytes={}",
                    job.id, job.organizationId, job.size);
        } catch (Exception e) {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanupError) {
                    log.debug("Unable to clean failed Credit Bureau export", cleanupError);
                }
            }
            job.status = Status.FAILED;
            job.error = safeMessage(e);
            job.completedAt = Instant.now();
            log.error("Credit Bureau export failed. jobId={}, organizationId={}",
                    job.id, job.organizationId, e);
        }
    }

    public Path completedFile(Job job) {
        if (job == null || job.status != Status.COMPLETED || job.path == null) {
            return null;
        }
        Path root = Path.of(stagingDir).toAbsolutePath().normalize();
        Path file = Path.of(job.path).toAbsolutePath().normalize();
        return file.startsWith(root) && Files.isRegularFile(file) ? file : null;
    }

    @Scheduled(fixedRateString = "${app.credit-bureau.export.cleanup-ms:600000}")
    public void cleanup() {
        Instant cutoff = Instant.now().minus(JOB_TTL);
        jobs.entrySet().removeIf(entry -> {
            Job job = entry.getValue();
            Instant reference = job.completedAt != null ? job.completedAt : job.createdAt;
            if (reference.isAfter(cutoff)) {
                return false;
            }
            if (job.path != null) {
                try {
                    Files.deleteIfExists(Path.of(job.path));
                } catch (IOException e) {
                    log.debug("Unable to delete expired Credit Bureau export", e);
                }
            }
            return true;
        });
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "Credit Bureau export failed. Please retry the report.";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    public enum Status { QUEUED, RUNNING, COMPLETED, FAILED }

    public static final class Job {
        private final String id;
        private final Long organizationId;
        private final Long branchId;
        private final Long borrowerId;
        private final LocalDate from;
        private final LocalDate to;
        private final Instant createdAt = Instant.now();
        private volatile Status status = Status.QUEUED;
        private volatile Instant startedAt;
        private volatile Instant completedAt;
        private volatile String path;
        private volatile long size;
        private volatile String error;

        private Job(String id, Long organizationId, Long branchId, Long borrowerId,
                    LocalDate from, LocalDate to) {
            this.id = id;
            this.organizationId = organizationId;
            this.branchId = branchId;
            this.borrowerId = borrowerId;
            this.from = from;
            this.to = to;
        }

        public String getId() { return id; }
        public Long getOrganizationId() { return organizationId; }
        public Long getBranchId() { return branchId; }
        public Long getBorrowerId() { return borrowerId; }
        public LocalDate getFrom() { return from; }
        public LocalDate getTo() { return to; }
        public Instant getCreatedAt() { return createdAt; }
        public Status getStatus() { return status; }
        public Instant getStartedAt() { return startedAt; }
        public Instant getCompletedAt() { return completedAt; }
        public long getSize() { return size; }
        public String getError() { return error; }
    }
}
