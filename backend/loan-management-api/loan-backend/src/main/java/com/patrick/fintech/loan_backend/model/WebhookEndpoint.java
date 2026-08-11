package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "organization_id",
            nullable = false
    )
    private Organization organization;

    // ============================================================
    // WEBHOOK URL
    // ============================================================

    @Column(
            nullable = false,
            length = 1000
    )
    private String url;

    // ============================================================
    // DESCRIPTION
    // ============================================================

    @Column(length = 500)
    private String description;

    // ============================================================
    // HMAC SECRET
    // ============================================================

    @Column(length = 500)
    private String secret;

    // ============================================================
    // ACTIVE
    // ============================================================

    @Column(
            nullable = false
    )
    private boolean active;

    // ============================================================
    // SUBSCRIBED EVENTS
    // ============================================================

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "webhook_events",
            joinColumns = @JoinColumn(
                    name = "webhook_id"
            )
    )
    @Column(
            name = "event_type",
            nullable = false,
            length = 100
    )
    @Builder.Default
    private List<String> subscribedEvents =
            new ArrayList<>();

    // ============================================================
    // FAILURE COUNT
    // ============================================================

    @Column(
            nullable = false
    )
    private Integer failureCount;

    // ============================================================
    // LAST DELIVERY
    // ============================================================

    private LocalDateTime lastDeliveryAt;

    @Column(length = 500)
    private String lastDeliveryStatus;

    // ============================================================
    // CREATED
    // ============================================================

    @Column(
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    // ============================================================
    // CREATE DEFAULTS
    // ============================================================

    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        /*
         * Only default active to true when no meaningful value has
         * been supplied by the application.
         *
         * Because primitive boolean cannot represent "not supplied",
         * existing application code should explicitly set active
         * when it needs a specific value.
         */
        active = true;

        if (failureCount == null) {
            failureCount = 0;
        }

        if (subscribedEvents == null) {
            subscribedEvents = new ArrayList<>();
        }
    }

    // ============================================================
    // UPDATE DEFAULTS
    // ============================================================

    @PreUpdate
    protected void onUpdate() {

        if (failureCount == null) {
            failureCount = 0;
        }

        if (subscribedEvents == null) {
            subscribedEvents = new ArrayList<>();
        }
    }
}