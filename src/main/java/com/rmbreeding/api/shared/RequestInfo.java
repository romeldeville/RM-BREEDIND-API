package com.rmbreeding.api.shared;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Best-effort request metadata for audit and privacy records. Never fails a business action
 * because the request context is unavailable.
 */
public final class RequestInfo {

    private RequestInfo() {
    }

    public static String clientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        String candidate = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",")[0].trim();
        return normalizeIp(candidate);
    }

    public static String userAgent() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String agent = request.getHeader("User-Agent");
        return agent == null || agent.isBlank() ? null : agent;
    }

    private static HttpServletRequest currentRequest() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                return attributes.getRequest();
            }
        } catch (IllegalStateException ignored) {
            // No request bound to the current thread (scheduled job, async consumer, unit test).
        }
        return null;
    }

    static String normalizeIp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.startsWith("[") && candidate.endsWith("]")) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }
        int zone = candidate.indexOf('%');
        if (zone >= 0) {
            candidate = candidate.substring(0, zone);
        }
        if (!candidate.matches("[0-9a-fA-F:.]+") || candidate.indexOf(':') < 0 && !candidate.contains(".")) {
            return null;
        }
        return candidate;
    }
}
