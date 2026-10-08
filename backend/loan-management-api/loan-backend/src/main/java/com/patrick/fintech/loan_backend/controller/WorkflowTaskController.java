package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.WorkflowTaskCreateRequest;
import com.patrick.fintech.loan_backend.dto.WorkflowTaskKpiResponse;
import com.patrick.fintech.loan_backend.dto.WorkflowTaskResponse;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.model.WorkflowTask;
import com.patrick.fintech.loan_backend.service.WorkflowTaskService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Authenticated operator work queue.
 *
 * All task reads and mutations are delegated to WorkflowTaskService, which
 * performs organization and assignee scoping. A task is operational metadata;
 * completing it never substitutes for the financial authorization path.
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class WorkflowTaskController {

    private final WorkflowTaskService taskService;
    private final CurrentUserUtil currentUserUtil;

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<WorkflowTaskResponse>>> mine(
            @RequestParam(value = "limit", defaultValue = "25") int limit) {
        User actor = currentUserUtil.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(taskService.getMine(actor, limit)));
    }

    @GetMapping("/mine/kpi")
    public ResponseEntity<ApiResponse<WorkflowTaskKpiResponse>> mineKpi() {
        User actor = currentUserUtil.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(taskService.getMineKpi(actor)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WorkflowTaskResponse>> create(
            @RequestBody WorkflowTaskCreateRequest request) {
        User actor = currentUserUtil.getCurrentUser();
        WorkflowTask saved = taskService.createTask(request, actor);
        return ResponseEntity.ok(ApiResponse.ok(
                "Task assigned",
                WorkflowTaskResponse.from(saved, LocalDateTime.now())));
    }

    @PostMapping("/{taskId}/start")
    public ResponseEntity<ApiResponse<WorkflowTaskResponse>> start(
            @PathVariable Long taskId) {
        User actor = currentUserUtil.getCurrentUser();
        WorkflowTask updated = taskService.start(taskId, actor);
        return ResponseEntity.ok(ApiResponse.ok(
                "Task started",
                WorkflowTaskResponse.from(updated, LocalDateTime.now())));
    }

    @PostMapping("/{taskId}/complete")
    public ResponseEntity<ApiResponse<WorkflowTaskResponse>> complete(
            @PathVariable Long taskId) {
        User actor = currentUserUtil.getCurrentUser();
        WorkflowTask updated = taskService.complete(taskId, actor);
        return ResponseEntity.ok(ApiResponse.ok(
                "Task completed",
                WorkflowTaskResponse.from(updated, LocalDateTime.now())));
    }
}
