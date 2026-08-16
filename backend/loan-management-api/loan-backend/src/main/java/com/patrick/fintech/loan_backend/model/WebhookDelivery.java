package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@JsonIgnoreProperties({
        "hibernateLazyInitializer",
        "handler"
})
@Entity
@Table(
        name = "webhook_deliveries",
        indexes = {

                @Index(
                        name = "idx_webhook_delivery_endpoint",
                        columnList = "webhook_endpoint_id"
                ),

                @Index(
                        name = "idx_webhook_delivery_organization",
                        columnList = "organization_id"
                ),

                @Index(
                        name = "idx_webhook_delivery_event",
                        columnList = "event_type"
                ),

                @Index(
                        name = "idx_webhook_delivery_created",
                        columnList = "created_at"
                )
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDelivery {

    // ============================================================
    // ID
    // ============================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ============================================================
    // WEBHOOK ENDPOINT
    // ============================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "webhook_endpoint_id",
            nullable = false
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private WebhookEndpoint webhookEndpoint;

    // ============================================================
    // ORGANIZATION
    // ============================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "organization_id",
            nullable = false
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Organization organization;

    // ============================================================
    // EVENT
    // ============================================================

    @Column(
            name = "event_type",
            nullable = false,
            length = 100
    )
    private String eventType;

    // ============================================================
    // PAYLOAD
    // ============================================================

    @Column(
            name = "payload",
            columnDefinition = "TEXT"
    )
    private String payload;

    // ============================================================
    // ENDPOINT URL
    // ============================================================

    @Column(
            name = "endpoint_url",
            columnDefinition = "TEXT"
    )
    private String endpointUrl;

    // ============================================================
    // RESULT
    // ============================================================

    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    private String status;

    @Column(
            name = "http_status"
    )
    private Integer httpStatus;

    @Column(
            name = "response_body",
            columnDefinition = "TEXT"
    )
    private String responseBody;

    @Column(
            name = "error_message",
            columnDefinition = "TEXT"
    )
    private String errorMessage;

    // ============================================================
    // ATTEMPTS
    // ============================================================

    @Column(
            name = "attempt_count",
            nullable = false
    )
    private Integer attemptCount;

    // ============================================================
    // TIMESTAMPS
    // ============================================================

    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;

    @Column(
            name = "delivered_at"
    )
    private LocalDateTime deliveredAt;

    // ============================================================
    // PRE-PERSIST
    // ============================================================

    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (attemptCount == null) {
            attemptCount = 1;
        }

        if (status == null || status.isBlank()) {
            status = "PENDING";
        }
    }
}