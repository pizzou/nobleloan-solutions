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
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs the heavy BNR XLSX generation outside the HTTP request. This prevents
 * Vercel/Render gateway timeouts from turning a successful report generation
 * into a 502 response. Job metadata is deliberately small; the generated XLSX
 * itself is stored in the configured staging directory.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BnrExportJobService {

    private static final Duration JOB_TTL = Duration.ofHours(2);

    private final BnrTemplateExportService exportService;

    @Value("${app.import.staging-dir:${java.io.tmpdir}/loansaas-imports}")
    private String stagingDir;

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    public Job create(
            Long organizationId,
            Long branchId,
            RegulatoryReportingService.ReportPeriod period,
            LocalDate from,
            LocalDate to) {

        String jobId = UUID.randomUUID().toString();
        Job job = new Job(jobId, organizationId, branchId, period, from, to);
        jobs.put(jobId, job);
        return job;
    }

    public Job get(String jobId) {
        return jobId == null ? null : jobs.get(jobId);
    }

    @Async("loansaasAsyncExecutor")
    public void process(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) return;

        job.status = Status.RUNNING;
        job.startedAt = Instant.now();

        try {
            Path root = Path.of(stagingDir).toAbsolutePath().normalize();
            Files.createDirectories(root);

            Path output = root.resolve("bnr-export-" + job.id + ".xlsx").normalize();
            if (!output.startsWith(root)) {
                throw new IllegalStateException("Invalid BNR export path.");
            }

            byte[] bytes = exportService.export(
                    job.organizationId,
                    job.branchId,
                    job.period,
                    job.from,
                    job.to);

            if (bytes == null || bytes.length == 0) {
                throw new IllegalStateException("BNR export produced an empty workbook.");
            }

            Files.write(
                    output,
                    bytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);

            job.path = output.toString();
            job.size = bytes.length;
            job.status = Status.COMPLETED;
            job.completedAt = Instant.now();

            log.info(
                    "BNR export job completed. jobId={}, organizationId={}, branchId={}, bytes={}",
                    job.id, job.organizationId, job.branchId, job.size);
        } catch (Exception e) {
            job.status = Status.FAILED;
            job.error = safeMessage(e);
            job.completedAt = Instant.now();
            log.error(
                    "BNR export job failed. jobId={}, organizationId={}, branchId={}",
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

    @Scheduled(fixedRateString = "${app.bnr.export.cleanup-ms:600000}")
    public void cleanup() {
        Instant cutoff = Instant.now().minus(JOB_TTL);
        jobs.entrySet().removeIf(entry -> {
            Job job = entry.getValue();
            Instant reference = job.completedAt != null ? job.completedAt : job.createdAt;
            if (reference.isAfter(cutoff)) return false;

            if (job.path != null) {
                try {
                    Files.deleteIfExists(Path.of(job.path));
                } catch (IOException e) {
                    log.warn("Unable to delete expired BNR export file. jobId={}", job.id, e);
                }
            }
            return true;
        });
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank()
                ? "BNR export failed. Please retry the report."
                : message.length() > 500 ? message.substring(0, 500) : message;
    }

    public enum Status { QUEUED, RUNNING, COMPLETED, FAILED }

    public static final class Job {
        private final String id;
        private final Long organizationId;
        private final Long branchId;
        private final RegulatoryReportingService.ReportPeriod period;
        private final LocalDate from;
        private final LocalDate to;
        private final Instant createdAt = Instant.now();
        private volatile Status status = Status.QUEUED;
        private volatile Instant startedAt;
        private volatile Instant completedAt;
        private volatile String path;
        private volatile long size;
        private volatile String error;

        private Job(String id, Long organizationId, Long branchId,
                    RegulatoryReportingService.ReportPeriod period,
                    LocalDate from, LocalDate to) {
            this.id = id;
            this.organizationId = organizationId;
            this.branchId = branchId;
            this.period = period;
            this.from = from;
            this.to = to;
        }

        public String getId() { return id; }
        public Long getOrganizationId() { return organizationId; }
        public Long getBranchId() { return branchId; }
        public RegulatoryReportingService.ReportPeriod getPeriod() { return period; }
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
