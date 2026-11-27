package com.patrick.fintech.loan_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Persisted Word contract draft. The draft may be refreshed while it has not
 * been sent,
 * so it reflects the latest loan terms. Once dispatched, the stored copy is not
 * overwritten by the draft-generation workflow.
 */
@Entity
@Table(name = "loan_contracts", indexes = {
        @Index(name = "idx_loan_contract_org_loan", columnList = "organization_id,loan_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanContract {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false, unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Loan loan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ContractStatus status = ContractStatus.DRAFT;

    @Column(name = "file_name", nullable = false, length = 180)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "document_data", nullable = false)
    private byte[] documentData;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_by_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User generatedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sent_by_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User sentBy;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "borrower_notified_at")
    private LocalDateTime borrowerNotifiedAt;

    @PrePersist
    protected void onCreate() {
        if (generatedAt == null)
            generatedAt = LocalDateTime.now();
        if (status == null)
            status = ContractStatus.DRAFT;
    }

    public enum ContractStatus {
        DRAFT, SENT_TO_BORROWER
    }
}
