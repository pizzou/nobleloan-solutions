package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Durable operator work item. A task is deliberately separate from financial
 * state: completing a task acknowledges work, but never performs the financial
 * operation itself.
 */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "workflow_tasks", indexes = {
        @Index(name = "idx_workflow_tasks_assignee_status", columnList = "organization_id,assigned_to,status,due_at"),
        @Index(name = "idx_workflow_tasks_entity", columnList = "organization_id,entity_type,entity_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_to", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User assignedTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User assignedBy;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "task_type", nullable = false, length = 60)
    private String taskType;

    @Column(name = "entity_type", length = 40)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "reference_number", length = 255)
    private String referenceNumber;

    @Column(length = 500)
    private String link;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "OPEN";

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String priority = "NORMAL";

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User completedBy;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null || status.isBlank()) {
            status = "OPEN";
        }
        if (priority == null || priority.isBlank()) {
            priority = "NORMAL";
        }
    }
}
