package com.patrick.fintech.loan_backend.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.LoanRequest;
import com.patrick.fintech.loan_backend.dto.PaymentGatewayRequest;
import com.patrick.fintech.loan_backend.dto.PaymentGatewayResponse;
import com.patrick.fintech.loan_backend.dto.publicportal.BorrowerDashboardResponse;
import com.patrick.fintech.loan_backend.dto.publicportal.DashboardSummaryResponse;
import com.patrick.fintech.loan_backend.model.AuditLog;
import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.BorrowerFile;
import com.patrick.fintech.loan_backend.model.ContactMessage;
import com.patrick.fintech.loan_backend.model.DocumentType;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanComment;
import com.patrick.fintech.loan_backend.model.LoanProduct;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.model.VerificationStatus;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.ContactMessageRepository;
import com.patrick.fintech.loan_backend.repository.LoanCommentRepository;
import com.patrick.fintech.loan_backend.repository.LoanProductRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import com.patrick.fintech.loan_backend.security.HmacIndexer;
import com.patrick.fintech.loan_backend.service.AirtelMobileMoneyService;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.BorrowerFileService;
import com.patrick.fintech.loan_backend.service.FlutterwaveService;
import com.patrick.fintech.loan_backend.service.IdempotencyService;
import com.patrick.fintech.loan_backend.service.LoanService;
import com.patrick.fintech.loan_backend.service.MailService;
import com.patrick.fintech.loan_backend.service.MtnMobileMoneyService;
import com.patrick.fintech.loan_backend.service.NotificationService;
import com.patrick.fintech.loan_backend.service.PaymentService;
import com.patrick.fintech.loan_backend.service.ReportExportService;
import com.patrick.fintech.loan_backend.service.SmsService;

import jakarta.transaction.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Slf4j
public class PublicController {

        /**
         * Optional deployment-level fallback for the current public website.
         * Configure PUBLIC_TENANT_SLUG in Render instead of hardcoding a tenant.
         */
        @Value("${app.public.default-tenant-slug:}")
        private String defaultPublicTenantSlug;

        private final OrganizationRepository orgRepo;
        private final BorrowerRepository borrowerRepo;
        private final UserRepository userRepo;
        private final LoanRepository loanRepo;

        private final LoanService loanService;
        private final BorrowerFileService fileService;
        private final SmsService smsService;
        private final NotificationService notificationService;
        private final MailService mailService;
        private final AuditService auditService;

        private final ObjectMapper objectMapper;
        private final LoanProductRepository loanProductRepo;
        private final IdempotencyService idempotencyService;

        private final LoanCommentRepository loanCommentRepo;
        private final ContactMessageRepository contactMessageRepo;

        private final FlutterwaveService flutterwaveService;
        private final MtnMobileMoneyService mtnMobileMoneyService;
        private final AirtelMobileMoneyService airtelMobileMoneyService;

        private final PaymentService paymentService;
        private final PaymentRepository paymentRepo;
        private final ReportExportService exportService;

        // ============================================================
        // PLATFORM RULES
        // ============================================================

        private static final BigDecimal MIN_LOAN_AMOUNT = new BigDecimal("500000.00");

        private static final int MIN_LOAN_DURATION_MONTHS = 1;

        private static final int MAX_LOAN_DURATION_MONTHS = 6;

        private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(
                        2,
                        RoundingMode.HALF_UP);

        // ============================================================
        // CONTACT
        // ============================================================

        @PostMapping("/contact")
        public ResponseEntity<ApiResponse<String>> submitContact(
                        @RequestBody Map<String, Object> body) {

                String slug = str(
                                body.get("tenantSlug"));

                Organization org = resolveOrg(slug);

                if (org == null) {

                        throw new RuntimeException(
                                        "We couldn't identify this lender. Please refresh and try again.");
                }

                String name = str(body.get("name"));

                String subject = str(body.get("subject"));

                String message = str(body.get("message"));

                String email = str(body.get("email"));

                String phone = str(body.get("phone"));

                if (name == null || message == null) {

                        throw new RuntimeException(
                                        "Name and message are required");
                }

                contactMessageRepo.save(
                                ContactMessage.builder()
                                                .organization(org)
                                                .name(name)
                                                .email(email)
                                                .phone(phone)
                                                .subject(subject)
                                                .message(message)
                                                .build());

                List<User> staff = userRepo.findByOrganization(org)
                                .stream()
                                .filter(
                                                u -> u.getRole() != null
                                                                && Set.of(
                                                                                "ADMIN",
                                                                                "MANAGER").contains(
                                                                                                u.getRole().getName()))
                                .toList();

                notificationService.notifyUsers(
                                staff,
                                "New Contact Message: "
                                                + (subject != null
                                                                ? subject
                                                                : "General Inquiry"),
                                name
                                                + (phone != null
                                                                ? " (" + phone + ")"
                                                                : "")
                                                + (email != null
                                                                ? " <" + email + ">"
                                                                : "")
                                                + ": "
                                                + message,
                                "info",
                                "/dashboard/messages");

                auditService.log(
                                org,
                                null,
                                "PUBLIC_CONTACT_MESSAGE",
                                "ORGANIZATION",
                                org.getId().toString(),
                                "Contact form submitted by " + name);

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                "Message received — we'll get back to you within 24 hours"));
        }

        // ============================================================
        // BORROWER DASHBOARD
        // ============================================================

        @GetMapping("/borrower/dashboard")
        public ResponseEntity<BorrowerDashboardResponse> borrowerDashboard(
                        @RequestParam String reference,
                        @RequestParam String phone) {

                return ResponseEntity.ok(
                                loanService.getBorrowerDashboard(
                                                reference,
                                                phone));
        }

        @GetMapping("/borrower/summary")
        public ResponseEntity<DashboardSummaryResponse> borrowerSummary(
                        @RequestParam String reference,
                        @RequestParam String phone) {

                verifyOwnership(reference, phone);

                return ResponseEntity.ok(
                                loanService.getBorrowerSummary(
                                                phone));
        }

        // ============================================================
        // APPLICATION STATUS
        // ============================================================

        @GetMapping("/applications/{reference}/status")
        @Transactional
        public ResponseEntity<ApiResponse<Map<String, Object>>> trackApplication(
                        @PathVariable String reference,
                        @RequestParam String phone) {

                Loan loan = verifyOwnership(
                                reference,
                                phone);

                Map<String, Object> result = new LinkedHashMap<>();

                result.put(
                                "reference",
                                loan.getReferenceNumber());

                result.put(
                                "status",
                                loan.getStatus() != null
                                                ? loan.getStatus().name()
                                                : null);

                result.put(
                                "statusLabel",
                                loan.getStatus() != null
                                                ? statusLabel(loan.getStatus())
                                                : null);

                result.put(
                                "statusSteps",
                                loan.getStatus() != null
                                                ? statusSteps(loan.getStatus())
                                                : List.of());

                result.put(
                                "progressSteps",
                                progressSteps(loan));

                result.put(
                                "timeline",
                                timeline(loan));

                result.put(
                                "loanType",
                                loan.getLoanType());

                result.put(
                                "amount",
                                money(
                                                loan.getAmountDecimal()));

                result.put(
                                "principal",
                                money(
                                                loan.getAmountDecimal()));

                result.put(
                                "currency",
                                loan.getCurrency());

                result.put(
                                "submittedDate",
                                loan.getCreatedAt());

                result.put(
                                "updatedDate",
                                loan.getUpdatedAt());

                result.put(
                                "rejectionReason",
                                loan.getStatus() == LoanStatus.REJECTED
                                                ? loan.getRejectionReason()
                                                : null);

                if (loan.getBorrower() != null) {

                        result.put(
                                        "maritalStatus",
                                        loan.getBorrower().getMaritalStatus());

                        if (loan.getOrganization() != null
                                        && loan.getOrganization().getId() != null) {

                                result.put(
                                                "documentsRequired",
                                                loanService.getDocumentRequirements(
                                                                loan.getId(),
                                                                loan.getOrganization().getId()));
                        }
                }

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                result));
        }

        // ============================================================
        // APPLICATION DOCUMENT UPLOAD
        // ============================================================

        @PostMapping("/applications/{reference}/documents")
        @Transactional
        public ResponseEntity<ApiResponse<Map<String, Object>>> uploadApplicationDocument(
                        @PathVariable String reference,
                        @RequestParam String phone,
                        @RequestParam String documentType,
                        @RequestPart("file") MultipartFile file) throws Exception {

                Loan loan = verifyOwnership(
                                reference,
                                phone);

                if (loan.getBorrower() == null) {

                        throw new RuntimeException(
                                        "This application has no borrower associated with it.");
                }

                if (file == null || file.isEmpty()) {

                        throw new RuntimeException(
                                        "Please select a document to upload.");
                }

                DocumentType docType;

                try {

                        docType = DocumentType.valueOf(
                                        documentType
                                                        .trim()
                                                        .toUpperCase());

                } catch (IllegalArgumentException e) {

                        throw new RuntimeException(
                                        "Unknown document type: " + documentType);
                }

                BorrowerFile saved = fileService.upload(
                                loan.getBorrower().getId(),
                                file,
                                docType,
                                true);

                auditService.log(
                                loan.getBorrower().getOrganization(),
                                null,
                                "APPLICANT_DOCUMENT_UPLOADED",
                                "BORROWER_FILE",
                                saved.getId().toString(),
                                docType
                                                + " uploaded by applicant for application "
                                                + loan.getReferenceNumber());

                Map<String, Object> result = new LinkedHashMap<>();

                result.put(
                                "id",
                                saved.getId());

                result.put(
                                "documentType",
                                saved.getDocumentType());

                result.put(
                                "fileName",
                                saved.getFileName());

                result.put(
                                "fileSize",
                                saved.getFileSize());

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                "Document uploaded",
                                                result));
        }

        // ============================================================
        // LIST DOCUMENTS
        // ============================================================

        @GetMapping("/applications/{reference}/documents")
        @Transactional
        public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listApplicationDocuments(
                        @PathVariable String reference,
                        @RequestParam String phone) {

                Loan loan = verifyOwnership(
                                reference,
                                phone);

                if (loan.getBorrower() == null) {

                        throw new RuntimeException(
                                        "This application has no borrower associated with it.");
                }

                List<Map<String, Object>> docs = fileService
                                .getByBorrowerMetadataOnly(
                                                loan.getBorrower().getId())
                                .stream()
                                .map(
                                                f -> {

                                                        Map<String, Object> m = new LinkedHashMap<>();

                                                        m.put(
                                                                        "id",
                                                                        f.getId());

                                                        m.put(
                                                                        "documentType",
                                                                        f.getDocumentType());

                                                        m.put(
                                                                        "fileName",
                                                                        f.getFileName());

                                                        m.put(
                                                                        "fileSize",
                                                                        f.getFileSize());

                                                        m.put(
                                                                        "uploadedAt",
                                                                        f.getUploadedAt());

                                                        m.put(
                                                                        "verificationStatus",
                                                                        f.getVerificationStatus());

                                                        return m;
                                                })
                                .toList();

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                docs));
        }

        // ============================================================
        // DELETE APPLICATION DOCUMENT
        // ============================================================

        @DeleteMapping("/applications/{reference}/documents/{fileId}")
        @Transactional
        public ResponseEntity<ApiResponse<Void>> deleteApplicationDocument(
                        @PathVariable String reference,
                        @PathVariable Long fileId,
                        @RequestParam String phone) {

                Loan loan = verifyOwnership(
                                reference,
                                phone);

                if (loan.getBorrower() == null) {

                        throw new RuntimeException(
                                        "This application has no borrower associated with it.");
                }

                BorrowerFile file = fileService.getById(
                                fileId);

                if (file == null) {

                        throw new RuntimeException(
                                        "Document not found.");
                }

                if (file.getBorrower() == null
                                || file.getBorrower().getId() == null
                                || !file.getBorrower()
                                                .getId()
                                                .equals(
                                                                loan.getBorrower().getId())) {

                        throw new RuntimeException(
                                        "Document not found.");
                }

                if (!file.isUploadedByApplicant()) {

                        throw new RuntimeException(
                                        "This document was added by our staff and can't be removed here.");
                }

                if (file.getVerificationStatus() == VerificationStatus.VERIFIED) {

                        throw new RuntimeException(
                                        "This document has already been verified and can no longer be removed.");
                }

                DocumentType documentType = file.getDocumentType();

                String fileName = file.getFileName();

                fileService.delete(
                                fileId);

                auditService.log(
                                loan.getBorrower().getOrganization(),
                                null,
                                "APPLICANT_DOCUMENT_DELETED",
                                "BORROWER_FILE",
                                fileId.toString(),
                                documentType
                                                + " ("
                                                + fileName
                                                + ") removed by applicant for application "
                                                + loan.getReferenceNumber());

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                "Document removed",
                                                null));
        }

        // ============================================================
        // APPLICATION COMMENTS
        // ============================================================

        @GetMapping("/applications/{reference}/comments")
        @Transactional
        public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getApplicationComments(
                        @PathVariable String reference,
                        @RequestParam String phone) {

                Loan loan = verifyOwnership(
                                reference,
                                phone);

                List<Map<String, Object>> comments = loanCommentRepo
                                .findVisibleToApplicantByLoanId(
                                                loan.getId())
                                .stream()
                                .map(
                                                c -> {

                                                        Map<String, Object> m = new LinkedHashMap<>();

                                                        m.put(
                                                                        "message",
                                                                        c.getMessage());

                                                        m.put(
                                                                        "createdAt",
                                                                        c.getCreatedAt());

                                                        String roleLabel = c.getAuthor() != null
                                                                        && c.getAuthor().getRole() != null
                                                                                        ? humanizeRole(
                                                                                                        c.getAuthor()
                                                                                                                        .getRole()
                                                                                                                        .getName())
                                                                                        : (loan.getOrganization() != null
                                                                                                        && loan.getOrganization()
                                                                                                                        .getName() != null
                                                                                                                                        ? loan.getOrganization()
                                                                                                                                                        .getName()
                                                                                                                                        : "Our")
                                                                                                        + " Team";

                                                        m.put(
                                                                        "from",
                                                                        roleLabel);

                                                        return m;
                                                })
                                .toList();

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                comments));
        }

        private String humanizeRole(
                        String roleName) {

                if (roleName == null
                                || roleName.isBlank()) {

                        return "Our Team";
                }

                String[] parts = roleName
                                .toLowerCase()
                                .split("_");

                StringBuilder sb = new StringBuilder();

                for (String p : parts) {

                        if (!p.isBlank()) {

                                sb.append(
                                                Character.toUpperCase(
                                                                p.charAt(0)));

                                if (p.length() > 1) {

                                        sb.append(
                                                        p.substring(1));
                                }

                                sb.append(' ');
                        }
                }

                return sb
                                .toString()
                                .trim();
        }

        // ============================================================
        // PUBLIC PAYMENT INITIATION
        // ============================================================

        @PostMapping("/applications/{reference}/payments/initiate")
        @Transactional
        public ResponseEntity<ApiResponse<Map<String, Object>>> initiatePublicPayment(
                        @PathVariable String reference,
                        @RequestParam String phone,
                        @RequestBody Map<String, Object> body) {

                if (body == null) {

                        body = new LinkedHashMap<>();
                }

                Loan loan = verifyOwnership(
                                reference,
                                phone);

                if (loan.getStatus() != LoanStatus.ACTIVE
                                && loan.getStatus() != LoanStatus.OVERDUE) {

                        throw new RuntimeException(
                                        "This loan isn't currently accepting payments (status: "
                                                        + loan.getStatus()
                                                        + ").");
                }

                String method = normalizePaymentMethod(
                                str(
                                                body.get("paymentMethod")));

                if (method == null) {

                        throw new RuntimeException(
                                        "Payment method is required.");
                }

                String network = normalizeNetwork(
                                str(
                                                body.get("network")));

                if ("MOBILE_MONEY".equals(method)) {

                        if ("MTN".equals(network)) {

                                method = "MTN_MOBILE_MONEY";

                        } else if ("AIRTEL".equals(network)) {

                                method = "AIRTEL_MOBILE_MONEY";

                        } else {

                                throw new RuntimeException(
                                                "For mobile money payments, network must be MTN or AIRTEL.");
                        }
                }

                if ("MTN_MOBILE_MONEY".equals(method)) {

                        network = "MTN";

                } else if ("AIRTEL_MOBILE_MONEY".equals(method)) {

                        network = "AIRTEL";
                }

                // ========================================================
                // PAYMENT AMOUNT
                // ========================================================

                BigDecimal requestedAmount = decimal(
                                body.get("amount"));

                BigDecimal dueAmount;

                if (requestedAmount != null
                                && requestedAmount.compareTo(
                                                ZERO) > 0) {

                        dueAmount = money(
                                        requestedAmount);

                } else if (loan.getNextInstallmentAmountDecimal() != null
                                && loan.getNextInstallmentAmountDecimal()
                                                .compareTo(ZERO) > 0) {

                        dueAmount = money(
                                        loan.getNextInstallmentAmountDecimal());

                } else {

                        dueAmount = money(
                                        loan.getOutstandingBalanceDecimal());
                }

                if (dueAmount.compareTo(
                                ZERO) <= 0) {

                        throw new RuntimeException(
                                        "There's nothing due on this loan right now.");
                }

                BigDecimal outstandingBalance = money(
                                loan.getOutstandingBalanceDecimal());

                if (outstandingBalance.compareTo(ZERO) > 0
                                && dueAmount.compareTo(
                                                outstandingBalance) > 0) {

                        dueAmount = outstandingBalance;
                }

                // ========================================================
                // GATEWAY REQUEST
                // ========================================================

                PaymentGatewayRequest req = new PaymentGatewayRequest();

                /*
                 * PaymentGatewayRequest currently uses numeric gateway
                 * amount values, so convert only here.
                 */
                req.setAmount(
                                dueAmount.doubleValue());

                req.setPaymentMethod(
                                method);

                String paymentPhone = str(
                                body.get("phoneNumber"));

                if (paymentPhone == null) {

                        paymentPhone = phone;
                }

                req.setPhoneNumber(
                                paymentPhone);

                req.setNetwork(
                                network);

                String cardNumber = str(body.get("cardNumber"));
                String cardCvv = str(body.get("cardCvv"));
                String cardExpiryMonth = str(body.get("cardExpiryMonth"));
                String cardExpiryYear = str(body.get("cardExpiryYear"));

                if (cardNumber != null || cardCvv != null || cardExpiryMonth != null || cardExpiryYear != null) {
                        throw new IllegalArgumentException(
                                        "Raw card details are not accepted by the public API. Use the payment provider's hosted or tokenized checkout flow.");
                }

                req.setAccountNumber(
                                str(
                                                body.get("accountNumber")));

                req.setBankCode(
                                str(
                                                body.get("bankCode")));

                req.setEmail(
                                str(
                                                body.get("email")));

                req.setRedirectUrl(
                                str(
                                                body.get("redirectUrl")));

                String description = "Loan repayment "
                                + loan.getReferenceNumber();

                log.info(
                                "[PUBLIC PAYMENT] Payment request. loan={}, method={}, network={}, amount={}, phone={}",
                                loan.getId(),
                                method,
                                network,
                                dueAmount,
                                maskPhone(paymentPhone));

                PaymentGatewayResponse gatewayResponse;

                if ("MTN_MOBILE_MONEY".equals(method)) {

                        gatewayResponse = mtnMobileMoneyService.initiate(
                                        loan.getId(),
                                        req,
                                        dueAmount.doubleValue(),
                                        loan.getCurrency(),
                                        description);

                } else if ("AIRTEL_MOBILE_MONEY".equals(method)) {

                        gatewayResponse = airtelMobileMoneyService.initiate(
                                        loan.getId(),
                                        req,
                                        dueAmount.doubleValue(),
                                        loan.getCurrency(),
                                        description);

                } else if ("FLUTTERWAVE".equals(method)
                                || "CARD".equals(method)
                                || "BANK_TRANSFER".equals(method)
                                || "FLUTTERWAVE_MOBILE_MONEY".equals(method)) {

                        if ("FLUTTERWAVE_MOBILE_MONEY"
                                        .equals(method)) {

                                req.setPaymentMethod(
                                                "MOBILE_MONEY");
                        }

                        gatewayResponse = flutterwaveService.initiatePayment(
                                        loan.getId(),
                                        req,
                                        dueAmount.doubleValue(),
                                        loan.getCurrency(),
                                        description);

                } else {

                        throw new RuntimeException(
                                        "Unsupported payment method: "
                                                        + method);
                }

                if (gatewayResponse == null) {

                        throw new RuntimeException(
                                        "Payment provider returned no response.");
                }

                Map<String, Object> result = new LinkedHashMap<>();

                result.put(
                                "status",
                                gatewayResponse.getStatus());

                result.put(
                                "message",
                                gatewayResponse.getMessage());

                result.put(
                                "transactionId",
                                gatewayResponse.getTransactionId());

                result.put(
                                "provider",
                                gatewayResponse.getProvider());

                result.put(
                                "paymentType",
                                gatewayResponse.getPaymentType());

                BigDecimal gatewayAmount = gatewayResponse.getAmount() != null
                                ? money(
                                                BigDecimal.valueOf(
                                                                gatewayResponse.getAmount()))
                                : dueAmount;

                result.put(
                                "amount",
                                gatewayAmount);

                result.put(
                                "currency",
                                gatewayResponse.getCurrency() != null
                                                ? gatewayResponse.getCurrency()
                                                : loan.getCurrency());

                result.put(
                                "redirectUrl",
                                gatewayResponse.getRedirectUrl());

                result.put(
                                "paymentMethod",
                                method);

                result.put(
                                "network",
                                network);

                // ========================================================
                // PAYMENT CONFIRMED
                // ========================================================

                if ("success".equalsIgnoreCase(
                                gatewayResponse.getStatus())) {

                        String transactionId = gatewayResponse.getTransactionId();

                        if (transactionId == null
                                        || transactionId.isBlank()) {

                                throw new RuntimeException(
                                                "Payment provider confirmed the payment but did not return a transaction ID.");
                        }

                        BigDecimal confirmedAmount = gatewayResponse.getAmount() != null
                                        ? money(
                                                        BigDecimal.valueOf(
                                                                        gatewayResponse.getAmount()))
                                        : dueAmount;

                        if (confirmedAmount.compareTo(
                                        ZERO) <= 0) {

                                throw new RuntimeException(
                                                "The confirmed payment amount must be greater than zero.");
                        }

                        paymentService.recordPayment(
                                        loan.getId(),
                                        confirmedAmount,
                                        method,
                                        transactionId,
                                        "GATEWAY",
                                        "Paid via "
                                                        + (gatewayResponse.getProvider() != null
                                                                        ? gatewayResponse.getProvider()
                                                                        : method),
                                        null);

                        result.put(
                                        "recorded",
                                        true);

                        result.put(
                                        "transactionId",
                                        transactionId);

                        result.put(
                                        "confirmedAmount",
                                        confirmedAmount);

                        auditService.log(
                                        loan.getOrganization(),
                                        null,
                                        "PUBLIC_PAYMENT_COMPLETED",
                                        "LOAN",
                                        loan.getId().toString(),
                                        "Borrower self-service payment of "
                                                        + confirmedAmount
                                                        + " "
                                                        + loan.getCurrency()
                                                        + " completed via "
                                                        + gatewayResponse.getProvider()
                                                        + " for loan "
                                                        + loan.getReferenceNumber()
                                                        + ". Gateway transaction: "
                                                        + transactionId);

                        return ResponseEntity.ok(
                                        ApiResponse.ok(
                                                        "Payment completed",
                                                        result));
                }

                // ========================================================
                // PAYMENT PENDING
                // ========================================================

                if ("pending".equalsIgnoreCase(
                                gatewayResponse.getStatus())) {

                        result.put(
                                        "recorded",
                                        false);

                        auditService.log(
                                        loan.getOrganization(),
                                        null,
                                        "PUBLIC_PAYMENT_INITIATED",
                                        "LOAN",
                                        loan.getId().toString(),
                                        "Borrower self-service payment of "
                                                        + dueAmount
                                                        + " "
                                                        + loan.getCurrency()
                                                        + " initiated through "
                                                        + gatewayResponse.getProvider()
                                                        + " and is awaiting confirmation for loan "
                                                        + loan.getReferenceNumber());

                        return ResponseEntity.ok(
                                        ApiResponse.ok(
                                                        gatewayResponse.getMessage() != null
                                                                        ? gatewayResponse.getMessage()
                                                                        : "Payment initiated. Please confirm the payment on your phone.",
                                                        result));
                }

                // ========================================================
                // PAYMENT FAILED
                // ========================================================

                String failureMessage = gatewayResponse.getMessage() != null
                                ? gatewayResponse.getMessage()
                                : "Payment failed.";

                auditService.log(
                                loan.getOrganization(),
                                null,
                                "PUBLIC_PAYMENT_FAILED",
                                "LOAN",
                                loan.getId().toString(),
                                "Borrower payment failed through "
                                                + gatewayResponse.getProvider()
                                                + ": "
                                                + failureMessage);

                throw new RuntimeException(
                                failureMessage);
        }

        // ============================================================
        // NORMALIZE PAYMENT METHOD
        // ============================================================

        private String normalizePaymentMethod(
                        String method) {

                if (method == null
                                || method.isBlank()) {

                        return null;
                }

                String value = method
                                .trim()
                                .toUpperCase()
                                .replace(
                                                "-",
                                                "_")
                                .replace(
                                                " ",
                                                "_");

                return switch (value) {

                        case "MOBILE_MONEY",
                                        "MOBILEMONEY" ->
                                "MOBILE_MONEY";

                        case "MTN",
                                        "MOMO",
                                        "MTN_MOMO",
                                        "MTN_MOBILE",
                                        "MTN_MOBILE_MONEY",
                                        "MTN_MOBILEMONEY" ->
                                "MTN_MOBILE_MONEY";

                        case "AIRTEL",
                                        "AIRTEL_MONEY",
                                        "AIRTEL_MOBILE",
                                        "AIRTEL_MOBILE_MONEY",
                                        "AIRTEL_MOBILEMONEY" ->
                                "AIRTEL_MOBILE_MONEY";

                        case "FLW",
                                        "FLUTTERWAVE" ->
                                "FLUTTERWAVE";

                        case "FLUTTERWAVE_MOMO",
                                        "FLUTTERWAVE_MOBILE_MONEY",
                                        "FLUTTERWAVE_MOBILEMONEY" ->
                                "FLUTTERWAVE_MOBILE_MONEY";

                        case "CARD",
                                        "CREDIT_CARD",
                                        "DEBIT_CARD" ->
                                "CARD";

                        case "BANK",
                                        "BANK_TRANSFER",
                                        "BANKTRANSFER" ->
                                "BANK_TRANSFER";

                        default ->
                                value;
                };
        }

        // ============================================================
        // NORMALIZE NETWORK
        // ============================================================

        private String normalizeNetwork(
                        String network) {

                if (network == null
                                || network.isBlank()) {

                        return null;
                }

                String value = network
                                .trim()
                                .toUpperCase()
                                .replace(
                                                "-",
                                                "_")
                                .replace(
                                                " ",
                                                "_");

                return switch (value) {

                        case "MTN",
                                        "MTN_RW",
                                        "MTN_RWA",
                                        "MTN_RWA_250" ->
                                "MTN";

                        case "AIRTEL",
                                        "AIRTEL_RW",
                                        "AIRTEL_RWA",
                                        "AIRTEL_RWA_250" ->
                                "AIRTEL";

                        case "VODAFONE",
                                        "VODA" ->
                                "VODAFONE";

                        default ->
                                value;
                };
        }

        // ============================================================
        // MASK PHONE
        // ============================================================

        private String maskPhone(
                        String phone) {

                if (phone == null
                                || phone.length() < 5) {

                        return "***";
                }

                return "***"
                                + phone.substring(
                                                Math.max(
                                                                0,
                                                                phone.length() - 4));
        }

        // ============================================================
        // LOAN AGREEMENT PDF
        // ============================================================

        @GetMapping("/applications/{reference}/documents/agreement.pdf")
        @Transactional
        public ResponseEntity<byte[]> downloadAgreement(
                        @PathVariable String reference,
                        @RequestParam String phone) {

                Loan loan = verifyOwnership(
                                reference,
                                phone);

                initializePdfAssociations(
                                loan);

                byte[] pdf = exportService.toPdf(
                                "Loan Agreement",
                                List.of(
                                                "Field",
                                                "Detail"),
                                agreementRows(loan),
                                loan.getOrganization().getName());

                return pdfResponse(
                                pdf,
                                "Loan-Agreement-"
                                                + loan.getReferenceNumber());
        }

        // ============================================================
        // REPAYMENT SCHEDULE PDF
        // ============================================================

        @GetMapping("/applications/{reference}/documents/schedule.pdf")
        @Transactional
        public ResponseEntity<byte[]> downloadSchedule(
                        @PathVariable String reference,
                        @RequestParam String phone) {

                Loan loan = verifyOwnership(
                                reference,
                                phone);

                initializePdfAssociations(
                                loan);

                List<String> columns = List.of(
                                "#",
                                "Due Date",
                                "Principal",
                                "Interest",
                                "Total",
                                "Balance",
                                "Status");

                List<Map<String, Object>> rows = paymentRepo
                                .findByLoanId(
                                                loan.getId())
                                .stream()
                                .sorted(
                                                Comparator.comparing(
                                                                p -> p.getInstallmentNumber() == null
                                                                                ? Integer.MAX_VALUE
                                                                                : p.getInstallmentNumber()))
                                .map(
                                                p -> {

                                                        Map<String, Object> m = new LinkedHashMap<>();

                                                        m.put(
                                                                        "#",
                                                                        p.getInstallmentNumber());

                                                        m.put(
                                                                        "Due Date",
                                                                        p.getDueDate());

                                                        m.put(
                                                                        "Principal",
                                                                        money(
                                                                                        p.getPrincipalComponentDecimal()));

                                                        m.put(
                                                                        "Interest",
                                                                        money(
                                                                                        p.getInterestComponentDecimal()));

                                                        m.put(
                                                                        "Total",
                                                                        money(
                                                                                        p.getAmountDecimal()));

                                                        m.put(
                                                                        "Balance",
                                                                        money(
                                                                                        p.getOutstandingAfterDecimal()));

                                                        m.put(
                                                                        "Status",
                                                                        p.getStatus());

                                                        return m;
                                                })
                                .toList();

                byte[] pdf = exportService.toPdf(
                                "Repayment Schedule",
                                columns,
                                rows,
                                loan.getOrganization().getName());

                return pdfResponse(
                                pdf,
                                "Repayment-Schedule-"
                                                + loan.getReferenceNumber());
        }

        // ============================================================
        // DISBURSEMENT RECEIPT PDF
        // ============================================================

        @GetMapping("/applications/{reference}/documents/receipt.pdf")
        @Transactional
        public ResponseEntity<byte[]> downloadReceipt(
                        @PathVariable String reference,
                        @RequestParam String phone) {

                Loan loan = verifyOwnership(
                                reference,
                                phone);

                initializePdfAssociations(
                                loan);

                if (loan.getDisbursedAt() == null) {

                        throw new RuntimeException(
                                        "This loan hasn't been disbursed yet — no receipt is available.");
                }

                byte[] pdf = exportService.toPdf(
                                "Disbursement Receipt",
                                List.of(
                                                "Field",
                                                "Detail"),
                                receiptRows(loan),
                                loan.getOrganization().getName());

                return pdfResponse(
                                pdf,
                                "Disbursement-Receipt-"
                                                + loan.getReferenceNumber());
        }

        // ============================================================
        // INITIALIZE PDF ASSOCIATIONS
        // ============================================================

        private void initializePdfAssociations(
                        Loan loan) {

                if (loan.getBorrower() != null) {

                        loan.getBorrower().getId();
                        loan.getBorrower().getFullName();
                        loan.getBorrower().getOrganization();
                }

                if (loan.getOrganization() != null) {

                        loan.getOrganization().getId();
                        loan.getOrganization().getName();
                }

                if (loan.getLoanOfficer() != null) {

                        loan.getLoanOfficer().getId();
                        loan.getLoanOfficer().getFullName();
                }
        }

        // ============================================================
        // PDF RESPONSE
        // ============================================================

        private ResponseEntity<byte[]> pdfResponse(
                        byte[] bytes,
                        String filenameBase) {

                if (bytes == null
                                || bytes.length == 0) {

                        throw new RuntimeException(
                                        "The PDF could not be generated.");
                }

                return ResponseEntity.ok()
                                .contentType(
                                                MediaType.APPLICATION_PDF)
                                .header(
                                                HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=\""
                                                                + filenameBase
                                                                + ".pdf\"")
                                .body(
                                                bytes);
        }

        // ============================================================
        // AGREEMENT ROWS
        // ============================================================

        private List<Map<String, Object>> agreementRows(
                        Loan loan) {

                List<Map<String, Object>> rows = new ArrayList<>();

                java.util.function.BiConsumer<String, Object> add = (
                                k,
                                v) -> {

                        Map<String, Object> m = new LinkedHashMap<>();

                        m.put(
                                        "Field",
                                        k);

                        m.put(
                                        "Detail",
                                        v);

                        rows.add(m);
                };

                add.accept(
                                "Borrower",
                                loan.getBorrower() != null
                                                ? loan.getBorrower().getFullName()
                                                : null);

                add.accept(
                                "Reference Number",
                                loan.getReferenceNumber());

                add.accept(
                                "Loan Type",
                                loan.getLoanType());

                add.accept(
                                "Principal Amount",
                                value(
                                                loan.getCurrency())
                                                + " "
                                                + formatMoney(
                                                                loan.getAmountDecimal()));

                add.accept(
                                "Interest Rate",
                                formatRate(
                                                loan.getInterestRateDecimal())
                                                + "% per month");

                add.accept(
                                "Management Fee",
                                formatRate(
                                                loan.getManagementFeeRateDecimal())
                                                + "% per month");

                add.accept(
                                "Processing Fee",
                                value(
                                                loan.getCurrency())
                                                + " "
                                                + formatMoney(
                                                                loan.getProcessingFeeDecimal()));

                add.accept(
                                "Processing Fee Paid",
                                value(
                                                loan.getCurrency())
                                                + " "
                                                + formatMoney(
                                                                loan.getProcessingFeePaidDecimal()));

                add.accept(
                                "Net Disbursement",
                                value(
                                                loan.getCurrency())
                                                + " "
                                                + formatMoney(
                                                                calculateNetDisbursement(
                                                                                loan)));

                add.accept(
                                "Duration",
                                loan.getDurationMonths()
                                                + " months");

                add.accept(
                                "Repayment Frequency",
                                loan.getRepaymentFrequency());

                add.accept(
                                "Total Repayable",
                                value(
                                                loan.getCurrency())
                                                + " "
                                                + formatMoney(
                                                                loan.getTotalRepayableDecimal()));

                add.accept(
                                "Start Date",
                                loan.getStartDate());

                add.accept(
                                "Maturity Date",
                                loan.getMaturityDate());

                add.accept(
                                "Purpose",
                                loan.getPurpose());

                add.accept(
                                "Collateral",
                                loan.getCollateralDescription());

                add.accept(
                                "Loan Officer",
                                loan.getLoanOfficer() != null
                                                ? loan.getLoanOfficer().getFullName()
                                                : "—");

                return rows;
        }

        // ============================================================
        // RECEIPT ROWS
        // ============================================================

        private String value(String currency) {
                return currency == null || currency.isBlank()
                                ? ""
                                : currency.trim();
        }

        private List<Map<String, Object>> receiptRows(
                        Loan loan) {

                List<Map<String, Object>> rows = new ArrayList<>();

                java.util.function.BiConsumer<String, Object> add = (
                                k,
                                v) -> {

                        Map<String, Object> m = new LinkedHashMap<>();

                        m.put(
                                        "Field",
                                        k);

                        m.put(
                                        "Detail",
                                        v);

                        rows.add(m);
                };

                add.accept(
                                "Borrower",
                                loan.getBorrower() != null
                                                ? loan.getBorrower().getFullName()
                                                : null);

                add.accept(
                                "Reference Number",
                                loan.getReferenceNumber());

                add.accept(
                                "Gross Loan Amount",
                                String.valueOf(loan.getCurrency())
                                                + " "
                                                + formatMoney(loan.getAmountDecimal()));

                add.accept(
                                "Processing Fee (2%)",
                                value(loan.getCurrency())
                                                + " "
                                                + formatMoney(
                                                                loan.getProcessingFeeDecimal()));

                add.accept(
                                "Processing Fee Paid",
                                value(
                                                loan.getCurrency())
                                                + " "
                                                + formatMoney(
                                                                loan.getProcessingFeePaidDecimal()));

                add.accept(
                                "Net Amount Received",
                                value(
                                                loan.getCurrency())
                                                + " "
                                                + formatMoney(
                                                                calculateNetDisbursement(
                                                                                loan)));

                add.accept(
                                "Interest Calculation Principal",
                                value(
                                                loan.getCurrency())
                                                + " "
                                                + formatMoney(
                                                                loan.getAmountDecimal()));

                add.accept(
                                "Disbursement Date",
                                loan.getDisbursedAt());

                add.accept(
                                "Loan Officer",
                                loan.getLoanOfficer() != null
                                                ? loan.getLoanOfficer().getFullName()
                                                : "—");

                add.accept(
                                "First Installment Due",
                                loan.getNextPaymentDate());

                return rows;
        }

        // ============================================================
        // TENANT CONFIGURATION
        // ============================================================

        /**
         * Resolve the public tenant from the browser hostname.
         *
         * The frontend sends X-Tenant-Host explicitly. The Host header is a
         * server-side fallback. For the current single-tenant Vercel deployment,
         * PUBLIC_TENANT_SLUG may be configured on Render as a safe fallback.
         */
        @GetMapping("/tenant/current")
        public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentTenantConfig(
                        @RequestHeader(value = "X-Tenant-Host", required = false) String tenantHost,
                        @RequestHeader(value = "Host", required = false) String hostHeader) {

                String requestedHost = tenantHost != null && !tenantHost.isBlank()
                                ? tenantHost
                                : hostHeader;

                Organization org = resolveOrgByPublicHost(
                                requestedHost);

                if (org == null
                                && defaultPublicTenantSlug != null
                                && !defaultPublicTenantSlug.isBlank()) {

                        org = orgRepo.findBySlugIgnoreCase(
                                        defaultPublicTenantSlug.trim())
                                        .orElse(null);
                }

                if (org == null) {
                        log.warn(
                                        "Public tenant resolution failed. hostname='{}'",
                                        requestedHost);

                        return ResponseEntity
                                        .status(404)
                                        .body(
                                                        ApiResponse.error(
                                                                        "Public tenant could not be resolved for this hostname."));
                }

                return getTenantConfig(
                                org.getSlug());
        }

        @GetMapping("/tenant/{slug}")
        public ResponseEntity<ApiResponse<Map<String, Object>>> getTenantConfig(
                        @PathVariable String slug) {

                Organization org = resolveOrg(slug);

                if (org == null) {

                        return ResponseEntity
                                        .status(404)
                                        .build();
                }

                Map<String, Object> config = new LinkedHashMap<>();

                config.put(
                                "id",
                                org.getId());

                config.put(
                                "name",
                                org.getName());

                config.put(
                                "slug",
                                slug);

                config.put(
                                "country",
                                org.getCountry());

                config.put(
                                "currency",
                                org.getDefaultCurrency());

                config.put(
                                "primaryColor",
                                org.getPrimaryColor() != null
                                                ? org.getPrimaryColor()
                                                : "#0D6B3E");

                config.put(
                                "accentColor",
                                org.getAccentColor() != null
                                                ? org.getAccentColor()
                                                : "#F5A623");

                config.put(
                                "logoUrl",
                                org.getLogoUrl());

                config.put(
                                "contactEmail",
                                org.getContactEmail());

                config.put(
                                "contactPhone",
                                org.getContactPhone());

                config.put(
                                "address",
                                org.getAddress());

                config.put(
                                "website",
                                org.getWebsite());

                config.put(
                                "registrationNumber",
                                org.getRegistrationNumber());

                List<Map<String, Object>> publicServices = servicesFor(org);

                BigDecimal configuredMinimum = org.getMinLoanAmountDecimal() != null
                                ? org.getMinLoanAmountDecimal()
                                : null;

                BigDecimal configuredMaximum = org.getMaxLoanAmountDecimal() != null
                                ? org.getMaxLoanAmountDecimal()
                                : null;

                if (configuredMinimum == null && !publicServices.isEmpty()) {
                        configuredMinimum = publicServices.stream()
                                        .map(item -> decimalFromObject(item.get("minAmount")))
                                        .filter(Objects::nonNull)
                                        .min(BigDecimal::compareTo)
                                        .orElse(null);
                }

                if (configuredMaximum == null && !publicServices.isEmpty()) {
                        configuredMaximum = publicServices.stream()
                                        .map(item -> decimalFromObject(item.get("maxAmount")))
                                        .filter(Objects::nonNull)
                                        .max(BigDecimal::compareTo)
                                        .orElse(null);
                }

                config.put(
                                "minLoanAmount",
                                configuredMinimum);

                config.put(
                                "maxLoanAmount",
                                configuredMaximum);

                config.put(
                                "status",
                                org.getStatus());

                config.put(
                                "tagline",
                                org.getTagline());

                config.put(
                                "mission",
                                org.getMission());

                config.put(
                                "vision",
                                org.getVision());

                config.put(
                                "founded",
                                org.getFoundedYear() != null
                                                ? org.getFoundedYear().toString()
                                                : null);

                config.put(
                                "mapUrl",
                                org.getMapUrl());

                Map<String, String> social = new LinkedHashMap<>();

                if (org.getFacebookUrl() != null) {

                        social.put(
                                        "facebook",
                                        org.getFacebookUrl());
                }

                if (org.getInstagramUrl() != null) {

                        social.put(
                                        "instagram",
                                        org.getInstagramUrl());
                }

                if (org.getLinkedinUrl() != null) {

                        social.put(
                                        "linkedin",
                                        org.getLinkedinUrl());
                }

                if (org.getTwitterUrl() != null) {

                        social.put(
                                        "twitter",
                                        org.getTwitterUrl());
                }

                if (org.getWhatsappUrl() != null) {

                        social.put(
                                        "whatsapp",
                                        org.getWhatsappUrl());
                }

                config.put(
                                "socialMedia",
                                social);

                config.put(
                                "services",
                                publicServices);

                Map<String, Object> hero = new LinkedHashMap<>();
                hero.put(
                                "headline",
                                org.getHeroHeadline() != null
                                                ? org.getHeroHeadline()
                                                : "");
                hero.put(
                                "subtext",
                                org.getHeroSubtext() != null
                                                ? org.getHeroSubtext()
                                                : "");
                config.put(
                                "hero",
                                hero);

                config.put(
                                "stats",
                                parseListOrDefault(
                                                org.getStatsJson(),
                                                List.of()));

                config.put(
                                "testimonials",
                                parseListOrDefault(
                                                org.getTestimonialsJson(),
                                                List.of()));

                config.put(
                                "team",
                                parseListOrDefault(
                                                org.getTeamJson(),
                                                List.of()));

                config.put(
                                "paymentMethods",
                                paymentMethods());

                BigDecimal primaryInterestRate = publicServices.stream()
                                .map(item -> decimalFromObject(item.get("monthlyInterestRate")))
                                .filter(Objects::nonNull)
                                .findFirst()
                                .orElse(null);

                BigDecimal primaryManagementFeeRate = publicServices.stream()
                                .map(item -> decimalFromObject(item.get("monthlyManagementFeeRate")))
                                .filter(Objects::nonNull)
                                .findFirst()
                                .orElse(null);

                BigDecimal primaryProcessingFeeRate = publicServices.stream()
                                .map(item -> decimalFromObject(item.get("processingFeeRate")))
                                .filter(Objects::nonNull)
                                .findFirst()
                                .orElse(null);

                Integer minimumTerm = publicServices.stream()
                                .map(item -> integerFromObject(item.get("minTermMonths")))
                                .filter(Objects::nonNull)
                                .min(Integer::compareTo)
                                .orElse(MIN_LOAN_DURATION_MONTHS);

                Integer maximumTerm = publicServices.stream()
                                .map(item -> integerFromObject(item.get("maxTermMonths")))
                                .filter(Objects::nonNull)
                                .max(Integer::compareTo)
                                .orElse(MAX_LOAN_DURATION_MONTHS);

                config.put(
                                "monthlyInterestRate",
                                primaryInterestRate);

                config.put(
                                "monthlyManagementFeeRate",
                                primaryManagementFeeRate);

                config.put(
                                "processingFeeRate",
                                primaryProcessingFeeRate);

                config.put(
                                "minLoanDurationMonths",
                                minimumTerm);

                config.put(
                                "maxLoanDurationMonths",
                                maximumTerm);

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                config));
        }

        // ============================================================
        // PAYMENT METHODS
        // ============================================================

        private List<Map<String, Object>> paymentMethods() {

                List<Map<String, Object>> methods = new ArrayList<>();

                Map<String, Object> mobileMoney = new LinkedHashMap<>();

                mobileMoney.put(
                                "id",
                                "MOBILE_MONEY");

                mobileMoney.put(
                                "name",
                                "Mobile Money");

                mobileMoney.put(
                                "shortName",
                                "Mobile Money");

                mobileMoney.put(
                                "type",
                                "MOBILE_MONEY");

                mobileMoney.put(
                                "provider",
                                "MTN_AIRTEL");

                boolean mtnAvailable = mtnMobileMoneyService.isAvailable();

                boolean airtelAvailable = airtelMobileMoneyService.isConfigured();

                mobileMoney.put(
                                "available",
                                mtnAvailable
                                                || airtelAvailable);

                mobileMoney.put(
                                "networks",
                                List.of(
                                                Map.of(
                                                                "id",
                                                                "MTN",
                                                                "name",
                                                                "MTN Mobile Money",
                                                                "available",
                                                                mtnAvailable),
                                                Map.of(
                                                                "id",
                                                                "AIRTEL",
                                                                "name",
                                                                "Airtel Money",
                                                                "available",
                                                                airtelAvailable)));

                methods.add(
                                mobileMoney);

                Map<String, Object> mtn = new LinkedHashMap<>();

                mtn.put(
                                "id",
                                "MTN_MOBILE_MONEY");

                mtn.put(
                                "name",
                                "MTN Mobile Money");

                mtn.put(
                                "shortName",
                                "MTN MoMo");

                mtn.put(
                                "type",
                                "MOBILE_MONEY");

                mtn.put(
                                "provider",
                                "MTN");

                mtn.put(
                                "available",
                                mtnAvailable);

                methods.add(
                                mtn);

                Map<String, Object> airtel = new LinkedHashMap<>();

                airtel.put(
                                "id",
                                "AIRTEL_MOBILE_MONEY");

                airtel.put(
                                "name",
                                "Airtel Money");

                airtel.put(
                                "shortName",
                                "Airtel Money");

                airtel.put(
                                "type",
                                "MOBILE_MONEY");

                airtel.put(
                                "provider",
                                "AIRTEL");

                airtel.put(
                                "available",
                                airtelAvailable);

                methods.add(
                                airtel);

                Map<String, Object> flutterwave = new LinkedHashMap<>();

                flutterwave.put(
                                "id",
                                "FLUTTERWAVE");

                flutterwave.put(
                                "name",
                                "Flutterwave");

                flutterwave.put(
                                "shortName",
                                "Flutterwave");

                flutterwave.put(
                                "type",
                                "GATEWAY");

                flutterwave.put(
                                "provider",
                                "FLUTTERWAVE");

                flutterwave.put(
                                "available",
                                flutterwaveService.isConfigured());

                methods.add(
                                flutterwave);

                return methods;
        }

        // ============================================================
        // LOAN APPLICATION
        // ============================================================

        @PostMapping("/loan-application")
        @Transactional
        public ResponseEntity<ApiResponse<Map<String, Object>>> submitApplication(
                        @RequestBody Map<String, Object> body,
                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

                if (body == null) {

                        throw new IllegalArgumentException(
                                        "Application body is required");
                }

                String slug = str(
                                body.get("tenantSlug"));

                if (idempotencyKey == null
                                || idempotencyKey.isBlank()) {

                        throw new IllegalArgumentException(
                                        "Idempotency-Key header is required for public loan applications");
                }

                Organization org = resolveOrg(slug);

                if (org == null) {

                        throw new RuntimeException(
                                        "We couldn't identify this lender. Please refresh the page and try again.");
                }

                var idempotency = idempotencyService.checkOrReserve(
                                idempotencyKey,
                                org,
                                "POST /public/loan-application",
                                body.toString());

                if (idempotency.isReplay()) {

                        return ResponseEntity.ok(
                                        ApiResponse.ok(
                                                        "Application received",
                                                        Map.of(
                                                                        "status",
                                                                        "RECEIVED",
                                                                        "message",
                                                                        "Already submitted")));
                }

                String phone = str(body.get("phone"));

                if (phone == null
                                || phone.isBlank()
                                || phone.trim().length() > 40) {

                        throw new RuntimeException(
                                        "Phone number is required");
                }

                String firstName = str(
                                body.get("firstName"));

                if (firstName == null
                                || firstName.isBlank()) {

                        throw new RuntimeException(
                                        "First name is required");
                }

                BigDecimal amount = decimal(
                                body.get("amount"));

                if (amount == null
                                || amount.compareTo(
                                                ZERO) <= 0) {

                        throw new RuntimeException(
                                        "Loan amount is required");
                }

                amount = money(amount);

                /*
                 * Product-specific minimum amount.
                 * The backend LoanService remains the final authority.
                 */
                BigDecimal publicConfiguredMinimum = org.getMinLoanAmountDecimal() != null
                                ? org.getMinLoanAmountDecimal()
                                : MIN_LOAN_AMOUNT;

                if (amount.compareTo(
                                publicConfiguredMinimum) < 0) {

                        throw new RuntimeException(
                                        "Minimum loan amount is "
                                                        + formatMoney(
                                                                        publicConfiguredMinimum));
                }

                String inputEmail = str(
                                body.get("email"));

                if (inputEmail == null
                                || inputEmail.isBlank()) {

                        throw new RuntimeException(
                                        "Email is required");
                }

                String gender = str(
                                body.get("gender"));

                if (gender == null
                                || gender.isBlank()) {

                        throw new RuntimeException(
                                        "Gender is required");
                }

                String maritalStatus = str(
                                body.get("maritalStatus"));

                if (maritalStatus == null
                                || maritalStatus.isBlank()) {

                        throw new RuntimeException(
                                        "Marital status is required");
                }

                String nationalId = str(
                                body.get("nationalId"));

                if (nationalId == null
                                || !nationalId
                                                .trim()
                                                .matches(
                                                                "\\d{16}")) {

                        throw new RuntimeException(
                                        "National ID is required and must be exactly 16 digits");
                }

                nationalId = nationalId.trim();

                boolean acceptedTerms = body.get("acceptedTerms") != null
                                && Boolean.parseBoolean(
                                                body.get(
                                                                "acceptedTerms").toString());

                if (!acceptedTerms) {

                        throw new RuntimeException(
                                        "You must accept the Terms & Conditions to submit an application");
                }

                if ("Married".equalsIgnoreCase(
                                maritalStatus)
                                && str(
                                                body.get(
                                                                "spouseFullName")) == null) {

                        throw new RuntimeException(
                                        "Spouse's full name is required for married applicants");
                }

                if ("Single".equalsIgnoreCase(
                                maritalStatus)
                                && str(
                                                body.get(
                                                                "singleCertificateNumber")) == null) {

                        throw new RuntimeException(
                                        "Single Status Certificate number is required for single applicants");
                }

                Borrower borrower = borrowerRepo
                                .findByPhoneHashAndOrganization_Id(
                                                HmacIndexer.index(
                                                                phone),
                                                org.getId())
                                .orElseGet(
                                                () -> Borrower.builder()
                                                                .organization(org)
                                                                .build());

                borrower.setFirstName(
                                firstName);

                borrower.setLastName(
                                str(
                                                body.get(
                                                                "lastName")));

                borrower.setPhone(
                                phone);

                borrower.setEmail(
                                inputEmail.trim());

                borrower.setNationalId(
                                nationalId);

                borrower.setDateOfBirth(
                                date(
                                                body.get(
                                                                "dateOfBirth")));

                borrower.setGender(
                                gender);

                borrower.setMaritalStatus(
                                maritalStatus);

                borrower.setSingleCertificateNumber(
                                str(
                                                body.get(
                                                                "singleCertificateNumber")));

                borrower.setSpouseFullName(
                                str(
                                                body.get(
                                                                "spouseFullName")));

                borrower.setSpouseNationalId(
                                str(
                                                body.get(
                                                                "spouseNationalId")));

                borrower.setSpousePhone(
                                str(
                                                body.get(
                                                                "spousePhone")));

                borrower.setSpouseConsent(
                                body.get(
                                                "spouseConsent") != null
                                                                ? Boolean.parseBoolean(
                                                                                body.get(
                                                                                                "spouseConsent")
                                                                                                .toString())
                                                                : null);

                borrower.setAddress(
                                str(
                                                body.get(
                                                                "address")));

                borrower.setAddressLine1(
                                str(
                                                body.get(
                                                                "address")));

                borrower.setCity(
                                str(
                                                body.get(
                                                                "city")));

                borrower.setStateProvince(
                                str(
                                                body.get(
                                                                "province")));

                borrower.setCountry(
                                org.getCountry());

                borrower.setEmploymentType(
                                str(
                                                body.get(
                                                                "employmentType")));

                borrower.setEmployerName(
                                str(
                                                body.get(
                                                                "employerName")));

                borrower.setJobTitle(
                                str(
                                                body.get(
                                                                "jobTitle")));

                borrower.setMonthlyIncome(
                                num(
                                                body.get(
                                                                "monthlyIncome")));

                borrower.setMonthlyExpenses(
                                num(
                                                body.get(
                                                                "monthlyExpenses")));

                borrower = borrowerRepo.save(
                                borrower);

                // ========================================================
                // PRODUCT-SPECIFIC DURATION RULE
                // ========================================================

                Loan.LoanType loanType = mapLoanType(
                                str(body.get("loanType")));

                LoanProduct publicProduct = loanProductRepo
                                .findFirstByOrganization_IdAndLoanTypeAndActiveTrue(
                                                org.getId(),
                                                loanType)
                                .orElseThrow(() -> new RuntimeException(
                                                "This lender has not configured an active product for "
                                                                + loanType));

                int months = publicProduct.getMinTermMonths() != null
                                ? publicProduct.getMinTermMonths()
                                : MIN_LOAN_DURATION_MONTHS;

                if (body.get("durationMonths") != null) {
                        try {
                                months = Integer.parseInt(
                                                body.get("durationMonths").toString());
                        } catch (NumberFormatException e) {
                                throw new RuntimeException(
                                                "Duration must be a valid number of months.");
                        }
                }

                int productMinMonths = publicProduct.getMinTermMonths() != null
                                ? publicProduct.getMinTermMonths()
                                : MIN_LOAN_DURATION_MONTHS;

                int productMaxMonths = publicProduct.getMaxTermMonths() != null
                                ? publicProduct.getMaxTermMonths()
                                : MAX_LOAN_DURATION_MONTHS;

                if (months < productMinMonths
                                || months > productMaxMonths) {

                        throw new RuntimeException(
                                        "Loan duration must be between "
                                                        + productMinMonths
                                                        + " and "
                                                        + productMaxMonths
                                                        + " months for this product.");
                }

                BigDecimal collateralValue = decimal(
                                body.get(
                                                "collateralValue"));

                /*
                 * The platform currently supports monthly repayment.
                 */
                Loan.RepaymentFrequency repaymentFrequency = Loan.RepaymentFrequency.MONTHLY;

                /*
                 * Public applications do not accept client-selected rates.
                 * Pricing comes exclusively from the organization's active
                 * LoanProduct and is snapshotted by LoanService.
                 */
                BigDecimal interestRate = publicProduct.getInterestRateDecimal();

                String interestRateType = publicProduct.getInterestRateType() != null
                                ? publicProduct.getInterestRateType()
                                : "MONTHLY";

                LoanRequest req = LoanRequest.builder()
                                .borrowerId(
                                                borrower.getId())
                                .amount(
                                                amount)
                                .interestRate(
                                                interestRate)
                                .interestRateType(
                                                interestRateType)
                                .durationMonths(
                                                months)
                                .currency(
                                                org.getDefaultCurrency())
                                .purpose(
                                                str(
                                                                body.get(
                                                                                "purpose")))
                                .notes(
                                                "Submitted via public website")
                                .collateralValue(
                                                collateralValue)
                                .collateralDescription(
                                                str(
                                                                body.get(
                                                                                "collateral")))
                                .loanType(
                                                loanType)
                                .repaymentFrequency(
                                                repaymentFrequency)
                                .disbursementMethod(
                                                str(
                                                                body.get(
                                                                                "disbursementMethod")))
                                .disbursementAccount(
                                                str(
                                                                body.get(
                                                                                "disbursementAccount")))
                                .startDate(
                                                LocalDate.now()
                                                                .toString())
                                .build();

                Loan loan = loanService.createLoan(
                                req,
                                org.getId(),
                                null);

                /*
                 * LoanService resolves and snapshots the selected
                 * organization's active LoanProduct pricing.
                 */
                loan.setTermsAcceptedAt(
                                LocalDateTime.now());

                loan = loanRepo.save(
                                loan);

                notifyStaff(
                                org,
                                borrower,
                                loan);

                try {

                        mailService.sendApplicationReceived(
                                        loan);

                } catch (Exception e) {

                        log.error(
                                        "Application email failed: {}",
                                        e.getMessage());
                }

                try {

                        smsService.sendCustom(
                                        phone,
                                        String.format(
                                                        "%s: Thank you %s! We received your loan application %s for %s %s. Terms: %s%% monthly interest, %s%% monthly management fee, %s%% processing fee.",
                                                        org.getName(),
                                                        firstName,
                                                        loan.getReferenceNumber(),
                                                        loan.getCurrency(),
                                                        formatMoney(amount),
                                                        loan.getInterestRateDecimal(),
                                                        loan.getManagementFeeRateDecimal(),
                                                        loan.getProcessingFeeRateDecimal()));

                } catch (Exception e) {

                        log.warn(
                                        "Application confirmation SMS failed: {}",
                                        e.getMessage());
                }

                auditService.log(
                                org,
                                null,
                                "PUBLIC_LOAN_APPLICATION",
                                "LOAN",
                                loan.getId().toString(),
                                "Online application submitted by "
                                                + borrower.getFullName()
                                                + " via "
                                                + org.getName()
                                                + " website");

                ApiResponse<Map<String, Object>> response = ApiResponse.ok(
                                "Application received",
                                Map.of(
                                                "reference",
                                                loan.getReferenceNumber(),

                                                "loanId",
                                                loan.getId(),

                                                "message",
                                                "We will contact you within 24-48 hours",

                                                "status",
                                                "RECEIVED",

                                                "monthlyInterestRate",
                                                loan.getInterestRateDecimal(),

                                                "monthlyManagementFeeRate",
                                                loan.getManagementFeeRateDecimal(),

                                                "processingFeeRate",
                                                loan.getProcessingFeeRateDecimal()));

                idempotencyService.recordSuccess(
                                idempotencyKey,
                                org,
                                response,
                                200);

                return ResponseEntity.ok(
                                response);
        }

        // ============================================================
        // PRODUCTS
        // ============================================================

        @GetMapping("/tenant/{slug}/products")
        public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getProducts(
                        @PathVariable String slug) {

                Organization org = resolveOrg(slug);

                if (org == null) {
                        return ResponseEntity
                                        .status(404)
                                        .build();
                }

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                servicesFor(org)));
        }

        private List<Map<String, Object>> servicesFor(
                        Organization org) {

                if (org == null || org.getId() == null) {
                        return List.of();
                }

                List<LoanProduct> products = loanProductRepo
                                .findByOrganization_IdAndActiveTrueOrderByDisplayOrderAsc(
                                                org.getId());

                if (products == null || products.isEmpty()) {
                        return parseListOrDefault(
                                        org.getServicesJson(),
                                        List.of());
                }

                return products
                                .stream()
                                .filter(Objects::nonNull)
                                .map(p -> {
                                        Map<String, Object> m = new LinkedHashMap<>();

                                        BigDecimal interest = p.getInterestRateDecimal();
                                        BigDecimal management = p.getManagementFeePercentDecimal();
                                        BigDecimal processing = p.getProcessingFeePercentDecimal();
                                        BigDecimal minAmount = p.getMinAmountDecimal();
                                        BigDecimal maxAmount = p.getMaxAmountDecimal();

                                        Integer minTerm = p.getMinTermMonths();
                                        Integer maxTerm = p.getMaxTermMonths();

                                        m.put("id", p.getId());
                                        m.put("title", p.getName());
                                        m.put("icon", p.getIcon());
                                        m.put("loanType", p.getLoanType());
                                        m.put("rate", interest != null
                                                        ? interest + "% / "
                                                                        + (p.getInterestRateType() == null ? "month"
                                                                                        : p.getInterestRateType()
                                                                                                        .toLowerCase())
                                                        : null);
                                        m.put("monthlyInterestRate", interest);
                                        m.put("monthlyManagementFeeRate", management);
                                        m.put("processingFeeRate", processing);
                                        m.put("minAmount", minAmount);
                                        m.put("maxAmount", maxAmount);
                                        m.put("minTermMonths", minTerm);
                                        m.put("maxTermMonths", maxTerm);
                                        m.put("maxAmountLabel",
                                                        maxAmount == null ? "Unlimited" : maxAmount.toPlainString());
                                        m.put("term", minTerm != null && maxTerm != null
                                                        ? minTerm + " to " + maxTerm + " months"
                                                        : null);
                                        m.put("description", p.getDescription());
                                        m.put("displayOrder", p.getDisplayOrder());

                                        return m;
                                })
                                .toList();
        }

        private BigDecimal decimalFromObject(Object value) {
                if (value == null) {
                        return null;
                }
                try {
                        return new BigDecimal(String.valueOf(value));
                } catch (NumberFormatException ex) {
                        return null;
                }
        }

        private Integer integerFromObject(Object value) {
                if (value == null) {
                        return null;
                }
                try {
                        return Integer.valueOf(String.valueOf(value));
                } catch (NumberFormatException ex) {
                        return null;
                }
        }

        private List<Map<String, Object>> parseListOrDefault(
                        String json,
                        List<Map<String, Object>> fallback) {

                if (json == null
                                || json.isBlank()) {

                        return fallback;
                }

                try {

                        List<Map<String, Object>> parsed = objectMapper.readValue(
                                        json,
                                        new TypeReference<List<Map<String, Object>>>() {
                                        });

                        return parsed == null
                                        || parsed.isEmpty()
                                                        ? fallback
                                                        : parsed;

                } catch (Exception e) {

                        log.warn(
                                        "Could not parse CMS JSON: {}",
                                        e.getMessage());

                        return fallback;
                }
        }

        // ============================================================
        // STATUS
        // ============================================================

        private String statusLabel(
                        LoanStatus status) {

                if (status == null) {
                        return "Unknown";
                }

                return switch (status) {

                        case PENDING,
                                        UNDER_REVIEW ->
                                "Under Review";

                        case APPROVED ->
                                "Approved — awaiting disbursement";

                        case REJECTED ->
                                "Not Approved";

                        case DISBURSED,
                                        ACTIVE ->
                                "Active — funds disbursed";

                        case OVERDUE ->
                                "Active — payment overdue";

                        case DEFAULTED ->
                                "In Default";

                        case RESTRUCTURED ->
                                "Restructured";

                        case WRITTEN_OFF ->
                                "Written Off";

                        case PAID,
                                        CLOSED ->
                                "Completed";

                        case CANCELLED ->
                                "Cancelled";
                };
        }

        private List<Map<String, Object>> statusSteps(
                        LoanStatus status) {

                int stage = switch (status) {

                        case PENDING,
                                        UNDER_REVIEW ->
                                1;

                        case APPROVED ->
                                2;

                        case REJECTED,
                                        CANCELLED ->
                                -1;

                        default ->
                                3;
                };

                String[] labels = {

                                "Application Received",
                                "Under Review",
                                "Decision Made",
                                "Funds Disbursed"
                };

                List<Map<String, Object>> steps = new ArrayList<>();

                for (int i = 0; i < labels.length; i++) {

                        Map<String, Object> s = new LinkedHashMap<>();

                        s.put(
                                        "label",
                                        labels[i]);

                        s.put(
                                        "complete",
                                        stage >= 0
                                                        && i <= stage);

                        s.put(
                                        "failed",
                                        stage == -1
                                                        && i == 1);

                        steps.add(
                                        s);
                }

                return steps;
        }

        private List<Map<String, Object>> progressSteps(
                        Loan loan) {

                LoanStatus status = loan.getStatus();

                boolean failedApplication = status == LoanStatus.REJECTED
                                || status == LoanStatus.CANCELLED;

                boolean docsUploaded = false;
                boolean docsVerified = false;

                if (loan.getBorrower() != null
                                && loan.getOrganization() != null
                                && loan.getOrganization().getId() != null) {

                        Map<String, Object> docReq = loanService.getDocumentRequirements(
                                        loan.getId(),
                                        loan.getOrganization().getId());

                        docsUploaded = Boolean.TRUE.equals(
                                        docReq.get(
                                                        "readyToApprove"));

                        docsVerified = Boolean.TRUE.equals(
                                        docReq.get(
                                                        "readyToDisburse"));
                }

                boolean underReview = status != LoanStatus.PENDING;

                boolean creditAssessed = loan.getRiskScore() != null;

                boolean approved = loan.getApprovedAt() != null;

                boolean disbursed = loan.getDisbursedAt() != null;

                boolean closed = status == LoanStatus.PAID
                                || status == LoanStatus.CLOSED;

                String[] labels = {

                                "Application Submitted",
                                "Documents Uploaded",
                                "Documents Verified",
                                "Under Review",
                                "Credit Assessment",
                                "Loan Approved",
                                "Loan Disbursed",
                                "Loan Active",
                                "Loan Closed"
                };

                boolean[] complete = {

                                true,
                                docsUploaded,
                                docsVerified,
                                underReview,
                                creditAssessed,
                                approved,
                                disbursed,
                                disbursed,
                                closed
                };

                List<Map<String, Object>> steps = new ArrayList<>();

                boolean failurePlaced = false;

                for (int i = 0; i < labels.length; i++) {

                        Map<String, Object> s = new LinkedHashMap<>();

                        s.put(
                                        "label",
                                        labels[i]);

                        boolean isFailurePoint = failedApplication
                                        && !failurePlaced
                                        && !complete[i];

                        s.put(
                                        "complete",
                                        complete[i]
                                                        && !failedApplication);

                        s.put(
                                        "failed",
                                        isFailurePoint);

                        if (isFailurePoint) {

                                failurePlaced = true;
                        }

                        steps.add(
                                        s);
                }

                return steps;
        }

        // ============================================================
        // TIMELINE
        // ============================================================

        private List<Map<String, Object>> timeline(
                        Loan loan) {

                List<Map.Entry<LocalDateTime, String>> raw = new ArrayList<>();

                if (loan.getCreatedAt() != null) {

                        raw.add(
                                        Map.entry(
                                                        loan.getCreatedAt(),
                                                        "Application submitted"));
                }

                if (loan.getBorrower() != null) {

                        List<BorrowerFile> files = fileService.getByBorrowerMetadataOnly(
                                        loan.getBorrower().getId());

                        files.stream()
                                        .map(
                                                        BorrowerFile::getUploadedAt)
                                        .filter(
                                                        Objects::nonNull)
                                        .min(
                                                        Comparator.naturalOrder())
                                        .ifPresent(
                                                        d -> raw.add(
                                                                        Map.entry(
                                                                                        d,
                                                                                        "Documents uploaded")));

                        files.stream()
                                        .map(
                                                        BorrowerFile::getVerifiedAt)
                                        .filter(
                                                        Objects::nonNull)
                                        .max(
                                                        Comparator.naturalOrder())
                                        .ifPresent(
                                                        d -> raw.add(
                                                                        Map.entry(
                                                                                        d,
                                                                                        "Documents verified")));
                }

                if (loan.getApprovedAt() != null) {

                        raw.add(
                                        Map.entry(
                                                        loan.getApprovedAt()
                                                                        .atStartOfDay(),
                                                        "Loan approved"));
                }

                if (loan.getStatus() == LoanStatus.REJECTED
                                && loan.getUpdatedAt() != null) {

                        raw.add(
                                        Map.entry(
                                                        loan.getUpdatedAt(),
                                                        "Application not approved"));
                }

                if (loan.getDisbursedAt() != null) {

                        raw.add(
                                        Map.entry(
                                                        loan.getDisbursedAt(),
                                                        "Loan disbursed"));
                }

                return raw.stream()
                                .sorted(
                                                Map.Entry.comparingByKey())
                                .map(
                                                e -> {

                                                        Map<String, Object> m = new LinkedHashMap<>();

                                                        m.put(
                                                                        "date",
                                                                        e.getKey());

                                                        m.put(
                                                                        "label",
                                                                        e.getValue());

                                                        return m;
                                                })
                                .toList();
        }

        // ============================================================
        // TENANT RESOLUTION
        // ============================================================

        /**
         * Resolve a tenant by the public hostname.
         *
         * publicDomain is the indexed production mapping. The organization
         * website hostname is retained as a compatibility fallback for tenants
         * that predate the publicDomain field.
         */
        private Organization resolveOrgByPublicHost(
                        String rawHost) {

                String host = normalizePublicHost(rawHost);

                if (host.isBlank()) {
                        return null;
                }

                Organization byPublicDomain = orgRepo
                                .findByPublicDomainIgnoreCase(host)
                                .orElse(null);

                if (byPublicDomain != null) {
                        return byPublicDomain;
                }

                List<Organization> websiteMatches = orgRepo.findAll()
                                .stream()
                                .filter(org -> host.equals(
                                                normalizePublicHost(org.getWebsite())))
                                .toList();

                if (websiteMatches.size() > 1) {
                        log.error(
                                        "Ambiguous public website hostname '{}' maps to {} organizations",
                                        host,
                                        websiteMatches.size());
                        return null;
                }

                return websiteMatches.isEmpty()
                                ? null
                                : websiteMatches.get(0);
        }

        private String normalizePublicHost(
                        String rawHost) {

                if (rawHost == null || rawHost.isBlank()) {
                        return "";
                }

                try {
                        java.net.URI uri = new java.net.URI(
                                        rawHost.contains("://")
                                                        ? rawHost.trim()
                                                        : "https://" + rawHost.trim());

                        String host = uri.getHost();

                        if (host == null || host.isBlank()) {
                                return "";
                        }

                        return host
                                        .trim()
                                        .toLowerCase()
                                        .replaceFirst("^www\\.", "");

                } catch (Exception e) {
                        log.debug(
                                        "Unable to normalize public tenant host '{}'",
                                        rawHost);
                        return "";
                }
        }

        private Organization resolveOrg(
                        String slug) {

                if (slug == null
                                || slug.isBlank()) {

                        return null;
                }

                String normalized = normalizeTenantKey(slug);

                List<Organization> matches = orgRepo.findAll()
                                .stream()
                                .filter(o -> {
                                        String name = o.getName() == null
                                                        ? ""
                                                        : normalizeTenantKey(o.getName());
                                        String registration = o.getRegistrationNumber() == null
                                                        ? ""
                                                        : normalizeTenantKey(o.getRegistrationNumber());
                                        return name.equals(normalized) || registration.equals(normalized);
                                })
                                .toList();

                if (matches.size() != 1) {
                        if (matches.size() > 1) {
                                log.error("Ambiguous public tenant identifier received: {}", slug);
                        }
                        return null;
                }

                return matches.get(0);
        }

        private String normalizeTenantKey(
                        String value) {

                if (value == null) {
                        return "";
                }

                return value
                                .trim()
                                .toLowerCase()
                                .replace(
                                                "-",
                                                "")
                                .replace(
                                                "_",
                                                "")
                                .replace(
                                                " ",
                                                "");
        }

        // ============================================================
        // VERIFY BORROWER OWNERSHIP
        // ============================================================

        private Loan verifyOwnership(
                        String reference,
                        String phone) {

                if (reference == null
                                || reference.isBlank()
                                || reference.trim().length() > 100) {

                        throw new RuntimeException(
                                        "Application reference number is required.");
                }

                if (phone == null
                                || phone.isBlank()) {

                        throw new RuntimeException(
                                        "Phone number is required.");
                }

                Loan loan = loanRepo
                                .findByReferenceNumber(
                                                reference
                                                                .trim()
                                                                .toUpperCase())
                                .orElseThrow(
                                                () -> new RuntimeException(
                                                                "We couldn't find an application with that reference number."));

                Borrower borrower = loan.getBorrower();

                if (borrower == null) {

                        throw new RuntimeException(
                                        "This application has no borrower associated with it.");
                }

                String suppliedHash = HmacIndexer.index(
                                phone.trim());

                String storedHash = borrower.getPhoneHash();

                if (storedHash == null
                                || !storedHash.equals(
                                                suppliedHash)) {

                        throw new RuntimeException(
                                        "We couldn't find an application with that reference number and phone number.");
                }

                return loan;
        }

        // ============================================================
        // NOTIFY STAFF
        // ============================================================

        private void notifyStaff(
                        Organization org,
                        Borrower borrower,
                        Loan loan) {

                List<User> staff = userRepo.findByOrganization(org)
                                .stream()
                                .filter(
                                                u -> u.getRole() != null
                                                                && Set.of(
                                                                                "ADMIN",
                                                                                "MANAGER",
                                                                                "LOAN_OFFICER").contains(
                                                                                                u.getRole().getName()))
                                .toList();

                notificationService.notifyUsers(
                                staff,
                                "New Loan Application",
                                borrower.getFullName()
                                                + " applied for "
                                                + loan.getCurrency()
                                                + " "
                                                + fmt(
                                                                loan.getAmountDecimal())
                                                + " ("
                                                + loan.getLoanType()
                                                + ") — Ref "
                                                + loan.getReferenceNumber(),
                                "info",
                                "/dashboard/loans/"
                                                + loan.getId());
        }

        // ============================================================
        // LOAN TYPE
        // ============================================================

        private Loan.LoanType mapLoanType(
                        String label) {

                if (label == null) {

                        return Loan.LoanType.PERSONAL;
                }

                String l = label
                                .trim()
                                .toLowerCase();

                if (l.contains("business")
                                || l.contains("sme")) {

                        return Loan.LoanType.BUSINESS;
                }

                if (l.contains("agri")) {

                        return Loan.LoanType.AGRICULTURAL;
                }

                if (l.contains("salary")) {

                        return Loan.LoanType.SALARY_ADVANCE;
                }

                if (l.contains("micro")) {

                        return Loan.LoanType.MICROFINANCE;
                }

                if (l.contains("auto")
                                || l.contains("car")
                                || l.contains("asset")) {

                        return Loan.LoanType.ASSET_FINANCE;
                }

                if (l.contains("mortgage")
                                || l.contains("home")) {

                        return Loan.LoanType.MORTGAGE;
                }

                if (l.contains("student")
                                || l.contains("education")) {

                        return Loan.LoanType.STUDENT;
                }

                if (l.contains("emergency")) {

                        return Loan.LoanType.EMERGENCY;
                }

                if (l.contains("trade")) {

                        return Loan.LoanType.TRADE_FINANCE;
                }

                if (l.contains("group")) {

                        return Loan.LoanType.GROUP;
                }

                return Loan.LoanType.PERSONAL;
        }

        // ============================================================
        // REPAYMENT FREQUENCY
        // ============================================================

        private Loan.RepaymentFrequency mapRepaymentFrequency(
                        String value) {

                /*
                 * Platform currently supports monthly repayment only.
                 */
                return Loan.RepaymentFrequency.MONTHLY;

        }

        // ============================================================
        // HELPERS
        // ============================================================

        private String str(
                        Object o) {

                if (o == null) {
                        return null;
                }

                String value = o.toString().trim();

                return value.isBlank()
                                ? null
                                : value;
        }

        private Double num(
                        Object o) {

                if (o == null) {
                        return null;
                }

                if (o instanceof Number number) {

                        double value = number.doubleValue();

                        return Double.isFinite(
                                        value)
                                                        ? value
                                                        : null;
                }

                String value = o.toString().trim();

                if (value.isBlank()) {
                        return null;
                }

                try {

                        double parsed = Double.parseDouble(
                                        value);

                        return Double.isFinite(
                                        parsed)
                                                        ? parsed
                                                        : null;

                } catch (NumberFormatException e) {

                        return null;
                }
        }

        private BigDecimal decimal(
                        Object o) {

                if (o == null) {
                        return null;
                }

                if (o instanceof BigDecimal bd) {

                        return bd.setScale(
                                        2,
                                        RoundingMode.HALF_UP);
                }

                if (o instanceof Integer integer) {

                        return BigDecimal.valueOf(
                                        integer.longValue()).setScale(
                                                        2,
                                                        RoundingMode.HALF_UP);
                }

                if (o instanceof Long value) {

                        return BigDecimal.valueOf(
                                        value).setScale(
                                                        2,
                                                        RoundingMode.HALF_UP);
                }

                if (o instanceof Double value) {

                        if (!Double.isFinite(value)) {
                                return null;
                        }

                        return BigDecimal.valueOf(
                                        value).setScale(
                                                        2,
                                                        RoundingMode.HALF_UP);
                }

                if (o instanceof Float value) {

                        if (!Float.isFinite(value)) {
                                return null;
                        }

                        return BigDecimal.valueOf(
                                        value.doubleValue()).setScale(
                                                        2,
                                                        RoundingMode.HALF_UP);
                }

                String value = o.toString().trim();

                if (value.isBlank()) {
                        return null;
                }

                try {

                        return new BigDecimal(
                                        value.replace(
                                                        ",",
                                                        ""))
                                        .setScale(
                                                        2,
                                                        RoundingMode.HALF_UP);

                } catch (NumberFormatException e) {

                        return null;
                }
        }

        private BigDecimal money(
                        BigDecimal value) {

                if (value == null) {
                        return ZERO;
                }

                return value.setScale(
                                2,
                                RoundingMode.HALF_UP);
        }

        private String formatMoney(
                        BigDecimal value) {

                return String.format(
                                java.util.Locale.ROOT,
                                "%,.2f",
                                money(value));
        }

        private String formatRate(
                        BigDecimal value) {

                return String.format(
                                java.util.Locale.ROOT,
                                "%.2f",
                                value == null
                                                ? 0.0
                                                : value.doubleValue());
        }

        private BigDecimal calculateNetDisbursement(
                        Loan loan) {

                if (loan == null) {
                        return ZERO;
                }

                BigDecimal gross = money(
                                loan.getAmountDecimal());

                BigDecimal fee = money(
                                loan.getProcessingFeeDecimal());

                return money(
                                gross.subtract(
                                                fee).max(
                                                                ZERO));
        }

        private String fmt(
                        BigDecimal value) {

                return formatMoney(
                                value);
        }

        private LocalDate date(
                        Object o) {

                if (o == null) {
                        return null;
                }

                String value = o.toString().trim();

                if (value.isBlank()) {
                        return null;
                }

                try {

                        return LocalDate.parse(
                                        value);

                } catch (Exception e) {

                        return null;
                }
        }
}