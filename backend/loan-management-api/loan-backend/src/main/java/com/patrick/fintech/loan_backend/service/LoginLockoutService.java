package com.patrick.fintech.loan_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Database-atomic login failure counting across concurrent requests and JVMs.
 * Locked accounts cannot be cleared just by supplying a valid password.
 * No scheduled unlock is needed: the lock expires after exactly 24 hours.
 */
@Service
@RequiredArgsConstructor
public class LoginLockoutService {
    public static final int MAX_FAILED_ATTEMPTS = 3;
    public static final int LOCKOUT_MINUTES = 24 * 60;

    private final JdbcTemplate jdbcTemplate;

    public record Failure(int attempts, boolean locked) {}

    public Failure registerInvalidPassword(Long userId) {
        // All expressions read the pre-update row; expired locks begin a fresh
        // counter. PostgreSQL serializes concurrent UPDATEs on the same user.
        String sql = """
            UPDATE app_users
            SET failed_login_attempts = CASE
                    WHEN locked_until IS NOT NULL AND locked_until <= CURRENT_TIMESTAMP
                        THEN 1
                    ELSE COALESCE(failed_login_attempts, 0) + 1 END,
                locked_until = CASE
                    WHEN (CASE
                        WHEN locked_until IS NOT NULL AND locked_until <= CURRENT_TIMESTAMP
                            THEN 1
                        ELSE COALESCE(failed_login_attempts, 0) + 1 END) >= 3
                    THEN CURRENT_TIMESTAMP + INTERVAL '24 hours'
                    ELSE NULL END
            WHERE id = ?
              AND (locked_until IS NULL OR locked_until <= CURRENT_TIMESTAMP)
            RETURNING failed_login_attempts, (locked_until IS NOT NULL) AS locked
            """;
        List<Failure> updated = jdbcTemplate.query(sql,
                (rs, row) -> new Failure(rs.getInt("failed_login_attempts"), rs.getBoolean("locked")),
                userId);
        return updated.isEmpty() ? new Failure(MAX_FAILED_ATTEMPTS, true) : updated.get(0);
    }

    /** Returns false if a concurrent request has just locked the account. */
    public boolean clearAfterValidPassword(Long userId) {
        int updated = jdbcTemplate.update("""
                UPDATE app_users
                SET failed_login_attempts = 0, locked_until = NULL
                WHERE id = ?
                  AND (locked_until IS NULL OR locked_until <= CURRENT_TIMESTAMP)
                """, userId);
        return updated == 1;
    }
}
