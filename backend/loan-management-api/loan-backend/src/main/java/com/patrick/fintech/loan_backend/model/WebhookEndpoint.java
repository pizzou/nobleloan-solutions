package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties({
        "hibernateLazyInitializer",
        "handler"
})
@Entity
@Table(name = "webhook_endpoints")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEndpoint {

    // ============================================================
    // ID
    // ============================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ============================================================
    // ORGANIZATION
    // ============================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "organization_id",
            nullable = false
    )
    private Organization organization;

    // ============================================================
    // ENDPOINT
    // ============================================================

    @Column(
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String url;

    @Column(
            columnDefinition = "TEXT"
    )
    private String description;

    /**
     * HMAC-SHA256 signing secret.
     */
    @Column(
            columnDefinition = "TEXT"
    )
    private String secret;

    /**
     * Whether the endpoint should receive events.
     */
    @Column(
            nullable = false
    )
    private boolean active = true;

    // ============================================================
    // SUBSCRIBED EVENTS
    // ============================================================

    @ElementCollection
    @CollectionTable(
            name = "webhook_events",
            joinColumns = @JoinColumn(
                    name = "webhook_id"
            )
    )
    @Column(
            name = "event_type"
    )
    @Builder.Default
    private List<String> subscribedEvents =
            new ArrayList<>();

    // ============================================================
    // DELIVERY STATUS
    // ============================================================

    @Column(
            nullable = false
    )
    private Integer failureCount = 0;

    private LocalDateTime lastDeliveryAt;

    @Column(
            columnDefinition = "TEXT"
    )
    private String lastDeliveryStatus;

    private LocalDateTime createdAt;

    // ============================================================
    // PRE-PERSIST
    // ============================================================

    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (failureCount == null) {
            failureCount = 0;
        }

        if (subscribedEvents == null) {
            subscribedEvents = new ArrayList<>();
        }

        /*
         * New webhook endpoints are active by default.
         */
        active = true;
    }
}