package com.patrick.fintech.loan_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_reviews", uniqueConstraints = @UniqueConstraint(
        name = "uk_payment_review_request", columnNames = {"organization_id", "request_key"}))
@Getter
@Setter
public class PaymentReview {
    public enum Status { PENDING, APPROVED, REJECTED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Loan loan;

    @Column(nullable = false)
    private Long organizationId;

    @Column(nullable = false, length = 128)
    private String requestKey;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 32)
    private String method;

    @Column(nullable = false, length = 100)
    private String reference;

    @Column(length = 50)
    private String channel;

    @Column(length = 2000)
    private String notes;

    @Column(nullable = false)
    private Long submittedBy;

    @Column(nullable = false)
    private String submittedByName;

    @Column(nullable = false)
    private Instant submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    private Long decidedBy;
    private Instant decidedAt;

    @Column(length = 1000)
    private String decisionReason;
}
