package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.service.LoanPolicyPortfolioReconciliationService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * A one-time owner-controlled portfolio policy repair workflow.
 * There is no field for editable monetary amounts, and it is intentionally
 * NOT triggered from scheduled startup or GET requests.
 */
@RestController
@RequestMapping("/api/policy-recalculation/portfolio")
@RequiredArgsConstructor
public class LoanPolicyPortfolioReconciliationController {
    private final LoanPolicyPortfolioReconciliationService service;
    private final CurrentUserUtil currentUser;

    @GetMapping("/preview")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<ApiResponse<LoanPolicyPortfolioReconciliationService.Batch>> preview() {
        User actor = currentUser.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(service.run(
                actor.getOrganization().getId(), actor, false)));
    }

    @PostMapping("/apply-eligible")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<ApiResponse<LoanPolicyPortfolioReconciliationService.Batch>> apply() {
        User actor = currentUser.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(service.run(
                actor.getOrganization().getId(), actor, true)));
    }
}
