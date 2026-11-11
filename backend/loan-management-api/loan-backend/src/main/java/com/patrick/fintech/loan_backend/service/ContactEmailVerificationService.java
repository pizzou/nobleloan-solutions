package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.util.ContactVisitorMetadata;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Email ownership proof for public contact messages. Does not verify phone ownership. */
@Service
@RequiredArgsConstructor
public class ContactEmailVerificationService {
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");
    private final JdbcTemplate jdbc;
    private final MailService mailService;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.jwt.secret:${JWT_SECRET:}}")
    private String secret;

    public static String email(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.length() > 180 || !EMAIL.matcher(normalized).matches())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address.");
        return normalized;
    }

    /** A Rwanda phone number, entered as ten digits without a country prefix. */
    public static String phone(String raw) {
        String normalized = raw == null ? "" : raw.trim();
        if (!normalized.matches("[0-9]{10}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Enter a phone number containing exactly 10 digits.");
        return normalized;
    }

    public UUID start(String rawEmail, HttpServletRequest request) {
        String address = email(rawEmail);
        String ip = ContactVisitorMetadata.clientIp(request);
        // Database-based rate limits survive restarts; no unbounded in-memory cache.
        OffsetDateTime hour = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        Long sameEmail = jdbc.queryForObject(
                "SELECT count(*) FROM contact_email_challenges WHERE email=? AND created_at > ?",
                Long.class, address, hour);
        Long sameIp = jdbc.queryForObject(
                "SELECT count(*) FROM contact_email_challenges WHERE ip_address=? AND created_at > ?",
                Long.class, ip, hour);
        if ((sameEmail != null && sameEmail >= 5) || (sameIp != null && sameIp >= 15))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many verification requests. Try again later.");

        UUID id = UUID.randomUUID();
        String code = String.format("%06d", random.nextInt(1_000_000));
        OffsetDateTime expires = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10);
        jdbc.update("INSERT INTO contact_email_challenges(id,email,ip_address,code_hash,expires_at) VALUES(?,?,?,?,?)",
                id, address, ip, hash(id, code), expires);

        if (!mailService.sendContactVerificationCode(address, code)) {
            jdbc.update("DELETE FROM contact_email_challenges WHERE id=? AND consumed_at IS NULL", id);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not send the email verification code. Try again later.");
        }
        return id;
    }

    /** Called inside the message submission transaction; one challenge can authorize one message. */
    @Transactional
    public void consume(String token, String rawEmail, String code) {
        UUID id;
        try { id = UUID.fromString(token); }
        catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request an email verification code.");
        }
        String address = email(rawEmail);
        if (code == null || !code.matches("[0-9]{6}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the 6-digit email code.");

        int updated = jdbc.update(
                "UPDATE contact_email_challenges SET consumed_at=CURRENT_TIMESTAMP " +
                "WHERE id=? AND email=? AND code_hash=? AND expires_at>CURRENT_TIMESTAMP " +
                "AND consumed_at IS NULL AND attempts<5",
                id, address, hash(id, code));
        if (updated != 1) {
            // Record failed attempts in a separate transaction so a rejected request
            // cannot roll back the rate-limiting counter.
            org.springframework.transaction.support.TransactionTemplate tx =
                    new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.executeWithoutResult(status -> jdbc.update(
                    "UPDATE contact_email_challenges SET attempts=attempts+1 " +
                    "WHERE id=? AND email=? AND consumed_at IS NULL AND attempts<5", id, address));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Email verification code is invalid, expired or already used.");
        }
    }

    private String hash(UUID id, String code) {
        if (secret == null || secret.isBlank())
            throw new IllegalStateException("JWT secret is required to hash contact verification codes.");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((id.toString() + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("Cannot hash contact verification", ex); }
    }
}
