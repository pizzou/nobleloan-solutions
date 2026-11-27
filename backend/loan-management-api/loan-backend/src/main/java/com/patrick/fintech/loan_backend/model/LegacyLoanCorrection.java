package com.patrick.fintech.loan_backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** Append-only proposal: only the review outcome may be updated. */
@Entity
@Table(name = "legacy_loan_corrections")
@Getter @Setter @NoArgsConstructor
public class LegacyLoanCorrection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name="organization_id", nullable=false) private Long organizationId;
    @Column(name="loan_id", nullable=false) private Long loanId;
    @Column(name="maker_id", nullable=false) private Long makerId;
    @Column(name="checker_id") private Long checkerId;
    @Column(name="status", nullable=false, length=20) private String status = "PENDING";
    @Column(name="reason", nullable=false, length=2000) private String reason;
    @Column(name="evidence_reference", nullable=false, length=250) private String evidenceReference;
    @Column(name="before_snapshot", nullable=false, columnDefinition="TEXT") private String beforeSnapshot;
    @Column(name="after_snapshot", nullable=false, columnDefinition="TEXT") private String afterSnapshot;
    @Column(name="snapshot_hash", nullable=false, length=64) private String snapshotHash;
    @Column(name="review_reason", length=2000) private String reviewReason;
    @Column(name="journal_entry_id") private Long journalEntryId;
    /** Reviewed and applied directly by a Business Owner, with an explicit audit reason. */
    @Column(name="owner_direct", nullable=false) private Boolean ownerDirect = false;
    @Column(name="created_at", nullable=false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name="reviewed_at") private LocalDateTime reviewedAt;
    @Version private Long version;
}
