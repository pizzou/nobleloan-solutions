package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.config.JwtUtils;
import com.patrick.fintech.loan_backend.dto.*;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import com.patrick.fintech.loan_backend.service.*;
import com.patrick.fintech.loan_backend.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final AuthService authService;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final MfaService mfaService;
    private final MailService mailService;
    private final SmsService smsService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private static final SecureRandom OTP_RANDOM = new SecureRandom();

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest req) {
        User created = authService.register(req);
        auditService.log(created.getOrganization(), created, "USER_REGISTERED", "AUTH",
                String.valueOf(created.getId()), created.getName() + " (" + created.getEmail() + ") registered",
                null, null, "Authentication");
        return ResponseEntity.ok(safe(created));
    }

    /**
     * Staff roles that must complete a second factor at every login.
     *
     * ADMIN and MANAGER use a server-generated, short-lived OTP delivered to
     * BOTH the registered email address and registered mobile number. This is
     * deliberately independent of TOTP/Auth­enticator enrollment so staff are
     * not forced to use an authenticator application as their only login factor.
     */
    private static final java.util.Set<String> EMAIL_SMS_OTP_ROLES = java.util.Set.of("ADMIN", "MANAGER");

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final int OTP_EXPIRY_MINUTES = 5;

    @PostMapping("/login")
    @Transactional
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest req) {
        if (req == null || req.getEmail() == null || req.getEmail().isBlank()
                || req.getPassword() == null || req.getPassword().isBlank()) {
            throw new RuntimeException("Email and password are required");
        }

        String email = req.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email).orElse(null);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        if (user != null && user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            long minutesLeft = java.time.Duration.between(now, user.getLockedUntil()).toMinutes() + 1;
            auditService.log(user.getOrganization(), user, "LOGIN_BLOCKED_ACCOUNT_LOCKED", "AUTH",
                    String.valueOf(user.getId()), "Login attempt rejected — account locked", null, null,
                    "Authentication");
            throw new RuntimeException(
                    "Account locked due to repeated failed logins. Try again in " + minutesLeft + " minute(s).");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, req.getPassword()));
        } catch (Exception e) {
            if (user != null) {
                int attempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
                user.setFailedLoginAttempts(attempts);
                if (attempts >= MAX_FAILED_ATTEMPTS) {
                    user.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
                    userRepository.save(user);
                    auditService.log(user.getOrganization(), user, "ACCOUNT_LOCKED", "AUTH",
                            String.valueOf(user.getId()), "Account locked after " + attempts + " failed login attempts",
                            null, null, "Authentication");
                    throw new RuntimeException(
                            "Too many failed attempts. Account locked for " + LOCKOUT_MINUTES + " minutes.");
                }
                userRepository.save(user);
                auditService.log(user.getOrganization(), user, "LOGIN_FAILED", "AUTH",
                        String.valueOf(user.getId()),
                        "Failed login attempt (" + attempts + "/" + MAX_FAILED_ATTEMPTS + ")",
                        null, null, "Authentication");
            }
            throw new RuntimeException("Invalid email or password");
        }

        user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Successful password check — reset the failure counter and any lock.
        if ((user.getFailedLoginAttempts() != null && user.getFailedLoginAttempts() > 0)
                || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }

        boolean emailSmsOtpRequired = isEmailSmsOtpRole(user);

        /*
         * ADMIN and MANAGER: email + SMS OTP is the login second factor.
         * This branch intentionally runs BEFORE the existing TOTP branch so an
         * authenticator app is not the only way these roles can authenticate.
         */
        if (emailSmsOtpRequired) {
            return handleEmailSmsLoginOtp(user, req.getOtp());
        }

        user.setLastLoginAt(java.time.LocalDateTime.now());
        userRepository.save(user);

        // Other roles keep the existing TOTP/Auth­enticator behaviour when enabled.
        if (user.isTwoFactorEnabled()) {
            if (req.getMfaCode() == null || req.getMfaCode().isBlank()) {
                return ResponseEntity.ok(Map.of(
                        "mfaRequired", true,
                        "email", user.getEmail()));
            }
            if (!mfaService.verifyCode(user, req.getMfaCode())) {
                throw new RuntimeException("Invalid MFA code");
            }
        } else {
            // Existing email OTP fallback for non-ADMIN/non-MANAGER users.
            return handleEmailLoginOtp(user, req.getOtp());
        }

        return successfulLogin(user);
    }

    private boolean isEmailSmsOtpRole(User user) {
        return user.getRole() != null
                && user.getRole().getName() != null
                && EMAIL_SMS_OTP_ROLES.contains(user.getRole().getName().trim().toUpperCase());
    }

    private ResponseEntity<Map<String, Object>> handleEmailSmsLoginOtp(User user, String submittedOtp) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        if (submittedOtp == null || submittedOtp.isBlank()) {
            if (user.getPhone() == null || user.getPhone().isBlank()) {
                auditService.log(user.getOrganization(), user, "LOGIN_OTP_SMS_UNAVAILABLE", "AUTH",
                        String.valueOf(user.getId()),
                        "ADMIN/MANAGER login OTP could not be issued because no mobile number is registered",
                        null, null, "Authentication");
                throw new RuntimeException(
                        "A registered mobile phone number is required for ADMIN and MANAGER login verification. Please ask an administrator to update your phone number.");
            }

            String code = String.format("%06d", OTP_RANDOM.nextInt(1_000_000));
            user.setLoginOtpHash(passwordEncoder.encode(code));
            user.setLoginOtpExpiresAt(now.plusMinutes(OTP_EXPIRY_MINUTES));
            user.setLoginOtpAttempts(0);
            user.setLastLoginAt(null);
            userRepository.save(user);

            mailService.sendLoginOtp(user, code);
            smsService.sendCustom(
                    user.getPhone(),
                    "Noble Loan Solutions: your login verification code is " + code
                            + ". It expires in " + OTP_EXPIRY_MINUTES
                            + " minutes. Do not share this code.");

            auditService.log(user.getOrganization(), user, "LOGIN_OTP_SENT", "AUTH",
                    String.valueOf(user.getId()),
                    "Login OTP sent to registered email and mobile number",
                    null, null, "Authentication");

            return ResponseEntity.ok(Map.of(
                    "otpRequired", true,
                    "otpDelivery", "EMAIL_AND_SMS",
                    "email", user.getEmail(),
                    "phone", maskPhone(user.getPhone()),
                    "message",
                    "A 6-digit verification code has been sent to your registered email address and mobile phone. It expires in "
                            + OTP_EXPIRY_MINUTES + " minutes."));
        }

        verifyLoginOtp(user, submittedOtp, now);
        return successfulLogin(user);
    }

    private ResponseEntity<Map<String, Object>> handleEmailLoginOtp(User user, String submittedOtp) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        if (submittedOtp == null || submittedOtp.isBlank()) {
            String code = String.format("%06d", OTP_RANDOM.nextInt(1_000_000));
            user.setLoginOtpHash(passwordEncoder.encode(code));
            user.setLoginOtpExpiresAt(now.plusMinutes(OTP_EXPIRY_MINUTES));
            user.setLoginOtpAttempts(0);
            userRepository.save(user);
            mailService.sendLoginOtp(user, code);
            return ResponseEntity.ok(Map.of(
                    "otpRequired", true,
                    "otpDelivery", "EMAIL",
                    "email", user.getEmail(),
                    "message", "A 6-digit verification code has been sent to your email address. It expires in "
                            + OTP_EXPIRY_MINUTES + " minutes."));
        }

        verifyLoginOtp(user, submittedOtp, now);
        return successfulLogin(user);
    }

    private void verifyLoginOtp(User user, String submittedOtp, java.time.LocalDateTime now) {
        if (user.getLoginOtpHash() == null || user.getLoginOtpExpiresAt() == null
                || user.getLoginOtpExpiresAt().isBefore(now)) {
            throw new RuntimeException("Your verification code has expired. Please sign in again to get a new one.");
        }

        int otpAttempts = user.getLoginOtpAttempts() == null ? 0 : user.getLoginOtpAttempts();
        if (otpAttempts >= MAX_OTP_ATTEMPTS) {
            user.setLoginOtpHash(null);
            user.setLoginOtpExpiresAt(null);
            user.setLoginOtpAttempts(0);
            userRepository.save(user);
            throw new RuntimeException("Too many incorrect codes. Please sign in again to get a new one.");
        }

        if (!passwordEncoder.matches(submittedOtp.trim(), user.getLoginOtpHash())) {
            user.setLoginOtpAttempts(otpAttempts + 1);
            userRepository.save(user);
            auditService.log(user.getOrganization(), user, "LOGIN_OTP_FAILED", "AUTH",
                    String.valueOf(user.getId()),
                    "Incorrect login verification code (" + (otpAttempts + 1) + "/" + MAX_OTP_ATTEMPTS + ")",
                    null, null, "Authentication");
            throw new RuntimeException("Incorrect verification code.");
        }

        // Consume the OTP immediately so it is single-use.
        user.setLoginOtpHash(null);
        user.setLoginOtpExpiresAt(null);
        user.setLoginOtpAttempts(0);
        user.setLastLoginAt(java.time.LocalDateTime.now());
        userRepository.save(user);

        auditService.log(user.getOrganization(), user, "LOGIN_OTP_VERIFIED", "AUTH",
                String.valueOf(user.getId()), "Login verification code accepted",
                null, null, "Authentication");
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank())
            return "";
        String value = phone.trim();
        if (value.length() <= 4)
            return "****";
        return "*".repeat(Math.max(0, value.length() - 4)) + value.substring(value.length() - 4);
    }

    private ResponseEntity<Map<String, Object>> successfulLogin(User user) {
        auditService.log(user.getOrganization(), user, "LOGIN_SUCCESS", "AUTH",
                String.valueOf(user.getId()), user.getName() + " signed in", null, null, "Authentication");

        Map<String, Object> body = safe(user);
        body.put("token", jwtUtils.generateToken(user.getEmail()));
        body.put("mfaRequired", false);
        body.put("mfaSetupRequired", false);
        body.put("otpRequired", false);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/me")
    @Transactional
    public ResponseEntity<Map<String, Object>> me(Authentication auth) {
        User user = userRepository.findByEmail(auth.getName()).orElseThrow(() -> new RuntimeException("Not found"));
        return ResponseEntity.ok(safe(user));
    }

    private Map<String, Object> safe(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", u.getId());
        m.put("name", u.getName());
        m.put("email", u.getEmail());
        m.put("role", u.getRole() != null ? u.getRole().getName() : null);
        m.put("twoFactorEnabled", u.isTwoFactorEnabled());
        m.put("mustChangePassword", u.isMustChangePassword());
        if (u.getOrganization() != null) {
            m.put("organizationId", u.getOrganization().getId());
            m.put("organizationName", u.getOrganization().getName());
            m.put("currency", u.getOrganization().getDefaultCurrency());
            m.put("locale", u.getOrganization().getLocale());
            m.put("timezone", u.getOrganization().getTimezone());
        } else {
            m.put("organizationId", null);
            m.put("organizationName", null);
            m.put("currency", "USD");
            m.put("locale", "en-US");
            m.put("timezone", "UTC");
        }
        return m;
    }
}
