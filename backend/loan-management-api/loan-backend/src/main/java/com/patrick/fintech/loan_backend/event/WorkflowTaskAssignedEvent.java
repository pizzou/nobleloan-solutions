package com.patrick.fintech.loan_backend.event;

import java.time.LocalDateTime;

public record WorkflowTaskAssignedEvent(
                Long taskId,
                Long organizationId,
                Long assigneeId,
                String assigneeName,
                String assigneeEmail,
                String title,
                String description,
                String taskType,
                String referenceNumber,
                LocalDateTime dueAt,
                String link) {
}
