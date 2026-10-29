package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.LegacyLoanCorrection;
import com.patrick.fintech.loan_backend.service.LegacyLoanCorrectionService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** Owner-only, organization-scoped inbox of corrections awaiting approval. */
@RestController
@RequestMapping("/api/legacy-corrections")
@RequiredArgsConstructor
public class LegacyLoanCorrectionQueueController {
    private final LegacyLoanCorrectionService service;
    private final CurrentUserUtil currentUser;

    @GetMapping("/pending")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<ApiResponse<List<LegacyLoanCorrection>>> pending() {
        return ResponseEntity.ok(ApiResponse.ok(
                service.pendingForBusinessOwner(currentUser.getCurrentUser())));
    }
}
