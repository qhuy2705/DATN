package com.PrimeCare.PrimeCare.modules.ratelimit.web;

import com.PrimeCare.PrimeCare.modules.ratelimit.config.RateLimitRule;
import com.PrimeCare.PrimeCare.modules.ratelimit.service.RateLimitDecision;
import com.PrimeCare.PrimeCare.modules.ratelimit.service.RateLimitRuleProvider;
import com.PrimeCare.PrimeCare.modules.ratelimit.service.RedisRateLimitService;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock
    private RedisRateLimitService redisRateLimitService;

    @Mock
    private RateLimitRuleProvider rateLimitRuleProvider;

    private RateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new RateLimitInterceptor(redisRateLimitService, rateLimitRuleProvider);
    }

    @Test
    void preHandleUsesForwardedForWhenRemoteAddressIsTrustedProxy() {
        ReflectionTestUtils.setField(interceptor, "trustedProxies", "10.0.0.0/24,127.0.0.1");
        RateLimitRule rule = rule("/api/public/assistant/ask", "PUBLIC_ASSISTANT_ASK", 10, 60, 6);
        when(rateLimitRuleProvider.findMatching("POST", "/api/public/assistant/ask")).thenReturn(Optional.of(rule));
        when(redisRateLimitService.consume(anyString(), anyString(), eq(10L), eq(60L), eq(6L)))
                .thenReturn(new RateLimitDecision(true, 10, 9, 0));

        MockHttpServletRequest request = request("POST", "/api/public/assistant/ask", "10.0.0.12");
        request.addHeader("X-Forwarded-For", "203.0.113.44, 10.0.0.12");

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        ArgumentCaptor<String> clientIp = ArgumentCaptor.forClass(String.class);
        verify(redisRateLimitService).consume(eq("PUBLIC_ASSISTANT_ASK"), clientIp.capture(), eq(10L), eq(60L), eq(6L));
        assertThat(clientIp.getValue()).isEqualTo("203.0.113.44");
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("10");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("9");
    }

    @Test
    void preHandleIgnoresForwardedForWhenRemoteAddressIsNotTrusted() {
        ReflectionTestUtils.setField(interceptor, "trustedProxies", "10.0.0.0/24");
        RateLimitRule rule = rule("/api/auth/login", "LOGIN", 5, 300, 30);
        when(rateLimitRuleProvider.findMatching("POST", "/api/auth/login")).thenReturn(Optional.of(rule));
        when(redisRateLimitService.consume(anyString(), anyString(), eq(5L), eq(300L), eq(30L)))
                .thenReturn(new RateLimitDecision(true, 5, 4, 0));

        MockHttpServletRequest request = request("POST", "/api/auth/login", "198.51.100.10");
        request.addHeader("X-Forwarded-For", "203.0.113.44");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();

        ArgumentCaptor<String> clientIp = ArgumentCaptor.forClass(String.class);
        verify(redisRateLimitService).consume(eq("LOGIN"), clientIp.capture(), eq(5L), eq(300L), eq(30L));
        assertThat(clientIp.getValue()).isEqualTo("198.51.100.10");
    }

    @Test
    void preHandleStripsPortFromTrustedProxyRemoteAddress() {
        ReflectionTestUtils.setField(interceptor, "trustedProxies", "10.0.0.12");
        RateLimitRule rule = rule("/api/auth/login", "LOGIN", 5, 300, 30);
        when(rateLimitRuleProvider.findMatching("POST", "/api/auth/login")).thenReturn(Optional.of(rule));
        when(redisRateLimitService.consume(anyString(), anyString(), eq(5L), eq(300L), eq(30L)))
                .thenReturn(new RateLimitDecision(true, 5, 4, 0));

        MockHttpServletRequest request = request("POST", "/api/auth/login", "10.0.0.12:54321");
        request.addHeader("X-Real-IP", "203.0.113.45");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();

        ArgumentCaptor<String> clientIp = ArgumentCaptor.forClass(String.class);
        verify(redisRateLimitService).consume(eq("LOGIN"), clientIp.capture(), eq(5L), eq(300L), eq(30L));
        assertThat(clientIp.getValue()).isEqualTo("203.0.113.45");
    }

    @Test
    void preHandleThrowsRateLimitedWhenRedisLimitIsExceeded() {
        ReflectionTestUtils.setField(interceptor, "trustedProxies", "");
        RateLimitRule rule = rule("/api/auth/login", "LOGIN", 5, 300, 30);
        when(rateLimitRuleProvider.findMatching("POST", "/api/auth/login")).thenReturn(Optional.of(rule));
        when(redisRateLimitService.consume("LOGIN", "198.51.100.10", 5, 300, 30))
                .thenReturn(new RateLimitDecision(false, 5, 0, 42));

        MockHttpServletRequest request = request("POST", "/api/auth/login", "198.51.100.10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED)
                );
        assertThat(response.getHeader("Retry-After")).isEqualTo("42");
    }

    @Test
    void preHandleAppliesCheckInPolicyToReceptionQrCheckIn() {
        ReflectionTestUtils.setField(interceptor, "trustedProxies", "");
        RateLimitRule rule = rule("/api/reception/appointments/check-in/qr", "CHECKIN", 30, 60, 6);
        when(rateLimitRuleProvider.findMatching("POST", "/api/reception/appointments/check-in/qr")).thenReturn(Optional.of(rule));
        when(redisRateLimitService.consume(anyString(), anyString(), eq(30L), eq(60L), eq(6L)))
                .thenReturn(new RateLimitDecision(true, 30, 29, 0));

        MockHttpServletRequest request = request("POST", "/api/reception/appointments/check-in/qr", "198.51.100.10");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();

        ArgumentCaptor<String> clientIp = ArgumentCaptor.forClass(String.class);
        verify(redisRateLimitService).consume(eq("CHECKIN"), clientIp.capture(), eq(30L), eq(60L), eq(6L));
        assertThat(clientIp.getValue()).isEqualTo("198.51.100.10");
    }

    @Test
    void preHandleIgnoresRequestWhenNoEnabledRuleMatches() {
        ReflectionTestUtils.setField(interceptor, "trustedProxies", "");
        when(rateLimitRuleProvider.findMatching("POST", "/api/auth/login")).thenReturn(Optional.empty());

        MockHttpServletRequest request = request("POST", "/api/auth/login", "198.51.100.10");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();

        verifyNoInteractions(redisRateLimitService);
    }

    private MockHttpServletRequest request(String method, String path, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remoteAddr);
        request.setRequestURI(path);
        return request;
    }

    private RateLimitRule rule(String pathPattern, String eventType, long limit, long windowSeconds, long bucketSeconds) {
        return new RateLimitRule(1L, pathPattern, "POST", eventType, limit, windowSeconds, bucketSeconds, 10);
    }
}
