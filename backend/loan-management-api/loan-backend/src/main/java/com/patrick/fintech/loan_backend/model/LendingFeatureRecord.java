package com.patrick.fintech.loan_backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "lending_feature_records", indexes = {
        @Index(name = "idx_lfr_org_type", columnList = "organization_id,feature_type"),
        @Index(name = "idx_lfr_org_status", columnList = "organization_id,status"),
        @Index(name = "idx_lfr_org_loan", columnList = "organization_id,loan_id"),
        @Index(name = "idx_lfr_org_due", columnList = "organization_id,due_date")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LendingFeatureRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id")
    private Loan loan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "borrower_id")
    private Borrower borrower;

    @Column(name = "feature_type", nullable = false, length = 60)
    private String featureType;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "priority", length = 20)
    private String priority;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "assigned_user_id")
    private Long assignedUserId;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = createdAt;
        if (status == null || status.isBlank()) status = "OPEN";
    }

    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(); }
}
