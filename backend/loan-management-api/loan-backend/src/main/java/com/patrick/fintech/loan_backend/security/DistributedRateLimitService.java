package com.patrick.fintech.loan_backend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DistributedRateLimitService {

    private final JdbcTemplate jdbc;

    /**
     * Fixed-window distributed rate limiter.
     *
     * PostgreSQL performs the increment atomically, so multiple
     * application instances can safely share the same limit.
     */
    @Transactional
    public Decision check(
            String key,
            int limit,
            long windowMillis) {

        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "Rate-limit key is required");
        }

        if (limit <= 0) {
            throw new IllegalArgumentException(
                    "Rate-limit must be greater than zero");
        }

        if (windowMillis <= 0) {
            throw new IllegalArgumentException(
                    "Rate-limit window must be greater than zero");
        }

        long now = System.currentTimeMillis();

        long window = now - (now % windowMillis);

        Integer count = jdbc.queryForObject(
                """
                        INSERT INTO rate_limit_buckets
                            (bucket_key, window_start, request_count)
                        VALUES
                            (?, ?, 1)
                        ON CONFLICT (bucket_key)
                        DO UPDATE SET
                            window_start =
                                CASE
                                    WHEN rate_limit_buckets.window_start = EXCLUDED.window_start
                                    THEN rate_limit_buckets.window_start
                                    ELSE EXCLUDED.window_start
                                END,
                            request_count =
                                CASE
                                    WHEN rate_limit_buckets.window_start = EXCLUDED.window_start
                                    THEN rate_limit_buckets.request_count + 1
                                    ELSE 1
                                END
                        RETURNING request_count
                        """,
                Integer.class,
                key,
                window);

        long retryAfter = Math.max(
                1,
                (window + windowMillis - now) / 1000);

        return new Decision(
                count != null && count <= limit,
                retryAfter);
    }

    /**
     * Deletes expired rate-limit buckets.
     *
     * This is intentionally database-backed so multiple application
     * instances share the same state.
     */
    @Transactional
    public void cleanup(
            long retentionMillis) {

        if (retentionMillis <= 0) {
            throw new IllegalArgumentException(
                    "Retention period must be greater than zero");
        }

        long cutoff = System.currentTimeMillis()
                - retentionMillis;

        jdbc.update(
                """
                        DELETE FROM rate_limit_buckets
                        WHERE window_start < ?
                        """,
                cutoff);
    }

    public record Decision(
            boolean allowed,
            long retryAfterSeconds) {
    }
}