package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.DashboardStats;
import com.patrick.fintech.loan_backend.dto.LoanRequest;
import com.patrick.fintech.loan_backend.dto.LoanResponse;
import com.patrick.fintech.loan_backend.dto.LoanCommentResponse;
import com.patrick.fintech.loan_backend.mapper.ResponseDtoMapper;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanComment;
import com.patrick.fintech.loan_backend.model.LoanApproval;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.LoanCommentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.IdempotencyService;
import com.patrick.fintech.loan_backend.service.DashboardService;
import com.patrick.fintech.loan_backend.service.LoanApprovalService;
import com.patrick.fintech.loan_backend.service.LoanService;
import com.patrick.fintech.loan_backend.service.LoanRestructuringService;
import com.patrick.fintech.loan_backend.service.MailService;
import com.patrick.fintech.loan_backend.service.PaymentService;
import com.patrick.fintech.loan_backend.service.RiskScoringService;
import com.patrick.fintech.loan_backend.service.SmsService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/loans")
@RequiredArgsConstructor
public class LoanController {

        private final LoanService loanService;

        private final DashboardService dashboardService;

        private final PaymentService paymentService;

        private final RiskScoringService riskScoringService;

        private final LoanApprovalService loanApprovalService;

        private final CurrentUserUtil currentUserUtil;

        private final LoanCommentRepository loanCommentRepo;

        private final SmsService smsService;

        private final MailService mailService;

        private final AuditService auditService;

        private final LoanRestructuringService loanRestructuringService;

        private final IdempotencyService idempotencyService;

        private final ObjectMapper objectMapper;

        // ================================================================
        // CREATE LOAN
        // ================================================================

        @PostMapping
        @PreAuthorize("hasAnyRole('LOAN_OFFICER','ADMIN','MANAGER')")
        public ResponseEntity<ApiResponse<LoanResponse>> createLoan(
                        @Valid @RequestBody LoanRequest req) {

                User user = currentUserUtil.getCurrentUser();

                Loan loan = loanService.createLoan(
                                req,
                                user.getOrganization().getId(),
                                user);

                /*
                 * Create the maker-checker approval chain immediately
                 * after the loan has been persisted.
                 */
                loanApprovalService.initiateChain(
                                loan);

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(
                                                ApiResponse.ok(
                                                                "Loan application created and submitted for approval",
                                                                ResponseDtoMapper.loan(loan)));
        }

        // ================================================================
        // GET LOANS
        // ================================================================

        @GetMapping
        public ResponseEntity<ApiResponse<Page<LoanResponse>>> getLoans(
                        @RequestParam(defaultValue = "0") int page,

                        @RequestParam(defaultValue = "20") int size,

                        @RequestParam(required = false) String status,

                        @RequestParam(required = false) String type) {

                Organization organization = currentUserUtil
                                .getCurrentUser()
                                .getOrganization();

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                ResponseDtoMapper.loans(loanService.getLoans(
                                                                organization,
                                                                page,
                                                                size,
                                                                status,
                                                                type))));
        }

        // ================================================================
        // GET SINGLE LOAN
        // ================================================================

        @GetMapping("/{id}")
        public ResponseEntity<ApiResponse<LoanResponse>> getLoan(
                        @PathVariable Long id) {

                Long organizationId = currentUserUtil
                                .getCurrentOrganizationId();

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                ResponseDtoMapper.loan(loanService.getLoanForOrg(
                                                                id,
                                                                organizationId))));
        }

        // ================================================================
        // GET LOAN SCHEDULE
        // ================================================================

        @GetMapping("/{id}/schedule")
        public ResponseEntity<ApiResponse<List<com.patrick.fintech.loan_backend.dto.PaymentResponse>>> getSchedule(
                        @PathVariable Long id) {

                Long organizationId = currentUserUtil
                                .getCurrentOrganizationId();

                /*
                 * Explicit organization ownership check.
                 */
                loanService.getLoanForOrg(
                                id,
                                organizationId);

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                ResponseDtoMapper.payments(paymentService.getLoanSchedule(
                                                                id,
                                                                organizationId))));
        }

        // ================================================================
        // DOCUMENT REQUIREMENTS
        // ================================================================

        @GetMapping("/{id}/document-requirements")
        public ResponseEntity<ApiResponse<Map<String, Object>>> getDocumentRequirements(
                        @PathVariable Long id) {

                Long organizationId = currentUserUtil
                                .getCurrentOrganizationId();

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                loanService.getDocumentRequirements(
                                                                id,
                                                                organizationId)));
        }

        // ================================================================
        // GET LOANS BY BORROWER
        // ================================================================

        @GetMapping("/borrower/{borrowerId}")
        public ResponseEntity<ApiResponse<List<LoanResponse>>> getByBorrower(
                        @PathVariable Long borrowerId) {

                Long organizationId = currentUserUtil
                                .getCurrentOrganizationId();

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                ResponseDtoMapper.loans(loanService
                                                                .getLoanRepository()
                                                                .findByBorrowerIdAndOrganizationId(
                                                                                borrowerId,
                                                                                organizationId))));
        }

        // ================================================================
        // RISK SCORE
        // ================================================================

        @GetMapping("/{id}/risk")
        public ResponseEntity<ApiResponse<RiskScoringService.RiskResult>> getRisk(
                        @PathVariable Long id) {

                Long organizationId = currentUserUtil
                                .getCurrentOrganizationId();

                Loan loan = loanService.getLoanForOrg(
                                id,
                                organizationId);

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                riskScoringService.score(
                                                                loan)));
        }

        @PostMapping("/{id}/approve")
        @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
        public ResponseEntity<ApiResponse<LoanResponse>> approveLoan(
                        @PathVariable Long id,
                        @RequestBody(required = false) Map<String, String> body,
                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

                User user = currentUserUtil.getCurrentUser();
                Map<String, String> request = body == null ? Map.of() : new TreeMap<>(body);

                return executeIdempotentLoanMutation(
                                id,
                                idempotencyKey,
                                "POST /loans/" + id + "/approve",
                                request,
                                user,
                                () -> {
                                        String notes = firstNonBlank(request.get("notes"), request.get("comments"));

                                        Double newInterestRate = parseOptionalDouble(request.get("interestRate"),
                                                        "interestRate");
                                        Double newProcessingFeeRate = parseOptionalDouble(
                                                        request.get("applicationFeeRate"), "applicationFeeRate");
                                        BigDecimal newApprovedAmount = parseOptionalBigDecimal(
                                                        request.get("approvedAmount"), "approvedAmount");

                                        loanApprovalService.decide(
                                                        id,
                                                        user,
                                                        "APPROVED",
                                                        notes,
                                                        newInterestRate,
                                                        newProcessingFeeRate,
                                                        newApprovedAmount);

                                        return loanService.getLoanForOrg(
                                                        id,
                                                        user.getOrganization().getId());
                                });
        }

        private Double parseOptionalDouble(String raw, String field) {
                if (raw == null || raw.isBlank())
                        return null;
                try {
                        return Double.valueOf(raw.trim());
                } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(field + " must be a valid number.");
                }
        }

        private BigDecimal parseOptionalBigDecimal(String raw, String field) {
                if (raw == null || raw.isBlank())
                        return null;
                try {
                        return new BigDecimal(raw.trim());
                } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(field + " must be a valid number.");
                }
        }

        private ResponseEntity<ApiResponse<LoanResponse>> executeIdempotentLoanMutation(
                        Long loanId,
                        String idempotencyKey,
                        String endpoint,
                        Map<String, String> request,
                        User user,
                        Supplier<Loan> mutation) {

                if (idempotencyKey == null || idempotencyKey.isBlank()) {
                        return ResponseEntity.ok(
                                        ApiResponse.ok(
                                                        "Loan mutation completed",
                                                        ResponseDtoMapper.loan(mutation.get())));
                }

                try {
                        var outcome = idempotencyService.checkOrReserve(
                                        idempotencyKey,
                                        user.getOrganization(),
                                        endpoint,
                                        objectMapper.writeValueAsString(new TreeMap<>(request)));

                        if (outcome.isReplay()) {
                                LoanResponse cached = outcome.cachedResponseBody() == null
                                                ? null
                                                : objectMapper.readValue(
                                                                outcome.cachedResponseBody(),
                                                                LoanResponse.class);

                                return ResponseEntity
                                                .status(outcome.cachedStatusCode() == null ? 200
                                                                : outcome.cachedStatusCode())
                                                .body(ApiResponse.ok("Request already processed", cached));
                        }

                        try {
                                Loan loan = mutation.get();
                                LoanResponse response = ResponseDtoMapper.loan(loan);
                                idempotencyService.recordSuccess(
                                                idempotencyKey,
                                                user.getOrganization(),
                                                response,
                                                HttpStatus.OK.value());

                                return ResponseEntity.ok(
                                                ApiResponse.ok("Loan mutation completed", response));
                        } catch (RuntimeException ex) {
                                idempotencyService.recordFailure(idempotencyKey, user.getOrganization());
                                throw ex;
                        }
                } catch (RuntimeException ex) {
                        throw ex;
                } catch (Exception ex) {
                        idempotencyService.recordFailure(idempotencyKey, user.getOrganization());
                        throw new IllegalStateException("Unable to process idempotent loan mutation", ex);
                }
        }

        private String firstNonBlank(
                        String... values) {

                if (values == null) {
                        return null;
                }

                for (String value : values) {

                        if (value != null
                                        && !value.isBlank()) {

                                return value.trim();
                        }
                }

                return null;
        }

        @PostMapping("/{id}/reject")
        @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
        public ResponseEntity<ApiResponse<LoanResponse>> rejectLoan(
                        @PathVariable Long id,
                        @RequestBody(required = false) Map<String, String> body,
                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

                User user = currentUserUtil.getCurrentUser();
                Map<String, String> request = body == null ? Map.of() : new TreeMap<>(body);

                return executeIdempotentLoanMutation(
                                id,
                                idempotencyKey,
                                "POST /loans/" + id + "/reject",
                                request,
                                user,
                                () -> {
                                        String reason = firstNonBlank(
                                                        request.get("reason"),
                                                        request.get("comments"),
                                                        request.get("notes"));

                                        if (reason == null) {
                                                reason = "Rejected by authorized approver.";
                                        }

                                        loanApprovalService.decide(id, user, "REJECTED", reason);

                                        return loanService.getLoanForOrg(
                                                        id,
                                                        user.getOrganization().getId());
                                });
        }

        @PostMapping("/{id}/disburse")
        @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
        public ResponseEntity<ApiResponse<LoanResponse>> disburseLoan(
                        @PathVariable Long id,
                        @RequestBody(required = false) Map<String, String> body,
                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

                User user = currentUserUtil.getCurrentUser();
                Map<String, String> request = body == null ? Map.of() : new TreeMap<>(body);

                return executeIdempotentLoanMutation(
                                id,
                                idempotencyKey,
                                "POST /loans/" + id + "/disburse",
                                request,
                                user,
                                () -> loanService.disburseLoan(
                                                id,
                                                user,
                                                request.getOrDefault("disbursementMethod", "BANK_TRANSFER")));
        }

        // ================================================================
        // LOAN EXTENSION / RESTRUCTURING
        // ================================================================

        // ================================================================
        // LOAN EXTENSION / RESTRUCTURING
        // ================================================================

        @PostMapping("/{id}/extend")
        @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER')")
        public ResponseEntity<ApiResponse<LoanResponse>> extendLoan(
                        @PathVariable Long id,
                        @RequestBody Map<String, Object> body) {

                User user = currentUserUtil.getCurrentUser();

                if (body == null) {
                        throw new IllegalArgumentException("Extension request body is required.");
                }

                Object rawMonths = body.get("extensionMonths");
                int extensionMonths;

                try {
                        extensionMonths = rawMonths instanceof Number
                                        ? ((Number) rawMonths).intValue()
                                        : Integer.parseInt(String.valueOf(rawMonths));
                } catch (Exception ex) {
                        throw new IllegalArgumentException("extensionMonths must be a valid integer.");
                }

                String reason = body.get("reason") == null
                                ? null
                                : String.valueOf(body.get("reason")).trim();

                if (extensionMonths < 1 || extensionMonths > 6) {
                        throw new IllegalArgumentException(
                                        "Extension period must be between 1 and 6 months.");
                }

                if (reason != null && reason.length() > 1000) {
                        throw new IllegalArgumentException(
                                        "Extension reason must not exceed 1000 characters.");
                }

                Loan loan = loanRestructuringService.extendLoan(
                                id,
                                user.getOrganization().getId(),
                                user,
                                extensionMonths,
                                reason);

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                "Loan extension applied successfully",
                                                ResponseDtoMapper.loan(loan)));
        }

        @PostMapping("/{id}/restructure")
        @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
        public ResponseEntity<ApiResponse<LoanResponse>> restructureLoan(
                        @PathVariable Long id,
                        @RequestBody Map<String, Object> body) {

                User user = currentUserUtil.getCurrentUser();

                if (body == null) {
                        throw new IllegalArgumentException("Restructuring request body is required.");
                }

                Object rawMonths = body.get("newMonths");
                int newMonths;

                try {
                        newMonths = rawMonths instanceof Number
                                        ? ((Number) rawMonths).intValue()
                                        : Integer.parseInt(String.valueOf(rawMonths));
                } catch (Exception ex) {
                        throw new IllegalArgumentException("newMonths must be a valid integer.");
                }

                Double newRate = null;
                Object rawRate = body.get("newRate");
                if (rawRate != null && !String.valueOf(rawRate).isBlank()) {
                        try {
                                newRate = Double.valueOf(String.valueOf(rawRate));
                        } catch (NumberFormatException ex) {
                                throw new IllegalArgumentException("newRate must be a valid number.");
                        }
                }

                String reason = body.get("reason") == null
                                ? null
                                : String.valueOf(body.get("reason"));

                Loan loan = loanRestructuringService.restructure(
                                id,
                                user.getOrganization().getId(),
                                user,
                                newMonths,
                                newRate,
                                reason);

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                "Loan restructured",
                                                ResponseDtoMapper.loan(loan)));
        }

        // ================================================================
        // UPDATE STATUS
        // ================================================================

        @PostMapping("/{id}/status")
        @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER')")
        public ResponseEntity<ApiResponse<LoanResponse>> updateStatus(
                        @PathVariable Long id,
                        @RequestBody Map<String, String> body,
                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

                if (body == null || body.get("status") == null || body.get("status").isBlank()) {
                        throw new IllegalArgumentException("status is required.");
                }

                User user = currentUserUtil.getCurrentUser();
                Map<String, String> request = new TreeMap<>(body);

                return executeIdempotentLoanMutation(
                                id,
                                idempotencyKey,
                                "POST /loans/" + id + "/status",
                                request,
                                user,
                                () -> {
                                        final LoanStatus newStatus;
                                        try {
                                                newStatus = LoanStatus.valueOf(
                                                                request.get("status").trim().toUpperCase());
                                        } catch (IllegalArgumentException e) {
                                                throw new IllegalArgumentException(
                                                                "Invalid loan status: " + request.get("status"));
                                        }

                                        return loanService.updateStatus(
                                                        id,
                                                        user,
                                                        newStatus,
                                                        request.get("notes"));
                                });
        }

        // ================================================================
        // DASHBOARD
        // ================================================================

        @GetMapping("/dashboard")
        public ResponseEntity<ApiResponse<DashboardStats>> getDashboard() {
                Long organizationId = currentUserUtil.getCurrentOrganizationId();

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                dashboardService.getStats(organizationId)));
        }

        // ================================================================
        // ADD STAFF COMMENT
        // ================================================================

        /**
         * Staff note on a loan application.
         *
         * Applicant-visible comments can be sent to the borrower.
         * Internal comments remain staff-only.
         */
        @PostMapping("/{id}/comments")
        @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER')")
        @Transactional
        public ResponseEntity<ApiResponse<LoanCommentResponse>> addComment(
                        @PathVariable Long id,

                        @RequestBody Map<String, Object> body) {

                User user = currentUserUtil.getCurrentUser();

                Loan loan = loanService.getLoanForOrg(
                                id,
                                user.getOrganization().getId());

                String message = body != null
                                && body.get("message") != null
                                                ? body.get("message")
                                                                .toString()
                                                                .trim()
                                                : "";

                if (message.isEmpty()) {

                        throw new IllegalArgumentException(
                                        "Comment message is required.");
                }

                boolean visibleToApplicant = body == null
                                || body.get("visibleToApplicant") == null
                                || Boolean.parseBoolean(
                                                body.get("visibleToApplicant")
                                                                .toString());

                LoanComment comment = loanCommentRepo.save(
                                LoanComment.builder()
                                                .loan(loan)
                                                .author(user)
                                                .message(message)
                                                .visibleToApplicant(
                                                                visibleToApplicant)
                                                .build());

                auditService.log(
                                loan.getOrganization(),
                                user,
                                "LOAN_COMMENT_ADDED",
                                "LOAN",
                                id.toString(),
                                (visibleToApplicant
                                                ? "Applicant-visible comment"
                                                : "Internal comment")
                                                + " added to loan "
                                                + loan.getReferenceNumber()
                                                + ": "
                                                + message,
                                null,
                                null,
                                "Loans");

                // ------------------------------------------------------------
                // SMS
                // ------------------------------------------------------------

                if (visibleToApplicant
                                && loan.getBorrower() != null
                                && loan.getBorrower().getPhone() != null
                                && !loan.getBorrower()
                                                .getPhone()
                                                .isBlank()) {

                        try {

                                smsService.sendCustom(
                                                loan.getBorrower().getPhone(),

                                                String.format(
                                                                "%s: New update on your application %s. "
                                                                                + "Please check your application status online for details.",

                                                                loan.getOrganization().getName(),

                                                                loan.getReferenceNumber()));

                        } catch (Exception e) {

                                /*
                                 * Notification failure must not roll back
                                 * the saved comment.
                                 */
                                org.slf4j.LoggerFactory
                                                .getLogger(LoanController.class)
                                                .warn(
                                                                "Failed to send applicant comment SMS for loan {}",
                                                                id,
                                                                e);
                        }
                }

                // ------------------------------------------------------------
                // EMAIL
                // ------------------------------------------------------------

                if (visibleToApplicant
                                && loan.getBorrower() != null
                                && loan.getBorrower().getEmail() != null
                                && !loan.getBorrower()
                                                .getEmail()
                                                .isBlank()) {

                        try {

                                mailService.sendLoanUpdateComment(
                                                loan,
                                                message);

                        } catch (Exception e) {

                                /*
                                 * Notification failure must not roll back
                                 * the saved comment.
                                 */
                                org.slf4j.LoggerFactory
                                                .getLogger(LoanController.class)
                                                .warn(
                                                                "Failed to send applicant comment email for loan {}",
                                                                id,
                                                                e);
                        }
                }

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(
                                                ApiResponse.ok(
                                                                "Comment added",
                                                                ResponseDtoMapper.loanComment(comment)));
        }

        @GetMapping("/{id}/comments")
        @Transactional(readOnly = true)
        public ResponseEntity<ApiResponse<List<LoanCommentResponse>>> getComments(
                        @PathVariable Long id) {

                User user = currentUserUtil.getCurrentUser();

                /*
                 * Explicit organization ownership check.
                 */
                loanService.getLoanForOrg(
                                id,
                                user.getOrganization().getId());

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                ResponseDtoMapper.loanComments(loanCommentRepo
                                                                .findByLoanIdOrderByCreatedAtAsc(
                                                                                id))));
        }
}