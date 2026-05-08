package com.PrimeCare.PrimeCare.modules.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshCookieServiceTest {

    @Test
    void prodRefreshCookieUsesSecureHttpOnlyStrictSameSiteAndRefreshPath() {
        RefreshCookieService service = new RefreshCookieService();
        ReflectionTestUtils.setField(service, "cookieName", "rt");
        ReflectionTestUtils.setField(service, "cookiePath", "/api/auth/refresh");
        ReflectionTestUtils.setField(service, "legacyCookiePath", "/api/auth");
        ReflectionTestUtils.setField(service, "sameSite", "Strict");
        ReflectionTestUtils.setField(service, "secure", true);
        ReflectionTestUtils.setField(service, "domain", "");
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.setRefreshTokenCookie(response, "refresh-token", 120);

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("rt=refresh-token");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("Secure");
        assertThat(setCookie).contains("Path=/api/auth/refresh");
        assertThat(setCookie).contains("SameSite=Strict");
        assertThat(setCookie).contains("Max-Age=120");
    }

    @Test
    void clearRefreshCookieAlsoClearsLegacyAuthPath() {
        RefreshCookieService service = new RefreshCookieService();
        ReflectionTestUtils.setField(service, "cookieName", "rt");
        ReflectionTestUtils.setField(service, "cookiePath", "/api/auth/refresh");
        ReflectionTestUtils.setField(service, "legacyCookiePath", "/api/auth");
        ReflectionTestUtils.setField(service, "sameSite", "Strict");
        ReflectionTestUtils.setField(service, "secure", true);
        ReflectionTestUtils.setField(service, "domain", "");
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.clearRefreshTokenCookie(response);

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(cookie -> assertThat(cookie).contains("Path=/api/auth/refresh"))
                .anySatisfy(cookie -> assertThat(cookie).contains("Path=/api/auth"));
    }
}
