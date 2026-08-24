package com.patrick.fintech.loan_backend.config;

import com.patrick.fintech.loan_backend.security.JwtAuthFilter;
import com.patrick.fintech.loan_backend.security.RateLimitFilter;
import com.patrick.fintech.loan_backend.security.RegulatoryApiKeyAuthFilter;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthFilter jwtFilter;
        private final RegulatoryApiKeyAuthFilter regulatoryApiKeyAuthFilter;
        private final RateLimitFilter rateLimitFilter;

        @Value("${app.cors.allowed-origins:https://nobleloan-solutions.vercel.app}")
        private String allowedOrigins;

        @Value("${app.security.expose-h2:false}")
        private boolean exposeH2;

        @Value("${app.security.expose-api-docs:false}")
        private boolean exposeApiDocs;

        @Bean
        public SecurityFilterChain filterChain(
                        HttpSecurity http) throws Exception {

                http

                                .cors(cors -> cors.configurationSource(corsSource()))

                                .csrf(csrf -> csrf.disable())

                                .sessionManagement(session -> session.sessionCreationPolicy(
                                                SessionCreationPolicy.STATELESS))

                                .exceptionHandling(exception -> exception

                                                .authenticationEntryPoint(
                                                                (request, response, authException) -> {

                                                                        response.setStatus(
                                                                                        jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);

                                                                        response.setContentType(
                                                                                        "application/json");

                                                                        response.setCharacterEncoding(
                                                                                        "UTF-8");

                                                                        response.getWriter().write(
                                                                                        """
                                                                                                        {
                                                                                                          "success": false,
                                                                                                          "error": "Authentication is required for this resource."
                                                                                                        }
                                                                                                        """);
                                                                })

                                                .accessDeniedHandler(
                                                                (request, response, accessDeniedException) -> {

                                                                        response.setStatus(
                                                                                        jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);

                                                                        response.setContentType(
                                                                                        "application/json");

                                                                        response.setCharacterEncoding(
                                                                                        "UTF-8");

                                                                        response.getWriter().write(
                                                                                        """
                                                                                                        {
                                                                                                          "success": false,
                                                                                                          "error": "You do not have permission to perform this action."
                                                                                                        }
                                                                                                        """);
                                                                }))

                                .authorizeHttpRequests(authorize -> authorize

                                                .requestMatchers(
                                                                "/api/auth/**")
                                                .permitAll()

                                                .requestMatchers(
                                                                "/api/public/**")
                                                .permitAll()

                                                .requestMatchers(
                                                                "/actuator/health",
                                                                "/actuator/health/**")
                                                .permitAll()

                                                .requestMatchers(
                                                                "/api/public/webhooks/**")
                                                .permitAll()

                                                .requestMatchers(
                                                                "/ws",
                                                                "/ws/**")
                                                .permitAll()

                                                .requestMatchers(
                                                                "/h2-console/**",
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html",
                                                                "/api-docs/**")
                                                .access((authentication,
                                                                context) -> new org.springframework.security.authorization.AuthorizationDecision(
                                                                                isDevelopmentSurfaceEnabled(
                                                                                                context.getRequest()
                                                                                                                .getRequestURI())))

                                                .anyRequest().authenticated())

                                // ============================================================
                                // SECURITY HEADERS
                                // ============================================================

                                .headers(headers -> headers
                                                .frameOptions(frame -> frame.sameOrigin())
                                                .httpStrictTransportSecurity(hsts -> hsts
                                                                .includeSubDomains(true)
                                                                .maxAgeInSeconds(31536000)))

                                // ============================================================
                                // RATE LIMIT
                                // ============================================================

                                .addFilterBefore(
                                                rateLimitFilter,
                                                UsernamePasswordAuthenticationFilter.class)

                                // ============================================================
                                // JWT
                                // ============================================================

                                .addFilterBefore(
                                                jwtFilter,
                                                UsernamePasswordAuthenticationFilter.class)

                                // ============================================================
                                // REGULATORY API KEY
                                // ============================================================

                                .addFilterBefore(
                                                regulatoryApiKeyAuthFilter,
                                                UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        // ================================================================
        // DEVELOPMENT SURFACES
        // ================================================================

        private boolean isDevelopmentSurfaceEnabled(String uri) {

                if (uri == null) {
                        return false;
                }

                if (uri.startsWith("/h2-console")) {
                        return exposeH2;
                }

                return exposeApiDocs;
        }

        // ================================================================
        // CORS
        // ================================================================

        @Bean
        public CorsConfigurationSource corsSource() {

                CorsConfiguration configuration = new CorsConfiguration();

                List<String> origins = Arrays.stream(
                                allowedOrigins.split(","))
                                .map(String::trim)
                                .filter(origin -> !origin.isBlank())
                                .toList();

                configuration.setAllowedOrigins(origins);

                configuration.setAllowedMethods(
                                List.of(
                                                "GET",
                                                "POST",
                                                "PUT",
                                                "PATCH",
                                                "DELETE",
                                                "OPTIONS"));

                configuration.setAllowedHeaders(
                                List.of(
                                                "Authorization",
                                                "Content-Type",
                                                "Accept",
                                                "Idempotency-Key",
                                                "X-Requested-With",
                                                "X-Tenant-Slug",
                                                "X-Tenant-Host",
                                                "X-Request-Id"));

                configuration.setExposedHeaders(
                                List.of(
                                                "Location",
                                                "Retry-After",
                                                "X-Request-Id"));

                configuration.setAllowCredentials(true);

                configuration.setMaxAge(1800L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

                source.registerCorsConfiguration(
                                "/**",
                                configuration);

                return source;
        }

        @Bean
        public AuthenticationManager authenticationManager(
                        AuthenticationConfiguration configuration)
                        throws Exception {

                return configuration.getAuthenticationManager();
        }
}