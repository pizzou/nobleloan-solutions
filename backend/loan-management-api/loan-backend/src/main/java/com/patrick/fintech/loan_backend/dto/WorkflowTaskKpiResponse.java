package com.patrick.fintech.loan_backend.dto;

public record WorkflowTaskKpiResponse(
                long openTasks,
                long overdueTasks,
                long urgentTasks) {
}
