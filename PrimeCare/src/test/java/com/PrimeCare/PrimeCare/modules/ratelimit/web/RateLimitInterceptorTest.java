package com.PrimeCare.PrimeCare.modules.ratelimit.web;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock
    private RedisRateLimitService redisRateLimitService;

    private RateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new RateLimitInterceptor(redisRateLimitService);
    }

    @Test
    void preHandleUsesForwardedForWhenRemoteAddressIsTrustedProxy() {
        ReflectionTestUtils.setField(interceptor, "trustedProxies", "10.0.0.0/24,127.0.0.1");
        when(redisRateLimitService.tryConsume(anyString(), anyLong(), anyLong())).thenReturn(true);

        MockHttpServletRequest request = request("POST", "/api/public/assistant/ask", "10.0.0.12");
        request.addHeader("X-Forwarded-For", "203.0.113.44, 10.0.0.12");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(redisRateLimitService).tryConsume(key.capture(), anyLong(), anyLong());
        assertThat(key.getValue()).isEqualTo("rl:PUBLIC_ASSISTANT_ASK:203.0.113.44");
    }

    @Test
    void preHandleIgnoresForwardedForWhenRemoteAddressIsNotTrusted() {
        ReflectionTestUtils.setField(interceptor, "trustedProxies", "10.0.0.0/24");
        when(redisRateLimitService.tryConsume(anyString(), anyLong(), anyLong())).thenReturn(true);

        MockHttpServletRequest request = request("POST", "/api/auth/login", "198.51.100.10");
        request.addHeader("X-Forwarded-For", "203.0.113.44");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(redisRateLimitService).tryConsume(key.capture(), anyLong(), anyLong());
        assertThat(key.getValue()).isEqualTo("rl:LOGIN:198.51.100.10");
    }

    @Test
    void preHandleStripsPortFromTrustedProxyRemoteAddress() {
        ReflectionTestUtils.setField(interceptor, "trustedProxies", "10.0.0.12");
        when(redisRateLimitService.tryConsume(anyString(), anyLong(), anyLong())).thenReturn(true);

        MockHttpServletRequest request = request("POST", "/api/auth/login", "10.0.0.12:54321");
        request.addHeader("X-Real-IP", "203.0.113.45");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(redisRateLimitService).tryConsume(key.capture(), anyLong(), anyLong());
        assertThat(key.getValue()).isEqualTo("rl:LOGIN:203.0.113.45");
    }

    @Test
    void preHandleThrowsRateLimitedWhenRedisLimitIsExceeded() {
        ReflectionTestUtils.setField(interceptor, "trustedProxies", "");
        when(redisRateLimitService.tryConsume(anyString(), anyLong(), anyLong())).thenReturn(false);

        MockHttpServletRequest request = request("POST", "/api/auth/login", "198.51.100.10");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED)
                );
    }

    private MockHttpServletRequest request(String method, String path, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remoteAddr);
        request.setRequestURI(path);
        return request;
    }
}
