package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Durable state for the long-running imported-loan accounting reconciliation.
 * This is deliberately persisted so a browser timeout never becomes an
 * accounting-status ambiguity.
 */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "financial_reconciliation_jobs", indexes = {
        @Index(name = "idx_fin_recon_job_org_created", columnList = "organization_id,created_at"),
        @Index(name = "idx_fin_recon_job_status", columnList = "status"),
        @Index(name = "idx_fin_recon_job_heartbeat", columnList = "heartbeat_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialReconciliationJob {

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_VERIFYING = "VERIFYING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    public static final String PHASE_QUEUED = "QUEUED";
    public static final String PHASE_BEFORE_RECONCILIATION = "BEFORE_RECONCILIATION";
    public static final String PHASE_OPENING_JOURNALS = "OPENING_JOURNALS";
    public static final String PHASE_FINAL_RECONCILIATION = "FINAL_RECONCILIATION";
    public static final String PHASE_COMPLETED = "COMPLETED";
    public static final String PHASE_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Organization organization;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User requestedBy;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false, length = 64)
    private String phase;

    @Column(nullable = false)
    private Integer processedLoans;

    @Column(nullable = false)
    private Integer journalAdjustmentsCreated;

    private Boolean beforeBalanced;
    private Boolean afterBalanced;

    @Column(precision = 19, scale = 2)
    private BigDecimal beforeMaximumDifference;

    @Column(precision = 19, scale = 2)
    private BigDecimal afterMaximumDifference;

    @Column(columnDefinition = "TEXT")
    private String resultJson;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime heartbeatAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (status == null) status = STATUS_QUEUED;
        if (phase == null) phase = PHASE_QUEUED;
        if (processedLoans == null) processedLoans = 0;
        if (journalAdjustmentsCreated == null) journalAdjustmentsCreated = 0;
        if (heartbeatAt == null) heartbeatAt = now;
    }
}
