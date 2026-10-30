package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.*;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.model.LegacyLoanCorrection;
import com.patrick.fintech.loan_backend.service.LegacyLoanCorrectionService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/loans/{loanId}/legacy-corrections")
@RequiredArgsConstructor
public class LegacyLoanCorrectionController {
    private final LegacyLoanCorrectionService service;
    private final CurrentUserUtil currentUser;

    @GetMapping
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER','ACCOUNTANT','ADMIN','MANAGER','LOAN_OFFICER','CREDIT_ANALYST','AUDITOR')")
    public ResponseEntity<ApiResponse<List<LegacyLoanCorrection>>> history(@PathVariable Long loanId){
        return ResponseEntity.ok(ApiResponse.ok(service.history(loanId,currentUser.getCurrentUser())));
    }
    @PostMapping("/preview")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER','ADMIN','MANAGER','ACCOUNTANT','LOAN_OFFICER','CREDIT_ANALYST')")
    public ResponseEntity<ApiResponse<LegacyLoanCorrectionService.Preview>> preview(
            @PathVariable Long loanId,@RequestBody LegacyLoanCorrectionRequest request){
        return ResponseEntity.ok(ApiResponse.ok(service.preview(loanId,request,currentUser.getCurrentUser())));
    }
    @PostMapping
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER','ADMIN','MANAGER','ACCOUNTANT','LOAN_OFFICER','CREDIT_ANALYST')")
    public ResponseEntity<ApiResponse<LegacyLoanCorrection>> submit(
            @PathVariable Long loanId,@RequestBody LegacyLoanCorrectionRequest request){
        return ResponseEntity.ok(ApiResponse.ok("Correction submitted for independent review",
            service.submit(loanId,request,currentUser.getCurrentUser())));
    }
    /** Explicit Business Owner override for verified untouched legacy imports. */
    @PostMapping("/owner-apply")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<ApiResponse<LegacyLoanCorrection>> ownerApply(
            @PathVariable Long loanId, @RequestBody LegacyLoanCorrectionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
            "Owner-authorized historical correction applied and audited",
            service.applyOwnerCorrection(loanId, request, currentUser.getCurrentUser())));
    }

    @PostMapping("/{correctionId}/approve")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<ApiResponse<LegacyLoanCorrection>> approve(
            @PathVariable Long loanId,@PathVariable Long correctionId,@RequestBody LegacyLoanCorrectionDecision decision){
        return ResponseEntity.ok(ApiResponse.ok("Correction approved and reconciled",
            service.decide(loanId,correctionId,true,decision,currentUser.getCurrentUser())));
    }
    @PostMapping("/{correctionId}/reject")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<ApiResponse<LegacyLoanCorrection>> reject(
            @PathVariable Long loanId,@PathVariable Long correctionId,@RequestBody LegacyLoanCorrectionDecision decision){
        return ResponseEntity.ok(ApiResponse.ok("Correction rejected",
            service.decide(loanId,correctionId,false,decision,currentUser.getCurrentUser())));
    }
}
