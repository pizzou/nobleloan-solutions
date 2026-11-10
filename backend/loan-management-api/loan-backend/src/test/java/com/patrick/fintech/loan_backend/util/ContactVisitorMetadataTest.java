package com.patrick.fintech.loan_backend.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContactVisitorMetadataTest {

    @Test
    void recognisesDeviceAndOnlyUsesForwardedIpFromInternalProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.19.0.6");
        request.addHeader("X-Real-IP", "41.186.10.20");
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0");
        ContactVisitorMetadata data = ContactVisitorMetadata.from(request,
                Map.of("timeZone", "Africa/Kigali"));
        assertEquals("41.186.10.20", data.ipAddress());
        assertEquals("Windows", data.operatingSystem());
        assertEquals("Desktop or laptop", data.deviceType());
        assertEquals("Chrome", data.browserName());
        assertEquals("Africa/Kigali", data.timeZone());
        assertNull(data.approximateLocation());
        assertFalse(data.locationConsent());
    }

    @Test
    void ignoresUntrustedForwardedIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("41.186.10.20");
        request.addHeader("X-Real-IP", "127.0.0.1");
        assertEquals("41.186.10.20", ContactVisitorMetadata.from(request, Map.of()).ipAddress());
    }

    @Test
    void onlySharesRoundedLocationWithConsent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ContactVisitorMetadata noConsent = ContactVisitorMetadata.from(request,
                Map.of("latitude", -1.9504321, "longitude", 30.0609771));
        assertNull(noConsent.approximateLocation());
        assertFalse(noConsent.locationConsent());
        ContactVisitorMetadata consent = ContactVisitorMetadata.from(request,
                Map.of("shareLocation", true, "latitude", -1.9504321, "longitude", 30.0609771));
        assertTrue(consent.locationConsent());
        assertEquals("Approximate latitude -1.95, longitude 30.06", consent.approximateLocation());
    }
}
