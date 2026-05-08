package com.PrimeCare.PrimeCare.modules.ratelimit.web;

import com.PrimeCare.PrimeCare.modules.ratelimit.config.RateLimitRule;
import com.PrimeCare.PrimeCare.modules.ratelimit.service.RedisRateLimitService;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisRateLimitService redisRateLimitService;

    @Value("${app.rate-limit.trusted-proxies:}")
    private String trustedProxies;

    private final List<RateLimitRule> rules = List.of(
            new RateLimitRule("/api/auth/login", "POST", 5, 300, "LOGIN"),
            new RateLimitRule("/api/auth/refresh", "POST", 10, 60, "REFRESH"),
            new RateLimitRule("/api/auth/patient/register", "POST", 10, 60, "PATIENT_REGISTER"),
            new RateLimitRule("/api/auth/password/forgot", "POST", 10, 60, "FORGOT_PASSWORD"),
            new RateLimitRule("/api/public/appointments", "POST", 20, 60, "CREATE_APPOINTMENT"),
            new RateLimitRule("/api/public/contact", "POST", 10, 60, "PUBLIC_CONTACT"),
            new RateLimitRule("/api/public/assistant/ask", "POST", 10, 60, "PUBLIC_ASSISTANT_ASK"),
            new RateLimitRule("/api/public/lookup/appointments/request-otp", "POST", 10, 60, "PUBLIC_LOOKUP_APPOINTMENT_REQUEST_OTP"),
            new RateLimitRule("/api/public/lookup/appointments/verify-otp", "POST", 10, 60, "PUBLIC_LOOKUP_APPOINTMENT_VERIFY_OTP"),
            new RateLimitRule("/api/public/lookup/results/request-otp", "POST", 10, 60, "PUBLIC_LOOKUP_RESULT_REQUEST_OTP"),
            new RateLimitRule("/api/public/lookup/results/verify-otp", "POST", 10, 60, "PUBLIC_LOOKUP_RESULT_VERIFY_OTP"),
            new RateLimitRule("/api/admin/appointments/check-in", "POST", 30, 60, "CHECKIN"),
            new RateLimitRule("/api/cashier/service-orders/", "POST", 20, 60, "INVOICE_CREATE")
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        String clientIp = resolveClientIp(request);

        for (RateLimitRule rule : rules) {
            if (path.startsWith(rule.pathPrefix()) && method.equalsIgnoreCase(rule.method())) {
                String key = "rl:" + rule.eventType() + ":" + clientIp;
                if (!redisRateLimitService.tryConsume(key, rule.limit(), rule.windowSeconds())) {
                    throw new ApiException(ErrorCode.RATE_LIMITED);
                }
                break;
            }
        }

        return true;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = normalizeIpCandidate(request.getRemoteAddr());
        if (remoteAddr == null) {
            return "unknown";
        }

        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        String forwardedFor = firstForwardedForIp(request.getHeader("X-Forwarded-For"));
        if (forwardedFor != null) {
            return forwardedFor;
        }

        String realIp = validatedIpCandidate(request.getHeader("X-Real-IP"));
        return realIp != null ? realIp : remoteAddr;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        InetAddress remoteIp = parseIp(remoteAddr);
        if (remoteIp == null || trustedProxies == null || trustedProxies.isBlank()) {
            return false;
        }

        for (String token : trustedProxies.split(",")) {
            String trustedProxy = token.trim();
            if (trustedProxy.isBlank()) {
                continue;
            }
            if (trustedProxy.contains("/")) {
                if (matchesCidr(remoteIp, trustedProxy)) {
                    return true;
                }
            } else {
                InetAddress trustedIp = parseIp(trustedProxy);
                if (remoteIp.equals(trustedIp)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String firstForwardedForIp(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return null;
        }
        return validatedIpCandidate(forwardedFor.split(",")[0]);
    }

    private boolean matchesCidr(InetAddress address, String cidr) {
        String[] parts = cidr.split("/", 2);
        if (parts.length != 2) {
            return false;
        }

        InetAddress network = parseIp(parts[0]);
        if (network == null || address.getAddress().length != network.getAddress().length) {
            return false;
        }

        int prefixLength;
        try {
            prefixLength = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException ex) {
            return false;
        }

        byte[] addressBytes = address.getAddress();
        byte[] networkBytes = network.getAddress();
        int maxPrefixLength = addressBytes.length * 8;
        if (prefixLength < 0 || prefixLength > maxPrefixLength) {
            return false;
        }

        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;

        for (int i = 0; i < fullBytes; i++) {
            if (addressBytes[i] != networkBytes[i]) {
                return false;
            }
        }

        if (remainingBits == 0) {
            return true;
        }

        int mask = 0xFF << (8 - remainingBits);
        return (addressBytes[fullBytes] & mask) == (networkBytes[fullBytes] & mask);
    }

    private InetAddress parseIp(String value) {
        String candidate = normalizeIpCandidate(value);
        if (candidate == null || !looksLikeIp(candidate)) {
            return null;
        }

        try {
            return InetAddress.getByName(candidate);
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    private String validatedIpCandidate(String value) {
        String candidate = normalizeIpCandidate(value);
        return parseIp(candidate) != null ? candidate : null;
    }

    private String normalizeIpCandidate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String candidate = value.trim();
        if ("unknown".equalsIgnoreCase(candidate)) {
            return null;
        }

        if (candidate.startsWith("[") && candidate.contains("]")) {
            return candidate.substring(1, candidate.indexOf(']')).trim();
        }

        int colon = candidate.indexOf(':');
        if (candidate.contains(".") && colon > 0 && colon == candidate.lastIndexOf(':')) {
            String port = candidate.substring(colon + 1);
            if (port.chars().allMatch(Character::isDigit)) {
                return candidate.substring(0, colon).trim();
            }
        }

        return candidate;
    }

    private boolean looksLikeIp(String value) {
        return (value.contains(".") || value.contains(":"))
                && value.chars().allMatch(ch ->
                Character.digit(ch, 16) >= 0 || ch == '.' || ch == ':' || ch == '%'
        );
    }
}
