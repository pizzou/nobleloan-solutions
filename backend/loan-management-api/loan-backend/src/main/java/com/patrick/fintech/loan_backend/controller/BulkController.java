package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.service.BulkDisbursementService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bulk")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('LOAN_OFFICER','TELLER','MANAGER','ADMIN')")
public class BulkController {
    private final BulkDisbursementService bulkService;
    private final CurrentUserUtil currentUserUtil;

    @PostMapping("/disburse")
    public ResponseEntity<ApiResponse<BulkDisbursementService.BulkDisbursementResult>> disburse(
            @RequestBody Map<String, Object> body) {
        var user = currentUserUtil.getCurrentUser();
        if (body == null || !(body.get("loanIds") instanceof List<?> rawIds) || rawIds.isEmpty()) {
            throw new IllegalArgumentException("loanIds must be a non-empty list");
        }
        List<Long> ids = rawIds.stream()
                .map(value -> {
                    if (!(value instanceof Number number)) {
                        throw new IllegalArgumentException("Every loan ID must be numeric");
                    }
                    long id = number.longValue();
                    if (id <= 0) {
                        throw new IllegalArgumentException("Every loan ID must be positive");
                    }
                    return id;
                })
                .distinct()
                .toList();
        if (user.getOrganization() == null || user.getOrganization().getId() == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "User is not associated with an organization");
        }
        return ResponseEntity.ok(ApiResponse.ok(bulkService.disburseAll(ids,
                user.getOrganization().getId(), user,
                body.getOrDefault("disbursementMethod", "BANK_TRANSFER").toString())));
    }
}
