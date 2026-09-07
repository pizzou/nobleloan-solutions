package com.patrick.fintech.loan_backend.model;
import jakarta.persistence.*; import lombok.*; import java.math.*; import java.time.*;
@Entity @Table(name="payment_settlements",uniqueConstraints=@UniqueConstraint(name="uk_settlement_provider_ref",columnNames={"organization_id","provider","provider_reference"}),indexes=@Index(name="idx_settlement_status",columnList="organization_id,status"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentSettlement{
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="organization_id",nullable=false) private Organization organization;
 @Column(name="provider",nullable=false,length=50) private String provider;
 @Column(name="provider_reference",nullable=false,length=150) private String providerReference;
 @Column(name="internal_payment_reference",length=150) private String internalPaymentReference;
 @Column(name="amount",nullable=false,precision=19,scale=2) private BigDecimal amount;
 @Column(name="currency",nullable=false,length=3) private String currency;
 @Column(name="settlement_date",nullable=false) private LocalDate settlementDate;
 @Column(name="bank_account_id") private Long bankAccountId;
 @Column(name="status",nullable=false,length=20) @Builder.Default private String status="UNRECONCILED";
 @Column(name="bank_statement_line_id") private Long bankStatementLineId;
 @Column(name="difference",precision=19,scale=2) private BigDecimal difference;
 @Column(name="matched_at") private LocalDateTime matchedAt;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 @PrePersist void pre(){if(createdAt==null)createdAt=LocalDateTime.now();}
}
