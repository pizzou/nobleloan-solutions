package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.model.LoanPrincipalAmendmentRequest;
import com.patrick.fintech.loan_backend.service.LoanPrincipalAmendmentService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping({"/api/loans/{loanId}/principal-amendments", "/api/loans/{loanId}/correction-requests"})
@RequiredArgsConstructor
public class LoanPrincipalAmendmentController {
    private final LoanPrincipalAmendmentService service;
    private final CurrentUserUtil currentUser;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','ACCOUNTANT','BUSINESS_OWNER','AUDITOR')")
    public ResponseEntity<List<LoanPrincipalAmendmentRequest>> history(@PathVariable Long loanId) {
        return ResponseEntity.ok(service.history(loanId, currentUser.getCurrentUser()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','ACCOUNTANT')")
    public ResponseEntity<LoanPrincipalAmendmentRequest> submit(
            @PathVariable Long loanId, @RequestBody LoanPrincipalAmendmentService.Proposal proposal) {
        return ResponseEntity.ok(service.submit(loanId, proposal, currentUser.getCurrentUser()));
    }

    @PostMapping("/{requestId}/approve")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<LoanPrincipalAmendmentRequest> approve(
            @PathVariable Long loanId, @PathVariable Long requestId,
            @RequestBody LoanPrincipalAmendmentService.Decision decision) {
        return ResponseEntity.ok(service.decide(loanId, requestId, true, decision, currentUser.getCurrentUser()));
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<LoanPrincipalAmendmentRequest> reject(
            @PathVariable Long loanId, @PathVariable Long requestId,
            @RequestBody LoanPrincipalAmendmentService.Decision decision) {
        return ResponseEntity.ok(service.decide(loanId, requestId, false, decision, currentUser.getCurrentUser()));
    }

    @PostMapping("/{requestId}/apply")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER','ACCOUNTANT')")
    public ResponseEntity<LoanPrincipalAmendmentRequest> apply(
            @PathVariable Long loanId, @PathVariable Long requestId) {
        return ResponseEntity.ok(service.apply(loanId, requestId, currentUser.getCurrentUser()));
    }
}
