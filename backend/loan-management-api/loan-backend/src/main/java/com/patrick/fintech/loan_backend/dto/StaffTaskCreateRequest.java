package com.patrick.fintech.loan_backend.dto;

import java.time.LocalDateTime;

public record StaffTaskCreateRequest(
        Long assigneeUserId,
        String taskType,
        String entityType,
        Long entityId,
        String reference,
        String title,
        String description,
        String priority,
        LocalDateTime dueAt) {
}
