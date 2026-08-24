package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.service.FinancialReconciliationService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/accounting/reconciliation")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','ACCOUNTANT')")
public class FinancialReconciliationController {

    private final FinancialReconciliationService reconciliationService;
    private final CurrentUserUtil currentUserUtil;

    @GetMapping
    public ResponseEntity<ApiResponse<FinancialReconciliationService.ReconciliationReport>> reconcile(
            @RequestParam(required = false) String asOf) {

        Long organizationId = currentUserUtil.getCurrentOrganizationId();
        if (organizationId == null || organizationId <= 0) {
            throw new IllegalStateException("No organization is associated with the current user.");
        }

        LocalDate date = asOf == null || asOf.isBlank()
                ? LocalDate.now()
                : LocalDate.parse(asOf.trim());

        FinancialReconciliationService.ReconciliationReport report =
                reconciliationService.reconcile(organizationId, date);

        return ResponseEntity.ok(ApiResponse.safe(report));
    }
}
