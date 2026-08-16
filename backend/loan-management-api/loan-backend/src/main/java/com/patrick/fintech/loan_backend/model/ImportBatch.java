package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * One run of importing a client's pre-existing manual (Excel/CSV) ledger into the platform.
 * Kept for audit — who imported what, when, and the per-row outcome — since a bulk data load
 * bypassing normal loan origination is exactly the kind of thing an auditor or regulator will
 * ask about later.
 */
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "import_batches")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ImportBatch {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imported_by")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User importedBy;

    private String fileName;
    private Integer totalRows;
    private Integer successCount;
    private Integer failureCount;
    private String status; // COMPLETED, FAILED, PARTIAL

    /** JSON array of per-row outcomes: row number, action taken, and any error. */
    @Column(columnDefinition = "TEXT")
    private String rowResults;

    private LocalDateTime createdAt;
    @Column(name="processed_rows") private Integer processedRows;
    @Column(name="progress_percent") private Integer progressPercent;
    @Column(name="file_size") private Long fileSize;
    @JsonIgnore @Column(name="staged_file_path", columnDefinition="TEXT") private String stagedFilePath;
    @JsonIgnore @Column(name="error_message", columnDefinition="TEXT") private String errorMessage;
    @JsonIgnore @Column(name="error_report_path", columnDefinition="TEXT") private String errorReportPath;


    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (successCount == null) successCount = 0;
        if (failureCount == null) failureCount = 0;
        if (totalRows == null) totalRows = 0;
    }
}
