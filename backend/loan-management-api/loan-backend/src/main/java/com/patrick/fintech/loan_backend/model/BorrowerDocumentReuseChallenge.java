package com.patrick.fintech.loan_backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** Short-lived, one-use email proof for reusing stored KYC on a new public loan. */
@Entity
@Table(name = "borrower_document_reuse_challenges")
public class BorrowerDocumentReuseChallenge {
    @Id
    private UUID id;
    @Column(nullable = false)
    private Long borrowerId;
    @Column(nullable = false)
    private Long organizationId;
    @Column(nullable = false, length = 100)
    private String otpHash;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime expiresAt;
    @Column(nullable = false)
    private int attempts;
    private LocalDateTime verifiedAt;
    @Column(length = 64)
    private String proofHash;
    private LocalDateTime proofExpiresAt;
    private LocalDateTime consumedAt;

    public UUID getId() {return id;}
    public void setId(UUID value) {id=value;}
    public Long getBorrowerId() {return borrowerId;}
    public void setBorrowerId(Long value) {borrowerId=value;}
    public Long getOrganizationId() {return organizationId;}
    public void setOrganizationId(Long value) {organizationId=value;}
    public String getOtpHash() {return otpHash;}
    public void setOtpHash(String value) {otpHash=value;}
    public LocalDateTime getCreatedAt() {return createdAt;}
    public void setCreatedAt(LocalDateTime value) {createdAt=value;}
    public LocalDateTime getExpiresAt() {return expiresAt;}
    public void setExpiresAt(LocalDateTime value) {expiresAt=value;}
    public int getAttempts() {return attempts;}
    public void setAttempts(int value) {attempts=value;}
    public LocalDateTime getVerifiedAt() {return verifiedAt;}
    public void setVerifiedAt(LocalDateTime value) {verifiedAt=value;}
    public String getProofHash() {return proofHash;}
    public void setProofHash(String value) {proofHash=value;}
    public LocalDateTime getProofExpiresAt() {return proofExpiresAt;}
    public void setProofExpiresAt(LocalDateTime value) {proofExpiresAt=value;}
    public LocalDateTime getConsumedAt() {return consumedAt;}
    public void setConsumedAt(LocalDateTime value) {consumedAt=value;}
}
