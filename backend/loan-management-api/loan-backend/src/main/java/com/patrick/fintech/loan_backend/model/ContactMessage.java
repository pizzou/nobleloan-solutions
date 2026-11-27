package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.patrick.fintech.loan_backend.util.UtcLocalDateTimeSerializer;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Persisted public contact message with captured request metadata. */
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Entity
@Table(name = "contact_messages", indexes = @Index(name = "idx_contact_messages_org", columnList = "organization_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContactMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Organization organization;

    private String name;
    private String email;
    private String phone;
    private String subject;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "device_type", length = 40)
    private String deviceType;

    @Column(name = "operating_system", length = 60)
    private String operatingSystem;

    @Column(name = "browser_name", length = 60)
    private String browserName;

    @Column(name = "time_zone", length = 100)
    private String timeZone;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /** Browser geolocation is approximate and permission-gated. */
    @Column(name = "approximate_location", length = 120)
    private String approximateLocation;

    @Builder.Default
    @Column(name = "location_consent", nullable = false)
    private boolean locationConsent = false;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Builder.Default
    private boolean read = false;

    @JsonSerialize(using = UtcLocalDateTimeSerializer.class)
    private LocalDateTime createdAt;

    @JsonSerialize(using = UtcLocalDateTimeSerializer.class)
    private LocalDateTime readAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null)
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        read = false;
    }
}
