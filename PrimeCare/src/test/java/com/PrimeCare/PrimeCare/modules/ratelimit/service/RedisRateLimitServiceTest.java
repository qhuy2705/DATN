package com.PrimeCare.PrimeCare.modules.ratelimit.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class RedisRateLimitServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void slidingWindowAllowsWithinLimit() {
        doReturn(List.of(1L, 5L, 4L, 0L))
                .when(stringRedisTemplate)
                .execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any());

        RedisRateLimitService service = new RedisRateLimitService(
                stringRedisTemplate,
                Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC)
        );

        RateLimitDecision decision = service.consume("LOGIN", "198.51.100.10", 5, 60, 6);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.limit()).isEqualTo(5);
        assertThat(decision.remaining()).isEqualTo(4);
        assertThat(decision.retryAfterSeconds()).isZero();
    }

    @Test
    void slidingWindowBlocksAboveLimit() {
        doReturn(List.of(0L, 5L, 0L, 12L))
                .when(stringRedisTemplate)
                .execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any());

        RedisRateLimitService service = new RedisRateLimitService(
                stringRedisTemplate,
                Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC)
        );

        RateLimitDecision decision = service.consume("LOGIN", "198.51.100.10", 5, 60, 6);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.limit()).isEqualTo(5);
        assertThat(decision.remaining()).isZero();
        assertThat(decision.retryAfterSeconds()).isEqualTo(12);
    }

    @Test
    void slidingWindowScriptUsesRedisHashAndNoFixedWindowIncrement() {
        assertThat(RedisRateLimitService.SLIDING_WINDOW_COUNTER_LUA)
                .contains("HGETALL", "HDEL", "HINCRBY", "EXPIRE")
                .doesNotContain("INCR', key")
                .doesNotContain("opsForValue");
    }
}
