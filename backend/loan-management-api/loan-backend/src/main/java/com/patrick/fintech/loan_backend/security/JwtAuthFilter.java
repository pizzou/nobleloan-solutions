package com.patrick.fintech.loan_backend.security;

import com.patrick.fintech.loan_backend.config.JwtUtils;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import com.patrick.fintech.loan_backend.service.CustomUserDetailsService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String AUTHENTICATED_USER_ATTRIBUTE =
            JwtAuthFilter.class.getName() + ".authenticatedUser";

    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;

    @Value("${app.auth.cookie.name:NLS_SESSION}")
    private String sessionCookieName;

    public JwtAuthFilter(
            JwtUtils jwtUtils,
            CustomUserDetailsService userDetailsService,
            UserRepository userRepository) {

        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
    }

    /**
     * CORS preflight requests must never require JWT authentication.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        /*
         * Do not replace an authentication that another Spring Security
         * mechanism has already established.
         */
        if (SecurityContextHolder
                .getContext()
                .getAuthentication() != null) {

            filterChain.doFilter(request, response);
            return;
        }

        String token = resolveToken(request);

        /*
         * Public endpoint or request without credentials.
         */
        if (token == null || token.isBlank()) {

            filterChain.doFilter(request, response);
            return;
        }

        try {

            /*
             * IMPORTANT:
             *
             * Parse and verify the JWT exactly once.
             *
             * The old implementation called several JwtUtils methods
             * independently, potentially parsing/verifying the same JWT
             * multiple times.
             */
            Claims claims;

            try {
                claims = jwtUtils.parseClaims(token);

            } catch (JwtException | IllegalArgumentException ex) {

                log.debug(
                        "JWT validation failed for request {} {}",
                        request.getMethod(),
                        request.getRequestURI()
                );

                /*
                 * Leave the request unauthenticated.
                 * Spring Security's configured AuthenticationEntryPoint
                 * handles protected endpoints.
                 */
                filterChain.doFilter(request, response);
                return;
            }

            /*
             * ============================================================
             * MFA SETUP TOKEN
             * ============================================================
             */
            Object purpose = claims.get("purpose");

            if ("mfa-setup".equals(purpose)
                    && !request.getRequestURI().startsWith("/api/mfa")) {

                writeMfaSetupRequired(response);
                return;
            }

            /*
             * ============================================================
             * USER
             * ============================================================
             */
            String email = claims.getSubject();

            if (email == null || email.isBlank()) {

                filterChain.doFilter(request, response);
                return;
            }

            /*
             * This is the single authoritative database lookup needed
             * by the JWT filter.
             *
             * The resulting User entity is then reused to build
             * UserDetails and exposed to controllers through the request
             * attribute.
             */
            User currentUser =
                    userRepository
                            .findSecurityUserByEmailIgnoreCase(email)
                            .orElse(null);

            if (currentUser == null
                    || currentUser.getStatus() != User.UserStatus.ACTIVE) {

                SecurityContextHolder.clearContext();

                filterChain.doFilter(request, response);
                return;
            }

            /*
             * ============================================================
             * TOKEN VERSION / REVOCATION
             * ============================================================
             */
            long tokenVersion = extractTokenVersion(claims);

            long currentTokenVersion =
                    currentUser.getTokenVersion() == null
                            ? 0L
                            : currentUser.getTokenVersion();

            if (tokenVersion != currentTokenVersion) {

                log.debug(
                        "Rejected revoked JWT for user {}",
                        email
                );

                SecurityContextHolder.clearContext();

                filterChain.doFilter(request, response);
                return;
            }

            /*
             * ============================================================
             * BUILD SPRING SECURITY USER
             * ============================================================
             *
             * No second database query.
             *
             * fromUser(User) converts the already-loaded authoritative
             * application User into Spring Security UserDetails.
             */
            UserDetails userDetails =
                    userDetailsService.fromUser(currentUser);

            /*
             * Reuse the same authoritative User entity for controllers
             * such as /auth/me.
             */
            request.setAttribute(
                    AUTHENTICATED_USER_ATTRIBUTE,
                    currentUser
            );

            /*
             * ============================================================
             * SECURITY CONTEXT
             * ============================================================
             */
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

            authentication.setDetails(
                    new WebAuthenticationDetailsSource()
                            .buildDetails(request)
            );

            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authentication);

        } catch (Exception exception) {

            /*
             * Never allow a malformed/stale token to crash the
             * application or authentication filter.
             */
            SecurityContextHolder.clearContext();

            log.debug(
                    "JWT authentication failed for {} {}: {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    exception.getMessage()
            );
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Resolve Authorization Bearer token first, then the HttpOnly
     * application session cookie.
     */
    private String resolveToken(HttpServletRequest request) {

        String authorization =
                request.getHeader("Authorization");

        if (authorization != null
                && !authorization.isBlank()
                && authorization.startsWith("Bearer ")) {

            String bearerToken =
                    authorization.substring(7).trim();

            if (!bearerToken.isBlank()) {
                return bearerToken;
            }
        }

        Cookie[] cookies = request.getCookies();

        if (cookies != null) {

            for (Cookie cookie : cookies) {

                if (sessionCookieName.equals(cookie.getName())) {

                    String cookieToken = cookie.getValue();

                    if (cookieToken != null
                            && !cookieToken.isBlank()) {

                        return cookieToken;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Extract tokenVersion from the already-parsed JWT claims.
     */
    private long extractTokenVersion(Claims claims) {

        Object value = claims.get("tokenVersion");

        if (value instanceof Number number) {
            return number.longValue();
        }

        if (value instanceof String text
                && !text.isBlank()) {

            return Long.parseLong(text);
        }

        /*
         * Preserve compatibility with tokens that were created before
         * tokenVersion was introduced.
         */
        return 0L;
    }

    private void writeMfaSetupRequired(
            HttpServletResponse response)
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

        response.getWriter().write(
                """
                {
                  "success": false,
                  "error": "Complete MFA setup before accessing this resource."
                }
                """
        );
    }
}