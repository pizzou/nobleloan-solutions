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
 * Protects browser mutations authenticated through the NLS_SESSION cookie.
 *
 * The application uses a stateless JWT stored in an HttpOnly cookie.
 * Because browsers automatically attach cookies, state-changing requests
 * must be protected against cross-site request forgery.
 *
 * This filter validates Origin / Referer / Fetch Metadata before allowing
 * authenticated cookie mutations to continue.
 *
 * Bearer-token clients are not subject to the browser-cookie origin rule.
 */
@Component
public class SameOriginMutationFilter extends OncePerRequestFilter {

    private final Set<String> allowedOrigins;
    private final String sessionCookieName;

    public SameOriginMutationFilter(
            @Value("${app.cors.allowed-origins:https://nobleloan-solutions.vercel.app}")
            String origins,

            @Value("${app.auth.cookie.name:NLS_SESSION}")
            String sessionCookieName) {

        String configured = origins == null ? "" : origins;

        this.allowedOrigins = Arrays.stream(configured.split(","))
                .map(this::normalize)
                .filter(origin -> !origin.isBlank())
                .collect(Collectors.toUnmodifiableSet());

        this.sessionCookieName =
                sessionCookieName == null || sessionCookieName.isBlank()
                        ? "NLS_SESSION"
                        : sessionCookieName.trim();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        /*
         * CORS preflight must never be rejected by this mutation filter.
         */
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {

        /*
         * Only state-changing requests need origin protection.
         */
        if (!isMutation(request)) {
            chain.doFilter(request, response);
            return;
        }

        /*
         * Only cookie-authenticated requests need this protection.
         *
         * Requests without NLS_SESSION may be:
         * - public API requests
         * - bearer-token API clients
         * - API-key clients
         */
        if (!hasSessionCookie(request)) {
            chain.doFilter(request, response);
            return;
        }

        /*
         * The JWT filter runs before this filter and should already have
         * populated the SecurityContext.
         *
         * A stale/invalid cookie is therefore allowed to continue to Spring
         * Security, which will return 401 where authentication is required.
         */
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getPrincipal() == null
                || "anonymousUser".equals(authentication.getPrincipal())) {

            chain.doFilter(request, response);
            return;
        }

        /*
         * Explicit Bearer authentication is not dependent on the browser
         * session cookie.
         */
        String authorization =
                request.getHeader("Authorization");

        if (authorization != null
                && authorization.regionMatches(
                        true,
                        0,
                        "Bearer ",
                        0,
                        7)
                && authorization.substring(7).trim().length() > 0) {

            chain.doFilter(request, response);
            return;
        }

        /*
         * Browser Origin header.
         */
        String origin =
                normalize(request.getHeader("Origin"));

        /*
         * Browser Referer fallback.
         */
        String refererOrigin =
                normalize(
                        originFromReferer(
                                request.getHeader("Referer")
                        )
                );

        /*
         * Never trust Origin: null for authenticated state-changing
         * cookie requests.
         */
        if ("null".equals(origin)) {
            reject(response);
            return;
        }

        /*
         * Normal Vercel browser request.
         *
         * Example:
         * Origin: https://nobleloan-solutions.vercel.app
         */
        if (isAllowedOrigin(origin)) {
            chain.doFilter(request, response);
            return;
        }

        /*
         * Some browsers/proxies may omit Origin.
         *
         * Referer is accepted as the fallback when its origin is explicitly
         * configured as an allowed application origin.
         */
        if (origin.isBlank()
                && isAllowedOrigin(refererOrigin)) {

            chain.doFilter(request, response);
            return;
        }

        /*
         * Reverse proxies can sometimes remove both Origin and Referer.
         *
         * Sec-Fetch-Site is generated by the browser and provides a safe
         * fallback for an actual same-origin browser request.
         */
        String fetchSite =
                request.getHeader("Sec-Fetch-Site");

        if (origin.isBlank()
                && refererOrigin.isBlank()
                && "same-origin".equalsIgnoreCase(fetchSite)) {

            chain.doFilter(request, response);
            return;
        }

        /*
         * Everything else is a cross-site or unverifiable mutation.
         */
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

            URI uri =
                    URI.create(referer.trim());

            if (uri.getScheme() == null
                    || uri.getHost() == null) {

                return "";
            }

            return uri.getScheme()
                    + "://"
                    + uri.getRawAuthority();

        } catch (Exception ignored) {

            return "";
        }
    }

    private String normalize(String value) {

        if (value == null) {
            return "";
        }

        String v =
                value.trim();

        if (v.isBlank()) {
            return "";
        }

        if ("null".equalsIgnoreCase(v)) {
            return "null";
        }

        try {

            URI uri =
                    URI.create(v);

            if (uri.getScheme() != null
                    && uri.getHost() != null) {

                String scheme =
                        uri.getScheme()
                                .toLowerCase(Locale.ROOT);

                String host =
                        uri.getHost()
                                .toLowerCase(Locale.ROOT);

                int port =
                        uri.getPort();

                boolean defaultPort =
                        ("http".equals(scheme) && port == 80)
                                || ("https".equals(scheme) && port == 443)
                                || port == -1;

                if (defaultPort) {
                    return scheme + "://" + host;
                }

                return scheme
                        + "://"
                        + host
                        + ":"
                        + port;
            }

        } catch (Exception ignored) {
            /*
             * Fall through to conservative normalization.
             */
        }

        while (v.endsWith("/")
                && v.length() > 8) {

            v =
                    v.substring(
                            0,
                            v.length() - 1
                    );
        }

        return v.toLowerCase(Locale.ROOT);
    }

    private void reject(HttpServletResponse response)
            throws IOException {

        response.setStatus(
                HttpServletResponse.SC_FORBIDDEN
        );

        response.setContentType(
                "application/json"
        );

        response.setCharacterEncoding(
                "UTF-8"
        );

        response.setHeader(
                "Cache-Control",
                "no-store, no-cache, must-revalidate, max-age=0"
        );

        response.setHeader(
                "Pragma",
                "no-cache"
        );

        response.getWriter().write(
                "{\"success\":false,\"error\":\"Cross-site state-changing request rejected.\"}"
        );
    }
}