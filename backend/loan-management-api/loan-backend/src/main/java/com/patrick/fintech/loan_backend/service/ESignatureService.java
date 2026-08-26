package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.ESignatureRequest;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.ESignatureRequestRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ESignatureService {

        private final ESignatureRequestRepository esignRepo;
        private final LoanRepository loanRepo;
        private final SmsService smsService;
        private final MailService mailService;
        private final AuditService auditService;

        /**
         * Production frontend URL.
         *
         * Example:
         * app.frontend.url=https://fintech01-aydw.vercel.app
         */
        @Value("${app.frontend.url:http://localhost:3000}")
        private String frontendUrl;

        /**
         * How long a signing request remains valid.
         *
         * Default: 7 days.
         */
        @Value("${app.esignature.expiry-days:7}")
        private long expiryDays;

        /**
         * Maximum incorrect OTP attempts.
         */
        @Value("${app.esignature.max-otp-attempts:5}")
        private int maxOtpAttempts;

        /**
         * Minimum interval between OTP resends.
         *
         * Default: 60 seconds.
         */
        @Value("${app.esignature.resend-cooldown-seconds:60}")
        private long resendCooldownSeconds;

        /**
         * Maximum OTP resends for one signing request.
         *
         * The current ESignatureRequest model does not expose a
         * dedicated resend counter, so cooldown remains the primary
         * protection here.
         */
        @Value("${app.esignature.max-resends:5}")
        private int maxResends;

        private static final SecureRandom RANDOM = new SecureRandom();

        private static final DateTimeFormatter DOCUMENT_DATE_FORMAT = DateTimeFormatter.ofPattern(
                        "dd MMM yyyy HH:mm",
                        Locale.US);

        private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(
                        2,
                        RoundingMode.HALF_UP);

        private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

        private static final BigDecimal MONTHLY_INTEREST_RATE = new BigDecimal("5.00");

        private static final BigDecimal MONTHLY_MANAGEMENT_FEE_RATE = new BigDecimal("5.00");

        private static final BigDecimal APPLICATION_FEE_RATE = new BigDecimal("2.00");

        // ================================================================
        // INITIATE SIGNATURE REQUEST
        // ================================================================

        @Transactional
        public ESignatureRequest initiate(
                        Long loanId,
                        String documentType,
                        String initiatedBy) {

                if (loanId == null) {
                        throw new IllegalArgumentException(
                                        "Loan ID is required");
                }

                Loan loan = loanRepo.findById(
                                loanId).orElseThrow(
                                                () -> new IllegalArgumentException(
                                                                "Loan not found: " + loanId));

                Borrower borrower = loan.getBorrower();

                if (borrower == null) {
                        throw new IllegalStateException(
                                        "Loan has no borrower on file");
                }

                Organization organization = loan.getOrganization();

                if (organization == null
                                || organization.getId() == null) {
                        throw new IllegalStateException(
                                        "Loan has no valid organization");
                }

                boolean hasPhone = borrower.getPhone() != null
                                && !borrower.getPhone().isBlank();

                boolean hasEmail = borrower.getEmail() != null
                                && !borrower.getEmail().isBlank();

                if (!hasPhone && !hasEmail) {
                        throw new IllegalStateException(
                                        "Borrower has no phone number or email address "
                                                        + "on file to receive the signing OTP");
                }

                List<ESignatureRequest> existingRequests = esignRepo.findByLoan_IdOrderByCreatedAtDesc(
                                loanId);

                if (existingRequests != null) {

                        for (ESignatureRequest existing : existingRequests) {

                                if (existing == null
                                                || existing.getStatus() == null) {
                                        continue;
                                }

                                ESignatureRequest.SignatureStatus status = existing.getStatus();

                                if (status != ESignatureRequest.SignatureStatus.SIGNED
                                                && status != ESignatureRequest.SignatureStatus.DECLINED
                                                && status != ESignatureRequest.SignatureStatus.EXPIRED) {

                                        throw new IllegalStateException(
                                                        "An active e-signature request already exists "
                                                                        + "for this loan. Resend the existing OTP "
                                                                        + "instead of creating another signing request.");
                                }
                        }
                }

                LocalDateTime now = LocalDateTime.now();

                String token = UUID.randomUUID()
                                .toString()
                                .replace(
                                                "-",
                                                "");

                String otp = generateOtp();

                String documentTypeValue = clean(documentType) != null
                                ? clean(documentType)
                                : "LOAN_AGREEMENT";

                String documentSnapshot = renderAgreement(
                                loan,
                                borrower,
                                now);

                String documentHash = sha256(documentSnapshot);

                String signLink = buildSigningLink(
                                token);

                ESignatureRequest request = ESignatureRequest.builder()
                                .loan(loan)
                                .borrower(borrower)
                                .organization(organization)
                                .signingToken(token)
                                .documentType(documentTypeValue)
                                .status(
                                                ESignatureRequest.SignatureStatus.OTP_SENT)
                                .otpCodeHash(
                                                sha256(otp))
                                .otpAttempts(0)
                                .otpSentAt(now)
                                .documentSnapshot(
                                                documentSnapshot)
                                .documentHash(
                                                documentHash)
                                .consentText(
                                                "By entering the verification code and "
                                                                + "typing your full legal name, you "
                                                                + "confirm that you have reviewed "
                                                                + "the loan agreement presented to "
                                                                + "you and agree that your electronic "
                                                                + "signature constitutes your "
                                                                + "acceptance of the agreement, "
                                                                + "subject to applicable law.")
                                .createdBy(
                                                clean(initiatedBy))
                                .sentAt(now)
                                .build();

                request = esignRepo.save(
                                request);

                sendSigningNotifications(
                                request,
                                borrower,
                                loan,
                                signLink,
                                otp);

                auditService.log(
                                organization,
                                null,
                                "ESIGNATURE_INITIATED",
                                "LOAN",
                                String.valueOf(
                                                loanId),
                                "E-signature request created for document type "
                                                + documentTypeValue
                                                + " by "
                                                + safeActor(initiatedBy),
                                null,
                                null,
                                "E-Signature");

                log.info(
                                "E-signature request {} initiated for loan {} by actor {}",
                                safeRequestId(request),
                                loanId,
                                safeActor(initiatedBy));

                return request;
        }

        // ================================================================
        // RESEND OTP
        // ================================================================

        @Transactional
        public ESignatureRequest resendOtp(
                        String token) {

                ESignatureRequest request = getActiveByToken(
                                token);

                LocalDateTime now = LocalDateTime.now();

                if (request.getOtpSentAt() != null) {

                        Duration elapsed = Duration.between(
                                        request.getOtpSentAt(),
                                        now);

                        if (elapsed.getSeconds() < resendCooldownSeconds) {

                                long remaining = resendCooldownSeconds
                                                - elapsed.getSeconds();

                                throw new IllegalStateException(
                                                "Please wait "
                                                                + Math.max(
                                                                                1,
                                                                                remaining)
                                                                + " seconds before requesting "
                                                                + "another verification code.");
                        }
                }

                String otp = generateOtp();

                request.setOtpCodeHash(
                                sha256(otp));

                request.setOtpSentAt(
                                now);

                request.setOtpAttempts(0);

                request = esignRepo.save(
                                request);

                String signLink = buildSigningLink(
                                request.getSigningToken());

                Borrower borrower = request.getBorrower();

                Loan loan = request.getLoan();

                if (borrower == null
                                || loan == null) {

                        throw new IllegalStateException(
                                        "Signing request is missing borrower or loan information");
                }

                sendSigningNotifications(
                                request,
                                borrower,
                                loan,
                                signLink,
                                otp);

                log.info(
                                "E-signature OTP resent for request {} and loan {}",
                                safeRequestId(request),
                                loan.getId());

                return request;
        }

        // ================================================================
        // VERIFY OTP AND SIGN
        // ================================================================

        @Transactional
        public ESignatureRequest verifyAndSign(
                        String token,
                        String otp,
                        String typedFullName,
                        String ipAddress,
                        String userAgent) {

                ESignatureRequest request = getActiveByToken(
                                token);

                String suppliedOtp = clean(otp);

                if (suppliedOtp == null
                                || !suppliedOtp.matches(
                                                "\\d{6}")) {

                        incrementOtpAttempt(
                                        request);

                        throw new IllegalArgumentException(
                                        "Verification code must contain exactly 6 digits.");
                }

                int attempts = request.getOtpAttempts() == null
                                ? 0
                                : request.getOtpAttempts();

                if (attempts >= maxOtpAttempts) {

                        throw new IllegalStateException(
                                        "Too many incorrect verification attempts. "
                                                        + "Request a new verification code.");
                }

                String signerName = clean(typedFullName);

                if (signerName == null
                                || signerName.length() < 3) {

                        throw new IllegalArgumentException(
                                        "Please type your full legal name to sign.");
                }

                if (signerName.length() > 200) {

                        throw new IllegalArgumentException(
                                        "Signer name is too long.");
                }

                String suppliedHash = sha256(
                                suppliedOtp);

                if (!secureEquals(
                                suppliedHash,
                                request.getOtpCodeHash())) {

                        incrementOtpAttempt(
                                        request);

                        throw new IllegalArgumentException(
                                        "Incorrect verification code.");
                }

                LocalDateTime now = LocalDateTime.now();

                request.setStatus(
                                ESignatureRequest.SignatureStatus.SIGNED);

                request.setSignerFullNameTyped(
                                signerName);

                request.setSignerIpAddress(
                                sanitizeIpAddress(
                                                ipAddress));

                request.setSignerUserAgent(
                                sanitizeUserAgent(
                                                userAgent));

                request.setSignedAt(
                                now);

                /*
                 * Invalidate OTP after successful signing.
                 */
                request.setOtpCodeHash(null);

                request.setOtpAttempts(
                                maxOtpAttempts);

                request = esignRepo.save(
                                request);

                auditService.log(
                                request.getOrganization(),
                                null,
                                "ESIGNATURE_SIGNED",
                                "LOAN",
                                String.valueOf(
                                                request.getLoan().getId()),
                                "Loan agreement successfully signed electronically.",
                                null,
                                null,
                                "E-Signature");

                log.info(
                                "Loan {} e-signature completed for request {}",
                                request.getLoan().getId(),
                                safeRequestId(request));

                return request;
        }

        // ================================================================
        // DECLINE SIGNATURE
        // ================================================================

        @Transactional
        public ESignatureRequest decline(
                        String token,
                        String reason) {

                ESignatureRequest request = getActiveByToken(
                                token);

                String cleanReason = clean(reason);

                if (cleanReason == null) {
                        cleanReason = "Borrower declined to sign.";
                }

                if (cleanReason.length() > 1000) {

                        cleanReason = cleanReason.substring(
                                        0,
                                        1000);
                }

                request.setStatus(
                                ESignatureRequest.SignatureStatus.DECLINED);

                request.setDeclinedAt(
                                LocalDateTime.now());

                request.setDeclineReason(
                                cleanReason);

                request.setOtpCodeHash(null);

                request = esignRepo.save(
                                request);

                auditService.log(
                                request.getOrganization(),
                                null,
                                "ESIGNATURE_DECLINED",
                                "LOAN",
                                String.valueOf(
                                                request.getLoan().getId()),
                                "Borrower declined the electronic signature request.",
                                null,
                                null,
                                "E-Signature");

                log.info(
                                "Loan {} e-signature request {} declined",
                                request.getLoan().getId(),
                                safeRequestId(request));

                return request;
        }

        // ================================================================
        // GET REQUEST BY TOKEN
        // ================================================================

        @Transactional(readOnly = true)
        public ESignatureRequest getByToken(
                        String token) {

                String cleanToken = clean(token);

                if (cleanToken == null) {

                        throw new IllegalArgumentException(
                                        "Signing token is required.");
                }

                return esignRepo.findBySigningToken(
                                cleanToken)
                                .orElseThrow(
                                                () -> new IllegalArgumentException(
                                                                "Signing link not found."));
        }

        // ================================================================
        // HISTORY
        // ================================================================

        @Transactional(readOnly = true)
        public List<ESignatureRequest> history(
                        Long loanId) {

                if (loanId == null) {
                        throw new IllegalArgumentException(
                                        "Loan ID is required.");
                }

                loanRepo.findById(
                                loanId)
                                .orElseThrow(
                                                () -> new IllegalArgumentException(
                                                                "Loan not found: " + loanId));

                return esignRepo
                                .findByLoan_IdOrderByCreatedAtDesc(
                                                loanId);
        }

        // ================================================================
        // GET ACTIVE REQUEST
        // ================================================================

        private ESignatureRequest getActiveByToken(
                        String token) {

                ESignatureRequest request = getByToken(
                                token);

                if (request.getStatus() == ESignatureRequest.SignatureStatus.SIGNED) {

                        throw new IllegalStateException(
                                        "This document has already been signed.");
                }

                if (request.getStatus() == ESignatureRequest.SignatureStatus.DECLINED) {

                        throw new IllegalStateException(
                                        "This signing request was declined.");
                }

                if (request.getStatus() == ESignatureRequest.SignatureStatus.EXPIRED) {

                        throw new IllegalStateException(
                                        "This signing link has expired. "
                                                        + "Ask your loan officer to create "
                                                        + "a new signing request.");
                }

                if (request.isExpired()) {

                        request.setStatus(
                                        ESignatureRequest.SignatureStatus.EXPIRED);

                        request.setOtpCodeHash(null);

                        esignRepo.save(
                                        request);

                        throw new IllegalStateException(
                                        "This signing link has expired. "
                                                        + "Ask your loan officer to create "
                                                        + "a new signing request.");
                }

                if (request.getStatus() != ESignatureRequest.SignatureStatus.OTP_SENT) {

                        throw new IllegalStateException(
                                        "This signing request is not available for signing.");
                }

                return request;
        }

        // ================================================================
        // SEND SMS + EMAIL
        // ================================================================

        private void sendSigningNotifications(
                        ESignatureRequest request,
                        Borrower borrower,
                        Loan loan,
                        String signLink,
                        String otp) {

                boolean hasPhone = borrower.getPhone() != null
                                && !borrower.getPhone().isBlank();

                boolean hasEmail = borrower.getEmail() != null
                                && !borrower.getEmail().isBlank();

                if (!hasPhone && !hasEmail) {

                        throw new IllegalStateException(
                                        "Borrower has no notification channel.");
                }

                String organizationName = orgName(
                                loan);

                if (hasPhone) {

                        try {

                                String message = String.format(
                                                Locale.US,
                                                "%s: Sign your loan agreement here: %s "
                                                                + "Verification code: %s. "
                                                                + "Code expires with the signing "
                                                                + "request. If you did not request "
                                                                + "this, contact your loan officer.",
                                                organizationName,
                                                signLink,
                                                otp);

                                smsService.sendCustom(
                                                borrower.getPhone(),
                                                message);

                        } catch (Exception ex) {

                                log.error(
                                                "Unable to send e-signature SMS for request {} "
                                                                + "and loan {}",
                                                safeRequestId(request),
                                                loan.getId(),
                                                ex);
                        }
                }

                if (hasEmail) {

                        try {

                                mailService.sendESignatureRequest(
                                                borrower,
                                                organizationName,
                                                signLink,
                                                otp);

                        } catch (Exception ex) {

                                log.error(
                                                "Unable to send e-signature email for request {} "
                                                                + "and loan {}",
                                                safeRequestId(request),
                                                loan.getId(),
                                                ex);
                        }
                }
        }

        // ================================================================
        // RENDER IMMUTABLE AGREEMENT SNAPSHOT
        // ================================================================

        private String renderAgreement(
                        Loan loan,
                        Borrower borrower,
                        LocalDateTime generatedAt) {

                Organization organization = loan.getOrganization();

                String organizationName = organization != null
                                && organization.getName() != null
                                                ? organization.getName().trim()
                                                : "Lender";

                String borrowerName = borrower.getFullName() != null
                                && !borrower.getFullName().isBlank()
                                                ? borrower.getFullName().trim()
                                                : buildBorrowerName(
                                                                borrower);

                String currency = loan.getCurrency() != null
                                ? loan.getCurrency().trim()
                                : "";

                /*
                 * Loan monetary values are BigDecimal.
                 */
                BigDecimal amount = money(
                                loan.getAmountDecimal());

                BigDecimal interestRate = money(
                                loan.getInterestRateDecimal());

                BigDecimal managementFeeRate = money(
                                loan.getManagementFeeRateDecimal());

                BigDecimal applicationFeeRate = money(
                                loan.getApplicationFeeRateDecimal());

                BigDecimal applicationFee = money(
                                loan.getApplicationFeeDecimal());

                BigDecimal totalRepayable = money(
                                loan.getTotalRepayableDecimal());

                int durationMonths = loan.getDurationMonths() != null
                                ? loan.getDurationMonths()
                                : 0;

                String frequency = loan.getRepaymentFrequency() != null
                                ? loan.getRepaymentFrequency().name()
                                : "MONTHLY";

                String purpose = loan.getPurpose() != null
                                && !loan.getPurpose().isBlank()
                                                ? loan.getPurpose().trim()
                                                : "General";

                String reference = loan.getReferenceNumber() != null
                                && !loan.getReferenceNumber().isBlank()
                                                ? loan.getReferenceNumber().trim()
                                                : "N/A";

                BigDecimal totalMonthlyChargeRate = money(
                                interestRate.add(
                                                managementFeeRate));

                return String.format(
                                Locale.US,

                                "LOAN AGREEMENT%n"
                                                + "Lender: %s%n"
                                                + "Borrower: %s%n"
                                                + "Loan Reference: %s%n"
                                                + "Principal Amount: %s %s%n"
                                                + "Monthly Interest Rate: %s%%%n"
                                                + "Monthly Management Fee Rate: %s%%%n"
                                                + "Total Monthly Charge Rate: %s%%%n"
                                                + "One-Time Processing Fee Rate: %s%%%n"
                                                + "One-Time Processing Fee: %s %s%n"
                                                + "Term: %d months, repaid %s%n"
                                                + "Total Repayable: %s %s%n"
                                                + "Purpose: %s%n%n"
                                                + "IMPORTANT DISBURSEMENT TERM%n"
                                                + "The application fee is deducted once from the "
                                                + "gross loan principal at disbursement. The "
                                                + "borrower receives the gross principal less the "
                                                + "2% application fee. Interest and management fee "
                                                + "are calculated according to the contractual "
                                                + "loan terms and are not reduced by the application "
                                                + "fee deduction.%n%n"
                                                + "By signing below, the Borrower acknowledges "
                                                + "receipt of the loan terms above, agrees to "
                                                + "repay the loan in accordance with the repayment "
                                                + "schedule provided, and consents to the Lender's "
                                                + "terms and conditions and applicable privacy "
                                                + "requirements.%n%n"
                                                + "Document generated: %s%n",

                                organizationName,
                                borrowerName,
                                reference,
                                currency,
                                formatMoney(amount),
                                formatRate(interestRate),
                                formatRate(managementFeeRate),
                                formatRate(totalMonthlyChargeRate),
                                formatRate(applicationFeeRate),
                                currency,
                                formatMoney(applicationFee),
                                durationMonths,
                                frequency,
                                currency,
                                formatMoney(totalRepayable),
                                purpose,
                                generatedAt.format(
                                                DOCUMENT_DATE_FORMAT));
        }

        // ================================================================
        // MONEY FORMATTING
        // ================================================================

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

                return money(
                                value).setScale(
                                                2,
                                                RoundingMode.HALF_UP)
                                .toPlainString();
        }

        private String formatRate(
                        BigDecimal value) {

                return money(
                                value).setScale(
                                                2,
                                                RoundingMode.HALF_UP)
                                .toPlainString();
        }

        // ================================================================
        // OTP GENERATOR
        // ================================================================

        private String generateOtp() {

                return String.format(
                                Locale.US,
                                "%06d",
                                RANDOM.nextInt(
                                                1_000_000));
        }

        // ================================================================
        // SIGNING LINK
        // ================================================================

        private String buildSigningLink(
                        String token) {

                String base = frontendUrl != null
                                ? frontendUrl.trim()
                                : "";

                while (base.endsWith("/")
                                && !base.isBlank()) {

                        base = base.substring(
                                        0,
                                        base.length() - 1);
                }

                if (base.isBlank()) {

                        throw new IllegalStateException(
                                        "Frontend URL is not configured.");
                }

                if (token == null
                                || token.isBlank()) {

                        throw new IllegalArgumentException(
                                        "Signing token is required.");
                }

                return base
                                + "/sign/"
                                + token;
        }

        // ================================================================
        // OTP ATTEMPT
        // ================================================================

        private void incrementOtpAttempt(
                        ESignatureRequest request) {

                int attempts = request.getOtpAttempts() == null
                                ? 0
                                : request.getOtpAttempts();

                attempts++;

                request.setOtpAttempts(
                                attempts);

                /*
                 * Lock the request after reaching the maximum number of
                 * incorrect attempts. The actual status remains available
                 * only until the controller/service decides to resend.
                 */
                if (attempts >= maxOtpAttempts) {

                        request.setOtpCodeHash(
                                        null);
                }

                esignRepo.save(
                                request);
        }

        // ================================================================
        // CONSTANT-TIME STRING COMPARISON
        // ================================================================

        private boolean secureEquals(
                        String expected,
                        String supplied) {

                if (expected == null
                                || supplied == null) {

                        return false;
                }

                return MessageDigest.isEqual(
                                expected.getBytes(
                                                StandardCharsets.UTF_8),
                                supplied.getBytes(
                                                StandardCharsets.UTF_8));
        }

        // ================================================================
        // SHA-256
        // ================================================================

        private String sha256(
                        String value) {

                if (value == null) {
                        return null;
                }

                try {

                        MessageDigest digest = MessageDigest.getInstance(
                                        "SHA-256");

                        byte[] hash = digest.digest(
                                        value.getBytes(
                                                        StandardCharsets.UTF_8));

                        StringBuilder result = new StringBuilder(
                                        hash.length * 2);

                        for (byte b : hash) {

                                result.append(
                                                String.format(
                                                                Locale.ROOT,
                                                                "%02x",
                                                                b & 0xff));
                        }

                        return result.toString();

                } catch (Exception ex) {

                        throw new IllegalStateException(
                                        "Unable to calculate SHA-256 hash.",
                                        ex);
                }
        }

        // ================================================================
        // BORROWER NAME
        // ================================================================

        private String buildBorrowerName(
                        Borrower borrower) {

                if (borrower == null) {
                        return "";
                }

                String first = borrower.getFirstName() != null
                                ? borrower.getFirstName().trim()
                                : "";

                String last = borrower.getLastName() != null
                                ? borrower.getLastName().trim()
                                : "";

                return (first
                                + " "
                                + last).trim();
        }

        // ================================================================
        // ORGANIZATION NAME
        // ================================================================

        private String orgName(
                        Loan loan) {

                if (loan == null
                                || loan.getOrganization() == null
                                || loan.getOrganization().getName() == null
                                || loan.getOrganization().getName().isBlank()) {

                        return "LoanSaaS";
                }

                return loan
                                .getOrganization()
                                .getName()
                                .trim();
        }

        // ================================================================
        // STRING CLEANING
        // ================================================================

        private String clean(
                        String value) {

                if (value == null) {
                        return null;
                }

                String cleaned = value.trim();

                return cleaned.isEmpty()
                                ? null
                                : cleaned;
        }

        // ================================================================
        // IP ADDRESS SANITIZATION
        // ================================================================

        private String sanitizeIpAddress(
                        String ipAddress) {

                String value = clean(ipAddress);

                if (value == null) {
                        return null;
                }

                if (value.length() > 100) {

                        return value.substring(
                                        0,
                                        100);
                }

                return value;
        }

        // ================================================================
        // USER AGENT SANITIZATION
        // ================================================================

        private String sanitizeUserAgent(
                        String userAgent) {

                String value = clean(userAgent);

                if (value == null) {
                        return null;
                }

                if (value.length() > 1000) {

                        return value.substring(
                                        0,
                                        1000);
                }

                return value;
        }

        // ================================================================
        // SAFE REQUEST ID
        // ================================================================

        private String safeRequestId(
                        ESignatureRequest request) {

                if (request == null
                                || request.getId() == null) {

                        return "NEW";
                }

                return String.valueOf(
                                request.getId());
        }

        // ================================================================
        // SAFE ACTOR
        // ================================================================

        private String safeActor(
                        String actor) {

                String value = clean(actor);

                if (value == null) {
                        return "SYSTEM";
                }

                if (value.length() > 100) {

                        return value.substring(
                                        0,
                                        100);
                }

                return value;
        }
}