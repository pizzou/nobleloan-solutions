package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.DashboardStats;
import com.patrick.fintech.loan_backend.dto.LoanRequest;
import com.patrick.fintech.loan_backend.dto.LoanResponse;
import com.patrick.fintech.loan_backend.dto.LoanCommentResponse;
import com.patrick.fintech.loan_backend.mapper.ResponseDtoMapper;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanContract;
import com.patrick.fintech.loan_backend.service.LoanContractService;
import com.patrick.fintech.loan_backend.model.LoanComment;
import com.patrick.fintech.loan_backend.model.LoanApproval;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.LoanCommentRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.LoanApprovalService;
import com.patrick.fintech.loan_backend.service.LoanService;
import com.patrick.fintech.loan_backend.service.LoanRestructuringService;
import com.patrick.fintech.loan_backend.service.LoanAdministrationService;
import com.patrick.fintech.loan_backend.service.MailService;
import com.patrick.fintech.loan_backend.service.IdempotencyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.service.PaymentService;
import com.patrick.fintech.loan_backend.service.RiskScoringService;
import com.patrick.fintech.loan_backend.service.SmsService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/loans")
@RequiredArgsConstructor
public class LoanController {

        private final LoanService loanService;

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
        private final LoanAdministrationService loanAdministrationService;
        private final LoanContractService loanContractService;

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

                // Create the contract after the application and its approval chain exist.
                // The contract is a draft only; it does not approve or disburse the loan.
                loanContractService.generateForNewLoan(loan, user);

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

        @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','ACCOUNTANT','BUSINESS_OWNER','CREDIT_ANALYST','AUDITOR')")
        @GetMapping
        public ResponseEntity<ApiResponse<Page<LoanResponse>>> getLoans(
                        @RequestParam(defaultValue = "0") int page,

                        @RequestParam(defaultValue = "20") int size,

                        @RequestParam(required = false) String status,

                        @RequestParam(required = false) String type,

                        @RequestParam(required = false) String search) {

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
                                                                type,
                                                                search))));
        }

        // ================================================================
        // GET SINGLE LOAN
        // ================================================================

        @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','ACCOUNTANT','BUSINESS_OWNER','CREDIT_ANALYST','AUDITOR')")
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
        // LOAN CONTRACT DRAFT
        // ================================================================

        @PostMapping("/{id}/contract/generate")
        @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER')")
        public ResponseEntity<ApiResponse<Map<String, Object>>> generateLoanContractDraft(@PathVariable Long id) {
                User user = currentUserUtil.getCurrentUser();
                Long organizationId = user.getOrganization().getId();
                Loan loan = loanService.getLoanForOrg(id, organizationId);
                LoanContract contract = loanContractService.generateForNewLoan(loan, user);
                return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                                "Loan contract draft is ready for review",
                                Map.of("loanId", id, "status", contract.getStatus().name(),
                                                "fileName", contract.getFileName(),
                                                "generatedAt", contract.getGeneratedAt().toString())));
        }

        @GetMapping("/{id}/contract/status")
        @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','BUSINESS_OWNER')")
        public ResponseEntity<ApiResponse<Map<String, Object>>> getLoanContractStatus(@PathVariable Long id) {
                User user = currentUserUtil.getCurrentUser();
                Long organizationId = user.getOrganization().getId();
                loanService.getLoanForOrg(id, organizationId);
                LoanContract contract = loanContractService.getForOrganization(id, organizationId);
                return ResponseEntity.ok(ApiResponse.ok("Contract status loaded",
                                Map.of("status", contract.getStatus().name(),
                                                "fileName", contract.getFileName(),
                                                "generatedAt", contract.getGeneratedAt().toString(),
                                                "sentAt",
                                                contract.getSentAt() == null ? "" : contract.getSentAt().toString(),
                                                "borrowerNoticeQueued", contract.getBorrowerNotifiedAt() != null)));
        }

        @GetMapping("/{id}/contract/draft")
        @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','BUSINESS_OWNER')")
        public ResponseEntity<byte[]> downloadLoanContractDraft(@PathVariable Long id) {
                User user = currentUserUtil.getCurrentUser();
                Long organizationId = user.getOrganization().getId();

                // Verify tenant-scoped loan access before returning contract bytes.
                loanService.getLoanForOrg(id, organizationId);
                LoanContract contract = loanContractService.getForOrganization(id, organizationId);
                auditService.log(
                                contract.getOrganization(), user,
                                "LOAN_CONTRACT_DRAFT_DOWNLOADED", "LOAN_CONTRACT",
                                String.valueOf(contract.getId()),
                                "Downloaded draft contract for loan " + contract.getLoan().getReferenceNumber()
                                                + "; sha256=" + contract.getSha256(),
                                null, null, "Loan Contracts");
                return ResponseEntity.ok()
                                .contentType(MediaType.parseMediaType(contract.getContentType()))
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=\"" + contract.getFileName().replace("\"", "")
                                                                + "\"")
                                .header(HttpHeaders.CACHE_CONTROL, "no-store, private")
                                .body(contract.getDocumentData());
        }

        @PostMapping("/{id}/contract/sent")
        @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER')")
        public ResponseEntity<ApiResponse<Map<String, Object>>> markLoanContractSent(
                        @PathVariable Long id,
                        @RequestBody(required = false) Map<String, Boolean> body) {
                User user = currentUserUtil.getCurrentUser();
                Long organizationId = user.getOrganization().getId();
                loanService.getLoanForOrg(id, organizationId);
                boolean termsReviewed = body != null && Boolean.TRUE.equals(body.get("termsReviewed"));
                LoanContract contract = loanContractService.markSent(id, organizationId, user, termsReviewed);
                return ResponseEntity.ok(ApiResponse.ok(
                                "Contract dispatch recorded. A borrower review notice is queued by email when email delivery is enabled.",
                                Map.of(
                                                "loanId", id,
                                                "status", contract.getStatus().name(),
                                                "sentAt",
                                                contract.getSentAt() == null ? "" : contract.getSentAt().toString(),
                                                "borrowerNoticeQueued", contract.getBorrowerNotifiedAt() != null)));
        }

        /**
         * Business Owner-only reporting classification migration. This endpoint
         * changes visibility only; it does not approve, disburse, or reprice a loan.
         */
        @PatchMapping("/{id}/visibility")
        @PreAuthorize("hasRole('BUSINESS_OWNER')")
        @Transactional
        public ResponseEntity<ApiResponse<LoanResponse>> changeLoanVisibility(
                        @PathVariable Long id,
                        @RequestBody Map<String, Boolean> body) {
                if (body == null || body.get("businessOwnerOnly") == null) {
                        throw new IllegalArgumentException("businessOwnerOnly must be supplied as true or false");
                }
                User actor = currentUserUtil.getCurrentUser();
                Loan updated = loanAdministrationService.changeReportingVisibility(
                                id, body.get("businessOwnerOnly"), actor);
                return ResponseEntity.ok(ApiResponse.ok(
                                "Loan reporting visibility updated",
                                ResponseDtoMapper.loan(updated)));
        }

        // ================================================================
        // GET LOAN SCHEDULE
        // ================================================================

        @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','ACCOUNTANT','BUSINESS_OWNER','CREDIT_ANALYST','AUDITOR')")
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

        @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','ACCOUNTANT','BUSINESS_OWNER','CREDIT_ANALYST','AUDITOR')")
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

        @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','ACCOUNTANT','BUSINESS_OWNER','CREDIT_ANALYST','AUDITOR')")
        @GetMapping("/borrower/{borrowerId}")
        public ResponseEntity<ApiResponse<List<LoanResponse>>> getByBorrower(
                        @PathVariable Long borrowerId) {

                Long organizationId = currentUserUtil
                                .getCurrentOrganizationId();

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                ResponseDtoMapper.loans(
                                                                loanService.getLoansByBorrowerForOrg(
                                                                                borrowerId,
                                                                                organizationId))));
        }

        // ================================================================
        // RISK SCORE
        // ================================================================

        @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','ACCOUNTANT','BUSINESS_OWNER','CREDIT_ANALYST','AUDITOR')")
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
        @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','BUSINESS_OWNER')")
        public ResponseEntity<ApiResponse<LoanResponse>> approveLoan(
                        @PathVariable Long id,
                        @RequestBody(required = false) Map<String, Object> body) {

                User user = currentUserUtil.getCurrentUser();

                String notes = body != null
                                ? firstNonBlank(
                                                body.get("notes") == null ? null : String.valueOf(body.get("notes")),
                                                body.get("comments") == null ? null
                                                                : String.valueOf(body.get("comments")))
                                : null;

                Double newInterestRate = parseDouble(body, "interestRate",
                                "interestRate must be a valid number.");
                Double applicationFeeRate = parseDouble(body, "applicationFeeRate",
                                "applicationFeeRate must be a valid number.");
                java.math.BigDecimal approvedAmount = parseMoney(body, "approvedAmount", "amount",
                                "Approved loan amount must be a valid monetary amount.");
                Boolean businessOwnerOnly = parseBusinessOwnerOnly(body);

                var decisionRecord = loanApprovalService.decide(
                                id,
                                user,
                                "APPROVED",
                                notes,
                                newInterestRate,
                                applicationFeeRate,
                                approvedAmount,
                                businessOwnerOnly);

                /*
                 * A BUSINESS_OWNER_ONLY loan becomes hidden from NORMAL_SCOPE
                 * immediately after final approval. Do not perform a second
                 * unrestricted lookup and do not use exception handling as a
                 * control-flow mechanism. The approval record already carries
                 * the authoritative loan classification.
                 */
                if (decisionRecord != null
                                && decisionRecord.getLoan() != null
                                && Boolean.TRUE.equals(decisionRecord.getLoan().getBusinessOwnerOnly())
                                && !com.patrick.fintech.loan_backend.service.ReportingScopeService
                                                .includeBusinessOwnerOnly()) {
                        return ResponseEntity.ok(
                                        ApiResponse.ok(
                                                        "Loan approval decision recorded",
                                                        null));
                }

                Loan loan = loanService.getLoanForOrg(
                                id,
                                user.getOrganization().getId());

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                "Loan approval decision recorded",
                                                ResponseDtoMapper.loan(loan)));
        }

        private Double parseDouble(
                        Map<String, Object> body,
                        String key,
                        String errorMessage) {
                if (body == null || body.get(key) == null) {
                        return null;
                }
                String raw = String.valueOf(body.get(key)).trim();
                if (raw.isBlank()) {
                        return null;
                }
                try {
                        return Double.valueOf(raw);
                } catch (NumberFormatException ex) {
                        throw new IllegalArgumentException(errorMessage);
                }
        }

        private java.math.BigDecimal parseMoney(
                        Map<String, Object> body,
                        String primaryKey,
                        String fallbackKey,
                        String errorMessage) {
                if (body == null) {
                        return null;
                }
                Object rawObject = body.get(primaryKey);
                if (rawObject == null) {
                        rawObject = body.get(fallbackKey);
                }
                if (rawObject == null) {
                        return null;
                }
                String raw = String.valueOf(rawObject).trim();
                if (raw.isBlank()) {
                        return null;
                }
                try {
                        java.math.BigDecimal value = new java.math.BigDecimal(raw);
                        if (value.signum() <= 0) {
                                throw new IllegalArgumentException(
                                                "Approved loan amount must be greater than zero.");
                        }
                        return value.setScale(2, java.math.RoundingMode.HALF_UP);
                } catch (NumberFormatException ex) {
                        throw new IllegalArgumentException(errorMessage);
                }
        }

        private Boolean parseBusinessOwnerOnly(
                        Map<String, Object> body) {

                if (body == null) {
                        return null;
                }

                Object rawObject = body.get("businessOwnerOnly");
                if (rawObject == null) {
                        rawObject = body.get("visibility");
                }

                if (rawObject == null) {
                        return null;
                }

                String value = String.valueOf(rawObject).trim();
                if (value.isBlank()) {
                        return null;
                }

                if ("BUSINESS_OWNER_ONLY".equalsIgnoreCase(value)) {
                        return Boolean.TRUE;
                }
                if ("NORMAL".equalsIgnoreCase(value)
                                || "NORMAL_SCOPE".equalsIgnoreCase(value)) {
                        return Boolean.FALSE;
                }
                if ("true".equalsIgnoreCase(value)
                                || "false".equalsIgnoreCase(value)) {
                        return Boolean.valueOf(value);
                }

                throw new IllegalArgumentException(
                                "businessOwnerOnly must be true/false, NORMAL, or BUSINESS_OWNER_ONLY.");
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
                        @RequestBody(required = false) Map<String, String> body) {

                User user = currentUserUtil.getCurrentUser();

                String reason = body != null
                                ? firstNonBlank(
                                                body.get("reason"),
                                                body.get("comments"),
                                                body.get("notes"))
                                : null;

                if (reason == null) {
                        reason = "Rejected by authorized approver.";
                }

                loanApprovalService.decide(
                                id,
                                user,
                                "REJECTED",
                                reason);

                Loan loan = loanService.getLoanForOrg(
                                id,
                                user.getOrganization().getId());

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                "Loan rejection decision recorded",
                                                ResponseDtoMapper.loan(loan)));
        }

        @PostMapping("/{id}/disburse")
        @PreAuthorize("hasAnyRole('LOAN_OFFICER','TELLER','MANAGER','ADMIN')")
        public ResponseEntity<ApiResponse<LoanResponse>> disburseLoan(
                        @PathVariable Long id,
                        @RequestBody(required = false) Map<String, String> body,
                        @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey)
                        throws Exception {

                User user = currentUserUtil.getCurrentUser();
                Organization org = user.getOrganization();
                if (org == null)
                        throw new IllegalStateException("User is not associated with an organization.");

                String method = body != null
                                ? body.getOrDefault("disbursementMethod", "BANK_TRANSFER")
                                : "BANK_TRANSFER";

                Map<String, String> request = body == null ? Map.of("disbursementMethod", method) : body;
                var outcome = idempotencyService.checkOrReserve(
                                idempotencyKey, org, "POST /loans/" + id + "/disburse",
                                objectMapper.writeValueAsString(new java.util.TreeMap<>(request)));

                if (outcome.isReplay()) {
                        LoanResponse cached = objectMapper.readValue(outcome.cachedResponseBody(), LoanResponse.class);
                        return ResponseEntity
                                        .status(outcome.cachedStatusCode() == null ? 200 : outcome.cachedStatusCode())
                                        .body(ApiResponse.ok("Loan already disbursed", cached));
                }

                try {
                        Loan loan = loanService.disburseLoan(id, user, method);
                        LoanResponse response = ResponseDtoMapper.loan(loan);
                        idempotencyService.recordSuccess(idempotencyKey, org, response, 200);
                        return ResponseEntity.ok(ApiResponse.ok("Loan disbursed", response));
                } catch (Exception ex) {
                        idempotencyService.recordFailure(idempotencyKey, org);
                        throw ex;
                }
        }

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
                                : String.valueOf(body.get("reason"));

                Loan loan = loanRestructuringService.extendLoan(
                                id,
                                user.getOrganization().getId(),
                                user,
                                extensionMonths,
                                reason);

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                "Loan extension approved",
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

                        @RequestBody Map<String, String> body) {

                if (body == null
                                || body.get("status") == null
                                || body.get("status").isBlank()) {

                        throw new IllegalArgumentException(
                                        "status is required.");
                }

                User user = currentUserUtil.getCurrentUser();

                LoanStatus newStatus;

                try {

                        newStatus = LoanStatus.valueOf(
                                        body.get("status")
                                                        .trim()
                                                        .toUpperCase());

                } catch (IllegalArgumentException e) {

                        throw new IllegalArgumentException(
                                        "Invalid loan status: "
                                                        + body.get("status"));
                }

                Loan loan = loanService.updateStatus(
                                id,
                                user,
                                newStatus,
                                body.get("notes"));

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                "Status updated",
                                                ResponseDtoMapper.loan(loan)));
        }

        // ================================================================
        // DASHBOARD
        // ================================================================

        @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','ACCOUNTANT','BUSINESS_OWNER','CREDIT_ANALYST','AUDITOR')")
        @GetMapping("/dashboard")
        public ResponseEntity<ApiResponse<DashboardStats>> getDashboard() {
                Organization organization = currentUserUtil.getCurrentUser().getOrganization();

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                loanService.getDashboard(organization)));
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
        @DeleteMapping("/{id}")
        @PreAuthorize("hasRole('BUSINESS_OWNER')")
        public ResponseEntity<ApiResponse<Void>> deleteLoan(
                        @PathVariable Long id,
                        @RequestBody(required = false) Map<String, String> body) {

                User actor = currentUserUtil.getCurrentUser();
                String confirmation = body == null ? null : body.get("confirmation");
                String reason = body == null ? null : body.get("reason");

                loanAdministrationService.deleteWithConfirmation(
                                id,
                                confirmation,
                                reason,
                                actor);

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                "Loan moved to the recycle bin for 30 days. "
                                                                + "It is excluded from normal accounting and reporting "
                                                                + "and can be restored during the retention period."));
        }

        @GetMapping("/recycle-bin")
        @PreAuthorize("hasRole('BUSINESS_OWNER')")
        public ResponseEntity<ApiResponse<List<LoanAdministrationService.LoanRecycleBinItem>>> recycleBin() {
                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                loanAdministrationService.listRecycleBin(
                                                                currentUserUtil.getCurrentUser())));
        }

        @PostMapping("/{id}/restore")
        @PreAuthorize("hasRole('BUSINESS_OWNER')")
        public ResponseEntity<ApiResponse<Void>> restoreLoan(
                        @PathVariable Long id,
                        @RequestBody(required = false) Map<String, String> body) {

                String confirmation = body == null ? null : body.get("confirmation");

                loanAdministrationService.restoreWithConfirmation(
                                id,
                                confirmation,
                                currentUserUtil.getCurrentUser());

                return ResponseEntity.ok(
                                ApiResponse.ok("Loan restored from the recycle bin"));
        }

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

        @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER')")
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