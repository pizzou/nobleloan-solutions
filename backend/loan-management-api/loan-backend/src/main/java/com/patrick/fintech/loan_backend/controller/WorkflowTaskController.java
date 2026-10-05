package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.WorkflowTaskResponse;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.service.WorkflowTaskService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflow-tasks")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class WorkflowTaskController {

    private final WorkflowTaskService workflowTaskService;
    private final CurrentUserUtil currentUserUtil;

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<WorkflowTaskResponse>>> myTasks() {
        User actor = currentUserUtil.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(workflowTaskService.getMyTasks(actor)));
    }

    @GetMapping("/my/kpi")
    public ResponseEntity<ApiResponse<WorkflowTaskService.TaskKpi>> myKpi() {
        User actor = currentUserUtil.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(workflowTaskService.getMyKpi(actor)));
    }
}
