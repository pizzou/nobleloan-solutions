package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * A message submitted through the public website's contact form. Previously
 * these only ever
 * existed as a one-shot in-app Notification to admins/managers — nothing was
 * ever saved, so
 * there was no way to browse past messages, mark them handled, or find one
 * again after the
 * notification list moved on. This is the permanent record; the Notification is
 * just the ping.
 */
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

    // Automatically recorded request metadata. Not a reliable personal identity.
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

    // Approximate coordinates are saved only with affirmative consent.
    @Column(name = "approximate_location", length = 120)
    private String approximateLocation;

    @Builder.Default
    @Column(name = "location_consent", nullable = false)
    private boolean locationConsent = false;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Builder.Default
    private boolean read = false;

    private LocalDateTime createdAt;
    private LocalDateTime readAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        read = false;
    }
}
