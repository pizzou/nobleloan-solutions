package com.patrick.fintech.loan_backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {
    private record Rule(String method, String path, int max, long window, String label) {
    }

    private final DistributedRateLimitService limiter;
    private final List<Rule> rules = List.of(
            new Rule("POST", "/api/auth/login", 20, TimeUnit.MINUTES.toMillis(5), "login"),
            new Rule("POST", "/api/auth/register", 5, TimeUnit.HOURS.toMillis(1), "register"),
            new Rule("POST", "/api/auth/forgot-password", 5, TimeUnit.HOURS.toMillis(1), "forgot"),
            new Rule("POST", "/api/auth/reset-password", 10, TimeUnit.HOURS.toMillis(1), "reset"),
            new Rule("ANY", "/api/public/", 60, TimeUnit.MINUTES.toMillis(1), "public"),
            new Rule("POST", "/api/public/webhooks/", 120, TimeUnit.MINUTES.toMillis(1), "webhook"));

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI(), method = req.getMethod();
        for (Rule r : rules) {
            if (!("ANY".equals(r.method) || r.method.equalsIgnoreCase(method)) || !path.startsWith(r.path))
                continue;
            String key = r.label + ":" + clientIp(req);
            try {
                var d = limiter.check(key, r.max, r.window);
                if (!d.allowed()) {
                    res.setStatus(429);
                    res.setHeader("Retry-After", String.valueOf(d.retryAfterSeconds()));
                    res.setContentType("application/json");
                    res.getWriter().write("{\"success\":false,\"error\":\"Too many requests.\"}");
                    return;
                }
            } catch (Exception e) {
                log.error("Rate limiter unavailable", e);
                if (path.startsWith("/api/auth/")) {
                    res.setStatus(503);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"success\":false,\"error\":\"Rate limiting service unavailable.\"}");
                    return;
                }
            }
            break;
        }
        chain.doFilter(req, res);
    }

    private String clientIp(HttpServletRequest request) {

        String remoteAddress = request.getRemoteAddr();

        if (remoteAddress == null
                || remoteAddress.isBlank()) {

            return "unknown";
        }

        return remoteAddress.trim();
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    public void cleanup() {
        try {
            limiterCleanup();
        } catch (Exception e) {
            log.warn("Rate-limit cleanup failed", e);
        }
    }

    private void limiterCleanup() {
        limiter.cleanup(TimeUnit.HOURS.toMillis(2));
    }
}
