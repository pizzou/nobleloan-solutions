package com.patrick.fintech.loan_backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CSRF defense for the HttpOnly session cookie. Browser requests that mutate
 * state must originate from one of the explicitly configured trusted origins.
 * Non-browser/API clients without Origin/Referer are allowed because they do not
 * carry the browser session cookie unless they explicitly opt into it.
 */
@Component
public class SameOriginMutationFilter extends OncePerRequestFilter {

    private final Set<String> allowedOrigins;

    public SameOriginMutationFilter(@Value("${app.cors.allowed-origins:}") String origins) {
        String configured = origins == null ? "" : origins;
        this.allowedOrigins = Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(this::normalize)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String method = request.getMethod();
        boolean mutation = "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method);

        if (!mutation || !hasSessionCookie(request)) {
            chain.doFilter(request, response);
            return;
        }

        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");
        String source = origin != null && !origin.isBlank() ? origin : originFromReferer(referer);

        if (source == null || !allowedOrigins.contains(normalize(source))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"error\":\"Cross-site state-changing request rejected.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean hasSessionCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return false;
        for (var cookie : request.getCookies()) {
            if ("NLS_SESSION".equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) return true;
        }
        return false;
    }

    private String originFromReferer(String referer) {
        if (referer == null || referer.isBlank()) return null;
        try {
            URI uri = URI.create(referer);
            if (uri.getScheme() == null || uri.getHost() == null) return null;
            return uri.getScheme() + "://" + uri.getRawAuthority();
        } catch (Exception e) {
            return null;
        }
    }

    private String normalize(String value) {
        String v = value == null ? "" : value.trim();
        while (v.endsWith("/") && v.length() > 8) v = v.substring(0, v.length() - 1);
        return v;
    }
}
