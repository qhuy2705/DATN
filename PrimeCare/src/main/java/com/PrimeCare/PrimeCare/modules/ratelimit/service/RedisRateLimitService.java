package com.PrimeCare.PrimeCare.modules.ratelimit.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisRateLimitService {
    private final StringRedisTemplate stringRedisTemplate;

    public boolean tryConsume(String key, long limit, long windowSeconds) {
        Long current = stringRedisTemplate.opsForValue().increment(key);
        if (current != null && current == 1L) {
            stringRedisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }
        return current != null && current <= limit;
    }
}
