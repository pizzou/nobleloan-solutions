package com.patrick.fintech.loan_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoStoreApiResponseFilterTest {

    @Test
    void apiResponseCannotBeMarkedCacheableByDownstreamHandler() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/borrowers");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain downstream = (servletRequest, servletResponse) -> {
            HttpServletResponse http = (HttpServletResponse) servletResponse;
            http.setHeader("Cache-Control", "public, max-age=86400");
            http.setHeader("Pragma", "cache");
            http.setHeader("Expires", "86400");
        };

        new NoStoreApiResponseFilter().doFilter(request, response, downstream);

        assertTrue(response.getHeader("Cache-Control").contains("no-store"));
        assertTrue(response.getHeader("Cache-Control").contains("private"));
        assertEquals("no-cache", response.getHeader("Pragma"));
        assertEquals("0", response.getHeader("Expires"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
    }

    @Test
    void publicWebsiteResponseDoesNotGetApiCacheHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/about");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setHeader("Cache-Control", "public, max-age=3600");

        new NoStoreApiResponseFilter().doFilter(request, response, (req, res) -> { });

        assertEquals("public, max-age=3600", response.getHeader("Cache-Control"));
    }
}
