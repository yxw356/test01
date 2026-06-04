package com.yuki.enterprise_private_rag_qa.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 审计辅助工具
 */
public final class AuditSupport {

    private AuditSupport() {
    }

    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    public static ClientInfo clientInfo(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new ClientInfo(null, "Unknown", "Unknown", "Unknown");
        }

        String lower = userAgent.toLowerCase();
        String deviceType = parseDeviceType(lower);
        String browser = parseBrowser(lower);
        String os = parseOs(lower);
        return new ClientInfo(userAgent, deviceType, browser, os);
    }

    private static String parseDeviceType(String lowerUserAgent) {
        if (lowerUserAgent.contains("ipad") || lowerUserAgent.contains("tablet")) {
            return "Tablet";
        }
        if (lowerUserAgent.contains("mobile")
                || lowerUserAgent.contains("iphone")
                || lowerUserAgent.contains("android")) {
            return "Mobile";
        }
        return "Desktop";
    }

    private static String parseBrowser(String lowerUserAgent) {
        if (lowerUserAgent.contains("edg/") || lowerUserAgent.contains("edge/")) {
            return "Edge";
        }
        if (lowerUserAgent.contains("firefox/")) {
            return "Firefox";
        }
        if (lowerUserAgent.contains("chrome/") || lowerUserAgent.contains("crios/")) {
            return "Chrome";
        }
        if (lowerUserAgent.contains("safari/")) {
            return "Safari";
        }
        return "Unknown";
    }

    private static String parseOs(String lowerUserAgent) {
        if (lowerUserAgent.contains("iphone") || lowerUserAgent.contains("ipad")) {
            return "iOS";
        }
        if (lowerUserAgent.contains("android")) {
            return "Android";
        }
        if (lowerUserAgent.contains("windows")) {
            return "Windows";
        }
        if (lowerUserAgent.contains("mac os x") || lowerUserAgent.contains("macintosh")) {
            return "macOS";
        }
        if (lowerUserAgent.contains("linux")) {
            return "Linux";
        }
        return "Unknown";
    }

    public record ClientInfo(String userAgent, String deviceType, String browser, String os) {
    }
}
