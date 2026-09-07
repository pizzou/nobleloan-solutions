package com.patrick.fintech.loan_backend.config;

import com.patrick.fintech.loan_backend.security.JwtAuthFilter;
import com.patrick.fintech.loan_backend.security.RateLimitFilter;
import com.patrick.fintech.loan_backend.security.RegulatoryApiKeyAuthFilter;

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
import com.patrick.fintech.loan_backend.security.SameOriginMutationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

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
            /*
             * ============================================================
             * CORS
             * ============================================================
             *
             * CORS must execute before authentication filters.
             * This is particularly important for browser OPTIONS
             * preflight requests.
             */
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            /*
             * The browser session is carried in an HttpOnly cookie, so CSRF
             * protection is mandatory for state-changing browser requests.
             * Provider webhooks are machine-to-machine and are authenticated
             * by their own signatures/secrets, so they are excluded below.
             */
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers(
                    "/api/public/webhooks/**"
                )
            )

            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            /*
             * ============================================================
             * AUTHENTICATION / AUTHORIZATION ERRORS
             * ============================================================
             *
             * Always return JSON instead of Spring's HTML error page.
             */
            .exceptionHandling(exception -> exception

                .authenticationEntryPoint((request, response, authException) -> {

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
                })

                .accessDeniedHandler((request, response, accessDeniedException) -> {

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
                })
            )

            /*
             * ============================================================
             * AUTHORIZATION
             * ============================================================
             */
            .authorizeHttpRequests(authorize -> authorize

                /*
                 * Browser CORS preflight must NEVER be blocked by JWT.
                 */
                .requestMatchers(HttpMethod.OPTIONS, "/**")
                .permitAll()

                /*
                 * Authentication endpoints.
                 */
                .requestMatchers("/api/auth/**")
                .permitAll()

                /*
                 * Public API.
                 */
                .requestMatchers("/api/public/**")
                .permitAll()

                /*
                 * Render / monitoring health checks.
                 *
                 * These endpoints must be reachable without a JWT so
                 * Render can determine whether the application is alive.
                 */
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/health/**"
                )
                .permitAll()

                /*
                 * Public webhook endpoints.
                 */
                .requestMatchers("/api/public/webhooks/**")
                .permitAll()

                /*
                 * WebSocket handshake.
                 */
                .requestMatchers(
                    "/ws",
                    "/ws/**"
                )
                .permitAll()

                /*
                 * Development-only surfaces.
                 */
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

                /*
                 * Everything else requires authentication.
                 */
                .anyRequest()
                .authenticated()
            )

            /*
             * ============================================================
             * SECURITY HEADERS
             * ============================================================
             */
            .headers(headers -> headers

                .frameOptions(frame -> frame.sameOrigin())

                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                )
            )

            /*
             * ============================================================
             * FILTER ORDER
             * ============================================================
             *
             * All custom filters are placed before the normal username /
             * password authentication filter.
             */
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

    /*
     * ================================================================
     * DEVELOPMENT SURFACES
     * ================================================================
     */
    private boolean isDevelopmentSurfaceEnabled(String uri) {

        if (uri == null) {
            return false;
        }

        if (uri.startsWith("/h2-console")) {
            return exposeH2;
        }

        return exposeApiDocs;
    }

    /*
     * ================================================================
     * CORS
     * ================================================================
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        /*
         * Build an explicit list of origins.
         *
         * Never use:
         *
         *     allowedOrigins = "*"
         *
         * together with credentials.
         */
        Set<String> originSet = new LinkedHashSet<>();

        if (allowedOrigins != null && !allowedOrigins.isBlank()) {

            Arrays.stream(allowedOrigins.split(","))
                .map(this::normalizeCorsOrigin)
                .filter(origin -> !origin.isBlank())
                .forEach(originSet::add);
        }

        configuration.setAllowedOrigins(
            new ArrayList<>(originSet)
        );

        /*
         * ============================================================
         * METHODS
         * ============================================================
         */
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

        /*
         * ============================================================
         * REQUEST HEADERS
         * ============================================================
         *
         * X-Api-Key is required by the external regulatory API filter.
         * X-Tenant-Slug is used by the frontend.
         * X-Request-Id is used by request tracing.
         */
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
                "X-Webhook-Secret"
            )
        );

        /*
         * ============================================================
         * RESPONSE HEADERS
         * ============================================================
         */
        configuration.setExposedHeaders(
            List.of(
                "Location",
                "Retry-After",
                "X-Request-Id",
                "Content-Disposition"
            )
        );

        /*
         * Your frontend uses JWT Authorization headers.
         *
         * Explicit origins above make credentials safe.
         */
        configuration.setAllowCredentials(true);

        /*
         * Browser may cache the preflight result for 30 minutes.
         */
        configuration.setMaxAge(1800L);

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
            "/**",
            configuration
        );

        return source;
    }

    /*
     * ================================================================
     * ORIGIN NORMALIZATION
     * ================================================================
     */
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

    /*
     * ================================================================
     * AUTHENTICATION MANAGER
     * ================================================================
     */
    @Bean
    public AuthenticationManager authenticationManager(
        AuthenticationConfiguration configuration
    ) throws Exception {

        return configuration.getAuthenticationManager();
    }
}