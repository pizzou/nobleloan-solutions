package com.patrick.fintech.loan_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;

/** Historical proposal; approval does not itself alter financial records. */
@Entity
@Table(name = "loan_principal_amendment_requests")
@Getter @Setter @NoArgsConstructor
public class LoanPrincipalAmendmentRequest {
    public enum CorrectionType { RECORDING_ERROR, ADDITIONAL_DISBURSEMENT, CONTRACT_TERMS }
    public enum Status { PENDING_REVIEW, REJECTED, APPROVED_FOR_RECONCILIATION, APPLIED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "organization_id", nullable = false) private Long organizationId;
    @Column(name = "loan_id", nullable = false) private Long loanId;
    @Column(name = "requester_id", nullable = false) private Long requesterId;
    @Column(name = "reviewer_id") private Long reviewerId;
    @Enumerated(EnumType.STRING) @Column(name = "correction_type", nullable = false)
    private CorrectionType correctionType;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false)
    private Status status = Status.PENDING_REVIEW;
    @Column(name = "old_principal", nullable = false) private BigDecimal oldPrincipal;
    @Column(name = "corrected_principal", nullable = false) private BigDecimal correctedPrincipal;
    @Column(name = "original_disbursed_amount", nullable = false) private BigDecimal originalDisbursedAmount;
    @Column(name = "original_total_paid", nullable = false) private BigDecimal originalTotalPaid;
    @Column(name = "old_disbursement_date") private LocalDate oldDisbursementDate;
    @Column(name = "corrected_disbursement_date") private LocalDate correctedDisbursementDate;
    @Column(name = "old_interest_rate") private BigDecimal oldInterestRate;
    @Column(name = "corrected_interest_rate") private BigDecimal correctedInterestRate;
    @Column(name = "old_management_fee_rate") private BigDecimal oldManagementFeeRate;
    @Column(name = "corrected_management_fee_rate") private BigDecimal correctedManagementFeeRate;
    @Column(name = "loan_reference", nullable = false) private String loanReference;
    @Column(name = "reason", nullable = false, length = 2000) private String reason;
    @Column(name = "evidence_reference", nullable = false, length = 250) private String evidenceReference;
    @Column(name = "reviewer_reason", length = 2000) private String reviewerReason;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "reviewed_at") private LocalDateTime reviewedAt;
    @Version private Long version;
}
