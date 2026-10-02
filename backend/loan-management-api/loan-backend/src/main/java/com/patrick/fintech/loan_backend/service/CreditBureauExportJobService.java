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
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Durable-in-process CRB workbook export job.
 *
 * Large regulatory exports must not occupy the HTTP request thread. The
 * generated workbook is written to a temporary file and exposed only after
 * an atomic move has completed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditBureauExportJobService {

    private static final Duration JOB_TTL = Duration.ofHours(2);

    private final CreditBureauRegulatoryExportService exportService;

    @Value("${app.import.staging-dir:${java.io.tmpdir}/loansaas-imports}")
    private String stagingDir;

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    public Job create(Long organizationId, Long branchId, LocalDate from, LocalDate to) {
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
                from,
                to);
        jobs.put(job.id, job);
        return job;
    }

    public Job get(String jobId) {
        return jobId == null ? null : jobs.get(jobId);
    }

    @Async("loansaasReportExecutor")
    public void process(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            log.warn("Ignoring unknown CRB export jobId={}", jobId);
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
        try {
            root = Path.of(stagingDir).toAbsolutePath().normalize();
            Files.createDirectories(root);

            Path output = root.resolve("credit-bureau-export-" + job.id + ".xlsx").normalize();
            Path temporary = root.resolve(".credit-bureau-export-" + job.id + ".tmp").normalize();

            if (!output.startsWith(root) || !temporary.startsWith(root)) {
                throw new IllegalStateException("Invalid Credit Bureau export path.");
            }

            byte[] bytes;
            try (ReportingScopeService.ScopeContext ignored =
                         ReportingScopeService.useScope(
                                 ReportingScopeService.includeBusinessOwnerOnly()
                                         ? ReportingScopeService.Scope.BUSINESS_OWNER
                                         : ReportingScopeService.Scope.NORMAL)) {
                bytes = exportService.export(
                        job.organizationId,
                        job.branchId,
                        null,
                        job.from,
                        job.to);
            }

            if (bytes == null || bytes.length == 0) {
                throw new IllegalStateException("Credit Bureau export produced an empty workbook.");
            }

            Files.write(
                    temporary,
                    bytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);

            try {
                Files.move(
                        temporary,
                        output,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }

            job.path = output.toString();
            job.size = bytes.length;
            job.status = Status.COMPLETED;
            job.completedAt = Instant.now();

            log.info(
                    "CRB export job completed. jobId={}, organizationId={}, branchId={}, bytes={}",
                    job.id, job.organizationId, job.branchId, job.size);

        } catch (Exception e) {
            if (root != null) {
                try {
                    Files.deleteIfExists(
                            root.resolve(".credit-bureau-export-" + job.id + ".tmp").normalize());
                } catch (Exception cleanupError) {
                    log.debug("Unable to clean temporary CRB export file. jobId={}", job.id, cleanupError);
                }
            }

            job.status = Status.FAILED;
            job.error = safeMessage(e);
            job.completedAt = Instant.now();

            log.error(
                    "CRB export job failed. jobId={}, organizationId={}, branchId={}",
                    job.id, job.organizationId, job.branchId, e);
        }
    }

    public Path completedFile(Job job) {
        if (job == null || job.status != Status.COMPLETED || job.path == null) {
            return null;
        }

        Path root = Path.of(stagingDir).toAbsolutePath().normalize();
        Path file = Path.of(job.path).toAbsolutePath().normalize();

        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            return null;
        }

        return file;
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
                    log.warn("Unable to delete expired CRB export file. jobId={}", job.id, e);
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

    public enum Status {
        QUEUED, RUNNING, COMPLETED, FAILED
    }

    public static final class Job {
        private final String id;
        private final Long organizationId;
        private final Long branchId;
        private final LocalDate from;
        private final LocalDate to;
        private final Instant createdAt = Instant.now();

        private volatile Status status = Status.QUEUED;
        private volatile Instant startedAt;
        private volatile Instant completedAt;
        private volatile String path;
        private volatile long size;
        private volatile String error;

        private Job(String id, Long organizationId, Long branchId, LocalDate from, LocalDate to) {
            this.id = id;
            this.organizationId = organizationId;
            this.branchId = branchId;
            this.from = from;
            this.to = to;
        }

        public String getId() { return id; }
        public Long getOrganizationId() { return organizationId; }
        public Long getBranchId() { return branchId; }
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
