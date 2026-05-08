package com.PrimeCare.PrimeCare.modules.auth.service;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class RefreshCookieService {

    @Value("${app.security.refreshCookie.name:rt}")
    private String cookieName;

    @Value("${app.security.refreshCookie.path:/api/auth/refresh}")
    private String cookiePath;

    @Value("${app.security.refreshCookie.legacyPath:/api/auth}")
    private String legacyCookiePath;

    @Value("${app.security.refreshCookie.sameSite:Strict}")
    private String sameSite;

    @Value("${app.security.refreshCookie.secure:false}")
    private boolean secure;

    @Value("${app.security.refreshCookie.domain:}")
    private String domain;

    public String getCookieName() {
        return cookieName;
    }

    public void setRefreshTokenCookie(HttpServletResponse res, String refreshToken, long maxAgeSeconds) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(cookieName, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .path(cookiePath)
                .sameSite(sameSite)
                .maxAge(maxAgeSeconds);

        if (domain != null && !domain.isBlank()) b.domain(domain);

        res.addHeader(HttpHeaders.SET_COOKIE, b.build().toString());
    }

    public void clearRefreshTokenCookie(HttpServletResponse res) {
        clearRefreshTokenCookie(res, cookiePath);
        if (legacyCookiePath != null && !legacyCookiePath.isBlank() && !legacyCookiePath.equals(cookiePath)) {
            clearRefreshTokenCookie(res, legacyCookiePath);
        }
    }

    private void clearRefreshTokenCookie(HttpServletResponse res, String path) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(secure)
                .path(path)
                .sameSite(sameSite)
                .maxAge(0);

        if (domain != null && !domain.isBlank()) b.domain(domain);

        res.addHeader(HttpHeaders.SET_COOKIE, b.build().toString());
    }
}
