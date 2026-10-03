package com.patrick.fintech.loan_backend.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import com.patrick.fintech.loan_backend.model.User;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@Slf4j
public class JwtUtils {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms:86400000}")
    private long expirationMs;

    /** Immutable key object reused for every JWT operation. */
    private SecretKey signingKey;

    @PostConstruct
    void initializeSigningKey() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.jwt.secret must be configured");
        }
        signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private SecretKey getSigningKey() {
        SecretKey key = signingKey;
        if (key == null) {
            // Defensive fallback for unusual direct-instantiation tests.
            key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            signingKey = key;
        }
        return key;
    }

    public String generateToken(String email) {
        return generateToken(email, 0L);
    }

    public String generateToken(User user) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalArgumentException("User is required to issue a session token");
        }
        return generateToken(user.getEmail(), user.getTokenVersion() == null ? 0L : user.getTokenVersion());
    }

    private String generateToken(String email, long tokenVersion) {
        return Jwts.builder()
                .subject(email)
                .claim("tokenVersion", tokenVersion)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * A short-lived, scope-restricted token issued when a role that requires
     * MFA logs in but hasn't enrolled yet. JwtAuthFilter only lets this token
     * reach /api/mfa/** — nothing else — until the user actually completes
     * MFA setup and gets a real session token from /api/auth/login.
     */
    public String generateSetupToken(String email) {
        return Jwts.builder()
                .subject(email)
                .claim("purpose", "mfa-setup")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 10 * 60 * 1000)) // 10 minutes
                .signWith(getSigningKey())
                .compact();
    }


    /**
     * Creates a short-lived, purpose-restricted challenge issued only after
     * the password has been successfully authenticated. It is not a session
     * token and must only be presented to /api/auth/send-login-otp.
     */
    public String generateLoginOtpChallengeToken(User user) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalArgumentException("User is required to issue an OTP challenge");
        }
        long tokenVersion = user.getTokenVersion() == null ? 0L : user.getTokenVersion();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("purpose", "login-otp")
                .claim("tokenVersion", tokenVersion)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 5 * 60 * 1000L))
                .signWith(getSigningKey())
                .compact();
    }

    /** Parses and verifies a JWT exactly once for request filters that need several claims. */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isLoginOtpChallengeToken(String token) {
        try {
            Object purpose = Jwts.parser().verifyWith(getSigningKey()).build()
                    .parseSignedClaims(token).getPayload().get("purpose");
            return "login-otp".equals(purpose);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isSetupToken(String token) {
        try {
            Object purpose = Jwts.parser().verifyWith(getSigningKey()).build()
                .parseSignedClaims(token).getPayload().get("purpose");
            return "mfa-setup".equals(purpose);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getEmailFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public long getTokenVersion(String token) {
        Object value = Jwts.parser().verifyWith(getSigningKey()).build()
                .parseSignedClaims(token).getPayload().get("tokenVersion");
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text) return Long.parseLong(text);
        return 0L;
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }
}
