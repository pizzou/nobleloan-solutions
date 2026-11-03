package com.patrick.fintech.loan_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RequestIdFilter extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(RequestIdFilter.class);

    private static final long SLOW_REQUEST_MS = 2_000L;
    private static final long SLOW_HEALTH_MS = 500L;

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        String requestId = request.getHeader(HEADER);

        if (requestId == null
                || !requestId.matches("[A-Za-z0-9_-]{1,64}")) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);

        long startedAt = System.nanoTime();
        boolean unhandledException = false;

        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            unhandledException = true;
            throw ex;
        } finally {
            try {
                long durationMs = TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - startedAt
                );

                int status = response.getStatus();
                String uri = request.getRequestURI();
                boolean apiRequest = uri != null
                        && uri.startsWith("/api/");
                boolean healthRequest = uri != null
                        && uri.startsWith("/actuator/health");

                if (apiRequest || healthRequest) {
                    boolean reportHealth = !healthRequest
                            || durationMs >= SLOW_HEALTH_MS
                            || status >= 500
                            || unhandledException;

                    if (reportHealth) {
                        String route = safeRoute(request);

                        if (status >= 500 || unhandledException) {
                            log.error(
                                "HTTP request failed method={} route={} status={} durationMs={} unhandled={}",
                                request.getMethod(),
                                route,
                                status,
                                durationMs,
                                unhandledException
                            );
                        } else if (durationMs >= SLOW_REQUEST_MS) {
                            log.warn(
                                "Slow HTTP request method={} route={} status={} durationMs={}",
                                request.getMethod(),
                                route,
                                status,
                                durationMs
                            );
                        } else {
                            log.info(
                                "HTTP request completed method={} route={} status={} durationMs={}",
                                request.getMethod(),
                                route,
                                status,
                                durationMs
                            );
                        }
                    }
                }
            } finally {
                MDC.remove(MDC_KEY);
            }
        }
    }

    private static String safeRoute(HttpServletRequest request) {
        Object matched = request.getAttribute(
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE
        );

        if (matched instanceof String pattern
                && !pattern.isBlank()) {
            return pattern;
        }

        String uri = request.getRequestURI();

        if (uri != null && uri.startsWith("/api/")) {
            String remainder = uri.substring(5);
            int separator = remainder.indexOf('/');

            String group = separator >= 0
                    ? remainder.substring(0, separator)
                    : remainder;

            if (group.matches("[A-Za-z0-9_-]{1,50}")) {
                return "/api/" + group + "/*";
            }

            return "/api/*";
        }

        return "/actuator/health/*";
    }
}
