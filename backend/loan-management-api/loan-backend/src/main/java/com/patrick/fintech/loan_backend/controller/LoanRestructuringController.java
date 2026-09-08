package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.LoanResponse;
import com.patrick.fintech.loan_backend.mapper.ResponseDtoMapper;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.service.LoanRestructuringService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/loans/{loanId}")
@RequiredArgsConstructor
public class LoanRestructuringController {
        private final LoanRestructuringService svc;
        private final CurrentUserUtil currentUserUtil;
        private final com.patrick.fintech.loan_backend.service.FinancialApprovalService financialApprovalService;

        @PostMapping("/write-off")
        @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
        @org.springframework.transaction.annotation.Transactional
        public ResponseEntity<ApiResponse<LoanResponse>> writeOff(@PathVariable Long loanId,
                        @RequestBody Map<String, String> body) {
                if (body == null) {
                        throw new IllegalArgumentException("Write-off request body is required.");
                }
                var u = currentUserUtil.getCurrentUser();
                Object approvalId = body.get("approvalId");
                if (approvalId == null) throw new IllegalArgumentException("approvalId is required for loan write-off");
                String reason = body.getOrDefault("reason", "Uncollectible");
                String operationId = loanId + "|" + reason;
                financialApprovalService.consumeApproved(Long.valueOf(approvalId.toString()), u.getOrganization(), "WRITE_OFF", operationId);
                return ResponseEntity.ok(ApiResponse.ok("Loan written off",
                                ResponseDtoMapper.loan(svc.writeOff(loanId, u.getOrganization().getId(), u,
                                                body.getOrDefault("reason", "Uncollectible")))));
        }

        @PostMapping("/moratorium")
        @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
        @org.springframework.transaction.annotation.Transactional
        public ResponseEntity<ApiResponse<LoanResponse>> moratorium(@PathVariable Long loanId,
                        @RequestBody Map<String, Object> body) {
                if (body == null || body.get("pauseMonths") == null) {
                        throw new IllegalArgumentException("pauseMonths is required");
                }
                var u = currentUserUtil.getCurrentUser();
                int pauseMonths = Integer.parseInt(body.get("pauseMonths").toString());
                String reason = body.getOrDefault("reason", "Payment holiday").toString();
                Object approvalId = body.get("approvalId");
                if (approvalId == null) {
                        throw new IllegalArgumentException("approvalId is required for moratorium");
                }
                String operationId = loanId + "|" + pauseMonths + "|" + reason;
                financialApprovalService.consumeApproved(Long.valueOf(approvalId.toString()), u.getOrganization(), "MORATORIUM", operationId);
                return ResponseEntity.ok(ApiResponse.ok("Moratorium granted",
                                ResponseDtoMapper.loan(svc.grantMoratorium(loanId, u.getOrganization().getId(), u, pauseMonths, reason))));
        }
}
