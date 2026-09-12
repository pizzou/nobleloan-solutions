package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.config.JwtUtils;
import com.patrick.fintech.loan_backend.dto.*;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import com.patrick.fintech.loan_backend.service.*;
import com.patrick.fintech.loan_backend.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;
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

    @Value("${app.environment:development}")
    private String applicationEnvironment;

    @Value("${app.auth.cookie.name:NLS_SESSION}")
    private String sessionCookieName;

    @Value("${app.auth.cookie.secure:true}")
    private boolean sessionCookieSecure;

    @Value("${app.auth.cookie.same-site:Lax}")
    private String sessionCookieSameSite;

    @Value("${app.jwt.expiration-ms:900000}")
    private long sessionMaxAgeMs;

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, Object>> csrf(CsrfToken token) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "headerName", token.getHeaderName(),
                "parameterName", token.getParameterName(),
                "token", token.getToken()
        ));
    }

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
    private static final java.util.Set<String> EMAIL_OTP_ROLES = java.util.Set.of("ADMIN", "MANAGER", "BUSINESS_OWNER");

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
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
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

        user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Successful password check — reset the failure counter and any lock.
        if ((user.getFailedLoginAttempts() != null && user.getFailedLoginAttempts() > 0)
                || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }

        boolean emailOtpRequired = isEmailOtpRole(user);

        /*
         * ADMIN and MANAGER: one email OTP is the login second factor.
         * This branch intentionally runs BEFORE the existing TOTP branch so an
         * authenticator app is not required for these roles.
         */
        if (emailOtpRequired) {
            return handleEmailOtp(user, req.getOtp());
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
            return handleEmailOtp(user, req.getOtp());
        }

        return successfulLogin(user);
    }

    private boolean isEmailOtpRole(User user) {
        return user.getRole() != null
                && user.getRole().getName() != null
                && EMAIL_OTP_ROLES.contains(user.getRole().getName().trim().toUpperCase());
    }

   
    private ResponseEntity<Map<String, Object>> handleEmailOtp(User user, String submittedOtp) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        if (submittedOtp == null || submittedOtp.isBlank()) {
            boolean activeOtp = user.getLoginOtpHash() != null
                    && user.getLoginOtpExpiresAt() != null
                    && user.getLoginOtpExpiresAt().isAfter(now);

            if (!activeOtp) {
                String code = String.format("%06d", OTP_RANDOM.nextInt(1_000_000));
                user.setLoginOtpHash(passwordEncoder.encode(code));
                user.setLoginOtpExpiresAt(now.plusMinutes(OTP_EXPIRY_MINUTES));
                user.setLoginOtpAttempts(0);
                user.setLastLoginAt(null);
                userRepository.save(user);

                // MailService.sendLoginOtp() is @Async, so the HTTP request
                // does not wait for the external email provider.
                mailService.sendLoginOtp(user, code);

                // Do not block login on the audit-chain database lock.
                auditService.logAuthenticationAsync(
                        user.getOrganization(),
                        user,
                        "LOGIN_OTP_SENT",
                        "AUTH",
                        String.valueOf(user.getId()),
                        "Login OTP issued and sent to registered email address",
                        null,
                        null,
                        "Authentication");
            }

            return ResponseEntity.ok(Map.of(
                    "otpRequired", true,
                    "otpDelivery", "EMAIL",
                    "email", user.getEmail(),
                    "message", activeOtp
                            ? "A verification code has already been sent to your email. Enter that code to continue."
                            : "A single 6-digit verification code has been sent to your email address. It expires in "
                                    + OTP_EXPIRY_MINUTES + " minutes."));
        }

        verifyLoginOtp(user, submittedOtp, now);
        return successfulLogin(user);
    }

    private void verifyLoginOtp(User user, String submittedOtp, java.time.LocalDateTime now) {
        // Serialize OTP consumption so two concurrent requests cannot both redeem
        // the same one-time code.
        user = userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new RuntimeException("User account not found"));

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
        body.put("mfaRequired", false);
        body.put("mfaSetupRequired", false);
        body.put("otpRequired", false);
        String token = jwtUtils.generateToken(user);
        ResponseCookie cookie = sessionCookie(token, false);
        return ResponseEntity.ok()
                .header("Set-Cookie", cookie.toString())
                .body(body);
    }

    @PostMapping("/logout")
    @Transactional
    public ResponseEntity<Map<String, Object>> logout(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return ResponseEntity.ok()
                    .header("Set-Cookie", sessionCookie("", true).toString())
                    .body(Map.of("success", true));
        }

        User user = userRepository.findByEmailIgnoreCase(auth.getName()).orElse(null);
        if (user != null) {
            user.setTokenVersion((user.getTokenVersion() == null ? 0L : user.getTokenVersion()) + 1L);
            userRepository.save(user);
            auditService.log(user.getOrganization(), user, "LOGOUT", "AUTH",
                    String.valueOf(user.getId()), "User session revoked by logout", null, null, "Authentication");
        }
        return ResponseEntity.ok()
                .header("Set-Cookie", sessionCookie("", true).toString())
                .body(Map.of("success", true));
    }

    private ResponseCookie sessionCookie(String value, boolean clear) {
        String sameSite = normalizeSameSite(sessionCookieSameSite);
        return ResponseCookie.from(sessionCookieName, value == null ? "" : value)
                .httpOnly(true)
                .secure(sessionCookieSecure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(clear ? java.time.Duration.ZERO : java.time.Duration.ofMillis(Math.max(1000L, sessionMaxAgeMs)))
                .build();
    }

    private String normalizeSameSite(String value) {
        if (value == null || value.isBlank()) {
            return "None";
        }
        String normalized = value.trim();
        if ("strict".equalsIgnoreCase(normalized)) return "Strict";
        if ("lax".equalsIgnoreCase(normalized)) return "Lax";
        if ("none".equalsIgnoreCase(normalized)) return "None";
        throw new IllegalStateException(
                "Invalid AUTH_COOKIE_SAME_SITE value. Use Strict, Lax, or None.");
    }

    @GetMapping("/me")
    @Transactional
    public ResponseEntity<Map<String, Object>> me(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "authenticated", false,
                    "message", "Authentication required"
            ));
        }

        User user = userRepository.findByEmailIgnoreCase(auth.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "authenticated", false,
                    "message", "Authentication required"
            ));
        }

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
