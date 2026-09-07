package com.patrick.fintech.loan_backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounting_periods",
        uniqueConstraints = @UniqueConstraint(name = "uk_accounting_period_org_month", columnNames = {"organization_id", "year", "month"}),
        indexes = @Index(name = "idx_accounting_period_org_status", columnList = "organization_id,status"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AccountingPeriod {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false) private Integer year;
    @Column(nullable = false) private Integer month;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private PeriodStatus status = PeriodStatus.OPEN;

    private LocalDateTime openedAt;
    private String openedBy;
    private LocalDateTime closedAt;
    private String closedBy;

    @PrePersist
    void onCreate() { if (openedAt == null) openedAt = LocalDateTime.now(); }

    public enum PeriodStatus { OPEN, CLOSED }
}
