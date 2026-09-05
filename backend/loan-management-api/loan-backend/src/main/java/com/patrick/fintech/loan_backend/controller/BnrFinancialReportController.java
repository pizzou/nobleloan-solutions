package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.service.BnrFinancialStatementService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/bnr/financial-reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','AUDITOR')")
public class BnrFinancialReportController {

    private final BnrFinancialStatementService
            bnrFinancialStatementService;

    private final CurrentUserUtil currentUserUtil;


    // ============================================================
    // BNR FINANCIAL STATEMENT
    // ============================================================

    /**
     * Accounting-based BNR financial statement.
     *
     * Source:
     *
     * JournalEntry
     *      ↓
     * JournalLine
     *      ↓
     * ChartOfAccount
     *
     * Example:
     *
     * GET /api/bnr/financial-reports/financial-statement
     *     ?from=2026-01-01
     *     &to=2026-01-31
     */
    @GetMapping("/financial-statement")
    public ResponseEntity<Map<String, Object>>
    getFinancialStatement(

            @RequestParam
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate from,

            @RequestParam
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate to
    ) {

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();

        Map<String, Object> report =
                bnrFinancialStatementService
                        .buildFinancialStatement(
                                organizationId,
                                from,
                                to
                        );

        return ResponseEntity.ok(report);
    }
}
