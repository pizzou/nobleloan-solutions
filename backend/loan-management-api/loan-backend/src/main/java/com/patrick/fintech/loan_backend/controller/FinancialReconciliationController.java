package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.service.AccountingService;
import com.patrick.fintech.loan_backend.service.FinancialReconciliationService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import com.patrick.fintech.loan_backend.service.ReportingScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/accounting/reconciliation")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','ACCOUNTANT')")
public class FinancialReconciliationController {

    private final FinancialReconciliationService reconciliationService;
    private final AccountingService accountingService;
    private final CurrentUserUtil currentUserUtil;

    /**
     * Read-only loan-level diagnostic. No accounting data is modified.
     */
    @GetMapping("/loans")
    public ResponseEntity<ApiResponse<List<FinancialReconciliationService.LoanReconciliationDiagnostic>>> diagnoseLoans() {

        Long organizationId = currentUserUtil.getCurrentOrganizationId();
        if (organizationId == null || organizationId <= 0) {
            throw new IllegalStateException("No organization is associated with the current user.");
        }

        return ResponseEntity.ok(
                ApiResponse.safe(reconciliationService.diagnoseLoanSubledger(organizationId)));
    }

    /**
     * Controlled repair for legacy disbursement journals that do not yet
     * contain the one-time application-fee income entry. The accounting
     * service only creates a correction when the GL deltas are provably
     * balanced; it never edits or deletes posted journals.
     */
    @org.springframework.web.bind.annotation.PostMapping("/repair-application-fees")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> repairApplicationFees() {

        Long organizationId = currentUserUtil.getCurrentOrganizationId();
        if (organizationId == null || organizationId <= 0) {
            throw new IllegalStateException("No organization is associated with the current user.");
        }

        java.util.Map<String, Object> result =
                accountingService.repairApplicationFeeAccountingForOrganization(
                        organizationId,
                        ReportingScopeService.includeBusinessOwnerOnly());

        return ResponseEntity.ok(ApiResponse.safe(result));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<FinancialReconciliationService.ReconciliationReport>> reconcile(
            @RequestParam(required = false) String asOf,
            @RequestParam(required = false) String from) {

        Long organizationId = currentUserUtil.getCurrentOrganizationId();
        if (organizationId == null || organizationId <= 0) {
            throw new IllegalStateException("No organization is associated with the current user.");
        }

        LocalDate date = asOf == null || asOf.isBlank()
                ? LocalDate.now()
                : LocalDate.parse(asOf.trim());
        LocalDate periodStart = from == null || from.isBlank()
                ? null
                : LocalDate.parse(from.trim());

        FinancialReconciliationService.ReconciliationReport report = reconciliationService.reconcile(
                organizationId, periodStart, date);

        return ResponseEntity.ok(ApiResponse.safe(report));
    }
}
