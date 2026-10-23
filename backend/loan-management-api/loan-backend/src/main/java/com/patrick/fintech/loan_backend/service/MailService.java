package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class MailService {

        private static final String BREVO_URL = "https://api.brevo.com/v3/smtp/email";

        private static final String DEFAULT_SENDER_NAME = "Noble Loan Solution";

        private final RestTemplate restTemplate;

        @Value("${app.mail.enabled:false}")
        private boolean mailEnabled;

        @Value("${app.mail.from:}")
        private String from;

        @Value("${app.mail.from-name:Noble Loan Solution}")
        private String fromName;

        @Value("${app.mail.brevo-api-key:}")
        private String brevoApiKey;

        @Value("${app.frontend.url:}")
        private String frontendUrl;

        public MailService() {
                SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

                factory.setConnectTimeout(5_000);
                factory.setReadTimeout(10_000);

                this.restTemplate = new RestTemplate(factory);
        }

        public boolean isMailEnabled() {
                return mailEnabled;
        }

        /**
         * Send the persisted, reviewed DOCX as a Brevo attachment. Unlike general
         * notifications this is synchronous: callers must not record dispatch when
         * the provider rejects the message.
         */
        public void sendLoanContractDraftWithAttachment(Loan loan, String fileName, byte[] document) {
                if (!mailEnabled) {
                        throw new IllegalStateException("Contract email is disabled; the draft was not sent.");
                }
                if (loan == null || loan.getBorrower() == null || loan.getBorrower().getEmail() == null
                                || loan.getBorrower().getEmail().isBlank()) {
                        throw new IllegalStateException("The borrower must have an email address before dispatch.");
                }
                if (document == null || document.length == 0) {
                        throw new IllegalStateException("The stored contract document is missing or empty.");
                }
                if (document.length > 10 * 1024 * 1024) {
                        throw new IllegalStateException("The contract exceeds the 10 MB email attachment limit.");
                }
                if (fileName == null || !fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".docx")) {
                        throw new IllegalStateException("The contract must have a DOCX filename.");
                }
                String reference = escapeHtml(loan.getReferenceNumber());
                String body = "<p>Dear " + escapeHtml(loan.getBorrower().getFullName()) + ",</p>"
                                + "<p>Please find attached your <strong>draft loan agreement</strong> for reference <strong>"
                                + reference + "</strong>.</p>"
                                + "<p>Review the borrower details, interest, fees, penalties, repayment schedule and all conditions. "
                                + "Contact Noble Loan Solutions Ltd before signing if any information is incorrect.</p>"
                                + "<p><strong>CONFIDENTIAL — DRAFT, NOT SIGNED.</strong> This email is not confirmation "
                                + "of loan approval or disbursement.</p>"
                                + "<p>Regards,<br/>Noble Loan Solutions Ltd</p>";
                if (!send(loan.getBorrower().getEmail(),
                                "Confidential Draft Loan Agreement — " + loan.getReferenceNumber(),
                                body, fileName, document)) {
                        throw new IllegalStateException(
                                        "The email provider did not accept the contract attachment; dispatch was not recorded.");
                }
        }

        @Async("mailAsyncExecutor")
        public void sendLoanContractDraftNotice(Loan loan) {
                if (!mailEnabled) {
                        log.info("[EMAIL] Loan contract draft notice queued: {}",
                                        loan != null ? loan.getReferenceNumber() : "unknown");
                        return;
                }
                if (loan == null || loan.getBorrower() == null) {
                        log.warn("[EMAIL] Contract draft notice skipped: loan/borrower is null");
                        return;
                }
                String to = loan.getBorrower().getEmail();
                if (to == null || to.isBlank()) {
                        log.warn("[EMAIL] Contract draft notice skipped: borrower email is missing");
                        return;
                }
                send(to,
                                "Draft Loan Contract Ready for Review — " + escapeHtml(loan.getReferenceNumber()),
                                "<p>Dear " + escapeHtml(loan.getBorrower().getFullName()) + ",</p>"
                                                + "<p>Noble Loan Solutions Ltd has recorded that a draft loan contract for your application "
                                                + "<strong>" + escapeHtml(loan.getReferenceNumber())
                                                + "</strong> has been sent to you separately by our loan officer.</p>"
                                                + "<p>Please review all borrower details, loan amounts, interest, fees and repayment terms carefully. "
                                                + "Contact the loan officer if anything is incorrect or unclear. Do not sign until the final terms have been reviewed.</p>"
                                                + "<p>This notice does not mean the contract has been signed, the loan has been approved, or funds have been disbursed.</p>"
                                                + "<p>Regards,<br/>Noble Loan Solutions Ltd</p>");
        }

        @Async("mailAsyncExecutor")
        public void sendApplicationReceived(Loan loan) {
                if (!mailEnabled) {
                        log.info(
                                        "[EMAIL] Application received: {}",
                                        loan != null ? loan.getReferenceNumber() : "unknown");
                        return;
                }

                if (loan == null || loan.getBorrower() == null) {
                        log.warn(
                                        "[EMAIL] Application-received email skipped: " +
                                                        "loan/borrower is null");
                        return;
                }

                String to = loan.getBorrower().getEmail();

                if (to == null || to.isBlank()) {
                        log.warn(
                                        "[EMAIL] Application-received email skipped: " +
                                                        "borrower email is missing");
                        return;
                }

                send(
                                to,
                                "Loan Application Received",
                                "<p>Dear " +
                                                safe(loan.getBorrower().getFullName()) +
                                                ",</p>" +
                                                "<p>We have successfully received your loan application.</p>" +
                                                "<p><strong>Reference Number:</strong> " +
                                                safe(loan.getReferenceNumber()) +
                                                "</p>" +
                                                "<p><strong>Current Status:</strong> Submitted</p>" +
                                                "<p>You can track your application from your borrower dashboard.</p>" +
                                                "<p>Thank you.<br/>Noble Loan Solution</p>");
        }

        @Async("mailAsyncExecutor")
        public void sendLoanUpdateComment(Loan loan, String message) {
                if (!mailEnabled) {
                        log.info(
                                        "[EMAIL] Loan update comment: {}",
                                        loan != null ? loan.getReferenceNumber() : "unknown");
                        return;
                }

                if (loan == null || loan.getBorrower() == null) {
                        log.warn(
                                        "[EMAIL] Loan update email skipped: " +
                                                        "loan/borrower is null");
                        return;
                }

                String to = loan.getBorrower().getEmail();

                if (to == null || to.isBlank()) {
                        return;
                }

                send(
                                to,
                                "New Update on Your Application — " +
                                                safe(loan.getReferenceNumber()),
                                "<h2>New Message From Your Loan Officer</h2>" +
                                                "<p>There's a new update on your loan application " +
                                                "<strong>" +
                                                safe(loan.getReferenceNumber()) +
                                                "</strong>:</p>" +
                                                "<blockquote style=\"" +
                                                "border-left:3px solid #0D9488;" +
                                                "margin:12px 0;" +
                                                "padding:8px 16px;" +
                                                "color:#374151;" +
                                                "background:#f9fafb;\">" +
                                                safe(message) +
                                                "</blockquote>" +
                                                "<p>You can view the full details and respond " +
                                                "from your application tracking page.</p>");
        }

        /**
         * Login OTP delivery is synchronous on purpose: the login endpoint must not
         * claim that a code was sent before the provider accepts it. The provider
         * request has bounded connect/read timeouts in this service's RestTemplate.
         * This method logs no recipient address or OTP value.
         */
        /** Synchronous email OTP for safe reuse of existing borrower's KYC. */
        public boolean sendBorrowerDocumentReuseOtp(Borrower borrower, String code) {
                if (!mailEnabled || borrower == null || borrower.getEmail() == null
                                || borrower.getEmail().isBlank() || code == null || !code.matches("[0-9]{6}")) {
                        return false;
                }
                return send(borrower.getEmail(), "Noble Loan Solutions — verify document reuse",
                                "<p>We received a request to reuse documents already held by Noble Loan Solutions Ltd "
                                                + "for a new loan application.</p>"
                                                + "<p>Your verification code is <strong>" + safe(code)
                                                + "</strong>.</p>"
                                                + "<p>It expires in 5 minutes. If you did not request this, ignore this message.</p>");
        }

        public boolean sendLoginOtp(User user, String code) {
                if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                        log.warn("Login OTP delivery skipped because the user email is missing");
                        return false;
                }
                if (code == null || code.isBlank()) {
                        log.warn("Login OTP delivery skipped because the code is missing");
                        return false;
                }
                if (!mailEnabled) {
                        log.warn("Login OTP delivery is disabled; set MAIL_ENABLED=true in the deployment environment.");
                        return false;
                }

                return send(
                                user.getEmail(),
                                "Your sign-in code",
                                "<h2>Sign-in Verification Code</h2>" +
                                                "<p>Use this code to finish signing in:</p>" +
                                                "<p style=\"" +
                                                "font-size:28px;" +
                                                "font-weight:bold;" +
                                                "letter-spacing:6px;" +
                                                "color:#0D6B3E;\">" +
                                                safe(code) +
                                                "</p>" +
                                                "<p>This code expires in 5 minutes. " +
                                                "If you didn't try to sign in, you can ignore " +
                                                "this email — your account is still safe, but " +
                                                "consider changing your password if it happens again.</p>");
        }

        @Async("mailAsyncExecutor")
        public void sendLoanApproved(Loan loan) {
                if (!mailEnabled) {
                        log.info(
                                        "[EMAIL] Loan approved: {}",
                                        loan != null ? loan.getReferenceNumber() : "unknown");
                        return;
                }

                if (loan == null || loan.getBorrower() == null) {
                        return;
                }

                String to = loan.getBorrower().getEmail();

                if (to == null || to.isBlank()) {
                        return;
                }

                BigDecimal requested = loan.getRequestedAmountDecimal() != null
                                ? loan.getRequestedAmountDecimal()
                                : loan.getAmountDecimal();

                BigDecimal approved = loan.getAmountDecimal();

                BigDecimal applicationFee = loan.getApplicationFeeDecimal() != null
                                ? loan.getApplicationFeeDecimal()
                                : BigDecimal.ZERO;

                if (requested == null) {
                        requested = BigDecimal.ZERO;
                }

                if (approved == null) {
                        approved = BigDecimal.ZERO;
                }

                BigDecimal net = approved
                                .subtract(applicationFee)
                                .max(BigDecimal.ZERO);

                BigDecimal interestRate = loan.getInterestRateDecimal();

                BigDecimal managementRate = loan.getManagementFeeRateDecimal();

                BigDecimal applicationRate = loan.getApplicationFeeRateDecimal();

                send(
                                to,
                                "Your Loan Has Been Approved — " +
                                                safe(loan.getReferenceNumber()),
                                "<h2>Loan Approved</h2>" +
                                                "<p>Your loan application <strong>" +
                                                safe(loan.getReferenceNumber()) +
                                                "</strong> has been reviewed and approved.</p>" +
                                                "<p>Requested amount: <strong>" +
                                                safe(loan.getCurrency()) +
                                                " " +
                                                requested +
                                                "</strong></p>" +
                                                "<p>Approved principal: <strong>" +
                                                safe(loan.getCurrency()) +
                                                " " +
                                                approved +
                                                "</strong></p>" +
                                                "<p>One-time application fee (" +
                                                safe(String.valueOf(applicationRate)) +
                                                "%): <strong>" +
                                                safe(loan.getCurrency()) +
                                                " " +
                                                applicationFee +
                                                "</strong></p>" +
                                                "<p>Net amount to be disbursed: <strong>" +
                                                safe(loan.getCurrency()) +
                                                " " +
                                                net +
                                                "</strong></p>" +
                                                "<p>Contractual interest: <strong>" +
                                                safe(String.valueOf(interestRate)) +
                                                "% monthly</strong>. " +
                                                "Management fee: <strong>" +
                                                safe(String.valueOf(managementRate)) +
                                                "% monthly</strong>.</p>" +
                                                "<p>There is no daily interest accrual. " +
                                                "Your repayment schedule is based on the approved " +
                                                "contractual monthly terms.</p>" +
                                                "<p>You will receive the funds after the disbursement " +
                                                "step. Please review your repayment schedule.</p>");
        }

        @Async("mailAsyncExecutor")
        public void sendLoanRejected(Loan loan) {
                if (!mailEnabled) {
                        log.info(
                                        "[EMAIL] Loan rejected: {}",
                                        loan != null ? loan.getReferenceNumber() : "unknown");
                        return;
                }

                if (loan == null || loan.getBorrower() == null) {
                        return;
                }

                String to = loan.getBorrower().getEmail();

                if (to == null || to.isBlank()) {
                        return;
                }

                send(
                                to,
                                "Update on Your Loan Application — " +
                                                safe(loan.getReferenceNumber()),
                                "<h2>Application Update</h2>" +
                                                "<p>We regret to inform you that your loan application " +
                                                "<strong>" +
                                                safe(loan.getReferenceNumber()) +
                                                "</strong> was not approved at this time.</p>" +
                                                "<p>Reason: " +
                                                safe(
                                                                loan.getRejectionReason() != null
                                                                                ? loan.getRejectionReason()
                                                                                : "See your loan officer")
                                                +
                                                "</p>");
        }

        @Async("mailAsyncExecutor")
        public void sendPaymentConfirmation(
                        Loan loan,
                        Double amount) {
                if (!mailEnabled) {
                        log.info(
                                        "[EMAIL] Payment confirmed: {} -> {}",
                                        loan != null ? loan.getReferenceNumber() : "unknown",
                                        amount);
                        return;
                }

                if (loan == null || loan.getBorrower() == null) {
                        return;
                }

                String to = loan.getBorrower().getEmail();

                if (to == null || to.isBlank()) {
                        return;
                }

                send(
                                to,
                                "Payment Received — " +
                                                safe(loan.getReferenceNumber()),
                                "<h2>Payment Confirmed</h2>" +
                                                "<p>We have received your payment of <strong>" +
                                                safe(loan.getCurrency()) +
                                                " " +
                                                amount +
                                                "</strong> on loan <strong>" +
                                                safe(loan.getReferenceNumber()) +
                                                "</strong>.</p>" +
                                                "<p>Outstanding balance: <strong>" +
                                                safe(loan.getCurrency()) +
                                                " " +
                                                loan.getOutstandingBalance() +
                                                "</strong></p>");
        }

        @Async("mailAsyncExecutor")
        public void sendLoanDisbursed(
                        Loan loan,
                        String method) {
                if (!mailEnabled) {
                        log.info(
                                        "[EMAIL] Loan disbursed: {}",
                                        loan != null ? loan.getReferenceNumber() : "unknown");
                        return;
                }

                if (loan == null || loan.getBorrower() == null) {
                        return;
                }

                String to = loan.getBorrower().getEmail();

                if (to == null || to.isBlank()) {
                        return;
                }

                send(
                                to,
                                "Funds Disbursed — " +
                                                safe(loan.getReferenceNumber()),
                                "<h2>Funds Sent Successfully</h2>" +
                                                "<p>Your loan <strong>" +
                                                safe(loan.getReferenceNumber()) +
                                                "</strong> has been disbursed.</p>" +
                                                "<p>Amount: <strong>" +
                                                safe(loan.getCurrency()) +
                                                " " +
                                                loan.getDisbursedAmount() +
                                                "</strong> via " +
                                                safe(method) +
                                                "</p>" +
                                                "<p>Your first payment is due on <strong>" +
                                                loan.getNextDueDate() +
                                                "</strong>. You can track your loan from your " +
                                                "borrower dashboard.</p>");
        }

        @Async("mailAsyncExecutor")
        public void sendPaymentDueReminder(Loan loan) {
                if (!mailEnabled) {
                        log.info(
                                        "[EMAIL] Payment reminder: {}",
                                        loan != null ? loan.getReferenceNumber() : "unknown");
                        return;
                }

                if (loan == null || loan.getBorrower() == null) {
                        return;
                }

                String to = loan.getBorrower().getEmail();

                if (to == null || to.isBlank()) {
                        return;
                }

                send(
                                to,
                                "Payment Due Reminder — " +
                                                safe(loan.getReferenceNumber()),
                                "<h2>Payment Reminder</h2>" +
                                                "<p>Your next payment on loan <strong>" +
                                                safe(loan.getReferenceNumber()) +
                                                "</strong> is due on <strong>" +
                                                loan.getNextDueDate() +
                                                "</strong>.</p>" +
                                                "<p>Please ensure your account has sufficient funds " +
                                                "to avoid penalties.</p>");
        }

        @Async("mailAsyncExecutor")
        public void sendLoanRestructured(
                        Loan loan,
                        String reason) {
                if (!mailEnabled) {
                        log.info(
                                        "[EMAIL] Loan restructured: {}",
                                        loan != null ? loan.getReferenceNumber() : "unknown");
                        return;
                }

                if (loan == null || loan.getBorrower() == null) {
                        return;
                }

                String to = loan.getBorrower().getEmail();

                if (to == null || to.isBlank()) {
                        return;
                }

                send(
                                to,
                                "Your Loan Terms Have Changed — " +
                                                safe(loan.getReferenceNumber()),
                                "<h2>Loan Restructured</h2>" +
                                                "<p>The terms on your loan <strong>" +
                                                safe(loan.getReferenceNumber()) +
                                                "</strong> have been updated:</p>" +
                                                "<p>New term: <strong>" +
                                                loan.getDurationMonths() +
                                                " months</strong> at <strong>" +
                                                loan.getInterestRate() +
                                                "%</strong></p>" +
                                                (reason != null && !reason.isBlank()
                                                                ? "<p>Reason: " +
                                                                                safe(reason) +
                                                                                "</p>"
                                                                : "")
                                                +
                                                "<p>Your repayment schedule has been recalculated — " +
                                                "please review it from your borrower dashboard.</p>");
        }

        @Async("mailAsyncExecutor")
        public void sendLoanWrittenOff(
                        Loan loan,
                        String reason) {
                if (!mailEnabled) {
                        log.info(
                                        "[EMAIL] Loan written off: {}",
                                        loan != null ? loan.getReferenceNumber() : "unknown");
                        return;
                }

                if (loan == null || loan.getBorrower() == null) {
                        return;
                }

                String to = loan.getBorrower().getEmail();

                if (to == null || to.isBlank()) {
                        return;
                }

                send(
                                to,
                                "Update on Your Loan — " +
                                                safe(loan.getReferenceNumber()),
                                "<h2>Account Status Update</h2>" +
                                                "<p>Your loan <strong>" +
                                                safe(loan.getReferenceNumber()) +
                                                "</strong> has been written off by " +
                                                safe(org(loan)) +
                                                ".</p>" +
                                                (reason != null && !reason.isBlank()
                                                                ? "<p>Reason: " +
                                                                                safe(reason) +
                                                                                "</p>"
                                                                : "")
                                                +
                                                "<p>Please contact us if you have questions " +
                                                "about what this means for your account.</p>");
        }

        @Async("mailAsyncExecutor")
        public void sendMoratoriumGranted(
                        Loan loan,
                        int pauseMonths,
                        String reason) {
                if (!mailEnabled) {
                        log.info(
                                        "[EMAIL] Moratorium granted: {}",
                                        loan != null ? loan.getReferenceNumber() : "unknown");
                        return;
                }

                if (loan == null || loan.getBorrower() == null) {
                        return;
                }

                String to = loan.getBorrower().getEmail();

                if (to == null || to.isBlank()) {
                        return;
                }

                send(
                                to,
                                "Payment Pause Approved — " +
                                                safe(loan.getReferenceNumber()),
                                "<h2>Payment Moratorium Granted</h2>" +
                                                "<p>Your upcoming payments on loan <strong>" +
                                                safe(loan.getReferenceNumber()) +
                                                "</strong> have been paused for <strong>" +
                                                pauseMonths +
                                                " month(s)</strong>.</p>" +
                                                (reason != null && !reason.isBlank()
                                                                ? "<p>Reason: " +
                                                                                safe(reason) +
                                                                                "</p>"
                                                                : "")
                                                +
                                                "<p>Your next due date is now <strong>" +
                                                loan.getNextDueDate() +
                                                "</strong>. No action is needed from you during the pause.</p>");
        }

        private String org(Loan loan) {
                if (loan == null || loan.getOrganization() == null) {
                        return "our team";
                }

                String name = loan.getOrganization().getName();

                return name != null && !name.isBlank()
                                ? name
                                : "our team";
        }

        /** Returns true only when all required delivery settings are present. */
        public boolean isPasswordResetEmailConfigured() {
                return mailEnabled
                                && brevoApiKey != null && !brevoApiKey.isBlank()
                                && from != null && !from.isBlank();
        }

        /**
         * Legacy asynchronous notification method retained for existing callers.
         * Password recovery uses the synchronous method below so failures do not
         * leave an undelivered, usable token in the database.
         */
        @Async("mailAsyncExecutor")
        public void sendPasswordResetEmail(User user, String resetLink) {
                if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                        return;
                }
                if (!mailEnabled) {
                        log.warn("Password reset email skipped because mail delivery is disabled");
                        return;
                }
                send(user.getEmail(), "Reset Your Noble Loan Solutions Password",
                                passwordResetHtml(user, resetLink));
        }

        /** Synchronous reset delivery. Does not log the recipient or reset link. */
        public boolean sendPasswordResetEmailNow(User user, String resetLink) {
                if (user == null || user.getEmail() == null || user.getEmail().isBlank()
                                || resetLink == null || resetLink.isBlank()
                                || !isPasswordResetEmailConfigured()) {
                        return false;
                }
                return send(user.getEmail(), "Reset Your Noble Loan Solutions Password",
                                passwordResetHtml(user, resetLink));
        }

        private String passwordResetHtml(User user, String resetLink) {
                String displayName = user.getName() == null ? "" : user.getName();
                return "<h2>Password Reset Request</h2>"
                                + "<p>Hello " + safe(displayName) + ",</p>"
                                + "<p>We received a request to reset your Noble Loan Solutions password. "
                                + "This link expires in 30 minutes and can only be used once.</p>"
                                + "<p><a href=\"" + safeAttribute(resetLink) + "\" style=\""
                                + "background:#0D9488;color:#fff;padding:12px 24px;border-radius:8px;"
                                + "text-decoration:none;font-weight:bold;\">Reset Password</a></p>"
                                + "<p>If you did not request this change, ignore this email and contact your administrator "
                                + "if you suspect unauthorized access.</p>";
        }

        /**
         * Sent alongside SMS when available.
         */
        @Async("mailAsyncExecutor")
        public void sendESignatureRequest(
                        Borrower borrower,
                        String orgName,
                        String signLink) {
                if (!mailEnabled) {
                        log.info(
                                        "[EMAIL] E-signature notification requested for {} " +
                                                        "(email delivery disabled)",
                                        borrower != null ? borrower.getEmail() : "unknown");
                        return;
                }

                if (borrower == null ||
                                borrower.getEmail() == null ||
                                borrower.getEmail().isBlank()) {
                        return;
                }

                send(
                                borrower.getEmail(),
                                "Your Loan Agreement Is Ready to Sign",
                                "<h2>Loan Agreement Ready for Signature</h2>" +
                                                "<p>Dear " +
                                                safe(borrower.getFullName()) +
                                                ",</p>" +
                                                "<p>" +
                                                safe(orgName) +
                                                " has sent your loan agreement for e-signature. " +
                                                "Click below to review and sign it.</p>" +
                                                "<p><a href=\"" +
                                                safeAttribute(signLink) +
                                                "\" style=\"" +
                                                "background:#0D9488;" +
                                                "color:#fff;" +
                                                "padding:12px 24px;" +
                                                "border-radius:8px;" +
                                                "text-decoration:none;" +
                                                "font-weight:bold;\">" +
                                                "Review &amp; Sign</a></p>" +
                                                "<p>We've also texted a verification code to your " +
                                                "phone — you'll need to enter it on that page to " +
                                                "complete your signature.</p>" +
                                                "<p>This link expires in 7 days. If you weren't " +
                                                "expecting this, please contact your loan officer.</p>");
        }

        @Async("mailAsyncExecutor")
        public void sendNewUserCredentials(
                        User user,
                        String tempPassword,
                        String loginLink) {
                if (!mailEnabled) {
                        log.info(
                                        "[EMAIL] New account notification requested for {} " +
                                                        "(email delivery disabled)",
                                        user != null ? user.getEmail() : "unknown");
                        return;
                }

                if (user == null ||
                                user.getEmail() == null ||
                                user.getEmail().isBlank()) {
                        return;
                }

                String organizationText = user.getOrganization() != null
                                ? " at " +
                                                safe(user.getOrganization().getName())
                                : "";

                String roleText = user.getRole() != null
                                ? " with the role <strong>" +
                                                safe(user.getRole().getName()) +
                                                "</strong>"
                                : "";

                send(
                                user.getEmail(),
                                "Your LoanSaaS Pro Account Has Been Created",
                                "<h2>Welcome to LoanSaaS Pro</h2>" +
                                                "<p>Hi " +
                                                safe(
                                                                user.getName() != null
                                                                                ? user.getName()
                                                                                : "")
                                                +
                                                ",</p>" +
                                                "<p>An administrator" +
                                                organizationText +
                                                " created a staff account for you" +
                                                roleText +
                                                ".</p>" +
                                                "<p><strong>Email:</strong> " +
                                                safe(user.getEmail()) +
                                                "<br/>" +
                                                "<strong>Temporary password:</strong> " +
                                                "<code style=\"" +
                                                "background:#f3f4f6;" +
                                                "padding:2px 8px;" +
                                                "border-radius:8px;" +
                                                "font-size:15px;\">" +
                                                safe(tempPassword) +
                                                "</code></p>" +
                                                "<p><a href=\"" +
                                                safeAttribute(loginLink) +
                                                "\" style=\"" +
                                                "background:#0D9488;" +
                                                "color:#fff;" +
                                                "padding:12px 24px;" +
                                                "border-radius:8px;" +
                                                "text-decoration:none;" +
                                                "font-weight:bold;\">" +
                                                "Sign In</a></p>" +
                                                "<p>You'll be asked to set your own password " +
                                                "the first time you sign in — the temporary one " +
                                                "above will stop working once you do.</p>" +
                                                "<p>If you weren't expecting this account, " +
                                                "please contact your administrator.</p>");
        }

        @Async("mailAsyncExecutor")
        public void sendBorrowerWelcome(Borrower borrower) {
                if (!mailEnabled) {
                        log.info(
                                        "[EMAIL] Borrower profile created: {}",
                                        borrower != null ? borrower.getEmail() : "unknown");
                        return;
                }

                if (borrower == null ||
                                borrower.getEmail() == null ||
                                borrower.getEmail().isBlank()) {
                        return;
                }

                send(
                                borrower.getEmail(),
                                "Your Profile Has Been Created",
                                "<h2>Welcome</h2>" +
                                                "<p>Dear " +
                                                safe(borrower.getFullName()) +
                                                ",</p>" +
                                                "<p>A borrower profile has been created for you. " +
                                                "Our team will reach out if we need any further " +
                                                "information from you.</p>" +
                                                "<p>Thank you.<br/>Noble Loan Solution</p>");
        }

        /**
         * Sent alongside SMS when available, and as the primary channel when
         * the borrower has no phone on file.
         */
        @Async("mailAsyncExecutor")
        public void sendESignatureRequest(
                        Borrower borrower,
                        String orgName,
                        String signLink,
                        String otp) {
                if (!mailEnabled) {
                        log.warn(
                                        "[EMAIL] E-signature delivery is disabled for {}; " +
                                                        "no link or OTP is logged.",
                                        borrower != null ? borrower.getEmail() : "unknown");
                        return;
                }

                if (borrower == null ||
                                borrower.getEmail() == null ||
                                borrower.getEmail().isBlank()) {
                        return;
                }

                send(
                                borrower.getEmail(),
                                "Your Loan Agreement Is Ready to Sign",
                                "<h2>Loan Agreement Ready for Signature</h2>" +
                                                "<p>Dear " +
                                                safe(borrower.getFullName()) +
                                                ",</p>" +
                                                "<p>" +
                                                safe(orgName) +
                                                " has sent your loan agreement for e-signature. " +
                                                "Click below to review and sign it.</p>" +
                                                "<p><a href=\"" +
                                                safeAttribute(signLink) +
                                                "\" style=\"" +
                                                "background:#0D9488;" +
                                                "color:#fff;" +
                                                "padding:12px 24px;" +
                                                "border-radius:8px;" +
                                                "text-decoration:none;" +
                                                "font-weight:bold;\">" +
                                                "Review &amp; Sign</a></p>" +
                                                "<p>Enter this verification code on that page " +
                                                "to complete your signature (also sent by SMS, " +
                                                "if we have a valid number on file):</p>" +
                                                "<p style=\"" +
                                                "font-size:28px;" +
                                                "font-weight:bold;" +
                                                "letter-spacing:6px;" +
                                                "color:#0D6B3E;\">" +
                                                safe(otp) +
                                                "</p>" +
                                                "<p>This link expires in 7 days. If you weren't " +
                                                "expecting this, please contact your loan officer.</p>");
        }

        @Async("mailAsyncExecutor")
        public void sendDocumentVerified(
                        Borrower borrower,
                        String documentType) {
                if (!mailEnabled) {
                        log.info(
                                        "[EMAIL] Document verified: {} for {}",
                                        documentType,
                                        borrower != null ? borrower.getEmail() : "unknown");
                        return;
                }

                if (borrower == null ||
                                borrower.getEmail() == null ||
                                borrower.getEmail().isBlank()) {
                        return;
                }

                send(
                                borrower.getEmail(),
                                "Document Verified: " +
                                                humanizeDocType(documentType),
                                "<h2>Document Verified</h2>" +
                                                "<p>Dear " +
                                                safe(borrower.getFullName()) +
                                                ",</p>" +
                                                "<p>Your <strong>" +
                                                safe(humanizeDocType(documentType)) +
                                                "</strong> has been verified. No further action " +
                                                "is needed for this document.</p>" +
                                                "<p>You can check your overall application status " +
                                                "from your borrower dashboard.</p>");
        }

        @Async("mailAsyncExecutor")
        public void sendDocumentRejected(
                        Borrower borrower,
                        String documentType,
                        String reason) {
                if (!mailEnabled) {
                        log.info(
                                        "[EMAIL] Document rejected: {} for {}",
                                        documentType,
                                        borrower != null ? borrower.getEmail() : "unknown");
                        return;
                }

                if (borrower == null ||
                                borrower.getEmail() == null ||
                                borrower.getEmail().isBlank()) {
                        return;
                }

                send(
                                borrower.getEmail(),
                                "Action Needed: " +
                                                humanizeDocType(documentType) +
                                                " Rejected",
                                "<h2>Document Rejected</h2>" +
                                                "<p>Dear " +
                                                safe(borrower.getFullName()) +
                                                ",</p>" +
                                                "<p>Your <strong>" +
                                                safe(humanizeDocType(documentType)) +
                                                "</strong> could not be accepted." +
                                                (reason != null && !reason.isBlank()
                                                                ? " Reason: " + safe(reason)
                                                                : "")
                                                +
                                                "</p>" +
                                                "<p>Please upload a corrected document from your " +
                                                "application tracking page.</p>");
        }

        @Async("mailAsyncExecutor")
        public void sendDocumentReplacementRequested(
                        Borrower borrower,
                        String documentType,
                        String note) {
                if (!mailEnabled) {
                        log.info(
                                        "[EMAIL] Document replacement requested: {} for {}",
                                        documentType,
                                        borrower != null ? borrower.getEmail() : "unknown");
                        return;
                }

                if (borrower == null ||
                                borrower.getEmail() == null ||
                                borrower.getEmail().isBlank()) {
                        return;
                }

                send(
                                borrower.getEmail(),
                                "Please Re-upload: " +
                                                humanizeDocType(documentType),
                                "<h2>Replacement Document Requested</h2>" +
                                                "<p>Dear " +
                                                safe(borrower.getFullName()) +
                                                ",</p>" +
                                                "<p>We need a new copy of your <strong>" +
                                                safe(humanizeDocType(documentType)) +
                                                "</strong>." +
                                                (note != null && !note.isBlank()
                                                                ? " " + safe(note)
                                                                : "")
                                                +
                                                "</p>" +
                                                "<p>Please upload it from your application tracking " +
                                                "page as soon as possible to avoid delays.</p>");
        }

        @Async("mailAsyncExecutor")
        public void sendOverdueReminder(
                        Loan loan,
                        Integer daysOverdue) {
                if (!mailEnabled) {
                        log.info(
                                        "[EMAIL] Overdue reminder: {} ({} days)",
                                        loan != null ? loan.getReferenceNumber() : "unknown",
                                        daysOverdue);
                        return;
                }

                if (loan == null || loan.getBorrower() == null) {
                        return;
                }

                String to = loan.getBorrower().getEmail();

                if (to == null || to.isBlank()) {
                        return;
                }

                send(
                                to,
                                "URGENT: Payment Overdue Notice — " +
                                                safe(loan.getReferenceNumber()),
                                "<h2>Payment Overdue Alert</h2>" +
                                                "<p>Dear " +
                                                safe(loan.getBorrower().getFullName()) +
                                                ",</p>" +
                                                "<p>Your loan <strong>" +
                                                safe(loan.getReferenceNumber()) +
                                                "</strong> is currently marked as " +
                                                "<strong>OVERDUE</strong> by <strong>" +
                                                daysOverdue +
                                                "</strong> days.</p>" +
                                                "<p>Please settle your outstanding balance " +
                                                "immediately to avoid further penalization, " +
                                                "legal escalation, or collection queue assignment.</p>");
        }

        private String humanizeDocType(String type) {
                if (type == null || type.isBlank()) {
                        return "document";
                }

                String[] parts = type.toLowerCase().split("_");

                StringBuilder sb = new StringBuilder();

                for (String part : parts) {
                        if (part == null || part.isBlank()) {
                                continue;
                        }

                        sb.append(
                                        Character.toUpperCase(part.charAt(0)))
                                        .append(part.substring(1))
                                        .append(' ');
                }

                return sb.toString().trim();
        }

        @Async("mailAsyncExecutor")
        public void sendWorkflowTaskAssigned(
                        String to,
                        String recipientName,
                        String title,
                        String description,
                        String taskType,
                        String referenceNumber,
                        java.time.LocalDateTime dueAt,
                        String link) {

                if (!mailEnabled) {
                        log.warn("[EMAIL] Workflow task email disabled for {}: {}", to, title);
                        return;
                }

                if (to == null || to.isBlank()) {
                        log.warn("[EMAIL] Workflow task email skipped: assignee email is missing");
                        return;
                }

                String dueText = dueAt == null ? "No due date"
                                : dueAt.format(
                                                java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"));
                String actionLink = toAbsoluteFrontendUrl(link);

                String button = actionLink.isBlank() ? ""
                                : "<p><a href=\"" + safeAttribute(actionLink)
                                                + "\" style=\"background:#0B1F3A;color:#fff;padding:11px 20px;"
                                                + "text-decoration:none;font-weight:bold;border-radius:7px;display:inline-block;\">"
                                                + "Open Task</a></p>";

                send(
                                to,
                                "Action required — " + safe(title),
                                "<h2>New Task Assigned</h2>"
                                                + "<p>Dear " + escapeHtml(recipientName) + ",</p>"
                                                + "<p>A new operational task has been assigned to you.</p>"
                                                + "<p><strong>Task:</strong> " + escapeHtml(title) + "</p>"
                                                + (referenceNumber == null || referenceNumber.isBlank()
                                                                ? ""
                                                                : "<p><strong>Reference:</strong> "
                                                                                + escapeHtml(referenceNumber) + "</p>")
                                                + "<p><strong>Due:</strong> " + escapeHtml(dueText) + "</p>"
                                                + (description == null || description.isBlank()
                                                                ? ""
                                                                : "<p><strong>Instructions:</strong><br/>"
                                                                                + escapeHtml(description) + "</p>")
                                                + button
                                                + "<p>Please complete the task through the Noble Loan Solutions dashboard. "
                                                + "Financial actions remain subject to the platform's normal authorization controls.</p>");
        }

        private String toAbsoluteFrontendUrl(String link) {
                String candidate = link == null ? "" : link.trim();
                if (candidate.isBlank() || candidate.startsWith("#") || candidate.startsWith("mailto:")) {
                        return candidate;
                }
                if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
                        return candidate;
                }
                String base = frontendUrl == null ? "" : frontendUrl.trim();
                if (base.isBlank()) {
                        log.warn("[EMAIL] Workflow task link omitted because app.frontend.url is not configured");
                        return "";
                }
                while (base.endsWith("/")) {
                        base = base.substring(0, base.length() - 1);
                }
                return base + (candidate.startsWith("/") ? candidate : "/" + candidate);
        }

        /**
         * Sends through Brevo HTTP API.
         *
         * The sender contains BOTH:
         *
         * email = actual authenticated sender mailbox
         * name = display name shown to the recipient
         *
         * Result:
         *
         * Noble Loan Solution <pmpumuropizzou@gmail.com>
         *
         * This method is intentionally private and is called from @Async public
         * methods. Therefore Brevo network latency does not block the login HTTP
         * request.
         */

        /** Shared, table-based branding for all transactional HTML emails. */
        private String brandEmailHtml(String content) {
                String base = frontendUrl == null ? "" : frontendUrl.trim();
                while (base.endsWith("/")) {
                        base = base.substring(0, base.length() - 1);
                }
                // Public HTTPS image URL: data URIs and local file paths are not reliable in
                // email.
                String logoUrl = base.startsWith("https://")
                                ? base + "/branding/nls-logo.png"
                                : "";
                String logo = logoUrl.isEmpty()
                                ? "<span style=\"font-family:Arial,sans-serif;font-size:22px;font-weight:700;color:#b58a28;\">NOBLE</span>"
                                : "<img src=\"" + escapeHtml(logoUrl)
                                                + "\" alt=\"Noble Loan Solutions Ltd\" width=\"205\" style=\"display:block;width:205px;max-width:100%;height:auto;border:0;\">";
                return "<!doctype html><html lang=\"en\"><head><meta charset=\"UTF-8\">"
                                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>"
                                + "<body style=\"margin:0;padding:0;background:#f3f5f8;font-family:Arial,Helvetica,sans-serif;color:#263445;\">"
                                + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"background:#f3f5f8;\"><tr><td align=\"center\" style=\"padding:32px 14px;\">"
                                + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"600\" style=\"width:100%;max-width:600px;background:#ffffff;border:1px solid #e3e7ee;\">"
                                + "<tr><td style=\"height:5px;background:#b58a28;font-size:1px;line-height:1px;\">&nbsp;</td></tr>"
                                + "<tr><td style=\"padding:28px 36px 23px;border-bottom:1px solid #e7ebf1;\">" + logo
                                + "<div style=\"font-size:10px;letter-spacing:2px;font-weight:700;color:#68758a;margin-top:14px;\">OFFICIAL CUSTOMER COMMUNICATION</div></td></tr>"
                                + "<tr><td style=\"padding:30px 36px 34px;font-size:15px;line-height:1.8;color:#263445;\">"
                                + content + "</td></tr>"
                                + "<tr><td style=\"background:#10263e;padding:24px 36px;color:#e8edf3;font-size:12px;line-height:1.7;\">"
                                + "<strong style=\"color:#e2bb66;letter-spacing:.4px;\">NOBLE LOAN SOLUTIONS LTD</strong><br>"
                                + "Kigali, Rwanda &nbsp;|&nbsp; <a href=\"https://nobleloansolutions.rw\" style=\"color:#e2bb66;text-decoration:none;\">nobleloansolutions.rw</a><br>"
                                + "<span style=\"color:#b9c4d2;\">Confidential. Intended only for the named recipient. Do not share your password or verification code.</span>"
                                + "</td></tr></table></td></tr></table></body></html>";
        }

        private boolean send(String to, String subject, String html) {
                return send(to, subject, html, null, null);
        }

        private boolean send(String to, String subject, String html, String attachmentName, byte[] attachmentData) {
                if (to == null || to.isBlank() || subject == null || subject.isBlank() || html == null
                                || brevoApiKey == null || brevoApiKey.isBlank()
                                || from == null || from.isBlank()) {
                        log.warn("Email delivery skipped because recipient, content, or mail configuration is missing");
                        return false;
                }

                String senderName = fromName == null || fromName.isBlank()
                                ? DEFAULT_SENDER_NAME
                                : fromName.trim();
                try {
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                        headers.set("api-key", brevoApiKey);

                        Map<String, Object> sender = Map.of("email", from.trim(), "name", senderName);
                        Map<String, Object> payload = new java.util.HashMap<>();
                        payload.put("sender", sender);
                        payload.put("to", List.of(Map.of("email", to.trim())));
                        payload.put("subject", subject);
                        payload.put("htmlContent", brandEmailHtml(html));
                        if (attachmentData != null && attachmentData.length > 0) {
                                payload.put("attachment", List.of(Map.of(
                                                "name", attachmentName,
                                                "content", Base64.getEncoder().encodeToString(attachmentData))));
                        }

                        ResponseEntity<String> response = restTemplate.exchange(
                                        BREVO_URL, HttpMethod.POST,
                                        new HttpEntity<>(payload, headers), String.class);
                        if (response.getStatusCode().is2xxSuccessful()) {
                                log.info("Email accepted by delivery provider; subject={}", subject);
                                return true;
                        }
                        log.warn("Email delivery provider rejected message; status={}, subject={}",
                                        response.getStatusCode().value(), subject);
                        return false;
                } catch (RestClientResponseException ex) {
                        // Report provider rejection status, but never log its response body or request
                        // headers.
                        log.warn("Email delivery provider rejected message; subject={}, status={}",
                                        subject, ex.getStatusCode().value());
                        return false;
                } catch (Exception ex) {
                        // Do not log recipient addresses, API credentials, message bodies, or reset
                        // links.
                        // Include only exception class names so connect/read timeout causes are
                        // diagnosable.
                        log.warn("Email delivery failed; subject={}, exceptionType={}, rootCauseType={}",
                                        subject, ex.getClass().getSimpleName(), rootCauseType(ex));
                        return false;
                }
        }

        private String rootCauseType(Throwable exception) {
                Throwable current = exception;
                for (int i = 0; i < 8 && current.getCause() != null && current.getCause() != current; i++) {
                        current = current.getCause();
                }
                return current.getClass().getSimpleName();
        }

        private String escapeHtml(Object value) {
                return value == null ? ""
                                : String.valueOf(value)
                                                .replace("&", "&amp;")
                                                .replace("<", "&lt;")
                                                .replace(">", "&gt;")
                                                .replace("\"", "&quot;")
                                                .replace("'", "&#39;");
        }

        private String safe(Object value) {
                return value == null
                                ? ""
                                : String.valueOf(value);
        }

        private String safeAttribute(String value) {
                if (value == null) {
                        return "";
                }

                return value
                                .replace("&", "&amp;")
                                .replace("\"", "&quot;")
                                .replace("<", "&lt;")
                                .replace(">", "&gt;");
        }
}