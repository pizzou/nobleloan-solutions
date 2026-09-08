package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.FinancialApproval;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.service.FinancialApprovalService;
import com.patrick.fintech.loan_backend.repository.FinancialApprovalRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/financial-approvals")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','ACCOUNTANT')")
public class FinancialApprovalController {

    private final FinancialApprovalService service;
    private final FinancialApprovalRepository approvalRepo;
    private final OrganizationRepository orgRepo;
    private final CurrentUserUtil current;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> submit(
            @RequestBody Request request
    ) {
        Organization organization = org();
        User user = current.getCurrentUser();

        FinancialApproval approval = service.submit(
                organization,
                request.operationType(),
                request.operationId(),
                request.amount(),
                request.reason(),
                request.payload(),
                user.getId(),
                user.getName()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toResponse(approval)));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> approve(
            @PathVariable Long id,
            @RequestBody(required = false) Decision decision
    ) {
        User user = current.getCurrentUser();

        FinancialApproval approval = service.approve(
                id,
                org(),
                user.getId(),
                user.getName(),
                user.getRole() == null ? null : user.getRole().getName(),
                decision == null ? null : decision.comments()
        );

        return ResponseEntity.ok(ApiResponse.ok(toResponse(approval)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reject(
            @PathVariable Long id,
            @RequestBody(required = false) Decision decision
    ) {
        User user = current.getCurrentUser();

        FinancialApproval approval = service.reject(
                id,
                org(),
                user.getId(),
                user.getName(),
                user.getRole() == null ? null : user.getRole().getName(),
                decision == null ? null : decision.comments()
        );

        return ResponseEntity.ok(ApiResponse.ok(toResponse(approval)));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> pending() {
        List<FinancialApproval> approvals =
                approvalRepo.findByOrganization_IdAndStatusOrderByCreatedAtAsc(
                        org().getId(),
                        "PENDING"
                );

        List<Map<String, Object>> response = new ArrayList<>(approvals.size());

        for (FinancialApproval approval : approvals) {
            response.add(toResponse(approval));
        }

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    private Map<String, Object> toResponse(FinancialApproval approval) {
        Map<String, Object> response = new LinkedHashMap<>();

        response.put("id", approval.getId());
        response.put("operationType", approval.getOperationType());
        response.put("operationId", approval.getOperationId());
        response.put("amount", approval.getAmount());
        response.put("currency", approval.getCurrency());
        response.put("makerUserId", approval.getMakerUserId());
        response.put("makerName", approval.getMakerName());
        response.put("checkerUserId", approval.getCheckerUserId());
        response.put("checkerName", approval.getCheckerName());
        response.put("requiredLevel", approval.getRequiredLevel());
        response.put("status", approval.getStatus());
        response.put("reason", approval.getReason());
        response.put("payloadHash", approval.getPayloadHash());
        response.put("createdAt", approval.getCreatedAt());
        response.put("decidedAt", approval.getDecidedAt());
        response.put("consumedAt", approval.getConsumedAt());

        return response;
    }

    private Organization org() {
        return orgRepo.findById(current.getCurrentOrganizationId())
                .orElseThrow(() -> new IllegalStateException("Organization not found"));
    }

    public record Request(
            String operationType,
            String operationId,
            BigDecimal amount,
            String reason,
            String payload
    ) {
    }

    public record Decision(String comments) {
    }
}
