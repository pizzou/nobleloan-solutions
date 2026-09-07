package com.patrick.fintech.loan_backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import com.patrick.fintech.loan_backend.config.JwtUtils;
import com.patrick.fintech.loan_backend.service.CustomUserDetailsService;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;

    public JwtAuthFilter(
        JwtUtils jwtUtils,
        CustomUserDetailsService userDetailsService,
        UserRepository userRepository
    ) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
    }

    /*
     * ============================================================
     * SKIP CORS PREFLIGHT
     * ============================================================
     *
     * OPTIONS requests are handled by Spring Security CORS.
     * JWT authentication must never interfere with them.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        return false;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        /*
         * If another authentication mechanism has already authenticated
         * this request, do not overwrite it.
         */
        if (SecurityContextHolder
                .getContext()
                .getAuthentication() != null) {

            filterChain.doFilter(request, response);
            return;
        }

        String header =
            request.getHeader("Authorization");

        String token = null;
        if (header != null && !header.isBlank() && header.startsWith("Bearer ")) {
            token = header.substring(7).trim();
        } else if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if ("NLS_SESSION".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        /* No usable bearer token or session cookie. Spring Security will decide
         * later whether the endpoint requires authentication. */
        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {

            /*
             * Invalid/expired JWT.
             *
             * Do not generate a custom 403 here.
             * Leave the request unauthenticated so Spring Security's
             * AuthenticationEntryPoint can return the standard 401.
             */
            if (!jwtUtils.validateToken(token)) {

                log.debug(
                    "JWT validation failed for request {} {}",
                    request.getMethod(),
                    request.getRequestURI()
                );

                filterChain.doFilter(request, response);
                return;
            }

            /*
             * ========================================================
             * MFA SETUP TOKEN
             * ========================================================
             */
            if (
                jwtUtils.isSetupToken(token)
                    && !request
                        .getRequestURI()
                        .startsWith("/api/mfa")
            ) {

                response.setStatus(
                    HttpServletResponse.SC_FORBIDDEN
                );

                response.setContentType(
                    "application/json"
                );

                response.setCharacterEncoding("UTF-8");

                response.getWriter().write(
                    """
                    {
                      "success": false,
                      "error": "Complete MFA setup before accessing this resource."
                    }
                    """
                );

                return;
            }

            /*
             * ========================================================
             * LOAD USER
             * ========================================================
             */
            String email =
                jwtUtils.getEmailFromToken(token);

            if (email == null || email.isBlank()) {

                filterChain.doFilter(request, response);
                return;
            }

            com.patrick.fintech.loan_backend.model.User currentUser =
                userRepository.findByEmail(email).orElse(null);

            if (currentUser == null || currentUser.getStatus() != com.patrick.fintech.loan_backend.model.User.UserStatus.ACTIVE) {
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            long tokenVersion = jwtUtils.getTokenVersion(token);
            long currentTokenVersion = currentUser.getTokenVersion() == null ? 0L : currentUser.getTokenVersion();
            if (tokenVersion != currentTokenVersion) {
                log.debug("Rejected revoked JWT for user {}", email);
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            UserDetails userDetails =
                userDetailsService.loadUserByUsername(email);

            /*
             * ========================================================
             * AUTHENTICATION
             * ========================================================
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
             * A stale, expired, malformed or otherwise unusable token
             * must not crash the request or application.
             *
             * Spring Security will produce 401 for protected resources.
             */
            SecurityContextHolder
                .clearContext();

            log.debug(
                "JWT authentication failed for {} {}: {}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getMessage()
            );
        }

        filterChain.doFilter(request, response);
    }
}