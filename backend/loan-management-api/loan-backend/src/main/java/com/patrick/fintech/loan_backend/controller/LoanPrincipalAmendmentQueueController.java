package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.model.LoanPrincipalAmendmentRequest;
import com.patrick.fintech.loan_backend.service.LoanPrincipalAmendmentService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping({"/api/principal-amendments", "/api/loan-correction-requests"})
@RequiredArgsConstructor
public class LoanPrincipalAmendmentQueueController {
    private final LoanPrincipalAmendmentService service;
    private final CurrentUserUtil currentUser;

    @GetMapping("/pending")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<List<LoanPrincipalAmendmentRequest>> pending() {
        return ResponseEntity.ok(service.pending(currentUser.getCurrentUser()));
    }
}
