package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.patrick.fintech.loan_backend.security.CryptoConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Immutable original web-application answers, separately encrypted at rest.
 * No loan balances, fees, schedule, or approval state are stored here.
 * The snapshot is visible only by first accessing the tenant-scoped Loan.
 */
@Entity
@Table(name = "loan_application_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class LoanApplicationSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_loan_application_snapshot_loan"))
    private Loan loan;

    /** Only whitelisted applicant-provided fields; encrypted with the existing PII converter. */
    @JsonIgnore
    @Convert(converter = CryptoConverter.class)
    @Column(name = "encrypted_answers", nullable = false, columnDefinition = "TEXT")
    private String originalAnswersJson;
}
