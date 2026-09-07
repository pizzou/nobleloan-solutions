package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.service.AccountingPeriodService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/accounting/periods")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
public class AccountingPeriodController {
    private final AccountingPeriodService service;
    private final CurrentUserUtil currentUserUtil;

    @PostMapping("/open")
    public ResponseEntity<ApiResponse<Object>> open(@RequestBody Map<String,Object> body) {
        return ResponseEntity.ok(ApiResponse.safe("Accounting period opened", service.open(
                currentUserUtil.getCurrentOrganizationId(), intValue(body,"year"), intValue(body,"month"), currentUserUtil.getCurrentUser().getName())));
    }

    @PostMapping("/close")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<Object>> close(@RequestBody Map<String,Object> body) {
        return ResponseEntity.ok(ApiResponse.safe("Accounting period closed", service.close(
                currentUserUtil.getCurrentOrganizationId(), intValue(body,"year"), intValue(body,"month"), currentUserUtil.getCurrentUser().getName())));
    }

    private int intValue(Map<String,Object> body, String key) {
        if (body == null || body.get(key) == null) throw new IllegalArgumentException(key + " is required");
        try { return Integer.parseInt(body.get(key).toString()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(key + " must be an integer"); }
    }
}
