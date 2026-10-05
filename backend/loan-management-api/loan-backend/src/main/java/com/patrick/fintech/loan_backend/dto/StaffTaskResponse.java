package com.patrick.fintech.loan_backend.dto;

import java.time.LocalDateTime;

public record StaffTaskResponse(
        Long id,
        String taskType,
        String entityType,
        Long entityId,
        String reference,
        String title,
        String description,
        String priority,
        String status,
        String assignedRole,
        Long assignedUserId,
        String assignedUserName,
        LocalDateTime dueAt,
        LocalDateTime createdAt,
        LocalDateTime claimedAt,
        boolean overdue,
        String actionLink) {
}
