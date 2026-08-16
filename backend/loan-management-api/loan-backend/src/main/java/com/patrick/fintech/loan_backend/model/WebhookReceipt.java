package com.patrick.fintech.loan_backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name="webhook_receipts", uniqueConstraints=@UniqueConstraint(name="uk_webhook_receipt_provider_key", columnNames={"provider","event_key"}), indexes=@Index(name="idx_webhook_receipt_created", columnList="created_at"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WebhookReceipt {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=40) private String provider;
    @Column(name="event_key",nullable=false,length=128) private String eventKey;
    @Column(name="payload_hash",nullable=false,length=64) private String payloadHash;
    @Column(nullable=false,length=20) @Builder.Default private String status="PROCESSING";
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @Column(name="processed_at") private LocalDateTime processedAt;
    @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
