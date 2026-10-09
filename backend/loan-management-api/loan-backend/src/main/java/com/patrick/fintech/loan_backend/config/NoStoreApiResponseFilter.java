package com.patrick.fintech.loan_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Prevents browsers and intermediaries from retaining customer/financial API
 * payloads. A response wrapper prevents endpoints from weakening this policy.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class NoStoreApiResponseFilter extends OncePerRequestFilter {

    private static final String CACHE_CONTROL =
            "no-store, no-cache, must-revalidate, max-age=0, private";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        return !(path.equals("/api") || path.startsWith("/api/") ||
                path.equals("/actuator") || path.startsWith("/actuator/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        StrictNoStoreResponseWrapper wrapped = new StrictNoStoreResponseWrapper(response);
        applyNoStoreHeaders(wrapped);
        try {
            filterChain.doFilter(request, wrapped);
        } finally {
            if (!wrapped.isCommitted()) applyNoStoreHeaders(wrapped);
        }
    }

    private static void applyNoStoreHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", CACHE_CONTROL);
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }

    private static String forcedValue(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "cache-control" -> CACHE_CONTROL;
            case "pragma" -> "no-cache";
            case "expires" -> "0";
            case "x-content-type-options" -> "nosniff";
            default -> null;
        };
    }

    private static final class StrictNoStoreResponseWrapper extends HttpServletResponseWrapper {
        private StrictNoStoreResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setHeader(String name, String value) {
            String forced = forcedValue(name);
            super.setHeader(name, forced == null ? value : forced);
        }

        @Override
        public void addHeader(String name, String value) {
            String forced = forcedValue(name);
            if (forced != null) {
                super.setHeader(name, forced);
            } else {
                super.addHeader(name, value);
            }
        }
    }
}
