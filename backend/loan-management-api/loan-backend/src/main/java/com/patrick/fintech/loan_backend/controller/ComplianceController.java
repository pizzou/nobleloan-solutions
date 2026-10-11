package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.mapper.ResponseDtoMapper;
import com.patrick.fintech.loan_backend.model.KycCheck;
import com.patrick.fintech.loan_backend.service.ComplianceService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/compliance")
@RequiredArgsConstructor
public class ComplianceController {
    private final ComplianceService complianceService;
    private final CurrentUserUtil currentUserUtil;

    @PostMapping("/borrowers/{id}/screen")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','CREDIT_ANALYST')")
    public ResponseEntity<ApiResponse<Object>> screen(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.safe(complianceService.runFullScreening(id, currentUserUtil.getCurrentOrganizationId())));
    }

    @GetMapping("/borrowers/{id}/history")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','CREDIT_ANALYST')")
    public ResponseEntity<ApiResponse<Object>> history(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.safe(complianceService.getHistoryForBorrower(
                id, currentUserUtil.getCurrentOrganizationId())));
    }

    @GetMapping("/borrowers/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','CREDIT_ANALYST')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.safe(Map.of("kycClear", complianceService.isKycCurrentlyClear(
                id, currentUserUtil.getCurrentOrganizationId()))));
    }

    @GetMapping("/pending-reviews")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CREDIT_ANALYST')")
    public ResponseEntity<ApiResponse<Object>> pending() {
        return ResponseEntity
                .ok(ApiResponse.safe(complianceService.getPendingReviews(currentUserUtil.getCurrentOrganizationId())));
    }

    @PostMapping("/checks/{checkId}/decide")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CREDIT_ANALYST')")
    public ResponseEntity<ApiResponse<Object>> decide(@PathVariable Long checkId,
            @RequestBody Map<String, String> body) {
        String decisionValue = body == null ? null : body.get("decision");
        if (decisionValue == null || decisionValue.isBlank()) {
            throw new IllegalArgumentException("A KYC review decision is required.");
        }
        return ResponseEntity.ok(ApiResponse.safe(complianceService.manualReview(
                checkId,
                currentUserUtil.getCurrentOrganizationId(),
                currentUserUtil.getCurrentUser().getName(),
                KycCheck.CheckResult.valueOf(decisionValue.trim().toUpperCase(java.util.Locale.ROOT)),
                body.getOrDefault("notes", ""))));
    }
}
