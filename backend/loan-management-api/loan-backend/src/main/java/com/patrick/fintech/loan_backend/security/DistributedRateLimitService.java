package com.patrick.fintech.loan_backend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DistributedRateLimitService {
    private final JdbcTemplate jdbc;

    public Decision check(String key, int limit, long windowMillis) {
        long now = System.currentTimeMillis();
        long window = now - (now % windowMillis);
        Integer count = jdbc.queryForObject(
                "INSERT INTO rate_limit_buckets(bucket_key,window_start,request_count) VALUES(?,?,1) " +
                        "ON CONFLICT(bucket_key) DO UPDATE SET window_start=EXCLUDED.window_start, " +
                        "request_count=CASE WHEN rate_limit_buckets.window_start=EXCLUDED.window_start " +
                        "THEN rate_limit_buckets.request_count+1 ELSE 1 END " +
                        "RETURNING request_count",
                Integer.class, key, window);
        long retry = Math.max(1, (window + windowMillis - now) / 1000);
        return new Decision(count != null && count <= limit, retry);
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    public void cleanup(long millis) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'cleanup'");
    }
}
