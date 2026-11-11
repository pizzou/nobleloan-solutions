package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.util.ContactVisitorMetadata;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;

/** Server-side one-time email ownership proof; phone ownership is NOT checked. */
@Service
@RequiredArgsConstructor
public class ContactEmailVerificationService {
    private static final Pattern EMAIL = Pattern.compile(
        "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.jwt.secret:${JWT_SECRET:}}") private String hmacKey;
    @Value("${app.mail.enabled:false}") private boolean mailEnabled;
    @Value("${app.mail.brevo-api-key:}") private String apiKey;
    @Value("${app.mail.from:}") private String senderEmail;
    @Value("${app.mail.from-name:Noble Loan Solutions}") private String senderName;

    public static String email(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.length() > 180 || !EMAIL.matcher(value).matches())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address.");
        return value;
    }

    public static String phone(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.matches("[0-9]{10}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone number must contain exactly 10 digits.");
        return value;
    }

    public UUID start(String submittedEmail, HttpServletRequest request) {
        String address = email(submittedEmail);
        String ip = ContactVisitorMetadata.clientIp(request);
        OffsetDateTime hour = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        Long emailCount = jdbc.queryForObject(
            "SELECT count(*) FROM contact_email_challenges WHERE email=? AND created_at>?", Long.class, address, hour);
        Long ipCount = jdbc.queryForObject(
            "SELECT count(*) FROM contact_email_challenges WHERE ip_address=? AND created_at>?", Long.class, ip, hour);
        if (emailCount != null && emailCount >= 5 || ipCount != null && ipCount >= 15)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Try later.");

        UUID id = UUID.randomUUID();
        String code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
        jdbc.update("INSERT INTO contact_email_challenges(id,email,ip_address,code_hash,expires_at) " +
                "VALUES(?,?,?,?,?)", id, address, ip, hash(id, code), OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10));
        if (!sendEmail(address, code)) {
            jdbc.update("DELETE FROM contact_email_challenges WHERE id=? AND consumed_at IS NULL", id);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Verification email delivery is unavailable. Please try later.");
        }
        return id;
    }

    @Transactional
    public void consume(String token, String address, String code) {
        UUID id;
        try { id = UUID.fromString(token); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request an email verification code."); }
        String verifiedAddress = email(address);
        if (code == null || !code.matches("[0-9]{6}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the 6-digit email code.");
        int changed = jdbc.update(
            "UPDATE contact_email_challenges SET consumed_at=CURRENT_TIMESTAMP " +
            "WHERE id=? AND email=? AND code_hash=? AND expires_at>CURRENT_TIMESTAMP " +
            "AND consumed_at IS NULL AND attempts<5", id, verifiedAddress, hash(id, code));
        if (changed != 1) {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.executeWithoutResult(status -> jdbc.update(
                    "UPDATE contact_email_challenges SET attempts=attempts+1 " +
                    "WHERE id=? AND email=? AND consumed_at IS NULL AND attempts<5", id, verifiedAddress));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Email verification code is incorrect, expired or already used.");
        }
    }

    private String hash(UUID id, String code) {
        if (hmacKey == null || hmacKey.isBlank()) throw new IllegalStateException("JWT secret missing");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((id+":"+code).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Cannot hash verification code",e); }
    }

    /** Provider acceptance is not a guarantee of inbox delivery; recipient must enter the code. */
    private boolean sendEmail(String to, String code) {
        if (!mailEnabled || apiKey == null || apiKey.isBlank() || senderEmail == null || senderEmail.isBlank())
            return false;
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(3000);
            factory.setReadTimeout(7000);
            RestTemplate http = new RestTemplate(factory);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", apiKey);
            Map<String,Object> body = Map.of(
                "sender", Map.of("email",senderEmail,"name",senderName),
                "to", List.of(Map.of("email",to)),
                "subject", "Verify your Noble Loan Solutions enquiry",
                "htmlContent", "<p>Enter this one-time code in the Noble Loan Solutions contact form:</p><h2>"+
                    code+"</h2><p>This code expires in 10 minutes. If you did not request it, ignore this email.</p>");
            return http.exchange("https://api.brevo.com/v3/smtp/email", HttpMethod.POST,
                new HttpEntity<>(body,headers),String.class).getStatusCode().is2xxSuccessful();
        } catch (Exception ex) {
            // No emails, API keys or code in logs.
            org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                "Contact verification mail request failed: {}",ex.getClass().getSimpleName());
            return false;
        }
    }
}
