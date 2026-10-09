package com.patrick.fintech.loan_backend.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ApiNoStoreFilterTest {

    @Test
    void apiResponsesAreMarkedNoStore() throws Exception {
        ApiNoStoreFilter filter = new ApiNoStoreFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/loans");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals("no-store, no-cache, must-revalidate, max-age=0",
                response.getHeader("Cache-Control"));
        assertEquals("no-cache", response.getHeader("Pragma"));
        assertEquals("0", response.getHeader("Expires"));
        assertEquals("Cookie, Authorization", response.getHeader("Vary"));
    }
}
