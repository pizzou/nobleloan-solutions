package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.PasswordResetToken;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.PasswordResetTokenRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import com.patrick.fintech.loan_backend.security.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;

/** Secure, single-use password recovery for active users only. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;
    private static final long TOKEN_LIFETIME_MINUTES = 30;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final AuditService auditService;

    @Value("${app.frontend.url:https://nobleloansolutions.rw}")
    private String frontendUrl;

    /**
     * Always has a generic public response. Unknown, disabled, suspended, deleted,
     * and recycled accounts do not receive reset tokens or email.
     */
    @Transactional
    public void requestReset(String email) {
        if (email == null || email.isBlank() || !mailService.isPasswordResetEmailConfigured()) {
            // Do not create a token that cannot be delivered. The controller keeps
            // the same generic response for every account state.
            return;
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (user == null || user.getDeletedAt() != null || user.getPurgedAt() != null
                || user.getStatus() != User.UserStatus.ACTIVE) {
            return;
        }

        // Generate a high-entropy URL-safe token; only its SHA-256 digest is stored.
        byte[] randomBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String resetUrl = normalizeFrontendUrl(frontendUrl) + "/reset-password?token=" + rawToken;

        // Replace older outstanding links so only the most recently requested
        // link remains valid. This is done only after eligibility and mail config checks.
        tokenRepository.deleteByUser_Id(user.getId());

        PasswordResetToken token = new PasswordResetToken();
        token.setToken(hashToken(rawToken));
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(TOKEN_LIFETIME_MINUTES));
        token.setUsed(false);
        tokenRepository.save(token);

        // Send synchronously so an unavailable/misconfigured mail provider does not
        // leave a valid reset token that the user never received. The mail service
        // returns false on delivery failure; public responses remain generic.
        if (!mailService.sendPasswordResetEmailNow(user, resetUrl)) {
            tokenRepository.delete(token);
            log.warn("Password reset email delivery failed; request suppressed");
            return;
        }

        auditService.log(user.getOrganization(), user, "PASSWORD_RESET_REQUESTED", "AUTH",
                String.valueOf(user.getId()), "Password reset requested", null, null, "Authentication");
        log.info("Password reset email accepted by provider");
    }

    /** Redeems a token once and revokes all previously issued JWT sessions. */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Invalid or expired reset link. Request a new one.");
        }
        PasswordResetToken token = tokenRepository.findByTokenAndUsedFalse(hashToken(rawToken))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid or expired reset link. Request a new one."));

        LocalDateTime now = LocalDateTime.now();
        if (token.getExpiresAt() == null || !token.getExpiresAt().isAfter(now)) {
            throw new IllegalArgumentException("Invalid or expired reset link. Request a new one.");
        }

        User user = token.getUser();
        if (user == null || user.getDeletedAt() != null || user.getPurgedAt() != null
                || user.getStatus() != User.UserStatus.ACTIVE) {
            throw new IllegalArgumentException("This account is not eligible for password reset.");
        }

        PasswordPolicy.validate(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setTokenVersion((user.getTokenVersion() == null ? 0L : user.getTokenVersion()) + 1L);
        user.setLoginOtpHash(null);
        user.setLoginOtpExpiresAt(null);
        user.setLoginOtpAttempts(0);
        user.setMustChangePassword(false);
        userRepository.save(user);

        token.setUsed(true);
        tokenRepository.save(token);

        auditService.log(user.getOrganization(), user, "PASSWORD_RESET_COMPLETED", "AUTH",
                String.valueOf(user.getId()), "Password reset completed", null, null, "Authentication");
        log.info("Password reset completed; existing sessions revoked");
    }

    private String normalizeFrontendUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("FRONTEND_URL must be configured for password recovery");
        }
        String normalized = value.trim().replaceAll("/+$", "");
        if (!(normalized.startsWith("https://") || normalized.startsWith("http://localhost:"))) {
            throw new IllegalStateException("FRONTEND_URL must use HTTPS outside local development");
        }
        return normalized;
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.trim().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash password reset token", ex);
        }
    }
}
