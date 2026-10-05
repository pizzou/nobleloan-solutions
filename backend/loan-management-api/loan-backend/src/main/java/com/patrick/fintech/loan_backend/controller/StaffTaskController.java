package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.StaffTaskCreateRequest;
import com.patrick.fintech.loan_backend.dto.StaffTaskDashboardResponse;
import com.patrick.fintech.loan_backend.dto.StaffTaskResponse;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.service.StaffTaskService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class StaffTaskController {

    private final StaffTaskService taskService;
    private final CurrentUserUtil currentUserUtil;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','BUSINESS_OWNER')")
    public ResponseEntity<ApiResponse<StaffTaskResponse>> create(
            @RequestBody StaffTaskCreateRequest request) {

        if (request == null) {
            throw new IllegalArgumentException("Task request is required.");
        }

        User actor = currentUserUtil.getCurrentUser();

        var task = taskService.createDirectTask(
                actor,
                request.assigneeUserId(),
                request.taskType(),
                request.entityType(),
                request.entityId(),
                request.reference(),
                request.title(),
                request.description(),
                request.priority(),
                request.dueAt());

        return ResponseEntity.ok(
                ApiResponse.ok(taskService.toResponseForController(task)));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<StaffTaskDashboardResponse>> dashboard() {
        User actor = currentUserUtil.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(taskService.getDashboard(actor)));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<StaffTaskResponse>>> mine() {
        User actor = currentUserUtil.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(taskService.getMyTasks(actor)));
    }

    @PostMapping("/{id}/claim")
    public ResponseEntity<ApiResponse<StaffTaskResponse>> claim(@PathVariable Long id) {
        User actor = currentUserUtil.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(taskService.claim(id, actor)));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<StaffTaskResponse>> complete(@PathVariable Long id) {
        User actor = currentUserUtil.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(taskService.complete(id, actor)));
    }
}
