package com.patrick.fintech.loan_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "workflow_tasks",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_workflow_task_assignee_key",
                columnNames = {"organization_id", "assigned_to_id", "task_key"}
        ),
        indexes = {
                @Index(name = "idx_workflow_tasks_assignee_status", columnList = "assigned_to_id,status,due_at"),
                @Index(name = "idx_workflow_tasks_email_pending", columnList = "status,email_sent_at,created_at"),
                @Index(name = "idx_workflow_tasks_reference", columnList = "organization_id,reference_type,reference_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowTask {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final String TYPE_LOAN_DISBURSEMENT = "LOAN_DISBURSEMENT";

    public static final String PRIORITY_HIGH = "HIGH";
    public static final String PRIORITY_NORMAL = "NORMAL";
    public static final String PRIORITY_LOW = "LOW";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_to_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User assignedTo;

    @Column(name = "task_key", nullable = false, length = 180)
    private String taskKey;

    @Column(name = "task_type", nullable = false, length = 80)
    private String taskType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(length = 255)
    private String link;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String priority = PRIORITY_NORMAL;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = STATUS_OPEN;

    @Column(name = "reference_type", length = 80)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "completed_by")
    private Long completedBy;

    @Column(name = "email_sent_at")
    private LocalDateTime emailSentAt;

    @Column(name = "email_attempts", nullable = false)
    @Builder.Default
    private Integer emailAttempts = 0;

    @Column(name = "last_email_attempt_at")
    private LocalDateTime lastEmailAttemptAt;

    @Column(name = "last_email_error", columnDefinition = "TEXT")
    private String lastEmailError;

    @Column(name = "reminder_count", nullable = false)
    @Builder.Default
    private Integer reminderCount = 0;

    @Column(name = "last_reminder_at")
    private LocalDateTime lastReminderAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = STATUS_OPEN;
        if (priority == null) priority = PRIORITY_NORMAL;
        if (emailAttempts == null) emailAttempts = 0;
        if (reminderCount == null) reminderCount = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isOpen() {
        return STATUS_OPEN.equalsIgnoreCase(status);
    }

    public boolean isOverdue(LocalDateTime now) {
        return isOpen() && dueAt != null && dueAt.isBefore(now);
    }
}
