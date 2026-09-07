package com.patrick.fintech.loan_backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name="financial_approvals", indexes={
 @Index(name="idx_fin_approval_org_status", columnList="organization_id,status"),
 @Index(name="idx_fin_approval_operation", columnList="organization_id,operation_type,operation_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FinancialApproval {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="organization_id",nullable=false) private Organization organization;
 @Column(name="operation_type",nullable=false,length=50) private String operationType;
 @Column(name="operation_id",length=100) private String operationId;
 @Column(name="amount",precision=19,scale=2) private BigDecimal amount;
 @Column(name="currency",nullable=false,length=3) @Builder.Default private String currency="RWF";
 @Column(name="maker_user_id",nullable=false) private Long makerUserId;
 @Column(name="maker_name",length=255) private String makerName;
 @Column(name="checker_user_id") private Long checkerUserId;
 @Column(name="checker_name",length=255) private String checkerName;
 @Column(name="required_level",nullable=false) @Builder.Default private Integer requiredLevel=1;
 @Column(name="status",nullable=false,length=20) @Builder.Default private String status="PENDING";
 @Column(name="reason",columnDefinition="TEXT") private String reason;
 @Column(name="payload_hash",nullable=false,length=64) private String payloadHash;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 @Column(name="decided_at") private LocalDateTime decidedAt;
 @PrePersist void prePersist(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
