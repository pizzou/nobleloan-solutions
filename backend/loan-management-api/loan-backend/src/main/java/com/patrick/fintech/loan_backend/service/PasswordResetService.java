package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.PasswordResetToken;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.PasswordResetTokenRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository              userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder             passwordEncoder;
    private final MailService                mailService;
    private final AuditService                auditService;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            tokenRepository.deleteByUser_Id(user.getId());
            PasswordResetToken t = new PasswordResetToken();
            String rawToken = UUID.randomUUID().toString() + UUID.randomUUID();
            t.setToken(hashToken(rawToken));
            t.setUser(user);
            t.setExpiresAt(LocalDateTime.now().plusHours(1));
            tokenRepository.save(t);
            String link = frontendUrl + "/reset-password?token=" + rawToken;
            mailService.sendPasswordResetEmail(user, link);
            auditService.log(user.getOrganization(), user, "PASSWORD_RESET_REQUESTED", "AUTH",
                String.valueOf(user.getId()), "Password reset requested for " + user.getEmail(),
                null, null, "Authentication");
            log.info("Password reset token generated for user={}", email);
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken t = tokenRepository.findByTokenAndUsedFalse(hashToken(token))
            .orElseThrow(() -> new RuntimeException("Invalid or expired reset token"));
        if (t.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new RuntimeException("Reset token has expired");
        User user = t.getUser();
        com.patrick.fintech.loan_backend.security.PasswordPolicy.validate(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setTokenVersion((user.getTokenVersion() == null ? 0L : user.getTokenVersion()) + 1L);
        userRepository.save(user);
        t.setUsed(true);
        tokenRepository.save(t);
        auditService.log(user.getOrganization(), user, "PASSWORD_RESET_COMPLETED", "AUTH",
            String.valueOf(user.getId()), "Password reset completed for " + user.getEmail(),
            null, null, "Authentication");
        log.info("Password reset successfully for user={}", user.getEmail());
    }
    private String hashToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Reset token is required");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(token.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash password reset token", e);
        }
    }

}
