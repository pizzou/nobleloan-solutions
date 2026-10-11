package com.patrick.fintech.loan_backend.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SameOriginMutationFilterTest {
    private final SameOriginMutationFilter filter =
            new SameOriginMutationFilter("https://nobleloansolutions.rw,https://www.nobleloansolutions.rw", "NLS_SESSION");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest authenticatedMutation(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/loans/17/payments");
        request.setRequestURI("/api/loans/17/payments");
        request.setCookies(new jakarta.servlet.http.Cookie("NLS_SESSION", "opaque-session-token"));
        if (origin != null) request.addHeader("Origin", origin);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "operator", null, List.of(new SimpleGrantedAuthority("ROLE_ACCOUNTANT"))));
        return request;
    }

    @Test
    void rejectsCrossOriginCookieAuthenticatedMutation() throws Exception {
        MockHttpServletRequest request = authenticatedMutation("https://attacker.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest(), "Rejected request must not reach the controller");
        assertTrue(response.getContentAsString().contains("Cross-site"));
        assertEquals("no-store, no-cache, must-revalidate, max-age=0", response.getHeader("Cache-Control"));
    }

    @Test
    void permitsConfiguredSameOriginCookieAuthenticatedMutation() throws Exception {
        MockHttpServletRequest request = authenticatedMutation("https://nobleloansolutions.rw");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest(), "Allowed request must reach the filter chain");
        assertNotEquals(403, response.getStatus());
    }

    @Test
    void rejectsOpaqueNullOrigin() throws Exception {
        MockHttpServletRequest request = authenticatedMutation("null");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void permitsExplicitBearerClientsWithoutBrowserCookieTrust() throws Exception {
        MockHttpServletRequest request = authenticatedMutation("https://attacker.example");
        request.addHeader("Authorization", "Bearer explicit-api-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertNotEquals(403, response.getStatus());
    }
}
