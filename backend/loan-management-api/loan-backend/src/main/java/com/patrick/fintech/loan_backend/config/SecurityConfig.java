package com.patrick.fintech.loan_backend.config;

import com.patrick.fintech.loan_backend.security.JwtAuthFilter;
import com.patrick.fintech.loan_backend.security.RateLimitFilter;
import com.patrick.fintech.loan_backend.security.RegulatoryApiKeyAuthFilter;
import com.patrick.fintech.loan_backend.security.SameOriginMutationFilter;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtFilter;
    private final RegulatoryApiKeyAuthFilter regulatoryApiKeyAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final SameOriginMutationFilter sameOriginMutationFilter;

    @Value("${app.cors.allowed-origins:https://nobleloan-solutions.vercel.app}")
    private String allowedOrigins;

    @Value("${app.security.expose-h2:false}")
    private boolean exposeH2;

    @Value("${app.security.expose-api-docs:false}")
    private boolean exposeApiDocs;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            // ============================================================
            // CORS
            // ============================================================
            .cors(cors -> cors
                .configurationSource(corsConfigurationSource())
            )

            // ============================================================
            // CSRF
            // ============================================================
            //
            // Browser-session mutations remain protected by the
            // SameOriginMutationFilter.
            //
            // Public APIs do not have an authenticated application
            // session, so Spring CSRF must not reject legitimate
            // public borrower/payment/application submissions.
            //
            // Authentication endpoints are also excluded because
            // authentication has not happened yet.
            // ============================================================
            .csrf(csrf -> csrf
                .csrfTokenRepository(
                    CookieCsrfTokenRepository.withHttpOnlyFalse()
                )
                .csrfTokenRequestHandler(
                    new CsrfTokenRequestAttributeHandler()
                )
                .ignoringRequestMatchers(
                    publicAndAuthenticationCsrfMatcher()
                )
            )

            // ============================================================
            // STATELESS
            // ============================================================
            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            // ============================================================
            // SECURITY ERROR HANDLING
            // ============================================================
            .exceptionHandling(exception -> exception

                .authenticationEntryPoint(
                    (request, response, authException) -> {

                        response.setStatus(
                            jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED
                        );

                        response.setContentType("application/json");
                        response.setCharacterEncoding("UTF-8");

                        response.getWriter().write("""
                            {
                              "success": false,
                              "error": "Authentication is required for this resource."
                            }
                            """);
                    }
                )

                .accessDeniedHandler(
                    (request, response, accessDeniedException) -> {

                        response.setStatus(
                            jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN
                        );

                        response.setContentType("application/json");
                        response.setCharacterEncoding("UTF-8");

                        response.getWriter().write("""
                            {
                              "success": false,
                              "error": "You do not have permission to perform this action."
                            }
                            """);
                    }
                )
            )

            // ============================================================
            // AUTHORIZATION
            // ============================================================
            .authorizeHttpRequests(authorize -> authorize

                // CORS preflight
                .requestMatchers(
                    HttpMethod.OPTIONS,
                    "/**"
                )
                .permitAll()

                // Authentication
                .requestMatchers(
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/auth/logout",
                    "/api/auth/forgot-password",
                    "/api/auth/reset-password",
                    "/api/auth/csrf"
                )
                .permitAll()

                // Authenticated account endpoints
                .requestMatchers(
                    "/api/auth/me",
                    "/api/auth/change-password"
                )
                .authenticated()

                // ========================================================
                // PUBLIC BORROWER/APPLICANT API
                // ========================================================
                .requestMatchers(
                    "/api/public/contact",
                    "/api/public/borrower/**",
                    "/api/public/applications/**",
                    "/api/public/tenant/**",
                    "/api/public/loan-application",
                    "/api/public/dashboard",
                    "/api/public/payment-schedule",
                    "/api/public/esignature/**",
                    "/api/public/webhooks/**"
                )
                .permitAll()

                // Health
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/health/**"
                )
                .permitAll()

                // WebSocket handshake
                .requestMatchers(
                    "/ws",
                    "/ws/**"
                )
                .permitAll()

                // Development surfaces
                .requestMatchers(
                    "/h2-console/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/api-docs/**"
                )
                .access((authentication, context) ->
                    new org.springframework.security.authorization.AuthorizationDecision(
                        isDevelopmentSurfaceEnabled(
                            context.getRequest().getRequestURI()
                        )
                    )
                )

                // Everything else requires authentication.
                .anyRequest()
                .authenticated()
            )

            // ============================================================
            // SECURITY HEADERS
            // ============================================================
            .headers(headers -> headers
                .frameOptions(frame ->
                    frame.sameOrigin()
                )
                .httpStrictTransportSecurity(hsts ->
                    hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31536000)
                )
            )

            // ============================================================
            // FILTER ORDER
            // ============================================================
            .addFilterBefore(
                rateLimitFilter,
                UsernamePasswordAuthenticationFilter.class
            )

            .addFilterBefore(
                sameOriginMutationFilter,
                UsernamePasswordAuthenticationFilter.class
            )

            .addFilterBefore(
                jwtFilter,
                UsernamePasswordAuthenticationFilter.class
            )

            .addFilterBefore(
                regulatoryApiKeyAuthFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }

    /**
     * CSRF exclusions for endpoints that intentionally accept requests
     * before an authenticated application session exists.
     *
     * We use Spring Security's RequestMatcher directly instead of
     * AntPathRequestMatcher, so no additional dependency is required.
     */
    @Bean
    public RequestMatcher publicAndAuthenticationCsrfMatcher() {

        return request -> {

            String method = request.getMethod();

            boolean mutation =
                HttpMethod.POST.matches(method)
                    || HttpMethod.PUT.matches(method)
                    || HttpMethod.PATCH.matches(method)
                    || HttpMethod.DELETE.matches(method);

            if (!mutation) {
                return false;
            }

            String uri = request.getRequestURI();

            if (uri == null) {
                return false;
            }

            // ------------------------------------------------------------
            // Authentication bootstrap endpoints
            // ------------------------------------------------------------
            if (
                uri.equals("/api/auth/login")
                    || uri.equals("/api/auth/register")
                    || uri.equals("/api/auth/logout")
                    || uri.equals("/api/auth/forgot-password")
                    || uri.equals("/api/auth/reset-password")
            ) {
                return true;
            }

            // ------------------------------------------------------------
            // Public API
            // ------------------------------------------------------------
            //
            // These endpoints intentionally support unauthenticated
            // borrowers/applicants.
            //
            // If an NLS_SESSION cookie is nevertheless present, the
            // SameOriginMutationFilter still protects the mutation.
            // ------------------------------------------------------------
            return uri.startsWith("/api/public/");
        };
    }

    private boolean isDevelopmentSurfaceEnabled(String uri) {

        if (uri == null) {
            return false;
        }

        if (uri.startsWith("/h2-console")) {
            return exposeH2;
        }

        return exposeApiDocs;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration =
            new CorsConfiguration();

        Set<String> originSet =
            new LinkedHashSet<>();

        if (
            allowedOrigins != null
                && !allowedOrigins.isBlank()
        ) {

            Arrays.stream(allowedOrigins.split(","))
                .map(this::normalizeCorsOrigin)
                .filter(origin -> !origin.isBlank())
                .forEach(originSet::add);
        }

        configuration.setAllowedOrigins(
            new ArrayList<>(originSet)
        );

        configuration.setAllowedMethods(
            List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS",
                "HEAD"
            )
        );

        configuration.setAllowedHeaders(
            List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With",
                "X-Api-Key",
                "X-Tenant-Slug",
                "X-Tenant-Host",
                "X-Request-Id",
                "Idempotency-Key",
                "X-Webhook-Secret",
                "X-XSRF-TOKEN"
            )
        );

        configuration.setExposedHeaders(
            List.of(
                "Location",
                "Retry-After",
                "X-Request-Id",
                "Content-Disposition"
            )
        );

        configuration.setAllowCredentials(true);

        configuration.setMaxAge(1800L);

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
            "/**",
            configuration
        );

        return source;
    }

    private String normalizeCorsOrigin(String origin) {

        if (origin == null) {
            return "";
        }

        String normalized = origin.trim();

        while (
            normalized.endsWith("/")
                && normalized.length() > 8
        ) {
            normalized =
                normalized.substring(
                    0,
                    normalized.length() - 1
                );
        }

        return normalized;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration)
            throws Exception {

        return configuration.getAuthenticationManager();
    }
}
