package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.service.BankReconciliationService;
import com.patrick.fintech.loan_backend.service.FinancialApprovalService;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/accounting/bank-reconciliation")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','ACCOUNTANT')")
public class BankReconciliationController {

    private final BankReconciliationService service;
    private final FinancialApprovalService approvalService;
    private final OrganizationRepository orgRepo;
    private final CurrentUserUtil current;

    @PostMapping("/{bankAccountId}/import")
    public ResponseEntity<ApiResponse<Integer>> importCsv(
            @PathVariable Long bankAccountId,
            @RequestParam("file") MultipartFile file
    ) throws Exception {
        Organization organization = org();

        int imported = service.importCsv(
                organization,
                bankAccountId,
                file.getInputStream(),
                current.getCurrentUser().getName()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.safe(imported));
    }

    @PostMapping("/{bankAccountId}/reconcile")
    public ResponseEntity<ApiResponse<BankReconciliationService.ReconciliationResult>> reconcile(
            @PathVariable Long bankAccountId,
            @RequestParam String from,
            @RequestParam String to
    ) {
        Organization organization = org();

        BankReconciliationService.ReconciliationResult result =
                service.reconcile(
                        organization,
                        bankAccountId,
                        LocalDate.parse(from),
                        LocalDate.parse(to),
                        current.getCurrentUser().getName()
                );

        return ResponseEntity.ok(ApiResponse.safe(result));
    }

    @PostMapping("/{bankAccountId}/sign")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> sign(
            @PathVariable Long bankAccountId,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam String reportHash,
            @RequestParam Long approvalId
    ) {
        Organization organization = org();

        var approval = approvalService.consumeApproved(
                approvalId,
                organization,
                "BANK_RECONCILIATION",
                bankAccountId + "|" + from + "|" + to
        );

        if (reportHash == null
                || reportHash.isBlank()
                || !reportHash.equalsIgnoreCase(approval.getPayloadHash())) {
            throw new IllegalStateException(
                    "Reconciliation report hash does not match the approved maker payload"
            );
        }

        java.util.Map<String, Object> response = new java.util.LinkedHashMap<>();
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

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    private Organization org() {
        return orgRepo.findById(current.getCurrentOrganizationId())
                .orElseThrow(() -> new IllegalStateException("Organization not found"));
    }
}
