package com.patrick.fintech.loan_backend.model;
import jakarta.persistence.*; import lombok.*; import java.math.*; import java.time.*;
@Entity @Table(name="bank_statement_lines",indexes={@Index(name="idx_bsl_org_account_date",columnList="organization_id,bank_account_id,transaction_date"),@Index(name="idx_bsl_match",columnList="bank_account_id,reconciliation_status")})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BankStatementLine{
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="organization_id",nullable=false) private Organization organization;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="bank_account_id",nullable=false) private BankAccount bankAccount;
 @Column(name="transaction_date",nullable=false) private LocalDate transactionDate;
 @Column(name="value_date") private LocalDate valueDate;
 @Column(name="reference",length=255) private String reference;
 @Column(name="description",columnDefinition="TEXT") private String description;
 @Column(name="amount",nullable=false,precision=19,scale=2) private BigDecimal amount;
 @Column(name="currency",nullable=false,length=3) @Builder.Default private String currency="RWF";
 @Column(name="external_id",length=150) private String externalId;
 @Column(name="reconciliation_status",nullable=false,length=20) @Builder.Default private String reconciliationStatus="UNMATCHED";
 @Column(name="matched_journal_entry_id") private Long matchedJournalEntryId;
 @Column(name="matched_at") private LocalDateTime matchedAt;
 @Column(name="matched_by",length=255) private String matchedBy;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 @PrePersist void pre(){if(createdAt==null)createdAt=LocalDateTime.now();}
}
