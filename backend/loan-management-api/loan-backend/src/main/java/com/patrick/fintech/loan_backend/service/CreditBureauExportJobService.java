package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.regulatory.CreditBureauRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes heavy Credit Bureau exports outside the HTTP request.
 * This prevents long XLSX/PDF generation from occupying Render request
 * threads until the gateway times out.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditBureauExportJobService {

    private static final Duration JOB_TTL = Duration.ofHours(2);
    private static final List<String> COLUMNS = List.of(
            "Borrower ID", "National ID", "Full Name", "Date of Birth", "Gender", "Phone",
            "Loan Number", "Loan Type", "Loan Status", "Repayment Classification", "Loan Amount",
            "Outstanding Balance", "Days Past Due", "Credit Score", "Date Opened", "Last Payment",
            "Maturity Date", "Date Closed", "Branch", "Currency");

    private final RegulatoryReportingService reportingService;
    private final ReportExportService reportExportService;
    private final CreditBureauRegulatoryExportService regulatoryExportService;

    @Value("${app.import.staging-dir:${java.io.tmpdir}/loansaas-imports}")
    private String stagingDir;

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    public Job create(Long organizationId, Long branchId, LocalDate from, LocalDate to, String format) {
        if (organizationId == null || organizationId <= 0) {
            throw new IllegalArgumentException("Organization is required for a Credit Bureau export job.");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Credit Bureau report start date cannot be after the end date.");
        }

        String normalized = format == null ? "xlsx" : format.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("xlsx", "csv", "pdf").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported Credit Bureau export format: " + normalized);
        }

        Job job = new Job(UUID.randomUUID().toString(), organizationId, branchId, from, to, normalized,
                ReportingScopeService.includeBusinessOwnerOnly());
        jobs.put(job.id, job);
        return job;
    }

    public Job get(String jobId) {
        return jobId == null ? null : jobs.get(jobId);
    }

    @Async("loansaasAsyncExecutor")
    public void process(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) return;

        synchronized (job) {
            if (job.status != Status.QUEUED) return;
            job.status = Status.RUNNING;
            job.startedAt = Instant.now();
        }

        try {
            Path root = Path.of(stagingDir).toAbsolutePath().normalize();
            Files.createDirectories(root);

            String extension = job.format.equals("xlsx") ? "xlsx" : job.format;
            Path output = root.resolve("credit-bureau-export-" + job.id + "." + extension).normalize();
            Path temporary = root.resolve(".credit-bureau-export-" + job.id + ".tmp").normalize();
            if (!output.startsWith(root) || !temporary.startsWith(root)) {
                throw new IllegalStateException("Invalid Credit Bureau export path.");
            }

            byte[] bytes;
            ReportingScopeService.Scope scope = job.includeBusinessOwnerOnly
                    ? ReportingScopeService.Scope.BUSINESS_OWNER
                    : ReportingScopeService.Scope.NORMAL;

            try (ReportingScopeService.ScopeContext ignored = ReportingScopeService.useScope(scope)) {
                if ("xlsx".equals(job.format)) {
                    bytes = regulatoryExportService.export(
                            job.organizationId, job.branchId, null, job.from, job.to);
                } else {
                    List<CreditBureauRecord> records = reportingService.buildCreditBureauExport(
                            job.organizationId, job.branchId, job.from, job.to);
                    List<Map<String, Object>> rows = toRows(records);

                    if ("csv".equals(job.format)) {
                        bytes = toCsv(rows);
                    } else {
                        bytes = reportExportService.toPdf(
                                "Credit Bureau Regulatory Report",
                                COLUMNS,
                                rows,
                                "Noble Loan Solutions");
                    }
                }
            }

            if (bytes == null || bytes.length == 0) {
                throw new IllegalStateException("Credit Bureau export produced an empty file.");
            }

            Files.write(temporary, bytes, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }

            job.path = output.toString();
            job.size = bytes.length;
            job.status = Status.COMPLETED;
            job.completedAt = Instant.now();
            log.info("Credit Bureau export completed. jobId={}, organizationId={}, format={}, bytes={}",
                    job.id, job.organizationId, job.format, job.size);
        } catch (Exception e) {
            job.status = Status.FAILED;
            job.error = safeMessage(e);
            job.completedAt = Instant.now();
            try {
                Path root = Path.of(stagingDir).toAbsolutePath().normalize();
                Files.deleteIfExists(root.resolve(".credit-bureau-export-" + job.id + ".tmp").normalize());
            } catch (Exception ignored) {
                // best-effort cleanup
            }
            log.error("Credit Bureau export failed. jobId={}, organizationId={}, format={}",
                    job.id, job.organizationId, job.format, e);
        }
    }

    public Path completedFile(Job job) {
        if (job == null || job.status != Status.COMPLETED || job.path == null) return null;
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
            if (reference.isAfter(cutoff)) return false;
            if (job.path != null) {
                try { Files.deleteIfExists(Path.of(job.path)); } catch (IOException ignored) {}
            }
            return true;
        });
    }

    private List<Map<String, Object>> toRows(List<CreditBureauRecord> records) {
        List<Map<String, Object>> rows = new ArrayList<>(records == null ? 0 : records.size());
        if (records == null) return rows;
        for (CreditBureauRecord r : records) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Borrower ID", r.getBorrowerId());
            row.put("National ID", r.getNationalId());
            row.put("Full Name", r.getFullName());
            row.put("Date of Birth", r.getDateOfBirth());
            row.put("Gender", r.getGender());
            row.put("Phone", r.getPhone());
            row.put("Loan Number", r.getLoanNumber());
            row.put("Loan Type", r.getLoanType());
            row.put("Loan Status", r.getLoanStatus());
            row.put("Repayment Classification", r.getRepaymentClassification());
            row.put("Loan Amount", r.getLoanAmount());
            row.put("Outstanding Balance", r.getOutstandingBalance());
            row.put("Days Past Due", r.getDaysPastDue());
            row.put("Credit Score", r.getCreditScore());
            row.put("Date Opened", r.getDateOpened());
            row.put("Last Payment", r.getLastPaymentDate());
            row.put("Maturity Date", r.getMaturityDate());
            row.put("Date Closed", r.getDateClosed());
            row.put("Branch", r.getBranchName());
            row.put("Currency", r.getCurrency());
            rows.add(row);
        }
        return rows;
    }

    private byte[] toCsv(List<Map<String, Object>> rows) {
        StringBuilder csv = new StringBuilder(Math.max(256, rows.size() * 160));
        csv.append(String.join(",", COLUMNS)).append('\n');
        for (Map<String, Object> row : rows) {
            for (int i = 0; i < COLUMNS.size(); i++) {
                if (i > 0) csv.append(',');
                csv.append(csvValue(row.get(COLUMNS.get(i))));
            }
            csv.append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csvValue(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value).replace("\"", "\"\"");
        return "\"" + text + "\"";
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return "Credit Bureau export failed. Please retry the report.";
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    public enum Status { QUEUED, RUNNING, COMPLETED, FAILED }

    public static final class Job {
        private final String id;
        private final Long organizationId;
        private final Long branchId;
        private final LocalDate from;
        private final LocalDate to;
        private final String format;
        private final boolean includeBusinessOwnerOnly;
        private final Instant createdAt = Instant.now();
        private volatile Status status = Status.QUEUED;
        private volatile Instant startedAt;
        private volatile Instant completedAt;
        private volatile String path;
        private volatile long size;
        private volatile String error;

        private Job(String id, Long organizationId, Long branchId, LocalDate from, LocalDate to,
                    String format, boolean includeBusinessOwnerOnly) {
            this.id = id; this.organizationId = organizationId; this.branchId = branchId;
            this.from = from; this.to = to; this.format = format;
            this.includeBusinessOwnerOnly = includeBusinessOwnerOnly;
        }

        public String getId() { return id; }
        public Long getOrganizationId() { return organizationId; }
        public Long getBranchId() { return branchId; }
        public LocalDate getFrom() { return from; }
        public LocalDate getTo() { return to; }
        public String getFormat() { return format; }
        public boolean isIncludeBusinessOwnerOnly() { return includeBusinessOwnerOnly; }
        public Instant getCreatedAt() { return createdAt; }
        public Status getStatus() { return status; }
        public Instant getStartedAt() { return startedAt; }
        public Instant getCompletedAt() { return completedAt; }
        public long getSize() { return size; }
        public String getError() { return error; }
    }
}
