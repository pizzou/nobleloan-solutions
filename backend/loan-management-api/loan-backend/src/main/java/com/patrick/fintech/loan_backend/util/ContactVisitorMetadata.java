package com.patrick.fintech.loan_backend.util;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.util.Locale;
import java.util.Map;

/**
 * Metadata collected for a public contact message. Browser characteristics
 * are best-effort user-agent hints, not verified computer identifiers.
 * Location is approximate and opt-in only; there is no hidden GPS tracking.
 */
public record ContactVisitorMetadata(
        String ipAddress,
        String deviceType,
        String operatingSystem,
        String browserName,
        String timeZone,
        String userAgent,
        String approximateLocation,
        boolean locationConsent) {

    public static ContactVisitorMetadata from(HttpServletRequest request, Map<String, Object> body) {
        String agent = clean(request.getHeader("User-Agent"), 512);
        String ua = agent == null ? "" : agent.toLowerCase(Locale.ROOT);

        String device;
        if (ua.contains("ipad") || ua.contains("tablet")) {
            device = "Tablet";
        } else if (ua.contains("mobile") || ua.contains("iphone") || ua.contains("android")) {
            device = "Mobile";
        } else if (ua.isEmpty()) {
            device = "Unknown";
        } else {
            device = "Desktop or laptop";
        }

        // Browser hints may expose a model, but not the user's private device hostname.
        String model = clean(request.getHeader("Sec-CH-UA-Model"), 30);
        if (model != null) {
            model = model.replace("\"", "").replaceAll("[^A-Za-z0-9 ._/-]", "").trim();
            if (!model.isBlank() && !model.equalsIgnoreCase("Unknown"))
                device = clean(device + " (" + model + ")", 40);
        }

        String os;
        if (ua.contains("windows"))
            os = "Windows";
        else if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios"))
            os = "iOS / iPadOS";
        else if (ua.contains("android"))
            os = "Android";
        else if (ua.contains("mac os") || ua.contains("macintosh"))
            os = "macOS";
        else if (ua.contains("linux"))
            os = "Linux";
        else
            os = "Unknown";

        String browser;
        if (ua.contains("edg/") || ua.contains("edga/"))
            browser = "Microsoft Edge";
        else if (ua.contains("firefox") || ua.contains("fxios"))
            browser = "Firefox";
        else if (ua.contains("chrome") || ua.contains("crios"))
            browser = "Chrome";
        else if (ua.contains("safari"))
            browser = "Safari";
        else
            browser = "Unknown";

        String zone = clean(body.get("timeZone"), 100);
        boolean permission = Boolean.TRUE.equals(body.get("shareLocation"));
        String location = permission ? approximateCoordinates(body.get("latitude"), body.get("longitude")) : null;

        return new ContactVisitorMetadata(
                clientIp(request), device, os, browser, zone, agent, location,
                permission && location != null);
    }

    /**
     * Trust X-Real-IP only from a local/private proxy hop. Nginx MUST replace,
     * not pass through, the client-supplied X-Real-IP header. Do not expose the
     * backend service directly on the public Internet.
     */
    public static String clientIp(HttpServletRequest request) {
        String remote = clean(request.getRemoteAddr(), 45);
        String header = clean(request.getHeader("X-Real-IP"), 45);
        if (header != null && header.matches("[0-9a-fA-F.:]{3,45}")
                && (header.contains(".") || header.contains(":"))
                && isPrivateProxy(remote)) {
            try {
                InetAddress.getByName(header); // only numeric address forms pass the filter
                return header;
            } catch (Exception ignored) {
                // Invalid forwarded address: use the actual socket peer.
            }
        }
        return remote;
    }

    private static boolean isPrivateProxy(String ip) {
        if (ip == null)
            return false;
        try {
            InetAddress a = InetAddress.getByName(ip);
            return a.isLoopbackAddress() || a.isSiteLocalAddress();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String approximateCoordinates(Object latitude, Object longitude) {
        try {
            double lat = Double.parseDouble(String.valueOf(latitude));
            double lon = Double.parseDouble(String.valueOf(longitude));
            if (!Double.isFinite(lat) || !Double.isFinite(lon)
                    || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                return null;
            }
            String a = BigDecimal.valueOf(lat).setScale(2, RoundingMode.HALF_UP).toPlainString();
            String b = BigDecimal.valueOf(lon).setScale(2, RoundingMode.HALF_UP).toPlainString();
            return "Approximate latitude " + a + ", longitude " + b;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String clean(Object value, int max) {
        if (value == null)
            return null;
        String text = value.toString().replaceAll("[\\p{Cntrl}]", "").trim();
        return text.isBlank() ? null : text.substring(0, Math.min(text.length(), max));
    }
}
