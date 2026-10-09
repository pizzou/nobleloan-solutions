package com.patrick.fintech.loan_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Prevents browsers, shared proxies and intermediate caches from retaining
 * financial/customer API responses. The lending UI authenticates with an
 * HttpOnly session cookie, so a stale cached GET is both incorrect and a
 * confidentiality risk.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiNoStoreFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (request.getRequestURI() != null && request.getRequestURI().startsWith(API_PREFIX)) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
            response.setHeader("Vary", "Cookie, Authorization");
        }

        filterChain.doFilter(request, response);
    }
}
