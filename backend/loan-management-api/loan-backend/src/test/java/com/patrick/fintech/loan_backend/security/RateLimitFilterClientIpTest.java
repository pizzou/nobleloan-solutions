package com.patrick.fintech.loan_backend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class RateLimitFilterClientIpTest {

    @Test
    void usesNginxRealIpWhenRequestArrivesFromPrivateDockerProxy() {
        RateLimitFilter filter = new RateLimitFilter(mock(DistributedRateLimitService.class));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.19.0.2");
        request.addHeader("X-Real-IP", "203.0.113.8");

        assertEquals("203.0.113.8", ReflectionTestUtils.invokeMethod(filter, "clientIp", request));
    }

    @Test
    void ignoresRealIpHeaderWhenDirectPeerIsNotPrivateProxy() {
        RateLimitFilter filter = new RateLimitFilter(mock(DistributedRateLimitService.class));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Real-IP", "198.51.100.4");

        assertEquals("203.0.113.10", ReflectionTestUtils.invokeMethod(filter, "clientIp", request));
    }

    @Test
    void fallsBackToProxyAddressWhenRealIpHeaderIsMalformed() {
        RateLimitFilter filter = new RateLimitFilter(mock(DistributedRateLimitService.class));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.19.0.2");
        request.addHeader("X-Real-IP", "203.0.113.8, 10.0.0.1");

        assertEquals("172.19.0.2", ReflectionTestUtils.invokeMethod(filter, "clientIp", request));
    }
}
