package com.patrick.fintech.loan_backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkflowTaskCreateRequest {
    private Long assigneeId;
    private String title;
    private String description;
    private String taskType;
    private String entityType;
    private Long entityId;
    private String referenceNumber;
    private String priority;
    private LocalDateTime dueAt;
    private String link;
}
