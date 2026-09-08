package com.patrick.fintech.loan_backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Distributed rate limiting with a bounded local degraded-mode fallback.
 *
 * The database limiter is authoritative during normal operation. If the
 * distributed store is temporarily unavailable, authentication and other
 * abuse-sensitive endpoints are NOT allowed to become completely unprotected:
 * a per-instance fixed-window limiter is used instead.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(String method, String path, int max, long window, String label, boolean degradedModeAllowed) {}

    private record LocalBucket(long windowStart, AtomicInteger count) {}

    private final DistributedRateLimitService limiter;
    private final ConcurrentHashMap<String, LocalBucket> localBuckets = new ConcurrentHashMap<>();

    private static final int MAX_LOCAL_BUCKETS = 100_000;

    private final List<Rule> rules = List.of(
            new Rule("POST", "/api/auth/login", 20, TimeUnit.MINUTES.toMillis(5), "login", true),
            new Rule("POST", "/api/auth/register", 5, TimeUnit.HOURS.toMillis(1), "register", true),
            new Rule("POST", "/api/auth/forgot-password", 5, TimeUnit.HOURS.toMillis(1), "forgot", true),
            new Rule("POST", "/api/auth/reset-password", 10, TimeUnit.HOURS.toMillis(1), "reset", true),

            // Webhooks must be evaluated before the generic public rule.
            new Rule("POST", "/api/public/webhooks/", 120, TimeUnit.MINUTES.toMillis(1), "webhook", true),
            new Rule("GET", "/api/public/payment-schedule", 20, TimeUnit.MINUTES.toMillis(1), "public-payment-schedule", true),
            new Rule("ANY", "/api/public/", 60, TimeUnit.MINUTES.toMillis(1), "public", true)
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        for (Rule rule : rules) {
            boolean methodMatches = "ANY".equalsIgnoreCase(rule.method()) || rule.method().equalsIgnoreCase(method);
            boolean pathMatches = path.startsWith(rule.path());
            if (!methodMatches || !pathMatches) {
                continue;
            }

            String key = rule.label() + ":" + clientIp(request);

            try {
                var decision = limiter.check(key, rule.max(), rule.window());
                if (!decision.allowed()) {
                    writeTooManyRequests(response, decision.retryAfterSeconds());
                    return;
                }
            } catch (Exception exception) {
                log.error("Distributed rate limiter unavailable for {} {}. Entering degraded local mode.", method, path, exception);

                var localDecision = localCheck(key, rule.max(), rule.window());
                if (!localDecision.allowed()) {
                    writeTooManyRequests(response, localDecision.retryAfterSeconds());
                    return;
                }

                if (!rule.degradedModeAllowed()) {
                    writeServiceUnavailable(response, 5);
                    return;
                }
            }

            break;
        }

        chain.doFilter(request, response);
    }

    private DistributedRateLimitService.Decision localCheck(String key, int limit, long windowMillis) {
        long now = System.currentTimeMillis();
        long window = now - (now % windowMillis);

        if (localBuckets.size() > MAX_LOCAL_BUCKETS) {
            cleanupLocalBuckets(now - Duration.ofHours(2).toMillis());
        }

        LocalBucket bucket = localBuckets.compute(key, (ignored, current) -> {
            if (current == null || current.windowStart() != window) {
                return new LocalBucket(window, new AtomicInteger(1));
            }
            current.count().incrementAndGet();
            return current;
        });

        long retryAfter = Math.max(1, (window + windowMillis - now) / 1000);
        return new DistributedRateLimitService.Decision(bucket.count().get() <= limit, retryAfter);
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(Math.max(1, retryAfterSeconds)));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {
                  "success": false,
                  "error": "Too many requests. Please try again later."
                }
                """);
    }

    private void writeServiceUnavailable(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(503);
        response.setHeader("Retry-After", String.valueOf(Math.max(1, retryAfterSeconds)));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {
                  "success": false,
                  "error": "Security protection is temporarily unavailable. Please retry shortly."
                }
                """);
    }

    private String clientIp(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress.trim();
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    public void cleanup() {
        try {
            limiter.cleanup(TimeUnit.HOURS.toMillis(2));
        } catch (Exception exception) {
            log.warn("Distributed rate-limit cleanup failed", exception);
        }
        cleanupLocalBuckets(System.currentTimeMillis() - Duration.ofHours(2).toMillis());
    }

    private void cleanupLocalBuckets(long cutoff) {
        localBuckets.entrySet().removeIf(entry -> entry.getValue().windowStart() < cutoff);
    }
}
