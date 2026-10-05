package com.patrick.fintech.loan_backend.dto;

import com.patrick.fintech.loan_backend.model.WorkflowTask;

import java.time.LocalDateTime;

public record WorkflowTaskResponse(
        Long id,
        String taskKey,
        String taskType,
        String title,
        String message,
        String link,
        String priority,
        String status,
        String referenceType,
        Long referenceId,
        LocalDateTime dueAt,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        String assignedToName,
        String assignedToEmail,
        LocalDateTime emailSentAt,
        Integer emailAttempts,
        String lastEmailError) {

    public static WorkflowTaskResponse from(WorkflowTask task) {
        String name = task.getAssignedTo() == null ? null : task.getAssignedTo().getName();
        String email = task.getAssignedTo() == null ? null : task.getAssignedTo().getEmail();

        return new WorkflowTaskResponse(
                task.getId(),
                task.getTaskKey(),
                task.getTaskType(),
                task.getTitle(),
                task.getMessage(),
                task.getLink(),
                task.getPriority(),
                task.getStatus(),
                task.getReferenceType(),
                task.getReferenceId(),
                task.getDueAt(),
                task.getCreatedAt(),
                task.getCompletedAt(),
                name,
                email,
                task.getEmailSentAt(),
                task.getEmailAttempts(),
                task.getLastEmailError());
    }
}
