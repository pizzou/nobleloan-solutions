package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.service.LoanPolicyRecalculationService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

/**
 * Owner-only policy recomputation from stored contractual facts and immutable
 * cash receipts. Preview is deliberately read-only; never use a GET endpoint
 * to post financial amendments.
 */
@RestController
@RequestMapping("/api/loans/{loanId}/policy-recalculation")
@RequiredArgsConstructor
public class LoanPolicyRecalculationController {
    private final LoanPolicyRecalculationService service;
    private final CurrentUserUtil currentUser;

    @GetMapping("/preview")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<ApiResponse<LoanPolicyRecalculationService.Preview>> preview(
            @PathVariable Long loanId,
            @RequestParam(required = false) LocalDate asOf) {
        User actor = currentUser.getCurrentUser();
        if (actor == null || actor.getOrganization() == null
                || actor.getOrganization().getId() == null)
            throw new IllegalStateException("Authenticated organization required");
        LocalDate date = asOf == null ? LocalDate.now() : asOf;
        if (date.isAfter(LocalDate.now()))
            throw new IllegalArgumentException("Cannot recalculate future financial periods");
        return ResponseEntity.ok(ApiResponse.ok(
                service.preview(loanId, actor.getOrganization().getId(), date)));
    }
}
