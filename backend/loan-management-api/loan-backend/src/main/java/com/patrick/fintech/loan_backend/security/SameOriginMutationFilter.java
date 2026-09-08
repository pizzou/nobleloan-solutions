package com.patrick.fintech.loan_backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Origin protection for browser requests authenticated by the NLS_SESSION
 * cookie.
 *
 * The application uses a stateless JWT stored in an HttpOnly cookie. Such a
 * cookie is automatically attached by a browser, so mutation endpoints must
 * reject cross-site browser requests even though there is no server session.
 * This filter is therefore the browser CSRF/origin control for cookie-auth
 * mutations. Bearer-token and API-key clients are not subject to this cookie
 * rule because their credentials are explicitly supplied by the client.
 */
@Component
public class SameOriginMutationFilter extends OncePerRequestFilter {

    private final Set<String> allowedOrigins;
    private final String sessionCookieName;

    public SameOriginMutationFilter(
            @Value("${app.cors.allowed-origins:https://nobleloan-solutions.vercel.app}") String origins,
            @Value("${app.auth.cookie.name:NLS_SESSION}") String sessionCookieName) {

        String configured = origins == null ? "" : origins;
        this.allowedOrigins = Arrays.stream(configured.split(","))
                .map(this::normalize)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());

        this.sessionCookieName = sessionCookieName == null || sessionCookieName.isBlank()
                ? "NLS_SESSION"
                : sessionCookieName.trim();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        if (!isMutation(request) || !hasSessionCookie(request)) {
            chain.doFilter(request, response);
            return;
        }

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getPrincipal() == null
                || "anonymousUser".equals(authentication.getPrincipal())) {
            chain.doFilter(request, response);
            return;
        }

        // Explicit bearer authentication is not dependent on a browser cookie.
        String authorization = request.getHeader("Authorization");
        if (authorization != null
                && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                && authorization.substring(7).trim().length() > 0) {
            chain.doFilter(request, response);
            return;
        }

        String origin = normalize(request.getHeader("Origin"));
        String refererOrigin = normalize(originFromReferer(request.getHeader("Referer")));

        // A browser must not be allowed to use the literal opaque Origin: null
        // for a cookie-authenticated state-changing request.
        if ("null".equals(origin)) {
            reject(response);
            return;
        }

        if (isAllowedOrigin(origin)
                || (origin.isBlank() && isAllowedOrigin(refererOrigin))) {
            chain.doFilter(request, response);
            return;
        }

        // Reverse proxies can remove Origin/Referer. For a browser request,
        // Sec-Fetch-Site=same-origin is a useful controlled fallback.
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if (origin.isBlank()
                && refererOrigin.isBlank()
                && "same-origin".equalsIgnoreCase(fetchSite)) {
            chain.doFilter(request, response);
            return;
        }

        reject(response);
    }

    private boolean isMutation(HttpServletRequest request) {
        String method = request.getMethod();
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
    }

    private boolean hasSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (sessionCookieName.equals(cookie.getName())
                    && cookie.getValue() != null
                    && !cookie.getValue().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private boolean isAllowedOrigin(String origin) {
        return origin != null
                && !origin.isBlank()
                && allowedOrigins.contains(origin);
    }

    private String originFromReferer(String referer) {
        if (referer == null || referer.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(referer.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return "";
            }
            return uri.getScheme() + "://" + uri.getRawAuthority();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        String v = value.trim();
        if (v.isBlank() || "null".equalsIgnoreCase(v)) {
            return v.isBlank() ? "" : "null";
        }

        try {
            URI uri = URI.create(v);
            if (uri.getScheme() != null && uri.getHost() != null) {
                String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
                String host = uri.getHost().toLowerCase(Locale.ROOT);
                int port = uri.getPort();
                boolean defaultPort = ("http".equals(scheme) && port == 80)
                        || ("https".equals(scheme) && port == 443)
                        || port == -1;
                return defaultPort
                        ? scheme + "://" + host
                        : scheme + "://" + host + ":" + port;
            }
        } catch (Exception ignored) {
            // Fall through to conservative string normalization.
        }

        while (v.endsWith("/") && v.length() > 8) {
            v = v.substring(0, v.length() - 1);
        }
        return v.toLowerCase(Locale.ROOT);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.getWriter().write(
                "{\"success\":false,\"error\":\"Cross-site state-changing request rejected.\"}");
    }
}
