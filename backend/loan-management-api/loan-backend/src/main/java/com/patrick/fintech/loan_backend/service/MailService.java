package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class MailService {

    private static final String BREVO_URL = "https://api.brevo.com/v3/smtp/email";

    /**
     * Keep external email calls outside the HTTP request thread.
     *
     * IMPORTANT:
     * Login OTP delivery must remain asynchronous. Making this synchronous
     * can make /auth/login wait for Brevo and recreate the login timeout.
     */
    private final RestTemplate restTemplate;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:}")
    private String from;

    @Value("${app.mail.brevo-api-key:}")
    private String brevoApiKey;

    public MailService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        /*
         * Brevo is an external dependency. Never allow an external mail
         * provider to block a worker indefinitely.
         *
         * 5 seconds to establish the connection and 10 seconds to receive
         * the response are sufficient for transactional email delivery.
         */
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);

        this.restTemplate = new RestTemplate(factory);
    }

    @Async
    public void sendApplicationReceived(Loan loan) {
        if (!mailEnabled) {
            log.info("[EMAIL] Application received: {}",
                    loan != null ? loan.getReferenceNumber() : "unknown");
            return;
        }

        if (loan == null || loan.getBorrower() == null) {
            log.warn("[EMAIL] Application-received email skipped: loan/borrower is null");
            return;
        }

        String to = loan.getBorrower().getEmail();

        if (to == null || to.isBlank()) {
            log.warn("[EMAIL] Application-received email skipped: borrower email is missing");
            return;
        }

        send(
                to,
                "Loan Application Received",
                "<p>Dear " + safe(loan.getBorrower().getFullName()) + ",</p>" +
                        "<p>We have successfully received your loan application.</p>" +
                        "<p><strong>Reference Number:</strong> " +
                        safe(loan.getReferenceNumber()) + "</p>" +
                        "<p><strong>Current Status:</strong> Submitted</p>" +
                        "<p>You can track your application from your borrower dashboard.</p>" +
                        "<p>Thank you.<br/>Loan Management System</p>"
        );
    }

    @Async
    public void sendLoanUpdateComment(Loan loan, String message) {
        if (!mailEnabled) {
            log.info("[EMAIL] Loan update comment: {}",
                    loan != null ? loan.getReferenceNumber() : "unknown");
            return;
        }

        if (loan == null || loan.getBorrower() == null) {
            log.warn("[EMAIL] Loan update email skipped: loan/borrower is null");
            return;
        }

        String to = loan.getBorrower().getEmail();

        if (to == null || to.isBlank()) {
            return;
        }

        send(
                to,
                "New Update on Your Application — " + safe(loan.getReferenceNumber()),
                "<h2>New Message From Your Loan Officer</h2>" +
                        "<p>There's a new update on your loan application <strong>" +
                        safe(loan.getReferenceNumber()) + "</strong>:</p>" +
                        "<blockquote style=\"border-left:3px solid #0D9488;margin:12px 0;padding:8px 16px;color:#374151;background:#f9fafb;\">" +
                        safe(message) +
                        "</blockquote>" +
                        "<p>You can view the full details and respond from your application tracking page.</p>"
        );
    }

    /**
     * Login OTP email.
     *
     * This MUST remain asynchronous.
     *
     * The authentication request should:
     *   1. authenticate the password,
     *   2. create/store the OTP challenge,
     *   3. return otpRequired=true,
     *   4. let this method deliver the email independently.
     *
     * The Brevo API must therefore never be allowed to hold the login
     * HTTP request open.
     */
    @Async
    public void sendLoginOtp(User user, String code) {
        if (user == null) {
            log.warn("[EMAIL] Login OTP delivery skipped: user is null");
            return;
        }

        String email = user.getEmail();

        if (email == null || email.isBlank()) {
            log.warn("[EMAIL] Login OTP delivery skipped: user email is missing");
            return;
        }

        if (code == null || code.isBlank()) {
            log.warn("[EMAIL] Login OTP delivery skipped for {}: OTP code is missing",
                    email);
            return;
        }

        if (!mailEnabled) {
            /*
             * Deliberately do NOT log the OTP itself.
             * This prevents OTP disclosure through production logs.
             */
            log.warn(
                    "[EMAIL] Login OTP delivery is disabled for {}; "
                            + "set MAIL_ENABLED=true in the deployment environment.",
                    email
            );
            return;
        }

        send(
                email,
                "Your sign-in code",
                "<h2>Sign-in Verification Code</h2>" +
                        "<p>Use this code to finish signing in:</p>" +
                        "<p style=\"font-size:28px;font-weight:bold;letter-spacing:6px;color:#0D6B3E;\">" +
                        safe(code) +
                        "</p>" +
                        "<p>This code expires in 5 minutes. If you didn't try to sign in, " +
                        "you can ignore this email — your account is still safe, but consider " +
                        "changing your password if it happens again.</p>"
        );
    }

    @Async
    public void sendLoanApproved(Loan loan) {
        if (!mailEnabled) {
            log.info("[EMAIL] Loan approved: {}",
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

        java.math.BigDecimal requested =
                loan.getRequestedAmountDecimal() != null
                        ? loan.getRequestedAmountDecimal()
                        : loan.getAmountDecimal();

        java.math.BigDecimal approved = loan.getAmountDecimal();

        java.math.BigDecimal applicationFee =
                loan.getApplicationFeeDecimal() != null
                        ? loan.getApplicationFeeDecimal()
                        : java.math.BigDecimal.ZERO;

        if (requested == null) {
            requested = java.math.BigDecimal.ZERO;
        }

        if (approved == null) {
            approved = java.math.BigDecimal.ZERO;
        }

        java.math.BigDecimal net =
                approved.subtract(applicationFee).max(java.math.BigDecimal.ZERO);

        java.math.BigDecimal interestRate = loan.getInterestRateDecimal();
        java.math.BigDecimal managementRate = loan.getManagementFeeRateDecimal();
        java.math.BigDecimal applicationRate = loan.getApplicationFeeRateDecimal();

        send(
                to,
                "Your Loan Has Been Approved — " + safe(loan.getReferenceNumber()),
                "<h2>Loan Approved</h2>" +
                        "<p>Your loan application <strong>" +
                        safe(loan.getReferenceNumber()) +
                        "</strong> has been reviewed and approved.</p>" +
                        "<p>Requested amount: <strong>" +
                        safe(loan.getCurrency()) + " " + requested +
                        "</strong></p>" +
                        "<p>Approved principal: <strong>" +
                        safe(loan.getCurrency()) + " " + approved +
                        "</strong></p>" +
                        "<p>One-time application fee (" +
                        safe(String.valueOf(applicationRate)) +
                        "%): <strong>" +
                        safe(loan.getCurrency()) + " " + applicationFee +
                        "</strong></p>" +
                        "<p>Net amount to be disbursed: <strong>" +
                        safe(loan.getCurrency()) + " " + net +
                        "</strong></p>" +
                        "<p>Contractual interest: <strong>" +
                        safe(String.valueOf(interestRate)) +
                        "% monthly</strong>. Management fee: <strong>" +
                        safe(String.valueOf(managementRate)) +
                        "% monthly</strong>.</p>" +
                        "<p>There is no daily interest accrual. Your repayment schedule " +
                        "is based on the approved contractual monthly terms.</p>" +
                        "<p>You will receive the funds after the disbursement step. " +
                        "Please review your repayment schedule.</p>"
        );
    }

    @Async
    public void sendLoanRejected(Loan loan) {
        if (!mailEnabled) {
            log.info("[EMAIL] Loan rejected: {}",
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
                "Update on Your Loan Application — " + safe(loan.getReferenceNumber()),
                "<h2>Application Update</h2>" +
                        "<p>We regret to inform you that your loan application <strong>" +
                        safe(loan.getReferenceNumber()) +
                        "</strong> was not approved at this time.</p>" +
                        "<p>Reason: " +
                        safe(loan.getRejectionReason() != null
                                ? loan.getRejectionReason()
                                : "See your loan officer") +
                        "</p>"
        );
    }

    @Async
    public void sendPaymentConfirmation(Loan loan, Double amount) {
        if (!mailEnabled) {
            log.info("[EMAIL] Payment confirmed: {} -> {}",
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
                "Payment Received — " + safe(loan.getReferenceNumber()),
                "<h2>Payment Confirmed</h2>" +
                        "<p>We have received your payment of <strong>" +
                        safe(loan.getCurrency()) + " " + amount +
                        "</strong> on loan <strong>" +
                        safe(loan.getReferenceNumber()) +
                        "</strong>.</p>" +
                        "<p>Outstanding balance: <strong>" +
                        safe(loan.getCurrency()) + " " +
                        loan.getOutstandingBalance() +
                        "</strong></p>"
        );
    }

    @Async
    public void sendLoanDisbursed(Loan loan, String method) {
        if (!mailEnabled) {
            log.info("[EMAIL] Loan disbursed: {}",
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
                "Funds Disbursed — " + safe(loan.getReferenceNumber()),
                "<h2>Funds Sent Successfully</h2>" +
                        "<p>Your loan <strong>" +
                        safe(loan.getReferenceNumber()) +
                        "</strong> has been disbursed.</p>" +
                        "<p>Amount: <strong>" +
                        safe(loan.getCurrency()) + " " +
                        loan.getDisbursedAmount() +
                        "</strong> via " + safe(method) + "</p>" +
                        "<p>Your first payment is due on <strong>" +
                        loan.getNextDueDate() +
                        "</strong>. You can track your loan from your borrower dashboard.</p>"
        );
    }

    @Async
    public void sendPaymentDueReminder(Loan loan) {
        if (!mailEnabled) {
            log.info("[EMAIL] Payment reminder: {}",
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
                "Payment Due Reminder — " + safe(loan.getReferenceNumber()),
                "<h2>Payment Reminder</h2>" +
                        "<p>Your next payment on loan <strong>" +
                        safe(loan.getReferenceNumber()) +
                        "</strong> is due on <strong>" +
                        loan.getNextDueDate() +
                        "</strong>.</p>" +
                        "<p>Please ensure your account has sufficient funds to avoid penalties.</p>"
        );
    }

    @Async
    public void sendLoanRestructured(Loan loan, String reason) {
        if (!mailEnabled) {
            log.info("[EMAIL] Loan restructured: {}",
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
                "Your Loan Terms Have Changed — " + safe(loan.getReferenceNumber()),
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
                                ? "<p>Reason: " + safe(reason) + "</p>"
                                : "") +
                        "<p>Your repayment schedule has been recalculated — please review it " +
                        "from your borrower dashboard.</p>"
        );
    }

    @Async
    public void sendLoanWrittenOff(Loan loan, String reason) {
        if (!mailEnabled) {
            log.info("[EMAIL] Loan written off: {}",
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
                "Update on Your Loan — " + safe(loan.getReferenceNumber()),
                "<h2>Account Status Update</h2>" +
                        "<p>Your loan <strong>" +
                        safe(loan.getReferenceNumber()) +
                        "</strong> has been written off by " +
                        safe(org(loan)) +
                        ".</p>" +
                        (reason != null && !reason.isBlank()
                                ? "<p>Reason: " + safe(reason) + "</p>"
                                : "") +
                        "<p>Please contact us if you have questions about what this means for your account.</p>"
        );
    }

    @Async
    public void sendMoratoriumGranted(
            Loan loan,
            int pauseMonths,
            String reason
    ) {
        if (!mailEnabled) {
            log.info("[EMAIL] Moratorium granted: {}",
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
                "Payment Pause Approved — " + safe(loan.getReferenceNumber()),
                "<h2>Payment Moratorium Granted</h2>" +
                        "<p>Your upcoming payments on loan <strong>" +
                        safe(loan.getReferenceNumber()) +
                        "</strong> have been paused for <strong>" +
                        pauseMonths +
                        " month(s)</strong>.</p>" +
                        (reason != null && !reason.isBlank()
                                ? "<p>Reason: " + safe(reason) + "</p>"
                                : "") +
                        "<p>Your next due date is now <strong>" +
                        loan.getNextDueDate() +
                        "</strong>. No action is needed from you during the pause.</p>"
        );
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

    @Async
    public void sendPasswordResetEmail(User user, String resetLink) {
        if (!mailEnabled) {
            log.info(
                    "[EMAIL] Password reset notification requested for {} " +
                            "(email delivery disabled)",
                    user != null ? user.getEmail() : "unknown"
            );
            return;
        }

        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }

        send(
                user.getEmail(),
                "Reset Your LoanSaaS Pro Password",
                "<h2>Password Reset Request</h2>" +
                        "<p>Hi " +
                        safe(user.getName() != null ? user.getName() : "") +
                        ",</p>" +
                        "<p>Click the link below to reset your password. " +
                        "This link expires in 1 hour.</p>" +
                        "<p><a href=\"" +
                        safeAttribute(resetLink) +
                        "\" style=\"background:#0D9488;color:#fff;padding:12px 24px;" +
                        "border-radius:8px;text-decoration:none;font-weight:bold;\">" +
                        "Reset Password</a></p>" +
                        "<p>If you did not request a password reset, please ignore this email.</p>"
        );
    }

    /**
     * Sent alongside SMS when available.
     */
    @Async
    public void sendESignatureRequest(
            Borrower borrower,
            String orgName,
            String signLink
    ) {
        if (!mailEnabled) {
            log.info(
                    "[EMAIL] E-signature notification requested for {} " +
                            "(email delivery disabled)",
                    borrower != null ? borrower.getEmail() : "unknown"
            );
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
                        "<p>Dear " + safe(borrower.getFullName()) + ",</p>" +
                        "<p>" + safe(orgName) +
                        " has sent your loan agreement for e-signature. " +
                        "Click below to review and sign it.</p>" +
                        "<p><a href=\"" +
                        safeAttribute(signLink) +
                        "\" style=\"background:#0D9488;color:#fff;padding:12px 24px;" +
                        "border-radius:8px;text-decoration:none;font-weight:bold;\">" +
                        "Review &amp; Sign</a></p>" +
                        "<p>We've also texted a verification code to your phone — " +
                        "you'll need to enter it on that page to complete your signature.</p>" +
                        "<p>This link expires in 7 days. If you weren't expecting this, " +
                        "please contact your loan officer.</p>"
        );
    }

    @Async
    public void sendNewUserCredentials(
            User user,
            String tempPassword,
            String loginLink
    ) {
        if (!mailEnabled) {
            log.info(
                    "[EMAIL] New account notification requested for {} " +
                            "(email delivery disabled)",
                    user != null ? user.getEmail() : "unknown"
            );
            return;
        }

        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }

        String organizationText =
                user.getOrganization() != null
                        ? " at " + safe(user.getOrganization().getName())
                        : "";

        String roleText =
                user.getRole() != null
                        ? " with the role <strong>" +
                        safe(user.getRole().getName()) +
                        "</strong>"
                        : "";

        send(
                user.getEmail(),
                "Your LoanSaaS Pro Account Has Been Created",
                "<h2>Welcome to LoanSaaS Pro</h2>" +
                        "<p>Hi " +
                        safe(user.getName() != null ? user.getName() : "") +
                        ",</p>" +
                        "<p>An administrator" +
                        organizationText +
                        " created a staff account for you" +
                        roleText +
                        ".</p>" +
                        "<p><strong>Email:</strong> " +
                        safe(user.getEmail()) +
                        "<br/>" +
                        "<strong>Temporary password:</strong> <code " +
                        "style=\"background:#f3f4f6;padding:2px 8px;border-radius:8px;" +
                        "font-size:15px;\">" +
                        safe(tempPassword) +
                        "</code></p>" +
                        "<p><a href=\"" +
                        safeAttribute(loginLink) +
                        "\" style=\"background:#0D9488;color:#fff;padding:12px 24px;" +
                        "border-radius:8px;text-decoration:none;font-weight:bold;\">" +
                        "Sign In</a></p>" +
                        "<p>You'll be asked to set your own password the first time you " +
                        "sign in — the temporary one above will stop working once you do.</p>" +
                        "<p>If you weren't expecting this account, please contact your administrator.</p>"
        );
    }

    @Async
    public void sendBorrowerWelcome(Borrower borrower) {
        if (!mailEnabled) {
            log.info(
                    "[EMAIL] Borrower profile created: {}",
                    borrower != null ? borrower.getEmail() : "unknown"
            );
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
                        "Our team will reach out if we need any further information from you.</p>" +
                        "<p>Thank you.<br/>Loan Management System</p>"
        );
    }

    /**
     * Sent alongside SMS when available, and as the primary channel when
     * the borrower has no phone on file.
     */
    @Async
    public void sendESignatureRequest(
            Borrower borrower,
            String orgName,
            String signLink,
            String otp
    ) {
        if (!mailEnabled) {
            log.warn(
                    "[EMAIL] E-signature delivery is disabled for {}; " +
                            "no link or OTP is logged.",
                    borrower != null ? borrower.getEmail() : "unknown"
            );
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
                        "\" style=\"background:#0D9488;color:#fff;padding:12px 24px;" +
                        "border-radius:8px;text-decoration:none;font-weight:bold;\">" +
                        "Review &amp; Sign</a></p>" +
                        "<p>Enter this verification code on that page to complete your " +
                        "signature (also sent by SMS, if we have a valid number on file):</p>" +
                        "<p style=\"font-size:28px;font-weight:bold;letter-spacing:6px;color:#0D6B3E;\">" +
                        safe(otp) +
                        "</p>" +
                        "<p>This link expires in 7 days. If you weren't expecting this, " +
                        "please contact your loan officer.</p>"
        );
    }

    @Async
    public void sendDocumentVerified(
            Borrower borrower,
            String documentType
    ) {
        if (!mailEnabled) {
            log.info(
                    "[EMAIL] Document verified: {} for {}",
                    documentType,
                    borrower != null ? borrower.getEmail() : "unknown"
            );
            return;
        }

        if (borrower == null ||
                borrower.getEmail() == null ||
                borrower.getEmail().isBlank()) {
            return;
        }

        send(
                borrower.getEmail(),
                "Document Verified: " + humanizeDocType(documentType),
                "<h2>Document Verified</h2>" +
                        "<p>Dear " +
                        safe(borrower.getFullName()) +
                        ",</p>" +
                        "<p>Your <strong>" +
                        safe(humanizeDocType(documentType)) +
                        "</strong> has been verified. No further action is needed for this document.</p>" +
                        "<p>You can check your overall application status from your borrower dashboard.</p>"
        );
    }

    @Async
    public void sendDocumentRejected(
            Borrower borrower,
            String documentType,
            String reason
    ) {
        if (!mailEnabled) {
            log.info(
                    "[EMAIL] Document rejected: {} for {}",
                    documentType,
                    borrower != null ? borrower.getEmail() : "unknown"
            );
            return;
        }

        if (borrower == null ||
                borrower.getEmail() == null ||
                borrower.getEmail().isBlank()) {
            return;
        }

        send(
                borrower.getEmail(),
                "Action Needed: " + humanizeDocType(documentType) + " Rejected",
                "<h2>Document Rejected</h2>" +
                        "<p>Dear " +
                        safe(borrower.getFullName()) +
                        ",</p>" +
                        "<p>Your <strong>" +
                        safe(humanizeDocType(documentType)) +
                        "</strong> could not be accepted." +
                        (reason != null && !reason.isBlank()
                                ? " Reason: " + safe(reason)
                                : "") +
                        "</p>" +
                        "<p>Please upload a corrected document from your application tracking page.</p>"
        );
    }

    @Async
    public void sendDocumentReplacementRequested(
            Borrower borrower,
            String documentType,
            String note
    ) {
        if (!mailEnabled) {
            log.info(
                    "[EMAIL] Document replacement requested: {} for {}",
                    documentType,
                    borrower != null ? borrower.getEmail() : "unknown"
            );
            return;
        }

        if (borrower == null ||
                borrower.getEmail() == null ||
                borrower.getEmail().isBlank()) {
            return;
        }

        send(
                borrower.getEmail(),
                "Please Re-upload: " + humanizeDocType(documentType),
                "<h2>Replacement Document Requested</h2>" +
                        "<p>Dear " +
                        safe(borrower.getFullName()) +
                        ",</p>" +
                        "<p>We need a new copy of your <strong>" +
                        safe(humanizeDocType(documentType)) +
                        "</strong>." +
                        (note != null && !note.isBlank()
                                ? " " + safe(note)
                                : "") +
                        "</p>" +
                        "<p>Please upload it from your application tracking page as soon as possible " +
                        "to avoid delays.</p>"
        );
    }

    @Async
    public void sendOverdueReminder(
            Loan loan,
            Integer daysOverdue
    ) {
        if (!mailEnabled) {
            log.info(
                    "[EMAIL] Overdue reminder: {} ({} days)",
                    loan != null ? loan.getReferenceNumber() : "unknown",
                    daysOverdue
            );
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
                        "</strong> is currently marked as <strong>OVERDUE</strong> by <strong>" +
                        daysOverdue +
                        "</strong> days.</p>" +
                        "<p>Please settle your outstanding balance immediately to avoid further " +
                        "penalization, legal escalation, or collection queue assignment.</p>"
        );
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

            sb.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1))
                    .append(' ');
        }

        return sb.toString().trim();
    }

    /**
     * Sends through Brevo HTTP API.
     *
     * This method is intentionally private and is called from @Async public
     * methods. Therefore Brevo network latency does not block the login HTTP
     * request.
     */
    private void send(
            String to,
            String subject,
            String html
    ) {
        if (to == null || to.isBlank()) {
            log.warn("[EMAIL] Email send skipped: recipient is blank");
            return;
        }

        if (subject == null || subject.isBlank()) {
            log.warn("[EMAIL] Email send skipped for {}: subject is blank", to);
            return;
        }

        if (html == null) {
            log.warn("[EMAIL] Email send skipped for {}: body is null", to);
            return;
        }

        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            log.error(
                    "[EMAIL] Email send skipped for {}: BREVO_API_KEY is not configured",
                    to
            );
            return;
        }

        if (from == null || from.isBlank()) {
            log.error(
                    "[EMAIL] Email send skipped for {}: MAIL_FROM is not configured",
                    to
            );
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.set("api-key", brevoApiKey);

            Map<String, Object> payload = Map.of(
                    "sender", Map.of("email", from),
                    "to", List.of(Map.of("email", to)),
                    "subject", subject,
                    "htmlContent", html
            );

            ResponseEntity<String> response = restTemplate.exchange(
                    BREVO_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info(
                        "[EMAIL] Email accepted by Brevo for {}: {}",
                        to,
                        subject
                );
            } else {
                log.error(
                        "[EMAIL] Brevo rejected email for {}. HTTP status: {}. Subject: {}",
                        to,
                        response.getStatusCode().value(),
                        subject
                );
            }

        } catch (Exception e) {
            /*
             * Never propagate an external email-provider failure back into
             * authentication, loan approval, disbursement, payment, etc.
             *
             * The transactional operation must remain authoritative.
             */
            log.error(
                    "[EMAIL] Email delivery failed for {}. Subject: {}. Error: {}",
                    to,
                    subject,
                    e.getMessage()
            );
        }
    }

    /**
     * Prevent null values from appearing as the literal string "null".
     */
    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * Minimal HTML attribute escaping for URLs placed inside href attributes.
     */
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