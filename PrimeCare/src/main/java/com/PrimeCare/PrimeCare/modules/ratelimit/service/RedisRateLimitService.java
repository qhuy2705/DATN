package com.PrimeCare.PrimeCare.modules.ratelimit.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

@Service
public class RedisRateLimitService {

    static final String SLIDING_WINDOW_COUNTER_LUA = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local windowMillis = tonumber(ARGV[2]) * 1000
            local bucketMillis = tonumber(ARGV[3]) * 1000
            local nowMillis = tonumber(ARGV[4])
            local ttlSeconds = tonumber(ARGV[5])

            local bucketStart = nowMillis - (nowMillis % bucketMillis)
            local windowStart = nowMillis - windowMillis
            local entries = redis.call('HGETALL', key)
            local total = 0
            local oldestBucket = nil

            for i = 1, #entries, 2 do
                local field = entries[i]
                local bucket = tonumber(field)
                local count = tonumber(entries[i + 1]) or 0

                if bucket == nil or bucket < windowStart then
                    redis.call('HDEL', key, field)
                else
                    total = total + count
                    if oldestBucket == nil or bucket < oldestBucket then
                        oldestBucket = bucket
                    end
                end
            end

            if total >= limit then
                local retryAfterMillis = bucketMillis
                if oldestBucket ~= nil then
                    retryAfterMillis = (oldestBucket + windowMillis) - nowMillis
                end
                if retryAfterMillis < 1000 then
                    retryAfterMillis = 1000
                end
                redis.call('EXPIRE', key, ttlSeconds)
                return {0, limit, 0, math.ceil(retryAfterMillis / 1000)}
            end

            local newTotal = total + 1
            redis.call('HINCRBY', key, tostring(bucketStart), 1)
            redis.call('EXPIRE', key, ttlSeconds)

            local remaining = limit - newTotal
            if remaining < 0 then
                remaining = 0
            end

            return {1, limit, remaining, 0}
            """;

    private static final DefaultRedisScript<List> SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>();

    static {
        SLIDING_WINDOW_SCRIPT.setScriptText(SLIDING_WINDOW_COUNTER_LUA);
        SLIDING_WINDOW_SCRIPT.setResultType(List.class);
    }

    private final StringRedisTemplate stringRedisTemplate;
    private final Clock clock;

    @Autowired
    public RedisRateLimitService(StringRedisTemplate stringRedisTemplate) {
        this(stringRedisTemplate, Clock.systemUTC());
    }

    RedisRateLimitService(StringRedisTemplate stringRedisTemplate, Clock clock) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.clock = clock;
    }

    public RateLimitDecision consume(
            String eventType,
            String clientIp,
            long limit,
            long windowSeconds,
            long bucketSeconds
    ) {
        validate(limit, windowSeconds, bucketSeconds);

        String key = "rl:swc:" + eventType + ":" + clientIp;
        long nowMillis = clock.millis();
        long ttlSeconds = windowSeconds + bucketSeconds + 60;

        List<?> result = stringRedisTemplate.execute(
                SLIDING_WINDOW_SCRIPT,
                List.of(key),
                String.valueOf(limit),
                String.valueOf(windowSeconds),
                String.valueOf(bucketSeconds),
                String.valueOf(nowMillis),
                String.valueOf(ttlSeconds)
        );

        return toDecision(result, limit);
    }

    private void validate(long limit, long windowSeconds, long bucketSeconds) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        if (windowSeconds < 1) {
            throw new IllegalArgumentException("windowSeconds must be >= 1");
        }
        if (bucketSeconds < 1 || bucketSeconds > windowSeconds) {
            throw new IllegalArgumentException("bucketSeconds must be between 1 and windowSeconds");
        }
    }

    private RateLimitDecision toDecision(List<?> result, long fallbackLimit) {
        if (result == null || result.size() < 4) {
            return new RateLimitDecision(false, fallbackLimit, 0, 1);
        }
        return new RateLimitDecision(
                asLong(result.get(0)) == 1L,
                asLong(result.get(1)),
                asLong(result.get(2)),
                asLong(result.get(3))
        );
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
