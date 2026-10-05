package com.patrick.fintech.loan_backend.dto;

import java.util.List;

public record StaffTaskDashboardResponse(
        long open,
        long dueToday,
        long overdue,
        long awaitingDisbursement,
        List<StaffTaskResponse> tasks) {
}
