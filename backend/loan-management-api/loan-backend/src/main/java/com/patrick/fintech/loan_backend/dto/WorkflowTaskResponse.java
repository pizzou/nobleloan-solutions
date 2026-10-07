package com.patrick.fintech.loan_backend.dto;

import com.patrick.fintech.loan_backend.model.WorkflowTask;

import java.time.LocalDateTime;

public record WorkflowTaskResponse(
        Long id,
        Long assigneeId,
        String assigneeName,
        String title,
        String description,
        String taskType,
        String entityType,
        Long entityId,
        String referenceNumber,
        String status,
        String priority,
        LocalDateTime dueAt,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        boolean overdue,
        String link) {

    public static WorkflowTaskResponse from(WorkflowTask task, LocalDateTime now) {
        LocalDateTime due = task.getDueAt();
        boolean overdue = due != null
                && due.isBefore(now)
                && !"COMPLETED".equalsIgnoreCase(task.getStatus())
                && !"CANCELLED".equalsIgnoreCase(task.getStatus());
        return new WorkflowTaskResponse(
                task.getId(),
                task.getAssignedTo() == null ? null : task.getAssignedTo().getId(),
                task.getAssignedTo() == null ? null : task.getAssignedTo().getName(),
                task.getTitle(),
                task.getDescription(),
                task.getTaskType(),
                task.getEntityType(),
                task.getEntityId(),
                task.getReferenceNumber(),
                task.getStatus(),
                task.getPriority(),
                task.getDueAt(),
                task.getCreatedAt(),
                task.getStartedAt(),
                task.getCompletedAt(),
                overdue,
                task.getLink());
    }
}
